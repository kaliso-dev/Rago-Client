# Rago Client

Competitive Minecraft 1.8.9 client for Hypixel Bedwars and Minemen Club, built as a
[Minecraft Forge](https://files.minecraftforge.net) 1.8.9 mod so it runs next to OptiFine.

**Current version: 0.7.0.** What it contains:

- a clean home screen (emblem, Singleplayer / Multiplayer / Play Hypixel, a dock of shortcuts) over a
  blurred landscape, and a helper that links to the OptiFine download when OptiFine is missing
- the Rago window (Right Shift): a **Mods** tab with mod profiles (Default, Bedwars, Practice,
  Skywars, or your own), mod cards with Enabled/Disabled, filters, sorting and search, and a page per
  mod with a live preview; a **Settings** tab with General, Performance and Controls pages
- menu animations (fades, slides, hover transitions), with a switch to turn them off, and an optional
  blur of the game behind the window
- a Performance page with Rago's own optimisations, Minecraft's video settings and, when OptiFine is
  installed, its performance, detail, animation and particle settings in one searchable list:
  - frame caps: menus above vanilla's fixed 30 FPS, and a lower cap while the window is unfocused
  - entity render distances for players, hostile mobs, passive mobs and other entities
  - hide arrows stuck in players, item frames, paintings, armor stands, XP orbs, fireworks, falling blocks
  - tile entity distance, and hide skulls, signs, enchanting books, banners, beacon beams, end portals
  - hide tall grass, ferns, flowers and dead bushes, a particle cap and a fog switch
- a Controls page to rebind every key (keyboard or mouse), with conflicts shown in red
- a module system: every feature is a module with an on/off switch and its own options
- a HUD editor: drag elements, snap them to the screen centre, resize them with the mouse wheel
- settings saved to `config/rago.json` (a broken file is set aside and defaults are used)
- PvP HUD: keystrokes (follows your bindings, with CPS), CPS counter, armour and held item with
  durability, potion effects with time left, coordinates and facing
- comfort mods: zoom (hold C, wheel to adjust), custom crosshair drawn in real pixels, fullbright,
  toggle sprint / sneak (input class, off by default)
- a competitive profile: one switch sets fast graphics, minimal particles, no clouds or shadows and
  uncapped FPS, plus OptiFine's own settings when OptiFine is installed (fast math, no smooth FPS,
  no dynamic FOV, fast leaves, no sky, stars, vignette or animated smoke/flame/explosions). Potion
  particles stay on so you can read your opponent's effects. Your own values are saved to
  `config/rago-profile-backup.json` and put back when you switch it off.
- performance HUD: FPS (with 1% low), frame time p50/p99, **server-reported** ping with jitter, capture status
- a 60-second frame capture written to CSV, with hardware and settings in the header
- `bench/analyse.py` to compare two sets of captures (e.g. without vs. with OptiFine)

Every module declares a policy class (visual, informational, input, packet). Input-class modules
are off by default, and packet-class modules are refused at registration: Rago does not ship them.

Rago never changes what is sent to the server. Every optimisation only changes what this client
draws. The jar contains one class transformer (`gg.rago.asm`), which only replaces the frame-rate
cap vanilla uses for menus and unfocused windows; everything else uses Forge events.

Toggle sprint/sneak is the only module that affects
input: it makes the vanilla key read as held, nothing more, and it is off until you turn it on.

## Install (players)

1. Install **Forge 1.8.9 - 11.15.1.2318** with the Forge installer ("Install client").
2. Download **OptiFine 1.8.9 HD U M5** yourself from [optifine.net](https://optifine.net/downloads)
   and put the jar in `.minecraft/mods`. Rago does not ship OptiFine: its license does not allow
   redistribution without permission from its author.
3. Put `rago-0.7.0+1.8.9.jar` in `.minecraft/mods`.
4. Start the *forge* profile in the Minecraft launcher.

OptiFine is optional: without it Rago runs on plain Forge, and the competitive profile only changes
the vanilla settings.

## Requirements (developers)

- A JDK 17 to 21 to run Gradle (the wrapper downloads Gradle 8.8, which cannot run on Java 22+). The mod and the game use
  **Java 8**, which Gradle downloads by itself on the first build.
- Python 3.8+ for `bench/analyse.py` (no extra packages)

## Build and test

```sh
./gradlew build          # compiles, runs the unit tests, writes build/libs/rago-0.7.0+1.8.9.jar
./gradlew test           # unit tests only
./gradlew runClient      # starts Minecraft 1.8.9 + Forge with Rago in ./run (offline dev account, no OptiFine)
```

On Windows (PowerShell) use `.\gradlew.bat` instead of `./gradlew`, e.g. `.\gradlew.bat runClient`.
To test with OptiFine, install the built jar in a real Forge profile as described above.

## Controls

| Key | Action |
| --- | --- |
| Right Shift | Open the Rago menu (modules, options, *Edit HUD*) |
| F10 | Show / hide the whole Rago HUD |
| C (hold) | Zoom; mouse wheel changes the zoom level while held |
| F12 | Start / stop a 60 s frame capture (saved to `rago/frames-YYYYMMDD-HHMMSS.csv`) |

All keys can be changed in Options → Controls → Rago. On a Mac keyboard, press `fn` with the F key.

In the HUD editor: drag to move, mouse wheel to resize, right-click to reset an element, Esc to save
and go back. The HUD only draws while you are in a world.

## Measuring performance

See [`bench/README.md`](bench/README.md) for the exact procedure. In short: same world, same spot,
same settings, 5 captures per configuration, then:

```sh
python3 bench/analyse.py bench/results/forge --vs bench/results/optifine --label-a forge --label-b optifine
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
