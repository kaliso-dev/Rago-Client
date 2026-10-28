# Rago Client

Competitive Minecraft 1.8.9 client for Hypixel Bedwars and Minemen Club, built as a mod on
[Ornithe](https://ornithemc.net) (Fabric Loader + Feather mappings + Ornithe Standard Libraries).

**Current version: 0.2.0, the first PvP mods.** It contains the foundation every feature plugs into,
the first PvP HUD mods and the performance probe from 0.0.1:

- a module system: every feature is a module with an on/off switch and its own options
- the Rago menu (Right Shift) to toggle modules and change their options
- a HUD editor: drag elements, snap them to the screen centre, resize them with the mouse wheel
- settings saved to `config/rago.json` (a broken file is set aside and defaults are used)
- PvP HUD: keystrokes (follows your bindings, with CPS), CPS counter, armour and held item with
  durability, potion effects with time left, coordinates and facing
- performance HUD: FPS (with 1% low), frame time p50/p99, **server-reported** ping with jitter, capture status
- a 60-second frame capture written to CSV, with hardware and settings in the header
- `bench/analyse.py` to compare two sets of captures (e.g. vanilla vs. vanilla + Argentum)

Every module declares a policy class (visual, informational, input, packet). Input-class modules
are off by default, and packet-class modules are refused at registration: Rago does not ship them.

Rago is informational only: it never changes gameplay, inputs, or what is sent to the server.

## Requirements

- JDK 21 or newer to build (the Gradle wrapper downloads Gradle 9.7.0 itself)
- Python 3.8+ for `bench/analyse.py` (no extra packages)

## Build and test

```sh
./gradlew build          # compiles, runs the unit tests, writes build/libs/rago-0.2.0+1.8.9.jar
./gradlew test           # unit tests only
./gradlew runClient      # starts Minecraft 1.8.9 with Rago in ./run (offline dev account)
```

On Windows (PowerShell) use `.\gradlew.bat` instead of `./gradlew`, e.g. `.\gradlew.bat runClient`. The dev client runs on LWJGL3 through
[legacy-lwjgl3](https://github.com/moehreag/legacy-lwjgl3), so it also works on Apple Silicon Macs.

## Controls

| Key | Action |
| --- | --- |
| Right Shift | Open the Rago menu (modules, options, *Edit HUD*) |
| F10 | Show / hide the whole Rago HUD |
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
