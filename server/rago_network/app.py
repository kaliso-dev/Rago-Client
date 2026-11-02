"""The HTTP API (JSON over HTTPS, behind a reverse proxy that handles TLS)."""

import hashlib
import json
import re
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from . import rewards
from .limits import Limiter
from .loadout import Whitelist
from .mojang import SessionServer
from .rewards import Catalogue, day_of, next_reset
from .store import Store

NAME = re.compile(r"^[A-Za-z0-9_]{1,16}$")
HEX32 = re.compile(r"^[0-9a-f]{32}$")
ROLES = ("user", "partner", "staff", "creator")
MAX_BODY = 16 * 1024
MAX_LOOKUP = 100
EMOTE = re.compile(r"^[a-z_]{1,24}$")
# an emote plays until its player moves; one not stopped by then is over after this many seconds
EMOTE_FOR = 600
# a purchase's key, chosen by the client, so that sending it twice buys once
KEY = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
ITEM = re.compile(r"^[a-z]{1,16}:[a-z0-9_]{1,32}$")
# the loadout fields whose values are cosmetics a player must own, and their catalogue type
OWNED_FIELDS = {"cape": "cape", "hat": "hat", "wings": "wings", "shield": "shield", "name_color": "name",
                "name_icon": "icon"}


def join_id(challenge):
    """The server id the client gives Mojang for a sign-in challenge: the SHA-1 of "rago-network:" and
    the challenge, in Minecraft's own form (two's complement hex). A Minecraft server asks for the hash
    of its key and a secret instead, so this sign-in can never be used to join a Minecraft server."""
    digest = hashlib.sha1(("rago-network:" + challenge).encode("utf-8")).digest()
    return format(int.from_bytes(digest, "big", signed=True), "x")


def dashed(uuid32):
    return f"{uuid32[:8]}-{uuid32[8:12]}-{uuid32[12:16]}-{uuid32[16:20]}-{uuid32[20:]}"


def undashed(value):
    """A uuid as 32 lowercase hex digits, or None."""
    if not isinstance(value, str):
        return None
    v = value.replace("-", "").lower()
    return v if HEX32.match(v) else None


class Network:
    """Everything the handlers need; no HTTP in here, so it can be tested directly."""

    def __init__(self, config, store=None, session_server=None, clock=time.time, rng=None):
        self.config = config
        self.clock = clock
        self.store = store or Store(config.get("database", "rago-network.db"))
        self.mojang = session_server or SessionServer(config.get("session_server", "https://sessionserver.mojang.com"))
        self.whitelist = Whitelist.load(config["cosmetics"]) if isinstance(config.get("cosmetics"), str) \
            else Whitelist(config["cosmetics"])
        catalogue = config.get("catalogue", {"items": {}})
        self.catalogue = Catalogue.load(catalogue) if isinstance(catalogue, str) else Catalogue(catalogue)
        self.rng = rng or secrets.SystemRandom()
        # players who sign in before this time (seconds since 1970) get the founder badge
        self.founder_until = config.get("founder_until")
        self.limits = Limiter()
        self.session_ttl = int(config.get("session_ttl", 86400))
        self.online_window = int(config.get("online_window", 150))
        self.roles = {undashed(k): v for k, v in config.get("roles", {}).items() if undashed(k) and v in ROLES}
        self.role_names = {k.lower(): v for k, v in config.get("role_names", {}).items() if v in ROLES}
        # accounts that own every cosmetic without a role (the creator's other accounts), by uuid
        self.own_everything = {u for u in map(undashed, config.get("own_everything", [])) if u}
        self._nonces = {}
        self._nonce_lock = threading.Lock()
        self._stats = (0.0, None)
        # the emotes playing now, in memory only: uuid -> (emote, speed, started)
        self._emotes = {}
        self._emote_lock = threading.Lock()

    # each method returns (status, body)

    def auth_start(self, body, ip):
        if not self.limits.allow("auth", ip, 10, 10):
            return 429, {"error": "slow_down"}
        name = body.get("name")
        if not isinstance(name, str) or not NAME.match(name):
            return 400, {"error": "bad_name"}
        server_id = secrets.token_hex(20)
        now = self.clock()
        with self._nonce_lock:
            self._nonces = {k: v for k, v in self._nonces.items() if v[1] > now}
            if len(self._nonces) > 100_000:
                return 503, {"error": "busy"}
            self._nonces[server_id] = (name.lower(), now + 60)
        return 200, {"serverId": server_id, "expiresIn": 60}

    def auth_finish(self, body, ip):
        if not self.limits.allow("auth", ip, 10, 10):
            return 429, {"error": "slow_down"}
        name, server_id = body.get("name"), body.get("serverId")
        if not isinstance(name, str) or not NAME.match(name) or not isinstance(server_id, str):
            return 400, {"error": "bad_request"}
        with self._nonce_lock:
            nonce = self._nonces.pop(server_id, None)
        if nonce is None or nonce[0] != name.lower() or nonce[1] <= self.clock():
            return 401, {"error": "unknown_server_id"}
        joined = self.mojang.has_joined(name, join_id(server_id))
        if joined is None:
            return 401, {"error": "not_verified"}
        uuid = dashed(joined[0])
        real_name = joined[1]
        role = self.roles.get(joined[0]) or self.role_names.get(real_name.lower()) or "user"
        token = secrets.token_hex(32)
        now = int(self.clock())
        self.store.sign_in(uuid, real_name, role, token, now + self.session_ttl, now)
        if self.founder_until and now < self.founder_until and "badge:founder" in self.catalogue.items:
            self.store.grant(uuid, "badge:founder", "founder", now)
        return 200, {"token": token, "uuid": uuid, "name": real_name, "role": self.store.role(uuid),
                     "expiresIn": self.session_ttl}

    def player(self, headers):
        auth = headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return None
        token = auth[7:].strip()
        if not re.match(r"^[0-9a-f]{64}$", token):
            return None
        return self.store.session(token, int(self.clock()))

    def presence(self, uuid, body):
        if not self.limits.allow("presence", uuid, 6, 3):
            return 429, {"error": "slow_down"}
        visible = body.get("visible", True)
        version = body.get("version")
        playing = body.get("playing", False)
        if not isinstance(visible, bool) or not isinstance(playing, bool) \
                or (version is not None and (not isinstance(version, str) or len(version) > 32)):
            return 400, {"error": "bad_request"}
        role = self.store.role(uuid)
        loadout = self.whitelist.clean(body.get("cosmetics"), role, uuid, self._owns(uuid, role))
        now = int(self.clock())
        self.store.presence(uuid, visible, loadout, version, now)
        answer = {"online": self._counts(now)["online"], "cosmetics": loadout}
        play_pass = self.catalogue.play_pass
        if playing and play_pass:
            # the client says "playing" while in a world and not away from the keyboard
            row, given = self.store.play(uuid, play_pass.season,
                                         lambda r: rewards.play_minute(r, day_of(now), now, play_pass), self.catalogue, now)
            answer["pass"] = {"minutes": row["minutes"], "tier": row["tier"], "given": given}
        return 200, answer

    def lookup(self, uuid, body):
        if not self.limits.allow("lookup", uuid, 30, 10):
            return 429, {"error": "slow_down"}
        wanted = body.get("uuids")
        if not isinstance(wanted, list) or len(wanted) > MAX_LOOKUP:
            return 400, {"error": "bad_request"}
        ids = sorted({dashed(u) for u in map(undashed, wanted) if u})
        found = self.store.lookup(ids, int(self.clock()) - self.online_window)
        return 200, {"players": {u: {"role": r, "cosmetics": l} for u, (r, l) in found.items()}}

    def emote(self, uuid, body):
        """The player starts an emote, or stops one ("none")."""
        if not self.limits.allow("emote", uuid, 30, 10):
            return 429, {"error": "slow_down"}
        name, speed = body.get("emote"), body.get("speed", 100)
        if not isinstance(name, str) or not EMOTE.match(name) or not isinstance(speed, int) or isinstance(speed, bool):
            return 400, {"error": "bad_request"}
        speed = max(50, min(200, speed))
        if name != "none" and not self._owns(uuid)("dance", name):
            return 403, {"error": "not_owned"}
        now = self.clock()
        with self._emote_lock:
            if name == "none":
                self._emotes.pop(uuid, None)
            else:
                if len(self._emotes) > 100_000:
                    self._emotes = {k: v for k, v in self._emotes.items() if now - v[2] < EMOTE_FOR}
                self._emotes[uuid] = (name, speed, now)
        return 200, {"ok": True}

    def emotes(self, uuid, body):
        """The emotes the players asked about play now, with how long they have been playing."""
        if not self.limits.allow("emotes", uuid, 90, 10):
            return 429, {"error": "slow_down"}
        wanted = body.get("uuids")
        if not isinstance(wanted, list) or len(wanted) > MAX_LOOKUP:
            return 400, {"error": "bad_request"}
        now = self.clock()
        out = {}
        with self._emote_lock:
            for u in {dashed(u) for u in map(undashed, wanted) if u}:
                e = self._emotes.get(u)
                if e is not None and now - e[2] < EMOTE_FOR:
                    out[u] = {"emote": e[0], "speed": e[1], "elapsed": int((now - e[2]) * 1000)}
        return 200, {"emotes": out}

    # --- the daily chest, Rago Points, the shop and the forge ---

    def _has_everything(self, uuid, role=None):
        """Whether the player owns every cosmetic: Rago's creator, and the accounts in own_everything."""
        if undashed(uuid) in self.own_everything:
            return True
        return (role if role is not None else self.store.role(uuid)) == "creator"

    def _owns(self, uuid, role=None):
        """owns(field, value): whether the player has that cosmetic. A value the catalogue does not
        know (none, a colour) or one kept for a role (the creator's capes) is the whitelist's call.
        Rago's creator and the accounts in own_everything have everything (to show it and try it)."""
        cache = []
        if role is None:
            role = self.store.role(uuid)
        if self._has_everything(uuid, role):
            return lambda field, value: True

        def owns(field, value):
            kind = OWNED_FIELDS.get(field, "dance" if field == "dance" else None)
            if kind is None or not isinstance(value, str):
                return True
            spec = self.catalogue.items.get(f"{kind}:{value}")
            if spec is None or "creator" in spec["sources"]:
                return True
            if not cache:
                cache.append(self.store.owned(uuid) | self.catalogue.starter)
            return f"{kind}:{value}" in cache[0]

        return owns

    def catalogue_info(self, ip):
        if not self.limits.allow("catalogue", ip, 30, 10):
            return 429, {"error": "slow_down"}
        return 200, self.catalogue.public()

    def rewards(self, uuid, body):
        """The player's points, streak and today's chest (opened or not, and when the next one comes)."""
        if not self.limits.allow("rewards", uuid, 30, 10):
            return 429, {"error": "slow_down"}
        now = self.clock()
        day = day_of(now)
        w = self.store.wallet(uuid)
        chest = self.store.chest(uuid, day)
        if chest is None:
            streak = rewards.next_streak(w["streak"], w["last_day"], w["joker_day"], day)[0]
        else:
            streak = w["streak"]
        return 200, {"points": w["points"], "streak": w["streak"], "opened": chest is not None, "chest": chest,
                     "todayStreak": streak, "nextReset": int(next_reset(now) - now),
                     "epicIn": max(1, rewards.EPIC_PITY - w["since_epic"]),
                     "legendaryIn": max(1, rewards.LEGENDARY_PITY - w["since_legendary"])}

    def open_chest(self, uuid, body):
        """Opens today's chest (once a day; asking again gives the same cards)."""
        if not self.limits.allow("open", uuid, 10, 3):
            return 429, {"error": "slow_down"}
        now = self.clock()
        day = day_of(now)

        def draw(wallet, owned, fragments):
            return rewards.open_chest(wallet, day, owned | self.catalogue.starter, fragments, self.catalogue, self.rng)

        chest, w, fresh = self.store.open_chest(uuid, day, draw, int(now))
        return 200, {"chest": chest, "points": w["points"], "streak": w["streak"], "fresh": fresh,
                     "nextReset": int(next_reset(now) - now)}

    def inventory(self, uuid, body):
        """Everything the player has: points, cosmetics (the starter pack included), fragments."""
        if not self.limits.allow("inventory", uuid, 30, 10):
            return 429, {"error": "slow_down"}
        w = self.store.wallet(uuid)
        owned = w["owned"] | self.catalogue.starter
        if self._has_everything(uuid):
            owned |= set(self.catalogue.items)
        return 200, {"points": w["points"], "owned": sorted(owned), "fragments": w["fragments"],
                     "streak": w["streak"]}

    def play_pass(self, uuid, body):
        """The play pass: the season's tiers, the minutes the player has played and today's."""
        if not self.limits.allow("pass", uuid, 30, 10):
            return 429, {"error": "slow_down"}
        play_pass = self.catalogue.play_pass
        if play_pass is None:
            return 404, {"error": "no_pass"}
        now = self.clock()
        row = self.store.playtime(uuid, play_pass.season) or dict(rewards.NEW_PLAY)
        today = row["day_minutes"] if row["day"] == day_of(now) else 0
        return 200, {**play_pass.public(), "minutes": row["minutes"], "tier": row["tier"], "today": today,
                     "nextReset": int(next_reset(now) - now)}

    def shop(self, uuid, body):
        """Today's shop, with what the player already has and the bundle's price for them."""
        if not self.limits.allow("shop", uuid, 30, 10):
            return 429, {"error": "slow_down"}
        now = self.clock()
        offer = rewards.shop_of(day_of(now), self.catalogue)
        owned = self.store.owned(uuid) | self.catalogue.starter
        prices = {i: self.catalogue.price(i) for i in set(offer["daily"]) | set(offer["permanent"]) | set(offer["bundle"])}
        return 200, {"daily": offer["daily"], "bundle": offer["bundle"], "permanent": offer["permanent"],
                     "prices": prices, "bundlePrice": rewards.bundle_price(offer["bundle"], owned, self.catalogue),
                     "owned": sorted(owned & set(prices)), "nextReset": int(next_reset(now) - now)}

    def buy(self, uuid, body):
        """Buys one cosmetic of today's shop, or the week's bundle, with Rago Points."""
        if not self.limits.allow("buy", uuid, 20, 5):
            return 429, {"error": "slow_down"}
        key, item, bundle = body.get("key"), body.get("item"), body.get("bundle", False)
        if not isinstance(key, str) or not KEY.match(key) or not isinstance(bundle, bool):
            return 400, {"error": "bad_request"}
        now = self.clock()
        offer = rewards.shop_of(day_of(now), self.catalogue)
        if bundle:
            items = offer["bundle"]
            if not items:
                return 404, {"error": "not_for_sale"}

            def check(owned, fragments):
                return "owned" if all(i in owned for i in items) else None

            have = self.store.owned(uuid) | self.catalogue.starter
            items = [i for i in items if i not in have]
            cost = rewards.bundle_price(items, set(), self.catalogue)
        else:
            if not isinstance(item, str) or not ITEM.match(item):
                return 400, {"error": "bad_request"}
            if item not in offer["daily"] and item not in offer["permanent"]:
                return 404, {"error": "not_for_sale"}
            items = [item]
            cost = self.catalogue.price(item)

            def check(owned, fragments):
                return "owned" if item in owned or item in self.catalogue.starter else None

        if not items:
            return 409, {"error": "owned"}
        result, points = self.store.spend(uuid, key, items, cost, "shop", int(now), check)
        if "error" in result:
            return 409, {"error": result["error"], "points": points}
        return 200, {"bought": result["items"], "cost": result["cost"], "points": points}

    def forge(self, uuid, body):
        """Finishes a started cosmetic, paying its missing fragments in points."""
        if not self.limits.allow("buy", uuid, 20, 5):
            return 429, {"error": "slow_down"}
        key, item = body.get("key"), body.get("item")
        if not isinstance(key, str) or not KEY.match(key) or not isinstance(item, str) or not ITEM.match(item) \
                or item not in self.catalogue.items or self.catalogue.pieces(item) is None:
            return 400, {"error": "bad_request"}
        have = self.store.wallet(uuid)["fragments"].get(item, 0)
        if have <= 0:
            return 409, {"error": "not_started"}
        cost = rewards.forge_cost(item, have, self.catalogue)

        def check(owned, fragments):
            if item in owned:
                return "owned"
            return None if fragments.get(item, 0) == have else "changed"

        result, points = self.store.spend(uuid, key, [item], cost, "forge", int(self.clock()), check)
        if "error" in result:
            return 409, {"error": result["error"], "points": points}
        return 200, {"forged": item, "cost": result["cost"], "points": points}

    def stats(self, ip):
        if not self.limits.allow("stats", ip, 60, 20):
            return 429, {"error": "slow_down"}
        return 200, self._counts(self.clock())

    def _counts(self, now):
        """Players online and in total, counted again at most every 15 seconds (every presence and
        every stats request would otherwise count the whole table)."""
        at, cached = self._stats
        if cached is None or now - at > 15 or now < at:
            online, total = self.store.counts(int(now) - self.online_window)
            cached = {"online": online, "players": total}
            self._stats = (now, cached)
        return cached

    def delete(self, uuid):
        return 200, {"deleted": self.store.delete(uuid) > 0}


class Handler(BaseHTTPRequestHandler):
    network = None
    trust_proxy = False
    timeout = 10
    server_version = "RagoNetwork"
    sys_version = ""

    def _ip(self):
        if self.trust_proxy:
            forwarded = self.headers.get("X-Forwarded-For")
            if forwarded:
                return forwarded.split(",")[-1].strip()
        return self.client_address[0]

    def _send(self, status, body):
        data = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _body(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return None
        if length < 0 or length > MAX_BODY:
            return None
        try:
            body = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
        except (ValueError, UnicodeDecodeError):
            return None
        return body if isinstance(body, dict) else None

    def do_GET(self):
        if self.path == "/v1/health":
            self._send(200, {"ok": True})
        elif self.path == "/":
            # someone opening the server's address in a browser: say what it is rather than "not found"
            self._send(200, {"service": "Rago Network", "ok": True, "health": "/v1/health", "stats": "/v1/stats"})
        elif self.path == "/v1/stats":
            self._send(*self.network.stats(self._ip()))
        elif self.path == "/v1/catalogue":
            self._send(*self.network.catalogue_info(self._ip()))
        else:
            self._send(404, {"error": "not_found"})

    def do_POST(self):
        body = self._body()
        if body is None:
            self._send(400, {"error": "bad_request"})
            return
        n = self.network
        if self.path == "/v1/auth/start":
            self._send(*n.auth_start(body, self._ip()))
            return
        if self.path == "/v1/auth/finish":
            self._send(*n.auth_finish(body, self._ip()))
            return
        signed_in = {"/v1/presence": n.presence, "/v1/lookup": n.lookup, "/v1/emote": n.emote, "/v1/emotes": n.emotes,
                     "/v1/rewards": n.rewards, "/v1/rewards/open": n.open_chest, "/v1/inventory": n.inventory,
                     "/v1/shop": n.shop, "/v1/shop/buy": n.buy, "/v1/forge": n.forge, "/v1/pass": n.play_pass}
        handle = signed_in.get(self.path)
        if handle is None:
            self._send(404, {"error": "not_found"})
            return
        uuid = n.player(self.headers)
        if uuid is None:
            self._send(401, {"error": "sign_in"})
        else:
            self._send(*handle(uuid, body))

    def do_DELETE(self):
        if self.path != "/v1/me":
            self._send(404, {"error": "not_found"})
            return
        uuid = self.network.player(self.headers)
        if uuid is None:
            self._send(401, {"error": "sign_in"})
        else:
            self._send(*self.network.delete(uuid))

    def log_message(self, fmt, *args):
        # no addresses or names in the logs
        pass


def serve(network, host="0.0.0.0", port=8080, trust_proxy=False):
    handler = type("BoundHandler", (Handler,), {"network": network, "trust_proxy": trust_proxy})
    server = ThreadingHTTPServer((host, port), handler)
    server.daemon_threads = True

    def prune():
        while True:
            time.sleep(3600)
            network.store.prune(int(network.clock()), int(network.config.get("forget_after", 180 * 86400)))

    threading.Thread(target=prune, name="prune", daemon=True).start()
    return server
