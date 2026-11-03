"""Downloads what the loader's compatibility API compiles against: Minecraft 1.8.9's client jar
and libraries from Mojang, and Forge's jar (only for the names it gives the game's classes and
members). Every file is checked against the SHA-1 Mojang or the launcher's Forge lock gives.

usage: python3 game.py <folder>   ->  <folder>/1.8.9.jar, <folder>/forge.jar, <folder>/libraries/
Nothing downloaded here is put in the loader's jar.
"""

import hashlib
import json
import os
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# the same version file the launcher pins (launcher/core/src/mojang.rs)
VERSION_URL = "https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json"
VERSION_SHA1 = "d546f1707a3f2b7d034eece5ea2e311eda875787"


def fetch(url, sha1, path):
    if os.path.isfile(path) and hashlib.sha1(open(path, "rb").read()).hexdigest() == sha1:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # Forge's maven refuses Python's default User-Agent
    request = urllib.request.Request(url, headers={"User-Agent": "rago-loader-ci"})
    data = urllib.request.urlopen(request, timeout=60).read()
    got = hashlib.sha1(data).hexdigest()
    if got != sha1:
        sys.exit(f"{url}: SHA-1 {got}, expected {sha1}")
    with open(path + ".part", "wb") as f:
        f.write(data)
    os.replace(path + ".part", path)


def allowed(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    ok = False
    for r in rules:
        if "os" in r and r["os"].get("name") != "linux":
            continue
        ok = r["action"] == "allow"
    return ok


def main(out):
    version_path = os.path.join(out, "1.8.9.json")
    fetch(VERSION_URL, VERSION_SHA1, version_path)
    version = json.load(open(version_path))
    client = version["downloads"]["client"]
    fetch(client["url"], client["sha1"], os.path.join(out, "1.8.9.jar"))
    count = 0
    for lib in version["libraries"]:
        artifact = lib.get("downloads", {}).get("artifact")
        if not allowed(lib) or "natives" in lib or not artifact:
            continue
        path = os.path.normpath(os.path.join(out, "libraries", artifact["path"]))
        if not path.startswith(os.path.join(out, "libraries")):
            sys.exit(f"bad library path {artifact['path']}")
        fetch(artifact["url"], artifact["sha1"], path)
        count += 1
    lock = json.load(open(os.path.join(ROOT, "launcher", "core", "data", "forge-1.8.9.json")))
    forge = next(l for l in lock["libraries"] if l["name"].startswith("net.minecraftforge:forge:"))
    fetch(forge["url"], forge["sha1"], os.path.join(out, "forge.jar"))
    print(f"Minecraft 1.8.9, {count} libraries and Forge's names in {out}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(os.path.abspath(sys.argv[1]))
