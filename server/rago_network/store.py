"""Storage: players, their sign-in sessions (only a hash of each token is kept), and what they have
won: Rago Points, cosmetics, fragments, the chests opened, time played and every change of points.

In SQLite (a file, or ":memory:" for the tests) or in PostgreSQL ("postgresql://..." as the database,
for example a free Neon database, so that nothing is lost when the server restarts)."""

import hashlib
import json
import sqlite3
import threading

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'user',
    visible INTEGER NOT NULL DEFAULT 1,
    loadout TEXT NOT NULL DEFAULT '{}',
    version TEXT,
    first_seen BIGINT NOT NULL,
    last_seen BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS users_last_seen ON users(last_seen);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    uuid TEXT NOT NULL,
    expires BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_uuid ON sessions(uuid);
CREATE TABLE IF NOT EXISTS wallets (
    uuid TEXT PRIMARY KEY,
    points INTEGER NOT NULL DEFAULT 0,
    streak INTEGER NOT NULL DEFAULT 0,
    last_day INTEGER,
    joker_day INTEGER,
    since_epic INTEGER NOT NULL DEFAULT 0,
    since_legendary INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS owned (
    uuid TEXT NOT NULL,
    item TEXT NOT NULL,
    source TEXT NOT NULL,
    at BIGINT NOT NULL,
    PRIMARY KEY (uuid, item)
);
CREATE TABLE IF NOT EXISTS fragments (
    uuid TEXT NOT NULL,
    item TEXT NOT NULL,
    count INTEGER NOT NULL,
    PRIMARY KEY (uuid, item)
);
CREATE TABLE IF NOT EXISTS chests (
    uuid TEXT NOT NULL,
    day INTEGER NOT NULL,
    cards TEXT NOT NULL,
    at BIGINT NOT NULL,
    PRIMARY KEY (uuid, day)
);
CREATE TABLE IF NOT EXISTS ledger (
    id {serial},
    uuid TEXT NOT NULL,
    delta INTEGER NOT NULL,
    reason TEXT NOT NULL,
    ref TEXT,
    at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS ledger_uuid ON ledger(uuid, at);
CREATE TABLE IF NOT EXISTS purchases (
    uuid TEXT NOT NULL,
    key TEXT NOT NULL,
    result TEXT NOT NULL,
    at BIGINT NOT NULL,
    PRIMARY KEY (uuid, key)
);
CREATE TABLE IF NOT EXISTS playtime (
    uuid TEXT NOT NULL,
    season TEXT NOT NULL,
    minutes INTEGER NOT NULL DEFAULT 0,
    day INTEGER,
    day_minutes INTEGER NOT NULL DEFAULT 0,
    last_credit BIGINT NOT NULL DEFAULT 0,
    tier INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, season)
);
"""

ECONOMY_TABLES = ("wallets", "owned", "fragments", "chests", "ledger", "purchases", "playtime")


class _Db:
    """One connection, to SQLite or PostgreSQL, used the same way: "?" placeholders, execute() giving
    something to fetch from, begin/commit/rollback. A PostgreSQL connection that was dropped (a free
    database sleeps when nobody uses it) is opened again on the next statement outside a transaction."""

    def __init__(self, target):
        self.pg = target.startswith(("postgres://", "postgresql://"))
        self.target = target
        self.in_transaction = False
        self._conn = None
        self._open()

    def _open(self):
        if self.pg:
            import psycopg  # only needed with PostgreSQL
            self._conn = psycopg.connect(self.target, autocommit=True, connect_timeout=15)
        else:
            self._conn = sqlite3.connect(self.target, check_same_thread=False, isolation_level=None)
            if self.target != ":memory:":
                self._conn.execute("PRAGMA journal_mode=WAL")
                # with WAL, NORMAL never corrupts the database; it only skips a disk flush per write,
                # so a presence (one small UPDATE) does not wait for the disk
                self._conn.execute("PRAGMA synchronous=NORMAL")

    def execute(self, sql, params=()):
        if not self.pg:
            return self._conn.execute(sql, params)
        import psycopg
        sql = sql.replace("?", "%s")
        try:
            return self._conn.execute(sql, params)
        except psycopg.OperationalError:
            if self.in_transaction:
                raise
            self._conn.close()
            self._open()
            return self._conn.execute(sql, params)

    def script(self, schema):
        schema = schema.replace("{serial}", "BIGSERIAL PRIMARY KEY" if self.pg else "INTEGER PRIMARY KEY AUTOINCREMENT")
        if self.pg:
            for statement in schema.split(";"):
                if statement.strip():
                    self.execute(statement)
        else:
            self._conn.executescript(schema)

    def begin(self):
        self.execute("BEGIN" if self.pg else "BEGIN IMMEDIATE")
        self.in_transaction = True

    def close(self):
        self._conn.close()

    def commit(self):
        self.in_transaction = False
        self.execute("COMMIT")

    def rollback(self):
        self.in_transaction = False
        try:
            self.execute("ROLLBACK")
        except Exception:
            # the connection is gone with the transaction: open it again on the next statement
            if self.pg:
                self._conn.close()
                self._open()

NEW_WALLET = {"points": 0, "streak": 0, "last_day": None, "joker_day": None, "since_epic": 0, "since_legendary": 0}


def token_hash(token):
    return hashlib.sha256(token.encode("ascii")).hexdigest()


class Store:
    def __init__(self, path):
        self._db = _Db(path)
        self._lock = threading.Lock()
        with self._lock:
            self._db.script(SCHEMA)

    def close(self):
        with self._lock:
            self._db.close()

    def sign_in(self, uuid, name, role, token, expires, now):
        """Records the player (name and role as of now) and a new session for them."""
        with self._lock:
            self._db.execute(
                "INSERT INTO users(uuid, name, role, first_seen, last_seen) VALUES(?, ?, ?, ?, ?) "
                "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, "
                "role = CASE WHEN excluded.role != 'user' THEN excluded.role ELSE users.role END",
                (uuid, name, role, now, now))
            self._db.execute("INSERT INTO sessions(token_hash, uuid, expires) VALUES(?, ?, ?)",
                             (token_hash(token), uuid, expires))

    def session(self, token, now):
        """The uuid a live session token belongs to, or None."""
        with self._lock:
            row = self._db.execute("SELECT uuid FROM sessions WHERE token_hash = ? AND expires > ?",
                                   (token_hash(token), now)).fetchone()
        return row[0] if row else None

    def role(self, uuid):
        with self._lock:
            row = self._db.execute("SELECT role FROM users WHERE uuid = ?", (uuid,)).fetchone()
        return row[0] if row else "user"

    def set_role(self, uuid, role):
        with self._lock:
            return self._db.execute("UPDATE users SET role = ? WHERE uuid = ?", (role, uuid)).rowcount

    def presence(self, uuid, visible, loadout, version, now):
        with self._lock:
            self._db.execute("UPDATE users SET visible = ?, loadout = ?, version = ?, last_seen = ? WHERE uuid = ?",
                             (1 if visible else 0, json.dumps(loadout, separators=(",", ":")), version, now, uuid))

    def lookup(self, uuids, since):
        """{uuid: (role, loadout)} for the given players who show they use Rago and were online since then."""
        if not uuids:
            return {}
        marks = ",".join("?" * len(uuids))
        with self._lock:
            rows = self._db.execute(
                f"SELECT uuid, role, loadout FROM users WHERE visible = 1 AND last_seen >= ? AND uuid IN ({marks})",
                (since, *uuids)).fetchall()
        return {u: (r, json.loads(l)) for u, r, l in rows}

    def counts(self, since):
        with self._lock:
            online = self._db.execute("SELECT COUNT(*) FROM users WHERE last_seen >= ?", (since,)).fetchone()[0]
            total = self._db.execute("SELECT COUNT(*) FROM users").fetchone()[0]
        return online, total

    def delete(self, uuid):
        """Forgets the player: their sessions and everything they won."""
        with self._lock:
            self._db.execute("DELETE FROM sessions WHERE uuid = ?", (uuid,))
            for table in ECONOMY_TABLES:
                self._db.execute(f"DELETE FROM {table} WHERE uuid = ?", (uuid,))
            return self._db.execute("DELETE FROM users WHERE uuid = ?", (uuid,)).rowcount

    def prune(self, now, forget_after):
        """Drops expired sessions and players not seen for `forget_after` seconds (what they won is
        kept: a player who comes back after a long break finds their collection)."""
        with self._lock:
            self._db.execute("DELETE FROM sessions WHERE expires <= ?", (now,))
            gone = [r[0] for r in self._db.execute("SELECT uuid FROM users WHERE last_seen < ?", (now - forget_after,))]
            for uuid in gone:
                self._db.execute("DELETE FROM sessions WHERE uuid = ?", (uuid,))
                self._db.execute("DELETE FROM users WHERE uuid = ?", (uuid,))
        return len(gone)

    # --- what players have won ---

    def _transaction(self, work):
        """Runs work() (which uses self._db) in one transaction, under the lock: all of it or none."""
        with self._lock:
            self._db.begin()
            try:
                result = work()
            except BaseException:
                self._db.rollback()
                raise
            self._db.commit()
            return result

    def _wallet(self, uuid):
        row = self._db.execute("SELECT points, streak, last_day, joker_day, since_epic, since_legendary "
                               "FROM wallets WHERE uuid = ?", (uuid,)).fetchone()
        if row is None:
            return dict(NEW_WALLET)
        return dict(zip(("points", "streak", "last_day", "joker_day", "since_epic", "since_legendary"), row))

    def _put_wallet(self, uuid, w):
        self._db.execute(
            "INSERT INTO wallets(uuid, points, streak, last_day, joker_day, since_epic, since_legendary) "
            "VALUES(?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET points = excluded.points, "
            "streak = excluded.streak, last_day = excluded.last_day, joker_day = excluded.joker_day, "
            "since_epic = excluded.since_epic, since_legendary = excluded.since_legendary",
            (uuid, w["points"], w["streak"], w["last_day"], w["joker_day"], w["since_epic"], w["since_legendary"]))

    def _owned(self, uuid):
        return {r[0] for r in self._db.execute("SELECT item FROM owned WHERE uuid = ?", (uuid,))}

    def _fragments(self, uuid):
        return {r[0]: r[1] for r in self._db.execute("SELECT item, count FROM fragments WHERE uuid = ?", (uuid,))}

    def _give(self, uuid, item, source, now):
        self._db.execute("INSERT INTO owned(uuid, item, source, at) VALUES(?, ?, ?, ?) ON CONFLICT DO NOTHING",
                         (uuid, item, source, now))
        self._db.execute("DELETE FROM fragments WHERE uuid = ? AND item = ?", (uuid, item))

    def _log(self, uuid, delta, reason, ref, now):
        if delta:
            self._db.execute("INSERT INTO ledger(uuid, delta, reason, ref, at) VALUES(?, ?, ?, ?, ?)",
                             (uuid, delta, reason, ref, now))

    def wallet(self, uuid):
        """{points, streak, last_day, joker_day, since_epic, since_legendary, owned, fragments}."""
        with self._lock:
            w = self._wallet(uuid)
            w["owned"] = self._owned(uuid)
            w["fragments"] = self._fragments(uuid)
        return w

    def owned(self, uuid):
        with self._lock:
            return self._owned(uuid)

    def chest(self, uuid, day):
        """That day's chest ({cards, gifts, streak, won}) if the player opened it, else None."""
        with self._lock:
            row = self._db.execute("SELECT cards FROM chests WHERE uuid = ? AND day = ?", (uuid, day)).fetchone()
        return json.loads(row[0]) if row else None

    def open_chest(self, uuid, day, draw, now):
        """Opens the chest of `day` once: draw(wallet, owned, fragments) gives (cards, new streak state,
        hand, gifts) and everything is saved together. Returns (chest, wallet after, opened now?), the
        chest being {cards, gifts, streak, won}; a second call for the same day gives the same chest
        back and changes nothing."""

        def work():
            row = self._db.execute("SELECT cards FROM chests WHERE uuid = ? AND day = ?", (uuid, day)).fetchone()
            if row is not None:
                return json.loads(row[0]), self._wallet(uuid), False
            w = self._wallet(uuid)
            cards, state, hand, gifts = draw(w, self._owned(uuid), self._fragments(uuid))
            w.update(state)
            w["points"] += hand.points
            self._put_wallet(uuid, w)
            for item in hand.new_items:
                self._give(uuid, item, "streak" if item in gifts else "chest", now)
            for item, count in hand.new_fragments.items():
                if item not in hand.new_items:
                    self._db.execute("INSERT INTO fragments(uuid, item, count) VALUES(?, ?, ?) "
                                     "ON CONFLICT(uuid, item) DO UPDATE SET count = excluded.count", (uuid, item, count))
            chest = {"cards": cards, "gifts": gifts, "streak": state["streak"], "won": hand.points}
            self._db.execute("INSERT INTO chests(uuid, day, cards, at) VALUES(?, ?, ?, ?)",
                             (uuid, day, json.dumps(chest, separators=(",", ":")), now))
            self._log(uuid, hand.points, "chest", str(day), now)
            return chest, w, True

        return self._transaction(work)

    def spend(self, uuid, key, items, cost, reason, now, check=None):
        """Takes `cost` points and gives `items`, once per `key` (a second call with the same key gives
        the first result back). check(owned, fragments) may refuse with an error string. Returns
        (result dict, wallet points after)."""

        def work():
            row = self._db.execute("SELECT result FROM purchases WHERE uuid = ? AND key = ?", (uuid, key)).fetchone()
            if row is not None:
                return json.loads(row[0]), self._wallet(uuid)["points"]
            w = self._wallet(uuid)
            owned = self._owned(uuid)
            error = check(owned, self._fragments(uuid)) if check else None
            if error is None and w["points"] < cost:
                error = "not_enough_points"
            if error is not None:
                return {"error": error}, w["points"]
            w["points"] -= cost
            self._put_wallet(uuid, w)
            for item in items:
                self._give(uuid, item, reason, now)
            self._log(uuid, -cost, reason, ",".join(items), now)
            result = {"items": list(items), "cost": cost}
            self._db.execute("INSERT INTO purchases(uuid, key, result, at) VALUES(?, ?, ?, ?)",
                             (uuid, key, json.dumps(result, separators=(",", ":")), now))
            return result, w["points"]

        return self._transaction(work)

    def grant(self, uuid, item, source, now):
        """Gives a cosmetic or badge for free (a founder badge, an event); True if it is new."""

        def work():
            had = self._db.execute("SELECT 1 FROM owned WHERE uuid = ? AND item = ?", (uuid, item)).fetchone()
            if had is None:
                self._give(uuid, item, source, now)
            return had is None

        return self._transaction(work)

    def ledger(self, uuid, limit=50):
        """The player's latest changes of points, newest first: (delta, reason, ref, at)."""
        with self._lock:
            return self._db.execute("SELECT delta, reason, ref, at FROM ledger WHERE uuid = ? ORDER BY at DESC, "
                                    "id DESC LIMIT ?", (uuid, limit)).fetchall()
