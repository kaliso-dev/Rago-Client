#!/usr/bin/env bash
# Builds and tests Rago's loader with its compatibility API: downloads Minecraft 1.8.9 (game.py),
# renames it with the loader itself, then compiles the API against it. Run from the repository's
# root with Java 21; the jar ends in loader/build/libs/.
set -euo pipefail
game="${1:-${RUNNER_TEMP:-/tmp}/rago-loader-game}"
python3 loader/ci/game.py "$game"
./gradlew --no-daemon -q -p loader jar
java -cp loader/build/libs/rago-loader-*.jar gg.rago.loader.Main remap "$game/forge.jar" "$game/1.8.9.jar" "$game/1.8.9-renamed.jar"
./gradlew --no-daemon -p loader build -PgameJar="$game/1.8.9-renamed.jar" -PgameLibs="$game/libraries"
jar=$(ls loader/build/libs/rago-loader-*.jar)
# the compatibility API is in, nothing of the game is
entries=$(unzip -l "$jar")
grep -q 'gg/rago/loader/compat/Hooks.class' <<< "$entries"
if grep -E ' net/minecraft/(client|server|world|entity|block|item|util)/' <<< "$entries"; then
  echo "game classes in $jar" >&2
  exit 1
fi
echo "built $jar"
