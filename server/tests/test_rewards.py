import calendar
import os
import random
import unittest

from rago_network import rewards
from rago_network.app import Network
from rago_network.rewards import Catalogue, day_of, next_reset, open_chest
from rago_network.store import NEW_WALLET, Store

from test_network import COSMETICS, FakeMojang, fresh_store, sign_in

CATALOGUE = os.path.join(os.path.dirname(__file__), "..", "catalogue.json")


def ts(*date):
    return calendar.timegm(date + (0,) * (6 - len(date)))


def small_catalogue():
    """A few cosmetics of each rarity in the chest and the shop, plus the real starter pack."""
    items = {"cape:rago": {"rarity": "rare", "sources": ["starter"]},
             "cape:black_cat": {"rarity": "mythic", "sources": ["creator"]},
             "dance:wave": {"rarity": "common", "sources": ["starter"]},
             "badge:founder": {"rarity": "legendary", "sources": ["founder"]},
             "badge:streak_7": {"rarity": "uncommon", "sources": ["streak"]},
             "badge:streak_30": {"rarity": "epic", "sources": ["streak"]},
             "badge:streak_100": {"rarity": "legendary", "sources": ["streak"]},
             "cape:streak_100": {"rarity": "legendary", "sources": ["streak"]}}
    kinds = ("hat", "wings", "shield", "cape", "dance")
    for rarity in rewards.RARITIES:
        for k in kinds:
            sources = ["chest"] + (["shop"] if rarity != "mythic" else [])
            items[f"{k}:{rarity}_{k}"] = {"rarity": rarity, "sources": sources}
    return {"items": items}


class Clock:
    def __init__(self, now):
        self.now = now

    def __call__(self):
        return self.now


def network(catalogue=None, now=None, founder_until=None):
    config = {"cosmetics": COSMETICS, "catalogue": catalogue or small_catalogue(), "role_names": {"qRago": "creator"}}
    if founder_until:
        config["founder_until"] = founder_until
    return Network(config, store=fresh_store(), session_server=FakeMojang(),
                   clock=Clock(now or ts(2026, 11, 2, 18)), rng=random.Random(7))


def uuid_of(n):
    return sign_in(n, "Steve")["uuid"]


class ParisDayTest(unittest.TestCase):
    def test_the_day_turns_at_midnight_in_paris(self):
        # winter: Paris is UTC+1, so 23:30 UTC is already the next day
        self.assertEqual(day_of(ts(2026, 11, 2, 22, 59)), day_of(ts(2026, 11, 2, 12)))
        self.assertEqual(day_of(ts(2026, 11, 2, 23, 0)), day_of(ts(2026, 11, 2, 12)) + 1)
        # summer: UTC+2
        self.assertEqual(day_of(ts(2026, 7, 1, 21, 59)), day_of(ts(2026, 7, 1, 12)))
        self.assertEqual(day_of(ts(2026, 7, 1, 22, 0)), day_of(ts(2026, 7, 1, 12)) + 1)

    def test_clocks_change_on_the_last_sundays_of_march_and_october(self):
        self.assertEqual(rewards.paris_offset(ts(2026, 3, 29, 0, 59)), 3600)
        self.assertEqual(rewards.paris_offset(ts(2026, 3, 29, 1, 0)), 7200)
        self.assertEqual(rewards.paris_offset(ts(2026, 10, 25, 0, 59)), 7200)
        self.assertEqual(rewards.paris_offset(ts(2026, 10, 25, 1, 0)), 3600)

    def test_the_next_reset_is_the_next_midnight_in_paris(self):
        self.assertEqual(next_reset(ts(2026, 7, 1, 12)), ts(2026, 7, 1, 22))
        self.assertEqual(next_reset(ts(2026, 11, 2, 18)), ts(2026, 11, 2, 23))
        self.assertEqual(next_reset(ts(2026, 11, 2, 23, 30)), ts(2026, 11, 3, 23))
        # the night clocks go back, the day is 25 hours long
        self.assertEqual(next_reset(ts(2026, 10, 24, 23)), ts(2026, 10, 25, 23))


class DrawTest(unittest.TestCase):
    def test_cards_follow_the_published_odds(self):
        rng = random.Random(1)
        n = 400_000
        counts = dict.fromkeys(rewards.RARITIES, 0)
        for _ in range(n):
            counts[rewards.roll_rarity(rng)] += 1
        for rarity, odds in zip(rewards.RARITIES, rewards.CARD_ODDS):
            self.assertAlmostEqual(counts[rarity] / n, odds / 10_000, delta=0.004, msg=rarity)

    def test_a_floor_gives_at_least_that_rarity(self):
        rng = random.Random(2)
        for _ in range(2000):
            self.assertIn(rewards.roll_rarity(rng, "epic"), ("epic", "legendary", "mythic"))
            self.assertIn(rewards.roll_rarity(rng, "legendary"), ("legendary", "mythic"))

    def test_streaks_go_on_forgive_one_day_a_week_and_fall_back_to_a_milestone(self):
        self.assertEqual(rewards.next_streak(0, None, None, 100), (1, None))
        self.assertEqual(rewards.next_streak(5, 99, None, 100), (6, None))
        self.assertEqual(rewards.next_streak(5, 100, None, 100), (5, None), "the same day changes nothing")
        # one missed day: the joker, then not again within a week
        self.assertEqual(rewards.next_streak(5, 98, None, 100), (6, 100))
        self.assertEqual(rewards.next_streak(6, 101, 100, 103), (1, 100))
        self.assertEqual(rewards.next_streak(20, 101, 100, 103), (15, 100), "back to 14, plus today")
        self.assertEqual(rewards.next_streak(20, 106, 100, 108), (21, 108), "a week later the joker is back")
        self.assertEqual(rewards.next_streak(45, 90, None, 100), (31, None))

    def test_streak_days_add_a_card_a_better_card_and_badges(self):
        self.assertEqual(rewards.streak_bonus(6), (3, None, []))
        self.assertEqual(rewards.streak_bonus(7), (4, None, ["badge:streak_7"]))
        self.assertEqual(rewards.streak_bonus(14), (4, "rare", []))
        self.assertEqual(rewards.streak_bonus(30), (3, "epic", ["badge:streak_30"]))
        self.assertEqual(rewards.streak_bonus(100), (3, None, ["badge:streak_100", "cape:streak_100"]))

    def test_after_too_many_chests_without_an_epic_one_comes(self):
        cat = Catalogue(small_catalogue())
        state = dict(NEW_WALLET, since_epic=rewards.EPIC_PITY - 1)
        for seed in range(50):
            cards, new, _, _ = open_chest(state, 10, set(), {}, cat, random.Random(seed))
            self.assertGreaterEqual(rewards.rank(cards[0]["rarity"]), rewards.rank("epic"))
            self.assertEqual(new["since_epic"], 0)
        state = dict(NEW_WALLET, since_legendary=rewards.LEGENDARY_PITY - 1)
        cards, new, _, _ = open_chest(state, 10, set(), {}, cat, random.Random(3))
        self.assertGreaterEqual(rewards.rank(cards[0]["rarity"]), rewards.rank("legendary"))

    def test_a_cosmetic_one_has_comes_back_as_points(self):
        cat = Catalogue({"items": {"hat:only": {"rarity": "legendary", "sources": ["chest"]}}})
        hand = rewards.Hand({"hat:only"}, {})
        card = rewards.draw_card("legendary", hand, cat, random.Random(1))
        self.assertEqual(card, {"kind": "duplicate", "item": "hat:only", "points": 1000, "rarity": "legendary"})
        self.assertEqual(hand.points, 1000)

    def test_fragments_go_to_a_started_cosmetic_and_complete_it(self):
        cat = Catalogue(small_catalogue())
        hand = rewards.Hand(set(), {"cape:common_cape": 2})
        target = rewards._fragment_target("common", hand, cat, random.Random(4))
        self.assertEqual(target, "cape:common_cape")
        card = hand.fragment(target, 1, cat)
        self.assertTrue(card["completed"])
        self.assertIn("cape:common_cape", hand.owned)
        self.assertNotIn("cape:common_cape", hand.fragments)

    def test_on_average_a_player_wins_about_85_points_a_day(self):
        # the plan's figure, with every rarity in the chest and nothing owned yet
        cat = Catalogue(small_catalogue())
        rng = random.Random(5)
        days = 20_000
        points = legendary = 0
        for _ in range(days):
            _, _, hand, _ = open_chest(dict(NEW_WALLET), 10, set(), {}, cat, rng)
            points += hand.points
        self.assertAlmostEqual(points / days, 85, delta=5)


class ChestTest(unittest.TestCase):
    def test_one_chest_a_day_and_asking_again_gives_the_same_cards(self):
        n = network()
        u = uuid_of(n)
        status, first = n.open_chest(u, {})
        self.assertEqual(status, 200)
        self.assertTrue(first["fresh"])
        self.assertEqual(len(first["chest"]["cards"]), 3)
        status, again = n.open_chest(u, {})
        self.assertFalse(again["fresh"])
        self.assertEqual(again["chest"], first["chest"])
        self.assertEqual(again["points"], first["points"])
        status, info = n.rewards(u, {})
        self.assertTrue(info["opened"])
        self.assertEqual(info["nextReset"], 5 * 3600)

    def test_the_next_day_brings_a_new_chest_and_the_streak_grows(self):
        n = network()
        u = uuid_of(n)
        n.open_chest(u, {})
        n.clock.now += 86400
        status, info = n.rewards(u, {})
        self.assertFalse(info["opened"])
        self.assertEqual(info["todayStreak"], 2)
        status, second = n.open_chest(u, {})
        self.assertEqual(second["streak"], 2)

    def test_points_add_up_with_the_ledger_and_cosmetics_are_kept(self):
        n = network()
        n.limits.allow = lambda *args: True
        u = uuid_of(n)
        total = 0
        for _ in range(40):
            status, opened = n.open_chest(u, {})
            total += opened["chest"]["won"]
            n.clock.now += 86400
        self.assertEqual(opened["points"], total)
        self.assertEqual(sum(row[0] for row in n.store.ledger(u, 1000)), total)
        status, inv = n.inventory(u, {})
        self.assertIn("badge:streak_7", inv["owned"])
        self.assertIn("badge:streak_30", inv["owned"])
        self.assertIn("cape:rago", inv["owned"], "the starter pack")
        self.assertGreater(len(inv["owned"]), 6)

    def test_deleting_a_player_forgets_what_they_won(self):
        n = network()
        u = uuid_of(n)
        n.open_chest(u, {})
        n.delete(u)
        self.assertEqual(n.store.wallet(u)["points"], 0)
        self.assertEqual(n.store.owned(u), set())
        self.assertIsNone(n.store.chest(u, day_of(n.clock())))


class ShopTest(unittest.TestCase):
    def rich(self, n, u, points):
        n.store._transaction(lambda: n.store._put_wallet(u, dict(NEW_WALLET, points=points)))

    def test_the_shop_is_the_same_for_everyone_and_changes_every_day(self):
        n = network()
        u = uuid_of(n)
        status, today = n.shop(u, {})
        self.assertEqual(len(today["daily"]), rewards.SHOP_DAILY)
        self.assertGreaterEqual(rewards.rank(n.catalogue.rarity(today["daily"][0])), rewards.rank("epic"))
        self.assertTrue(all(rewards.rank(n.catalogue.rarity(i)) <= rewards.rank("rare") for i in today["permanent"]))
        self.assertFalse(any(n.catalogue.rarity(i) == "mythic" for i in today["daily"] + today["permanent"]))
        days = {tuple(rewards.shop_of(d, n.catalogue)["daily"]) for d in range(100, 110)}
        self.assertGreater(len(days), 5)

    def test_buying_takes_the_price_once(self):
        n = network()
        u = uuid_of(n)
        status, offer = n.shop(u, {})
        item = offer["permanent"][0]
        status, body = n.buy(u, {"item": item, "key": "first-buy-1"})
        self.assertEqual((status, body["error"]), (409, "not_enough_points"))
        self.rich(n, u, 5000)
        status, body = n.buy(u, {"item": item, "key": "first-buy-2"})
        self.assertEqual(status, 200)
        self.assertEqual(body["points"], 5000 - n.catalogue.price(item))
        status, again = n.buy(u, {"item": item, "key": "first-buy-2"})
        self.assertEqual((status, again["points"]), (200, body["points"]), "the same key buys once")
        status, twice = n.buy(u, {"item": item, "key": "first-buy-3"})
        self.assertEqual((status, twice["error"]), (409, "owned"))
        self.assertIn(item, n.store.owned(u))

    def test_only_todays_items_are_for_sale(self):
        n = network()
        u = uuid_of(n)
        self.rich(n, u, 100_000)
        status, body = n.buy(u, {"item": "dance:mythic_dance", "key": "mythic-try"})
        self.assertEqual(status, 404)
        status, body = n.buy(u, {"item": "cape:black_cat", "key": "creator-try"})
        self.assertEqual(status, 404)
        status, body = n.buy(u, {"item": "nope", "key": "bad-item-1"})
        self.assertEqual(status, 400)
        status, body = n.buy(u, {"item": "dance:common_dance", "key": "x"})
        self.assertEqual(status, 400, "a key is 8 to 64 characters")

    def test_the_bundle_is_cheaper_together(self):
        n = network()
        u = uuid_of(n)
        status, offer = n.shop(u, {})
        self.assertGreaterEqual(len(offer["bundle"]), 2)
        full = sum(n.catalogue.price(i) for i in offer["bundle"])
        self.assertEqual(offer["bundlePrice"], full * 80 // 100)
        self.rich(n, u, 100_000)
        status, body = n.buy(u, {"bundle": True, "key": "bundle-key-1"})
        self.assertEqual(status, 200)
        self.assertEqual(body["cost"], offer["bundlePrice"])
        self.assertTrue(set(offer["bundle"]) <= n.store.owned(u))

    def test_the_forge_finishes_a_started_cosmetic(self):
        n = network()
        u = uuid_of(n)
        item = "cape:rare_cape"
        n.store._transaction(lambda: n.store._db.execute(
            "INSERT INTO fragments(uuid, item, count) VALUES(?, ?, 3)", (u, item)))
        self.assertEqual(rewards.forge_cost(item, 3, n.catalogue), 2 * 160)
        status, body = n.forge(u, {"item": item, "key": "forge-key-1"})
        self.assertEqual((status, body["error"]), (409, "not_enough_points"))
        self.rich(n, u, 1000)
        status, body = n.forge(u, {"item": item, "key": "forge-key-2"})
        self.assertEqual((status, body["points"]), (200, 680))
        self.assertIn(item, n.store.owned(u))
        self.assertNotIn(item, n.store.wallet(u)["fragments"])
        status, body = n.forge(u, {"item": "cape:epic_cape", "key": "forge-key-3"})
        self.assertEqual((status, body["error"]), (409, "not_started"))


class OwnershipTest(unittest.TestCase):
    def test_a_cosmetic_from_the_catalogue_is_shown_only_once_owned(self):
        cat = small_catalogue()
        cat["items"]["hat:crown"] = {"rarity": "epic", "sources": ["chest"]}
        n = network(cat)
        u = uuid_of(n)
        status, body = n.presence(u, {"cosmetics": {"hat": "crown", "cape": "rago"}})
        self.assertEqual(body["cosmetics"]["hat"], "none", "not won yet")
        self.assertEqual(body["cosmetics"]["cape"], "rago", "the starter pack")
        n.store.grant(u, "hat:crown", "chest", 1)
        status, body = n.presence(u, {"cosmetics": {"hat": "crown"}})
        self.assertEqual(body["cosmetics"]["hat"], "crown")

    def test_a_dance_one_does_not_have_is_refused(self):
        n = network()
        u = uuid_of(n)
        self.assertEqual(n.emote(u, {"emote": "rare_dance"})[0], 403)
        self.assertEqual(n.emote(u, {"emote": "wave"})[0], 200)
        self.assertEqual(n.emote(u, {"emote": "none"})[0], 200)
        self.assertEqual(n.emote(u, {"emote": "not_in_the_catalogue"})[0], 200)

    def test_players_who_join_before_the_rewards_are_founders(self):
        n = network(now=ts(2026, 11, 2), founder_until=ts(2026, 12, 1))
        self.assertIn("badge:founder", n.store.owned(uuid_of(n)))
        n = network(now=ts(2026, 12, 2), founder_until=ts(2026, 12, 1))
        self.assertNotIn("badge:founder", n.store.owned(uuid_of(n)))

    def test_the_real_catalogue_gives_one_free_cape_hat_wings_and_dance(self):
        cat = Catalogue.load(CATALOGUE)
        self.assertEqual(cat.starter, {"cape:rago", "hat:rago_cap", "wings:dragon_small", "dance:clap"})
        self.assertEqual({i for i, s in cat.items.items() if "creator" in s["sources"]},
                         {"cape:black_cat", "cape:qrago", "cape:pink_r"})
        self.assertEqual(len(cat.play_pass.tiers), 30)

    def test_every_cosmetic_a_loadout_can_show_is_in_the_catalogue(self):
        import json
        cat = Catalogue.load(CATALOGUE)
        with open(COSMETICS, encoding="utf-8") as f:
            fields = json.load(f)["fields"]
        kinds = {"cape": "cape", "hat": "hat", "wings": "wings", "shield": "shield", "name_color": "name",
                 "name_icon": "icon"}
        for field, kind in kinds.items():
            shown = {v for v in fields[field]["values"] if v != "none"}
            known = {i.split(":")[1] for i in cat.items if i.startswith(kind + ":")}
            self.assertEqual(shown, known, field)

    def test_rago_creator_has_everything(self):
        n = network()
        creator = sign_in(n, "qRago")["uuid"]
        status, body = n.presence(creator, {"cosmetics": {"hat": "crown", "name_color": "aurora"}})
        self.assertEqual((body["cosmetics"]["hat"], body["cosmetics"]["name_color"]), ("crown", "aurora"))
        self.assertEqual(set(n.inventory(creator, {})[1]["owned"]), set(n.catalogue.items))
        self.assertEqual(n.emote(creator, {"emote": "rare_dance"})[0], 200)

    def test_a_name_colour_and_icon_are_shown_once_owned(self):
        import json
        with open(CATALOGUE, encoding="utf-8") as f:
            n = network(json.load(f))
        u = uuid_of(n)
        status, body = n.presence(u, {"cosmetics": {"name_color": "mint", "name_icon": "heart"}})
        self.assertEqual((body["cosmetics"]["name_color"], body["cosmetics"]["name_icon"]), ("none", "none"))
        n.store.grant(u, "name:mint", "shop", 1)
        n.store.grant(u, "icon:heart", "shop", 1)
        status, body = n.presence(u, {"cosmetics": {"name_color": "mint", "name_icon": "heart"}})
        self.assertEqual((body["cosmetics"]["name_color"], body["cosmetics"]["name_icon"]), ("mint", "heart"))


def pass_catalogue():
    cat = small_catalogue()
    cat["items"]["hat:pass_hat"] = {"rarity": "rare", "sources": ["pass"]}
    cat["items"]["hat:common_hat"]["sources"] = ["chest", "shop", "pass"]
    cat["pass"] = {"season": "s1", "minutes_per_tier": 2, "daily_cap": 5,
                   "tiers": [{"points": 50}, {"item": "hat:pass_hat"}, {"item": "hat:common_hat"}, {"points": 70}]}
    return cat


class PlayPassTest(unittest.TestCase):
    def play(self, n, u, seconds=60, playing=True):
        # one presence a minute, as the client sends them (the rate limiter uses the real time)
        n.limits.allow = lambda *args: True
        n.clock.now += seconds
        return n.presence(u, {"playing": playing})[1].get("pass")

    def test_a_minute_counts_when_playing_once_a_minute(self):
        n = network(pass_catalogue())
        u = uuid_of(n)
        self.assertIsNone(self.play(n, u, playing=False), "in the menus")
        self.assertEqual(self.play(n, u)["minutes"], 1)
        self.assertEqual(self.play(n, u, 10)["minutes"], 1, "two presences ten seconds apart count once")
        self.assertEqual(self.play(n, u)["minutes"], 2)
        status, body = n.play_pass(u, {})
        self.assertEqual((body["minutes"], body["today"], body["tier"], len(body["tiers"])), (2, 2, 1, 4))

    def test_tiers_give_their_rewards_once(self):
        n = network(pass_catalogue())
        u = uuid_of(n)
        given = []
        for _ in range(4):
            given += self.play(n, u)["given"]
        self.assertEqual(given, [{"tier": 1, "points": 50}, {"tier": 2, "item": "hat:pass_hat"}])
        self.assertIn("hat:pass_hat", n.store.owned(u))
        self.assertEqual(n.store.wallet(u)["points"], 50)
        self.assertEqual(n.store.ledger(u)[0][:3], (50, "pass", "s1:1"))

    def test_a_tier_item_already_owned_gives_its_duplicate_points(self):
        n = network(pass_catalogue())
        u = uuid_of(n)
        n.store.grant(u, "hat:common_hat", "shop", 1)
        n.clock.now = ts(2026, 11, 2, 1)
        given = []
        for _ in range(5):
            given += self.play(n, u)["given"]
        n.clock.now = ts(2026, 11, 3, 1)
        given += self.play(n, u)["given"]
        self.assertEqual(given[-1], {"tier": 3, "points": rewards.DUPLICATE_POINTS["common"], "item": "hat:common_hat",
                                     "duplicate": True})

    def test_only_so_many_minutes_count_a_day(self):
        n = network(pass_catalogue())
        u = uuid_of(n)
        n.clock.now = ts(2026, 11, 2, 1)
        for _ in range(8):
            last = self.play(n, u)
        self.assertEqual(last["minutes"], 5, "the daily cap")
        n.clock.now = ts(2026, 11, 3, 1)
        self.assertEqual(self.play(n, u)["minutes"], 6, "a new day")

    def test_a_pass_tier_must_give_a_pass_item(self):
        cat = pass_catalogue()
        cat["pass"]["tiers"].append({"item": "hat:rare_hat"})
        with self.assertRaises(ValueError):
            Catalogue(cat)


if __name__ == "__main__":
    unittest.main()
