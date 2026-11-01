"""Checks a loadout (the cosmetics a player shows) against cosmetics.json."""

import json
import re

_COLOR = re.compile(r"^#[0-9A-Fa-f]{6}$")


class Whitelist:
    def __init__(self, data):
        self.fields = data["fields"]
        # {"field": {"value": ["role" or player uuid (32 hex digits), ...]}}
        self.exclusive = data.get("exclusive", {})

    @classmethod
    def load(cls, path):
        with open(path, encoding="utf-8") as f:
            return cls(json.load(f))

    def clean(self, loadout, role, uuid=None):
        """The loadout with unknown fields dropped and anything invalid put back to its default; an
        exclusive value is kept for the roles and the players (by uuid) it lists."""
        if not isinstance(loadout, dict):
            loadout = {}
        player = uuid.replace("-", "").lower() if isinstance(uuid, str) else None
        out = {}
        for key, spec in self.fields.items():
            value = loadout.get(key, spec["default"])
            out[key] = value if self._valid(key, spec, value, role, player) else spec["default"]
        return out

    def _valid(self, key, spec, value, role, player):
        kind = spec["type"]
        if kind == "choice":
            if not isinstance(value, str) or value not in spec["values"]:
                return False
            roles = self.exclusive.get(key, {}).get(value)
            return roles is None or role in roles or player is not None and player in roles
        if kind == "color":
            return isinstance(value, str) and _COLOR.match(value) is not None
        if kind == "int":
            return isinstance(value, int) and not isinstance(value, bool) and spec["min"] <= value <= spec["max"]
        if kind == "bool":
            return isinstance(value, bool)
        return False
