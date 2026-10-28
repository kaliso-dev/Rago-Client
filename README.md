# Rago Client

Competitive Minecraft 1.8.9 client for Hypixel Bedwars and Minemen Club, built as a mod on
[Ornithe](https://ornithemc.net) (Fabric Loader + Feather mappings + Ornithe Standard Libraries).

**Current version: 0.4.0.** It contains the foundation every feature plugs into, the first PvP mods,
the performance probe from 0.0.1, and runs by default with the Argentum rendering engine:

- a module system: every feature is a module with an on/off switch and its own options
- a Rago main menu instead of the vanilla title screen (quick join for Hypixel and Minemen Club)
- the Rago menu (Right Shift, or *Rago Mods* on the main menu): mods as cards with categories,
  search, switches and an options page per mod with sliders
- a HUD editor: drag elements, snap them to the screen centre, resize them with the mouse wheel
- settings saved to `config/rago.json` (a broken file is set aside and defaults are used)
- PvP HUD: keystrokes (follows your bindings, with CPS), CPS counter, armour and held item with
  durability, potion effects with time left, coordinates and facing
- comfort mods: zoom (hold C, wheel to adjust), custom crosshair drawn in real pixels, fullbright,
  toggle sprint / sneak (input class, off by default)
- performance HUD: FPS (with 1% low), frame time p50/p99, **server-reported** ping with jitter, capture status
- a 60-second frame capture written to CSV, with hardware and settings in the header
- `bench/analyse.py` to compare two sets of captures (e.g. vanilla vs. vanilla + Argentum)

Every module declares a policy class (visual, informational, input, packet). Input-class modules
are off by default, and packet-class modules are refused at registration: Rago does not ship them.

Rago never changes what is sent to the server. Toggle sprint/sneak is the only module that affects
input: it makes the vanilla key read as held, nothing more, and it is off until you turn it on.

## Performance stack (instead of OptiFine)

OptiFine cannot be bundled (its license forbids redistribution) and does not run on Ornithe. Rago uses
the Ornithe rendering mods instead, downloaded from Modrinth and never copied into Rago's jar, because
they are "All Rights Reserved":

| Mod | What it does |
| --- | --- |
| [Argentum](https://modrinth.com/mod/argentum) | Rendering engine (Celeritas/Sodium lineage): terrain, entities, culling, video settings |
| [Argentum Extras](https://modrinth.com/mod/argentum-extras) | Particles, clouds, leaves, fog, weather, FXAA and other detail settings |
| [Cera](https://modrinth.com/mod/cera) | OptiFine/MCPatcher resource-pack features (connected textures, custom sky, …) |

`runClient` loads all three. Argentum is compiled for **Java 25**, so Gradle downloads a Java 25
runtime for the game on first launch (Rago itself is still built with Java 21). For an A/B benchmark,
run without them with `-PnoArgentum`.

## Requirements

- JDK 21 or newer to build (the Gradle wrapper downloads Gradle 9.7.0 itself)
- Python 3.8+ for `bench/analyse.py` (no extra packages)

## Build and test

```sh
./gradlew build          # compiles, runs the unit tests, writes build/libs/rago-0.4.0+1.8.9.jar
./gradlew test           # unit tests only
./gradlew runClient      # starts Minecraft 1.8.9 with Rago + Argentum stack in ./run (offline dev account)
./gradlew runClient -PnoArgentum   # same without Argentum, Extras and Cera (benchmark baseline)
```

On Windows (PowerShell) use `.\gradlew.bat` instead of `./gradlew`, e.g. `.\gradlew.bat runClient`. The dev client runs on LWJGL3 through
[legacy-lwjgl3](https://github.com/moehreag/legacy-lwjgl3), so it also works on Apple Silicon Macs.

## Controls

| Key | Action |
| --- | --- |
| Right Shift | Open the Rago menu (modules, options, *Edit HUD*) |
| F10 | Show / hide the whole Rago HUD |
| C (hold) | Zoom; mouse wheel changes the zoom level while held |
| F12 | Start / stop a 60 s frame capture (saved to `run/rago/frames-YYYYMMDD-HHMMSS.csv`) |

All keys can be changed in Options → Controls → Rago. On a Mac keyboard, press `fn` with the F key.

In the HUD editor: drag to move, mouse wheel to resize, right-click to reset an element, Esc to save
and go back. The HUD only draws while you are in a world.

## Measuring performance

See [`bench/README.md`](bench/README.md) for the exact procedure. In short: same world, same spot,
same settings, 5 captures per configuration, then:

```sh
python3 bench/analyse.py bench/results/vanilla --vs bench/results/argentum --label-a vanilla --label-b argentum
```

Report the numbers as measured, with the hardware lines from the CSV headers.

## What the numbers mean

| Value | Definition |
| --- | --- |
| FPS | frames ÷ elapsed time over the last 1,000 frames |
| 1% low | 1 ÷ mean of the slowest 1% of those frames |
| p50 / p99 | nearest-rank percentiles of frame time (start of one frame to start of the next) |
| ping (server) | the latency the server reports for you in the tab list; Rago sends no packets to measure it |
| jitter | standard deviation of the last 60 ping samples (one per second) |
| CPS | presses of your attack (or use) binding that the game registered in the last second |

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
