# Rago Client

Competitive Minecraft 1.8.9 client for Hypixel Bedwars and Minemen Club, built as a mod on
[Ornithe](https://ornithemc.net) (Fabric Loader + Feather mappings + Ornithe Standard Libraries).

**Current version: 0.0.1, the frame-time and ping probe.** It does not add PvP features yet.
It exists to prove the toolchain and to measure performance honestly before anything else is built:

- an overlay with FPS, 1% low, frame-time p50/p99 and the **server-reported** ping with jitter
- a 60-second frame capture written to CSV, with hardware and settings in the header
- `bench/analyse.py` to compare two sets of captures (e.g. vanilla vs. vanilla + Argentum)

Rago is informational only: it never changes gameplay, inputs, or what is sent to the server.

## Requirements

- JDK 21 or newer to build (the Gradle wrapper downloads Gradle 9.7.0 itself)
- Python 3.8+ for `bench/analyse.py` (no extra packages)

## Build and test

```sh
./gradlew build          # compiles, runs the unit tests, writes build/libs/rago-0.0.1+1.8.9.jar
./gradlew test           # unit tests only
./gradlew runClient      # starts Minecraft 1.8.9 with Rago in ./run (offline dev account)
```

On Windows use `gradlew.bat` instead of `./gradlew`. The dev client runs on LWJGL3 through
[legacy-lwjgl3](https://github.com/moehreag/legacy-lwjgl3), so it also works on Apple Silicon Macs.

## Controls

| Key | Action |
| --- | --- |
| F10 | Show / hide the overlay |
| F12 | Start / stop a 60 s frame capture (saved to `run/rago/frames-YYYYMMDD-HHMMSS.csv`) |

Both keys can be changed in Options → Controls → Rago. On a Mac keyboard, press `fn` with the F key.

The overlay only draws while you are in a world.

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

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
