# Benchmark procedure

The goal is a fair A/B comparison where the only difference between A and B is the thing being tested.

## Setup (once)

1. Build Rago: `./gradlew build`.
2. Create two launcher instances (Prism Launcher recommended) for **Minecraft 1.8.9 with Ornithe**:
   - **A (baseline):** Rago jar + Ornithe Standard Libraries + legacy-lwjgl3.
   - **B (test):** the same, plus the mods being tested (for example Argentum, Argentum Extras, Cera from Modrinth).
3. Use the same Java runtime, memory (`-Xmx`), window size, and `options.txt` in both.
   Copy `options.txt` from A to B so video settings match.
4. Settings for controlled runs: VSync **off**, Max Framerate **Unlimited**, render distance **8**,
   GUI scale the same in both, fullscreen off, same window size.

## Scene

- Create one singleplayer world with a fixed seed, for example `rago-bench-1`, in Creative, Peaceful.
- Copy that world folder into the other instance so both load the exact same chunks.
- Pick a spot, press F3 and write down X, Y, Z, yaw and pitch here. Before each run, teleport
  there with `/tp <x> <y> <z> <yaw> <pitch>` and do not touch the mouse or keyboard during the capture.

Fill in after the first session:

| World seed | Position (x y z yaw pitch) | Render distance | Window |
| --- | --- | --- | --- |
| | | | |

## Each run

1. Launch the instance, load the world, `/tp` to the spot.
2. Wait 15 s (warm-up: chunks load, JIT settles).
3. Press F12. Do nothing for 60 s until the overlay shows `saved ...`.
4. Quit the game completely.
5. Repeat 5 times per instance. Alternate A, B, A, B... to spread out heat and background noise.

Close other heavy apps (browser, Discord video, recording software) for every run, and keep a
laptop on the charger for all runs.

## Results

Copy the CSVs from each instance's `.minecraft/rago/` folder into `bench/results/<label>/`, then run:

```sh
python3 bench/analyse.py bench/results/vanilla --vs bench/results/argentum --label-a vanilla --label-b argentum
```

The script prints each run, the median per side, the change, and a Mann-Whitney U p-value per metric.
It also flags header lines (GPU, Java, render distance, window, mods) that differ between runs. If
something you did not intend to change differs, discard those runs.

CSV files are ignored by git by default. Commit a results folder deliberately with `git add -f` when
you want to publish a measurement, together with a short note on the hardware.

## Multiplayer field data

Captures on Hypixel or MMC are useful but are not controlled: player count, entities and server load
change between runs. Keep them in a separate folder (for example `bench/results/field-hypixel-lobby/`)
and never mix them with controlled results.
