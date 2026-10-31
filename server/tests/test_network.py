import json
import os
import threading
import unittest
import urllib.error
import urllib.request

from rago_network.app import Network, join_id, serve
from rago_network.limits import Limiter
from rago_network.store import Store

COSMETICS = os.path.join(os.path.dirname(__file__), "..", "cosmetics.json")
QRAGO = "0123456789abcdef0123456789abcdef"
OTHER = "fedcba9876543210fedcba9876543210"


class FakeMojang:
    """Mojang's session server: knows who joined which server id."""

    def __init__(self):
        self.joined = {}
        self.players = {"qRago": QRAGO, "Steve": OTHER}

    def join(self, name, server_id):
        self.joined[(name, server_id)] = True

    def has_joined(self, name, server_id):
        if not self.joined.pop((name, server_id), False):
            return None
        return self.players[name], name


class Clock:
    def __init__(self):
        self.now = 1_000_000.0

    def __call__(self):
        return self.now


def network(clock=None, mojang=None):
    config = {"cosmetics": COSMETICS, "role_names": {"qRago": "creator"}}
    return Network(config, store=Store(":memory:"), session_server=mojang or FakeMojang(), clock=clock or Clock())


def sign_in(n, name="qRago", ip="1.2.3.4"):
    status, start = n.auth_start({"name": name}, ip)
    assert status == 200, start
    n.mojang.join(name, join_id(start["serverId"]))
    status, done = n.auth_finish({"name": name, "serverId": start["serverId"]}, ip)
    assert status == 200, done
    return done


class AuthTest(unittest.TestCase):
    def test_mojang_is_told_a_hash_of_the_challenge(self):
        # the same values as the client's NetworkClientTest; Minecraft's own form (two's complement hex)
        self.assertEqual(join_id("abc"), "3eb134e9faffe05e70d4f1174927717bece96767")
        self.assertEqual(join_id("3f1c9a0e5b7d2c4a6e8f0b1d3c5a7e9f2b4d6a8c"), "-1ed171fca8556eb96ab06194d04b3938f1367609")

    def test_joining_the_challenge_itself_is_refused(self):
        n = network()
        status, start = n.auth_start({"name": "qRago"}, "1.2.3.4")
        n.mojang.join("qRago", start["serverId"])
        status, body = n.auth_finish({"name": "qRago", "serverId": start["serverId"]}, "1.2.3.4")
        self.assertEqual(status, 401, body)

    def test_a_player_mojang_confirms_gets_a_token(self):
        n = network()
        done = sign_in(n)
        self.assertEqual(done["uuid"], "01234567-89ab-cdef-0123-456789abcdef")
        self.assertEqual(done["role"], "creator")
        self.assertEqual(n.player({"Authorization": "Bearer " + done["token"]}), done["uuid"])

    def test_without_the_join_at_mojang_there_is_no_token(self):
        n = network()
        status, start = n.auth_start({"name": "Steve"}, "1.2.3.4")
        status, body = n.auth_finish({"name": "Steve", "serverId": start["serverId"]}, "1.2.3.4")
        self.assertEqual(status, 401)

    def test_a_server_id_works_once_and_only_for_its_name(self):
        n = network()
        _, start = n.auth_start({"name": "Steve"}, "1.2.3.4")
        n.mojang.join("qRago", join_id(start["serverId"]))
        status, _ = n.auth_finish({"name": "qRago", "serverId": start["serverId"]}, "1.2.3.4")
        self.assertEqual(status, 401)
        status, _ = n.auth_finish({"name": "Steve", "serverId": start["serverId"]}, "1.2.3.4")
        self.assertEqual(status, 401, "used up by the first try")

    def test_server_ids_expire(self):
        clock = Clock()
        n = network(clock)
        _, start = n.auth_start({"name": "Steve"}, "1.2.3.4")
        n.mojang.join("Steve", join_id(start["serverId"]))
        clock.now += 61
        status, _ = n.auth_finish({"name": "Steve", "serverId": start["serverId"]}, "1.2.3.4")
        self.assertEqual(status, 401)

    def test_tokens_expire(self):
        clock = Clock()
        n = network(clock)
        done = sign_in(n)
        clock.now += 86401
        self.assertIsNone(n.player({"Authorization": "Bearer " + done["token"]}))

    def test_bad_names_are_refused(self):
        n = network()
        for name in ["", "a" * 17, "bad name", "é", None, 5]:
            self.assertEqual(n.auth_start({"name": name}, "1.2.3.4")[0], 400)

    def test_too_many_sign_ins_from_one_address_are_slowed(self):
        n = network()
        codes = [n.auth_start({"name": "Steve"}, "9.9.9.9")[0] for _ in range(12)]
        self.assertIn(429, codes)
        self.assertEqual(n.auth_start({"name": "Steve"}, "8.8.8.8")[0], 200)


class PresenceTest(unittest.TestCase):
    def test_a_visible_player_is_found_with_their_cosmetics(self):
        n = network()
        me = sign_in(n)
        other = sign_in(n, "Steve")
        n.presence(me["uuid"], {"visible": True, "version": "1.7.0", "cosmetics": {"cape": "flame", "hat": "crown"}})
        status, body = n.lookup(other["uuid"], {"uuids": [me["uuid"].replace("-", ""), "not-a-uuid"]})
        self.assertEqual(status, 200)
        found = body["players"][me["uuid"]]
        self.assertEqual(found["role"], "creator")
        self.assertEqual(found["cosmetics"]["cape"], "flame")
        self.assertEqual(found["cosmetics"]["hat"], "crown")
        self.assertEqual(found["cosmetics"]["wings"], "none", "missing fields take their default")

    def test_a_hidden_or_gone_player_is_not_found(self):
        clock = Clock()
        n = network(clock)
        me = sign_in(n)
        other = sign_in(n, "Steve")
        n.presence(me["uuid"], {"visible": False})
        self.assertEqual(n.lookup(other["uuid"], {"uuids": [me["uuid"]]})[1]["players"], {})
        n.presence(me["uuid"], {"visible": True})
        clock.now += 151
        self.assertEqual(n.lookup(other["uuid"], {"uuids": [me["uuid"]]})[1]["players"], {})

    def test_unknown_or_invalid_cosmetics_are_dropped(self):
        n = network()
        me = sign_in(n)
        _, body = n.presence(me["uuid"], {"cosmetics": {"cape": "<script>", "hat_scale": 9999, "hat_color": "red",
                                                        "wings_flap": "yes", "anything": 1, "shield": "gold"}})
        c = body["cosmetics"]
        self.assertEqual(c["cape"], "none")
        self.assertEqual(c["hat_scale"], 100)
        self.assertEqual(c["hat_color"], "#FFFFFF")
        self.assertEqual(c["wings_flap"], True)
        self.assertNotIn("anything", c)
        self.assertEqual(c["shield"], "gold")

    def test_exclusive_cosmetics_need_the_role(self):
        n = network()
        n.whitelist.fields["cape"]["values"].append("creator")
        n.whitelist.exclusive = {"cape": {"creator": ["creator"]}}
        me = sign_in(n)
        other = sign_in(n, "Steve")
        self.assertEqual(n.presence(me["uuid"], {"cosmetics": {"cape": "creator"}})[1]["cosmetics"]["cape"], "creator")
        self.assertEqual(n.presence(other["uuid"], {"cosmetics": {"cape": "creator"}})[1]["cosmetics"]["cape"], "none")

    def test_at_most_100_players_per_lookup(self):
        n = network()
        me = sign_in(n)
        self.assertEqual(n.lookup(me["uuid"], {"uuids": [QRAGO] * 101})[0], 400)

    def test_deleting_forgets_the_player_and_their_sessions(self):
        n = network()
        me = sign_in(n)
        self.assertEqual(n.delete(me["uuid"]), (200, {"deleted": True}))
        self.assertIsNone(n.player({"Authorization": "Bearer " + me["token"]}))

    def test_stats_count_who_is_online(self):
        n = network()
        me = sign_in(n)
        n.presence(me["uuid"], {})
        self.assertEqual(n.stats("1.2.3.4"), (200, {"online": 1, "players": 1}))


class LimiterTest(unittest.TestCase):
    def test_a_bucket_refills_with_time(self):
        t = [0.0]
        limiter = Limiter(clock=lambda: t[0])
        self.assertTrue(all(limiter.allow("x", "k", 60, 3) for _ in range(3)))
        self.assertFalse(limiter.allow("x", "k", 60, 3))
        t[0] += 1.0
        self.assertTrue(limiter.allow("x", "k", 60, 3))


class HttpTest(unittest.TestCase):
    def test_the_whole_flow_over_http(self):
        n = network()
        server = serve(n, "127.0.0.1", 0)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        base = f"http://127.0.0.1:{server.server_address[1]}"

        def call(method, path, body=None, token=None):
            data = None if body is None else json.dumps(body).encode()
            req = urllib.request.Request(base + path, data=data, method=method)
            req.add_header("Content-Type", "application/json")
            if token:
                req.add_header("Authorization", "Bearer " + token)
            try:
                with urllib.request.urlopen(req, timeout=5) as r:
                    return r.status, json.loads(r.read())
            except urllib.error.HTTPError as e:
                return e.code, json.loads(e.read())

        try:
            self.assertEqual(call("GET", "/v1/health"), (200, {"ok": True}))
            status, root = call("GET", "/")
            self.assertEqual((status, root["service"], root["ok"]), (200, "Rago Network", True))
            self.assertEqual(call("GET", "/nothing-here"), (404, {"error": "not_found"}))
            status, start = call("POST", "/v1/auth/start", {"name": "qRago"})
            n.mojang.join("qRago", join_id(start["serverId"]))
            status, done = call("POST", "/v1/auth/finish", {"name": "qRago", "serverId": start["serverId"]})
            self.assertEqual(status, 200)
            self.assertEqual(call("POST", "/v1/presence", {"visible": True})[0], 401)
            status, body = call("POST", "/v1/presence", {"visible": True, "cosmetics": {"cape": "rago"}}, done["token"])
            self.assertEqual((status, body["online"]), (200, 1))
            status, body = call("POST", "/v1/lookup", {"uuids": [done["uuid"]]}, done["token"])
            self.assertEqual(body["players"][done["uuid"]]["cosmetics"]["cape"], "rago")
            self.assertEqual(call("POST", "/v1/lookup", "not an object", done["token"])[0], 400)
            self.assertEqual(call("DELETE", "/v1/me", token=done["token"]), (200, {"deleted": True}))
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
