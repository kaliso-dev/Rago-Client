"""SQLite storage: players, their sign-in sessions (only a hash of each token is kept), friends."""

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
CREATE INDEX IF NOT EXISTS users_name ON users(name COLLATE NOCASE);
-- a friendship is stored once per side, so each player's list is one indexed read
CREATE TABLE IF NOT EXISTS friends (
    uuid TEXT NOT NULL,
    friend TEXT NOT NULL,
    since INTEGER NOT NULL,
    PRIMARY KEY (uuid, friend)
);
CREATE TABLE IF NOT EXISTS friend_requests (
    sender TEXT NOT NULL,
    receiver TEXT NOT NULL,
    at INTEGER NOT NULL,
    PRIMARY KEY (sender, receiver)
);
CREATE INDEX IF NOT EXISTS friend_requests_receiver ON friend_requests(receiver);
"""

FORGET = ("DELETE FROM sessions WHERE uuid = ?", "DELETE FROM friends WHERE uuid = ?1 OR friend = ?1",
          "DELETE FROM friend_requests WHERE sender = ?1 OR receiver = ?1", "DELETE FROM users WHERE uuid = ?")


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
            return self._forget(uuid)

    def _forget(self, uuid):
        """Removes the player, their sessions, friends and requests; the lock is held. The count of
        players removed (0 or 1)."""
        for sql in FORGET[:-1]:
            self._db.execute(sql, (uuid,))
        return self._db.execute(FORGET[-1], (uuid,)).rowcount

    # friends

    def find(self, name):
        """(uuid, name) of the Rago player with this name (any case), or None."""
        with self._lock:
            row = self._db.execute("SELECT uuid, name FROM users WHERE name = ? COLLATE NOCASE "
                                   "ORDER BY last_seen DESC LIMIT 1", (name,)).fetchone()
        return tuple(row) if row else None

    def friend_counts(self, uuid):
        """(friends, requests sent and still waiting)."""
        with self._lock:
            friends = self._db.execute("SELECT COUNT(*) FROM friends WHERE uuid = ?", (uuid,)).fetchone()[0]
            sent = self._db.execute("SELECT COUNT(*) FROM friend_requests WHERE sender = ?", (uuid,)).fetchone()[0]
        return friends, sent

    def relation(self, uuid, other):
        """"friends", "sent" (uuid asked other), "received" (other asked uuid) or None."""
        with self._lock:
            if self._db.execute("SELECT 1 FROM friends WHERE uuid = ? AND friend = ?", (uuid, other)).fetchone():
                return "friends"
            if self._db.execute("SELECT 1 FROM friend_requests WHERE sender = ? AND receiver = ?", (uuid, other)).fetchone():
                return "sent"
            if self._db.execute("SELECT 1 FROM friend_requests WHERE sender = ? AND receiver = ?", (other, uuid)).fetchone():
                return "received"
        return None

    def ask(self, sender, receiver, now):
        with self._lock:
            self._db.execute("INSERT OR IGNORE INTO friend_requests(sender, receiver, at) VALUES(?, ?, ?)",
                             (sender, receiver, now))

    def befriend(self, a, b, now):
        """Makes a and b friends and drops the requests between them."""
        with self._lock:
            self._db.execute("BEGIN")
            try:
                self._db.execute("DELETE FROM friend_requests WHERE (sender = ?1 AND receiver = ?2) OR (sender = ?2 AND receiver = ?1)",
                                 (a, b))
                self._db.execute("INSERT OR IGNORE INTO friends(uuid, friend, since) VALUES(?, ?, ?)", (a, b, now))
                self._db.execute("INSERT OR IGNORE INTO friends(uuid, friend, since) VALUES(?, ?, ?)", (b, a, now))
                self._db.execute("COMMIT")
            except BaseException:
                self._db.execute("ROLLBACK")
                raise

    def unfriend(self, a, b):
        """Ends a friendship, a request either way, or both. Whether anything was removed."""
        with self._lock:
            n = self._db.execute("DELETE FROM friends WHERE (uuid = ?1 AND friend = ?2) OR (uuid = ?2 AND friend = ?1)",
                                 (a, b)).rowcount
            n += self._db.execute("DELETE FROM friend_requests WHERE (sender = ?1 AND receiver = ?2) OR (sender = ?2 AND receiver = ?1)",
                                  (a, b)).rowcount
        return n > 0

    def friends(self, uuid, since):
        """The player's friends as (uuid, name, role, online) with online meaning seen since then and
        showing they use Rago, and the requests as (uuid, name) received and sent."""
        with self._lock:
            friends = self._db.execute(
                "SELECT u.uuid, u.name, u.role, (u.visible = 1 AND u.last_seen >= ?) FROM friends f "
                "JOIN users u ON u.uuid = f.friend WHERE f.uuid = ? ORDER BY u.name COLLATE NOCASE", (since, uuid)).fetchall()
            received = self._db.execute(
                "SELECT u.uuid, u.name FROM friend_requests r JOIN users u ON u.uuid = r.sender "
                "WHERE r.receiver = ? ORDER BY r.at DESC", (uuid,)).fetchall()
            sent = self._db.execute(
                "SELECT u.uuid, u.name FROM friend_requests r JOIN users u ON u.uuid = r.receiver "
                "WHERE r.sender = ? ORDER BY r.at DESC", (uuid,)).fetchall()
        return [(u, n, r, bool(o)) for u, n, r, o in friends], [tuple(x) for x in received], [tuple(x) for x in sent]

    def prune(self, now, forget_after):
        """Drops expired sessions and players not seen for `forget_after` seconds."""
        with self._lock:
            self._db.execute("DELETE FROM sessions WHERE expires <= ?", (now,))
            gone = [r[0] for r in self._db.execute("SELECT uuid FROM users WHERE last_seen < ?", (now - forget_after,))]
            for uuid in gone:
                self._forget(uuid)
        return len(gone)
