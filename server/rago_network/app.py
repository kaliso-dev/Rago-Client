"""The HTTP API (JSON over HTTPS, behind a reverse proxy that handles TLS)."""

import hashlib
import json
import re
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .limits import Limiter
from .loadout import Whitelist
from .mojang import SessionServer
from .store import Store

NAME = re.compile(r"^[A-Za-z0-9_]{1,16}$")
HEX32 = re.compile(r"^[0-9a-f]{32}$")
ROLES = ("user", "partner", "staff", "creator")
MAX_BODY = 16 * 1024
MAX_LOOKUP = 100


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

    def __init__(self, config, store=None, session_server=None, clock=time.time):
        self.config = config
        self.clock = clock
        self.store = store or Store(config.get("database", "rago-network.db"))
        self.mojang = session_server or SessionServer(config.get("session_server", "https://sessionserver.mojang.com"))
        self.whitelist = Whitelist.load(config["cosmetics"]) if isinstance(config.get("cosmetics"), str) \
            else Whitelist(config["cosmetics"])
        self.limits = Limiter()
        self.session_ttl = int(config.get("session_ttl", 86400))
        self.online_window = int(config.get("online_window", 150))
        self.roles = {undashed(k): v for k, v in config.get("roles", {}).items() if undashed(k) and v in ROLES}
        self.role_names = {k.lower(): v for k, v in config.get("role_names", {}).items() if v in ROLES}
        self._nonces = {}
        self._nonce_lock = threading.Lock()
        self._stats = (0.0, None)

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
        if not isinstance(visible, bool) or (version is not None and (not isinstance(version, str) or len(version) > 32)):
            return 400, {"error": "bad_request"}
        loadout = self.whitelist.clean(body.get("cosmetics"), self.store.role(uuid), uuid)
        now = int(self.clock())
        self.store.presence(uuid, visible, loadout, version, now)
        return 200, {"online": self._counts(now)["online"], "cosmetics": loadout}

    def lookup(self, uuid, body):
        if not self.limits.allow("lookup", uuid, 30, 10):
            return 429, {"error": "slow_down"}
        wanted = body.get("uuids")
        if not isinstance(wanted, list) or len(wanted) > MAX_LOOKUP:
            return 400, {"error": "bad_request"}
        ids = sorted({dashed(u) for u in map(undashed, wanted) if u})
        found = self.store.lookup(ids, int(self.clock()) - self.online_window)
        return 200, {"players": {u: {"role": r, "cosmetics": l} for u, (r, l) in found.items()}}

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
        if self.path not in ("/v1/presence", "/v1/lookup"):
            self._send(404, {"error": "not_found"})
            return
        uuid = n.player(self.headers)
        if uuid is None:
            self._send(401, {"error": "sign_in"})
        elif self.path == "/v1/presence":
            self._send(*n.presence(uuid, body))
        else:
            self._send(*n.lookup(uuid, body))

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
