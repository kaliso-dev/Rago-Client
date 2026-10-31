"""Mojang's session server: did this player just "join" this server id?"""

import json
import urllib.error
import urllib.parse
import urllib.request


class SessionServer:
    def __init__(self, base="https://sessionserver.mojang.com", timeout=5):
        self.base = base.rstrip("/")
        self.timeout = timeout

    def has_joined(self, name, server_id):
        """The player's (uuid without dashes, name) when Mojang confirms the join, else None."""
        query = urllib.parse.urlencode({"username": name, "serverId": server_id})
        url = f"{self.base}/session/minecraft/hasJoined?{query}"
        try:
            with urllib.request.urlopen(url, timeout=self.timeout) as r:
                if r.status != 200:
                    return None
                body = json.loads(r.read(65536).decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError):
            return None
        uid, real = body.get("id"), body.get("name")
        if not isinstance(uid, str) or len(uid) != 32 or not isinstance(real, str):
            return None
        return uid.lower(), real
