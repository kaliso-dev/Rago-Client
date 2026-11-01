"""SQLite storage: players, their sign-in sessions (only a hash of each token is kept)."""

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
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS users_last_seen ON users(last_seen);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    uuid TEXT NOT NULL,
    expires INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_uuid ON sessions(uuid);
"""


def token_hash(token):
    return hashlib.sha256(token.encode("ascii")).hexdigest()


class Store:
    def __init__(self, path):
        self._db = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._lock = threading.Lock()
        with self._lock:
            if path != ":memory:":
                self._db.execute("PRAGMA journal_mode=WAL")
                # with WAL, NORMAL never corrupts the database; it only skips a disk flush per write,
                # so a presence (one small UPDATE) does not wait for the disk
                self._db.execute("PRAGMA synchronous=NORMAL")
            self._db.executescript(SCHEMA)

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
        with self._lock:
            self._db.execute("DELETE FROM sessions WHERE uuid = ?", (uuid,))
            return self._db.execute("DELETE FROM users WHERE uuid = ?", (uuid,)).rowcount

    def prune(self, now, forget_after):
        """Drops expired sessions and players not seen for `forget_after` seconds."""
        with self._lock:
            self._db.execute("DELETE FROM sessions WHERE expires <= ?", (now,))
            gone = [r[0] for r in self._db.execute("SELECT uuid FROM users WHERE last_seen < ?", (now - forget_after,))]
            for uuid in gone:
                self._db.execute("DELETE FROM sessions WHERE uuid = ?", (uuid,))
                self._db.execute("DELETE FROM users WHERE uuid = ?", (uuid,))
        return len(gone)
