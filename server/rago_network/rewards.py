"""The daily chest, Rago Points, the shop and the forge: the rules only, no storage and no HTTP.

Everything random is drawn here, on the server; the client only shows what it is told. Nothing can
be bought with money, traded or given: points and cosmetics have no value outside Rago.
"""

import calendar
import hashlib
import json
import time

RARITIES = ("common", "uncommon", "rare", "epic", "legendary", "mythic")
# the chance of each rarity for one card, out of 10 000 (published in the client)
CARD_ODDS = (5500, 2700, 1200, 450, 130, 20)
# what a cosmetic one already has is worth in points when it comes again
DUPLICATE_POINTS = {"common": 15, "uncommon": 30, "rare": 75, "epic": 200, "legendary": 500, "mythic": 1250}
# shop prices; mythics are never sold
PRICES = {"common": 120, "uncommon": 300, "rare": 800, "epic": 2000, "legendary": 4000}
# how many fragments make a cosmetic; legendaries and mythics are never cut up
PIECES = {"common": 3, "uncommon": 4, "rare": 5, "epic": 6}
# after this many chests without one, the next chest has a card of at least that rarity
EPIC_PITY = 20
LEGENDARY_PITY = 90
# a missed day takes a streak back to the last of these it reached, not to nothing
MILESTONES = (7, 14, 30, 100)
# one missed day a week is forgiven
JOKER_EVERY = 7
CARDS = 3
SHOP_DAILY = 4
SHOP_BUNDLE_DISCOUNT = 20
ITEM_TYPES = ("cape", "wings", "hat", "shield", "dance", "name", "icon", "title", "card", "badge")
SOURCES = ("starter", "chest", "shop", "pass", "streak", "event", "founder", "creator")
# the play pass: a presence sent while playing adds one minute when the last minute counted is at
# least this many seconds old (presences come every minute)
PASS_STEP = 50


def rank(rarity):
    return RARITIES.index(rarity)


# --- the day, in Paris time (a fixed reset for everyone: midnight in France) ---

def _last_sunday(year, month):
    """The UTC timestamp of 01:00 UTC on the last Sunday of the month (when Europe changes clocks)."""
    last = calendar.timegm((year + (month == 12), month % 12 + 1, 1, 0, 0, 0)) - 86400
    back = (time.gmtime(last).tm_wday - 6) % 7
    return last - back * 86400 + 3600


def paris_offset(ts):
    """Seconds Paris is ahead of UTC at that moment (summer time from the last Sunday of March to the
    last Sunday of October, both at 01:00 UTC)."""
    year = time.gmtime(ts).tm_year
    return 7200 if _last_sunday(year, 3) <= ts < _last_sunday(year, 10) else 3600


def day_of(ts):
    """The day number (days since 1970-01-01) it is in Paris at that moment."""
    return int((ts + paris_offset(ts)) // 86400)


def next_reset(ts):
    """The UTC timestamp of the next midnight in Paris (always on a whole UTC hour)."""
    day = day_of(ts)
    t = (day + 1) * 86400 - 7200
    while day_of(t) == day:
        t += 3600
    return t


# --- the catalogue ---

class Catalogue:
    """Every cosmetic Rago Network knows, by id ("cape:aurora"), with its rarity and where it comes from."""

    def __init__(self, data):
        self.items = {}
        for item, spec in data["items"].items():
            kind, _, name = item.partition(":")
            rarity = spec.get("rarity")
            sources = spec.get("sources", [])
            if kind not in ITEM_TYPES or not name or rarity not in RARITIES or not sources \
                    or any(s not in SOURCES for s in sources):
                raise ValueError("bad catalogue item: " + item)
            if "shop" in sources and rarity == "mythic":
                raise ValueError("mythics are never sold: " + item)
            if kind == "badge" and ("shop" in sources or "chest" in sources):
                raise ValueError("badges are earned, never drawn or sold: " + item)
            self.items[item] = {"rarity": rarity, "sources": tuple(sources)}
        self.starter = frozenset(i for i, s in self.items.items() if "starter" in s["sources"])
        self.pools = {r: sorted(i for i, s in self.items.items() if "chest" in s["sources"] and s["rarity"] == r)
                      for r in RARITIES}
        self.shop = sorted(i for i, s in self.items.items() if "shop" in s["sources"])
        self.play_pass = PlayPass(data["pass"], self) if data.get("pass") else None

    @classmethod
    def load(cls, path):
        with open(path, encoding="utf-8") as f:
            return cls(json.load(f))

    def rarity(self, item):
        return self.items[item]["rarity"]

    def price(self, item):
        return PRICES[self.rarity(item)]

    def pieces(self, item):
        return PIECES.get(self.rarity(item))

    def public(self):
        """What the client is told about the catalogue."""
        out = {"items": {i: {"rarity": s["rarity"], "sources": list(s["sources"])} for i, s in self.items.items()},
               "odds": dict(zip(RARITIES, CARD_ODDS)), "prices": PRICES, "pieces": PIECES,
               "duplicates": DUPLICATE_POINTS, "cards": CARDS}
        if self.play_pass:
            out["pass"] = self.play_pass.public()
        return out


# --- the play pass: the more one plays, the more one wins ---

class PlayPass:
    """A season of tiers, one every `minutes_per_tier` minutes played (at most `daily_cap` minutes a
    day count), each giving points or a cosmetic. Free: time played is all it asks."""

    def __init__(self, data, catalogue):
        self.season = data["season"]
        self.minutes_per_tier = int(data["minutes_per_tier"])
        self.daily_cap = int(data["daily_cap"])
        self.tiers = []
        for tier in data["tiers"]:
            item, points = tier.get("item"), tier.get("points")
            if item is not None:
                if item not in catalogue.items or "pass" not in catalogue.items[item]["sources"]:
                    raise ValueError("a pass tier gives an item that does not come from the pass: " + str(item))
                self.tiers.append({"item": item})
            elif isinstance(points, int) and points > 0:
                self.tiers.append({"points": points})
            else:
                raise ValueError("a pass tier gives an item or points: " + json.dumps(tier))
        if not self.season or self.minutes_per_tier <= 0 or self.daily_cap <= 0 or not self.tiers:
            raise ValueError("bad pass")

    def tier_of(self, minutes):
        """How many tiers `minutes` played have reached."""
        return min(len(self.tiers), minutes // self.minutes_per_tier)

    def public(self):
        return {"season": self.season, "minutesPerTier": self.minutes_per_tier, "dailyCap": self.daily_cap,
                "tiers": self.tiers}


NEW_PLAY = {"minutes": 0, "day": None, "day_minutes": 0, "last_credit": 0, "tier": 0}


def play_minute(row, day, now, play_pass):
    """A presence while playing: the season's row ({minutes, day, day_minutes, last_credit, tier})
    with one more minute when one is due, and the tiers it reached ([tier numbers], from 1)."""
    row = dict(row)
    if row["day"] != day:
        row["day"], row["day_minutes"] = day, 0
    if now - row["last_credit"] < PASS_STEP or row["day_minutes"] >= play_pass.daily_cap:
        return row, []
    row["minutes"] += 1
    row["day_minutes"] += 1
    row["last_credit"] = now
    reached = play_pass.tier_of(row["minutes"])
    new = list(range(row["tier"] + 1, reached + 1))
    row["tier"] = max(row["tier"], reached)
    return row, new


# --- streaks ---

def milestone_floor(streak):
    floor = 0
    for m in MILESTONES:
        if streak >= m:
            floor = m
    return floor


def next_streak(streak, last_day, joker_day, day):
    """(streak, joker_day) once the chest of `day` is opened: one more after yesterday's, one more
    after one missed day if the week's joker is free, otherwise back to the last milestone, plus today."""
    if last_day is None:
        return 1, joker_day
    gap = day - last_day
    if gap <= 0:
        return streak, joker_day
    if gap == 1:
        return streak + 1, joker_day
    if gap == 2 and (joker_day is None or day - joker_day >= JOKER_EVERY):
        return streak + 1, day
    return milestone_floor(streak) + 1, joker_day


def streak_bonus(streak):
    """What a streak day adds: (cards, the lowest rarity of one card or None, badges and items given)."""
    cards = CARDS + (1 if streak % 7 == 0 else 0)
    floor = None
    if streak % 14 == 0:
        floor = "rare"
    if streak % 30 == 0:
        floor = "epic"
    gifts = []
    for m in (7, 30, 100):
        if streak == m:
            gifts.append(f"badge:streak_{m}")
    if streak == 100:
        gifts.append("cape:streak_100")
    return cards, floor, gifts


# --- the draw ---

def roll_rarity(rng, floor=None):
    """A card's rarity by the published odds; at least `floor` when given (drawn among the rarities
    from the floor up, with the same odds between them)."""
    low = rank(floor) if floor else 0
    odds = CARD_ODDS[low:]
    pick = rng.randrange(sum(odds))
    for i, weight in enumerate(odds):
        if pick < weight:
            return RARITIES[low + i]
        pick -= weight
    return RARITIES[-1]


class Hand:
    """What a player has while a chest is drawn: updated card after card, so two cards of one chest
    agree (a cosmetic drawn twice is a duplicate the second time)."""

    def __init__(self, owned, fragments):
        self.owned = set(owned)
        self.fragments = dict(fragments)
        self.points = 0
        self.new_items = []
        self.new_fragments = {}

    def give(self, item, catalogue):
        if item in self.owned:
            worth = DUPLICATE_POINTS[catalogue.rarity(item)]
            self.points += worth
            return {"kind": "duplicate", "item": item, "points": worth}
        self.owned.add(item)
        self.fragments.pop(item, None)
        self.new_items.append(item)
        return {"kind": "item", "item": item}

    def fragment(self, item, count, catalogue):
        need = catalogue.pieces(item)
        have = min(need, self.fragments.get(item, 0) + count)
        self.fragments[item] = have
        self.new_fragments[item] = have
        card = {"kind": "fragment", "item": item, "count": count, "have": have, "need": need}
        if have >= need:
            self.owned.add(item)
            self.fragments.pop(item)
            self.new_items.append(item)
            card["completed"] = True
        return card


def _fragment_target(rarity, hand, catalogue, rng):
    """The cosmetic a fragment of that rarity goes to: one already started first (the most advanced),
    otherwise one not owned yet; None when the player has them all."""
    pool = [i for i in catalogue.pools[rarity] if i not in hand.owned]
    if not pool:
        return None
    started = [i for i in pool if hand.fragments.get(i, 0) > 0]
    if started:
        best = max(hand.fragments[i] / catalogue.pieces(i) for i in started)
        return rng.choice(sorted(i for i in started if hand.fragments[i] / catalogue.pieces(i) == best))
    return rng.choice(pool)


def _points(n, hand):
    hand.points += n
    return {"kind": "points", "points": n}


def _item(rarity, hand, catalogue, rng, fallback_points):
    pool = catalogue.pools[rarity]
    if not pool:
        return _points(fallback_points, hand)
    return hand.give(rng.choice(pool), catalogue)


def _fragments(rarity, count, hand, catalogue, rng, fallback_points):
    target = _fragment_target(rarity, hand, catalogue, rng)
    if target is None:
        return _points(fallback_points, hand)
    return hand.fragment(target, count, catalogue)


def draw_card(rarity, hand, catalogue, rng):
    """What a card of that rarity holds (see the plan: points, fragments or a cosmetic)."""
    pick = rng.randrange(100)
    if rarity == "common":
        card = _points(15, hand) if pick < 70 else _fragments("common", 1, hand, catalogue, rng, 15)
    elif rarity == "uncommon":
        if pick < 50:
            card = _points(35, hand)
        elif pick < 80:
            card = _fragments("rare", 1, hand, catalogue, rng, 35)
        else:
            card = _item("common", hand, catalogue, rng, 35)
    elif rarity == "rare":
        if pick < 50:
            card = _item("rare", hand, catalogue, rng, 90)
        elif pick < 80:
            card = _points(90, hand)
        else:
            card = _fragments("epic", 2, hand, catalogue, rng, 90)
    elif rarity == "epic":
        card = _item("epic", hand, catalogue, rng, 250) if pick < 70 else _points(250, hand)
    else:
        card = _item(rarity, hand, catalogue, rng, DUPLICATE_POINTS[rarity])
    card["rarity"] = rarity
    return card


def open_chest(state, day, owned, fragments, catalogue, rng):
    """Opens the chest of `day` for a player.

    state: {"streak", "last_day", "joker_day", "since_epic", "since_legendary"}.
    Returns (cards, new state, hand, streak gifts): the hand holds the points won, the cosmetics now
    owned and the fragments changed."""
    streak, joker_day = next_streak(state["streak"], state["last_day"], state["joker_day"], day)
    count, floor, gifts = streak_bonus(streak)
    # the pity: a chest after too many without, has a card of that rarity (the first card)
    if state["since_legendary"] + 1 >= LEGENDARY_PITY:
        floor = "legendary"
    elif state["since_epic"] + 1 >= EPIC_PITY and (floor is None or rank(floor) < rank("epic")):
        floor = "epic"
    rarities = [roll_rarity(rng, floor if i == 0 else None) for i in range(count)]
    hand = Hand(owned, fragments)
    cards = [draw_card(r, hand, catalogue, rng) for r in rarities]
    for gift in gifts:
        if gift in catalogue.items and gift not in hand.owned:
            hand.owned.add(gift)
            hand.new_items.append(gift)
    best = max(rank(r) for r in rarities)
    new_state = {
        "streak": streak, "last_day": day, "joker_day": joker_day,
        "since_epic": 0 if best >= rank("epic") else state["since_epic"] + 1,
        "since_legendary": 0 if best >= rank("legendary") else state["since_legendary"] + 1,
    }
    return cards, new_state, hand, [g for g in gifts if g in catalogue.items]


# --- the shop ---

def _seeded(key, n):
    """A number in [0, n) that only depends on `key` (the same shop for every player on a day)."""
    return int.from_bytes(hashlib.sha256(key.encode("utf-8")).digest()[:8], "big") % n


def shop_of(day, catalogue):
    """The day's showcase (SHOP_DAILY items, the first an epic or legendary when there is one), the
    week's bundle (one item of each of up to three types, cheaper together) and the permanent
    catalogue (commons, uncommons and rares sold every day)."""
    sellable = catalogue.shop
    featured_pool = [i for i in sellable if rank(catalogue.rarity(i)) >= rank("epic")]
    daily = []
    if featured_pool:
        daily.append(featured_pool[_seeded(f"featured:{day}", len(featured_pool))])
    rest = [i for i in sellable if i not in daily]
    k = 0
    while rest and len(daily) < SHOP_DAILY:
        daily.append(rest.pop(_seeded(f"daily:{day}:{k}", len(rest))))
        k += 1
    week = day // 7
    bundle = []
    for kind in ("wings", "hat", "shield", "cape"):
        pool = [i for i in sellable if i.startswith(kind + ":") and rank(catalogue.rarity(i)) >= rank("rare")]
        if pool and len(bundle) < 3:
            bundle.append(pool[_seeded(f"bundle:{week}:{kind}", len(pool))])
    permanent = [i for i in sellable if rank(catalogue.rarity(i)) <= rank("rare")]
    return {"daily": daily, "bundle": bundle if len(bundle) >= 2 else [], "permanent": permanent}


def bundle_price(items, owned, catalogue):
    """The bundle's price for a player: the items they do not have yet, less the discount."""
    full = sum(catalogue.price(i) for i in items if i not in owned)
    return full * (100 - SHOP_BUNDLE_DISCOUNT) // 100


def forge_cost(item, have, catalogue):
    """Points to finish a started cosmetic: the missing fragments at the price's share each."""
    need = catalogue.pieces(item)
    share = -(-catalogue.price(item) // need)
    return max(0, need - have) * share
