# Rago Client

Competitive Minecraft 1.8.9 client for Hypixel Bedwars and Minemen Club, built as a
[Minecraft Forge](https://files.minecraftforge.net) 1.8.9 mod so it runs next to OptiFine.

**Current version: 1.4.0.** What it contains:

- a clean home screen (animated Rago emblem, Singleplayer / Multiplayer / Play Hypixel, a dock of
  shortcuts) over a looping, lightly blurred background video, and a helper that links to the
  OptiFine download when OptiFine is missing
- frosted glass panels and buttons in every Rago screen (they blur what is behind them, also with
  OptiFine's Fast Render), with a switch to use solid panels instead
- OptiFine inside the Rago jar for builds that bundle it (see [Bundling OptiFine](#bundling-optifine))
- [PolyPatcher](#patcher) (Patcher's fixes and optimisations) inside the Rago jar; its settings open
  with `/patcher`
- an **Accounts** screen in Rago's style (top right of the home screen, or the head button in the
  server list): add Microsoft accounts and switch between them in one click. The sign-in itself is
  done by [In-Game Account Switcher](#account-switcher), which is bundled
- the Rago window (Right Shift): a **Mods** tab with mod profiles (Default, Bedwars, Practice,
  Skywars, or your own), mod cards with Enabled/Disabled, filters, sorting and search, and a page per
  mod with a live preview; a **Settings** tab with General, Performance and Controls pages
- menu animations (fades, slides, hover transitions), with a switch to turn them off, and an optional
  blur of the game behind the window
- a Performance page with a one-click **Max performance** preset (with Undo) and Rago's own optimisations, Minecraft's video settings and, when OptiFine is
  installed, its performance, detail, animation and particle settings in one searchable list:
  - frame caps: menus above vanilla's fixed 30 FPS, and a lower cap while the window is unfocused
  - entity render distances for players, hostile mobs, passive mobs and other entities
  - hide arrows stuck in players, item frames, paintings, armor stands, XP orbs, fireworks, falling blocks
  - tile entity distance, and hide skulls, signs, enchanting books, banners, beacon beams, end portals
  - hide tall grass, ferns, flowers and dead bushes, a particle cap and a fog switch
- a Controls page to rebind every key (keyboard or mouse), with conflicts shown in red
- a module system: every feature is a module with an on/off switch and its own options
- a HUD editor: drag elements, snap them to the screen centre, resize them with the mouse wheel;
  elements left at their default place on the left edge stack themselves so they never overlap
- HUD styles: every text element has a preset (Rago, Vanilla, Box, Classic, Minimal) and then each
  detail on its own: font (Minecraft, Poppins, Inter), label before or after the value, separator,
  brackets, label, bracket and text colours, chroma, alignment, background colour and opacity, round
  corners, padding, minimum width and border. Colours have a picker with hue and opacity
- settings saved to `config/rago.json` (a broken file is set aside and defaults are used)
- PvP HUD: keystrokes (follows your bindings, with CPS; styles, key size, gap, round corners,
  idle and pressed colours, a smooth release fade), CPS counter, armour and held item with
  durability, potion effects with time left, coordinates and facing
- comfort mods: zoom (hold C, wheel to adjust), custom crosshair drawn in real pixels, fullbright,
  toggle sprint / sneak (input class, off by default)
- a competitive profile: one switch sets fast graphics, minimal particles, no clouds or shadows and
  uncapped FPS, plus OptiFine's own settings when OptiFine is installed (fast math, no smooth FPS,
  no dynamic FOV, fast leaves, no sky, stars, vignette or animated smoke/flame/explosions). Potion
  particles stay on so you can read your opponent's effects. Your own values are saved to
  `config/rago-profile-backup.json` and put back when you switch it off.
- PvP mods: block overlay (outline and fill colour, width up to 10, opacity), hitboxes (players,
  mobs, items, projectiles; colour, width up to 10, eye line, look direction), hit colour (the red
  tint on hurt players and mobs), hit particles multiplier, tab list ping in ms, chat (timestamps,
  repeated messages stacked into one line, up to 1,000 lines of history, background opacity down to
  fully transparent, text shadow on or off), name tags (styles, Minecraft, Poppins or Inter font,
  size, server or custom colour, background, round corners, border, faint through walls, your own
  name in F5), a movable scoreboard (scale, background opacity, red numbers optional, or hidden) and boss
  bar, reach display, combo counter, speed, clock, memory and server address HUDs
- new in 1.1:
  - name tags drawn in one batch per frame instead of one draw call per piece of text (about 5.8 →
    3.3 ms per frame for 60 players in the Rago style, 1.3 ms in the vanilla style, measured with
    `-Drago.bench.nametags=60`, see below)
  - motion blur: strength, always / when turning / when turning or moving, sensitivity, the same
    look at any frame rate, paused while zooming; the HUD stays sharp
  - the Rago cape (on by default, can be switched off; only you see it for now)
  - Bed Wars: Height Limit (blocks left before the map's build limit, learned from the server and
    kept per map in `config/rago-height-limits.json`, or set by hand), Block Counter (wool, clay,
    glass, wood, end stone, obsidian, ladders), and critical particles on every hit
  - Direction HUD, Pack Display HUD, enchantment glint colour (or rainbow) on items and armour
  - less memory: Rago's textures no longer keep a copy of their pixels in Java memory, and text is
    measured without building a 48-pixel glyph texture
- new in 1.2:
  - motion blur reworked: the trail is updated 60 times per second and laid over the sharp current
    frame, so 1000+ FPS no longer leaves pixel trails (most visible in the sky); lighter by default
  - smooth sneak: the camera lowers over a fifth of a second (1.7 animations; aiming keeps the real
    eye height)
  - Tab List: background and row opacity (transparent by default), server header and footer, text
    shadow, size
  - Particles: block, critical-hit, sharpness and other particles can each be hidden, resized and
    tinted; block-breaking particles can be multiplied (×1 to ×5)
  - Saturation & Colours: saturation, vibrance, contrast, brightness, gamma, temperature, tint and
    sharpening of the world in one shader pass (OpenGL 2.0), the HUD keeps its colours
  - presets on the Performance page: Ranked Bedwars (the mods a Bed Wars player needs and fast video
    settings) and Quality (colour grading, longer render distance, fancy graphics), with Undo
  - Hit Delay Fix (input class, off by default): a missed click no longer locks the attack key for
    10 ticks. Check the rules of the server you play on
  - eating fix (in gameplay fixes): when the server re-sends the stack in your hand while you eat or
    drink, the bite continues instead of starting over
  - Fast Shop (input class, off by default, Hypixel only): one click buys in the Bed Wars shop,
    affordable items are highlighted in their resource colour, and a sword or knockback stick you
    already own is not bought twice
  - Cosmetics (off by default, seen only by you): a hat (top hat, crown, halo) and wings (angel or
    dragon, with flapping), each with its colour
  - resource packs screen: every pack as a card with its icon, description and the textures it
    changes (sword, pearl, golden apple, wool...), search by typing, your own folders to sort packs,
    priority arrows; the vanilla screen stays one click away
  - a warning in chat when the separate "Rago NoHitDelay" test mod is loaded: its NoUseDelay lets go
    of right click before the server has finished the bite, so golden apples are cancelled on servers
  - the HUD works out each element's text once per frame instead of several times, and no longer
    filters the module list every frame
- new in 1.3:
  - Cosmetics redone in 3D: voxel hats (top hat, wizard hat, caps, red and green
    mushrooms) and voxel angel wings imported under CC BY 4.0 (credits in `tools/models/CREDITS.md`),
    Rago's own cap and beanie with the logo, butterfly and dragon wings extruded into voxels; each
    model is compiled once into a display list, so thousands of voxels are one draw call. Hat and
    wings size, logo colour, flapping speed
  - the Rago cape in six HD designs: Rago, Midnight, Flame, Ocean, Royal, Frost
  - name tags follow vanilla through walls: only a faint copy shows behind blocks (opacity
    configurable), and a maximum distance
  - the 1.2 Particles mod is merged into Particle Multiplier, which can now also hide block-breaking
    and block-mining particles
  - Item Scale (held and dropped items from 25% to 400%), Item Physics (dropped items lie on the
    ground and tumble while falling)
  - TNT Timer above lit TNT (format, colour by time, size, through walls, distance, fuse length for
    servers that change it)
  - Item Counter HUD (golden apples, pearls, fireballs, TNT, arrows, bridge eggs, snowballs, potions;
    each on or off, orange when low) and Bed Wars Generator Timers HUD (diamond and emerald, tier,
    read from the generators' floating text and counted down out of range)
  - Fast Shop buys like Meowtils: a click sends the purchase without picking the item up on the
    cursor
  - resource packs screen: each card shows the pack's end stone, wool, wood, obsidian, dirt and glass
    as small 3D blocks next to its items
  - Toggle Sprint: an option to hide its text; Speed HUD in km/h and with 0 to 3 decimals; Reach HUD
    decimals and how long it stays; Combo HUD end delay and minimum
  - TNT Timer, Item Counter, Generator Timers and Bed Wars Resources gather what they show once per
    tick instead of every frame
- new in 1.4:
  - the Locker (shirt button on the home screen): add any player's skin by name and wear it with a
    click (only you see it in game), or set it on your Minecraft account so everyone sees it; pick
    your cape, hat, wings and shield there too, on a 3D preview of your player you can turn
  - a Rago Optimizer shield in your left hand, seen in third person, in five styles (Rago, Crimson,
    Gold, Frost, Void); the blue cap is now red and the green one is gone
  - Bed Wars Stats: each player's star and FKDR in the tab list, coloured by a danger level out of 5
    (Rago's own formula: FKDR first, then star), nicked players marked, and a chat alert listing the
    most dangerous enemies when a game starts; needs your Hypixel API key
  - Trap Notifier: a sound and a chat line (no title) when one of your traps goes off, and a reminder
    when your team has had no trap for a while
  - Action Sounds: your own sounds for hitting a player (once the server confirms the hit), getting
    hit and breaking a block: seven sounds made by `tools/sounds.py`, satisfying 1.8 sounds (arrow
    ding, XP orb, item pop), or the attack sounds of recent versions, read from your own Minecraft
    install when one was launched (Rago ships none of Mojang's files)
  - Bed Wars Quests: your daily and weekly Bed Wars quests and what is left of each (final kills,
    beds, wins, games), from the Hypixel API, counted on live during the game from the chat
  - the Hypixel API key can be pasted on the page of each Hypixel mod (Level Head, Bed Wars Stats,
    Bed Wars Quests), which says whether the key works, is refused or is missing
  - Screenshots: COPY, OPEN, FOLDER and DELETE buttons under each F2 screenshot in the chat; COPY
    puts the image on the clipboard, ready to paste in Discord (optionally every time)
  - Raw Input (input class, off by default): the camera turns by what the mouse reports, read from
    the device through JInput (shipped with 1.8.9), without Windows pointer acceleration; the
    game's own mouse stays when no mouse can be read that way
  - Pack Display shows the pack's logo (its size is a setting) and keeps the colours in its name
    (both optional); sorting characters and extra spaces are dropped from the name
  - Client Fixes: the inventory stays centred with potion effects, an optional parallax fix, and
    servers can no longer probe your files through a resource pack link
  - the home screen's third button joins the Rago Optimizer Discord
  - the Hypixel API is asked faster while the key has plenty of requests left
- Hypixel mods: Level Head (network level or Bed Wars star above names, from the Hypixel API with
  your own key: `/rago hypixelkey <key>`, kept in `config/rago-hypixel-key.txt` only) and a Bed Wars
  resources counter (iron, gold, diamonds, emeralds)
- fixes and animations: No Hurt Cam (shake strength), 1.7 animations (block-hit swing, swing while
  eating, drinking or drawing a bow, red armour when hit, no re-equip bounce when only the stack
  changes, swing duration, held item position and size), gameplay fixes (input class, off by
  default: mouse delay fix, held keys kept after closing a menu, borderless fullscreen on F11), low
  fire, no pumpkin mask, no water overlay, hide achievements, hide the crosshair in F5, weather and
  time changers (only on your screen)
- engine tweaks: no forced full garbage collection on every world change, dropped stacks drawn
  once, no OpenGL error check every frame
- occlusion culling: entities and chests hidden behind solid blocks are not drawn (players only if
  you ask), shown on the F3 screen; and a 5 FPS cap while the window is minimised
- performance HUD: FPS (with 1% low), frame time p50/p99 (off by default), ping (see below)
- a 60-second frame capture written to CSV, with hardware and settings in the header (start and
  stop are shown in chat)
- `bench/analyse.py` to compare two sets of captures (e.g. without vs. with OptiFine)

Every module declares a policy class (visual, informational, input, packet). Input-class modules
are off by default, and packet-class modules are refused at registration: Rago does not ship them.

Rago never adds, delays or alters packets, and every optimisation only changes what this client
draws. The jar contains one class transformer (`gg.rago.asm`) with a few small patches, each
calling `gg.rago.hooks.Hooks`: the frame-rate cap for menus and unfocused windows, occlusion culling
of entities, the hurt camera strength, the 1.7 animations and held item, the hit colour, the tab list
ping, the chat history, background and shadow, the mouse delay fix, the world-switch clean-up,
dropped stacks, borderless fullscreen, the Rago cape and the glint colour. Each one keeps vanilla behaviour while its feature is off, and a patch that
does not fit (another mod changed the method) is skipped and logged. Start the game with
`-Drago.dumpClasses=true` to save such a class to `rago-dump/` for a bug report. Everything else
uses Forge events.

Rago does not include NoHitDelay or anything else that changes combat timing, reach or what the
client sends: those are cheats on Hypixel and Minemen Club.

Toggle sprint/sneak and the keybind fix are the only features that affect input: they make a
vanilla key read as held, nothing more, and they are off until you turn them on. The mouse delay
fix makes the crosshair target follow the camera you already see, instead of the head angle from
one tick earlier; it does not change reach, timing or what is sent.

### About ping

No client can make the network faster: ping is the time packets take between your PC and the
server. Vanilla 1.8.9 already turns off Nagle's algorithm (`TCP_NODELAY`) on the game connection.
What Rago changes is what you see and feel: higher and steadier FPS (the Performance page), no
mouse delay, fast world switching, and a ping counter that tells the truth. The tab list value is
computed by the server and large networks refresh it rarely, so the Ping HUD measures by default:
every few seconds (3 to 60, your choice) it opens a separate connection to the same server address
and times one status ping, exactly like the server list does. Choose *Server (tab)* to show the
server's value instead.

Comparing with another client: both run on the same network, so the real latency is the same.
Different numbers come from how and when each client measures (a status ping on a new connection,
the server's tab value, or a value averaged over time), and from the moment of the sample. To
compare like for like, set the Ping HUD to *Server (tab)* on both.

## Install (players)

1. Install **Forge 1.8.9 - 11.15.1.2318** with the Forge installer ("Install client").
2. Put `rago-1.4.0+1.8.9.jar` in `.minecraft/mods`. If your Rago build bundles OptiFine, that is
   all; otherwise download **OptiFine 1.8.9 HD U M5** from [optifine.net](https://optifine.net/downloads)
   and put it in `.minecraft/mods` too.
3. If you used a separate OptiFine jar before and your Rago build bundles it, you can delete the
   separate one (Rago uses it if it is still there, so nothing breaks either way).
4. Start the *forge* profile in the Minecraft launcher.

OptiFine is optional: without it Rago runs on plain Forge, and the competitive profile only changes
the vanilla settings.

## Requirements (developers)

- A JDK 17 to 21 to run Gradle (the wrapper downloads Gradle 8.8, which cannot run on Java 22+). The mod and the game use
  **Java 8**, which Gradle downloads by itself on the first build.
- Python 3.8+ for `bench/analyse.py` (no extra packages)

## Build and test

```sh
./gradlew build          # compiles, runs the unit tests, writes build/libs/rago-0.8.0+1.8.9.jar
./gradlew test           # unit tests only
./gradlew runClient      # starts Minecraft 1.8.9 + Forge with Rago in ./run (offline dev account, no OptiFine)
```

On Windows (PowerShell) use `.\gradlew.bat` instead of `./gradlew`, e.g. `.\gradlew.bat runClient`.
To test with OptiFine, install the built jar in a real Forge profile as described above.

### Account switcher

The Accounts screen uses [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher)
7.1.2 by The_Fireplace, The_BrainStone and VidTu, licensed under the LGPL-3.0. Its unmodified jar is
in `libs/ias/` with its licence, and is bundled in the Rago jar the same way as OptiFine (Forge
extracts it to `mods/1.8.9/`); its licence files are in `META-INF/licenses/in-game-account-switcher/`.
You can replace it with any other build of the same version. The Microsoft sign-in opens in your
browser on Microsoft's own page; Rago never sees your password. Tokens are kept by the switcher in
its `.iasms` file, encrypted with a key stored on the same PC: that stops casual reading, not
malware running as you. If you already have In-Game Account Switcher in your mods folder, Rago uses
that copy.

### Patcher

[PolyPatcher](https://github.com/Polyfrost/PolyPatcher) 1.10.4, the Polyfrost fork of Sk1er's
Patcher, is bundled unmodified like In-Game Account Switcher. It is licensed under CC BY-NC-SA 4.0,
so **Rago builds that contain it may only be shared non-commercially**; see `libs/patcher/README.md`.
The build downloads it from Modrinth and checks its SHA-512, so the repository never stores it;
`./gradlew build -PnoPatcher` leaves it out. On the first launch Forge extracts it to
`mods/1.8.9/`, Rago starts it with its access transformer, and Patcher downloads OneConfig, its
settings library. Rago turns off Forge's loading screen (`config/splash.properties`), which can crash
while Patcher starts. Right Shift still opens Rago's window; Patcher's settings open with `/patcher`.
If Patcher or PolyPatcher is already in your mods folder, Rago uses that copy.

### Bundling OptiFine

OptiFine's licence does not allow redistributing it without permission from its author (sp614x), so
the repository never contains it. To build a Rago jar with OptiFine inside, put
`OptiFine_1.8.9_HD_U_M5.jar` in `libs/optifine/` (ignored by git) and run the build. Forge extracts
it to `.minecraft/mods/1.8.9/` on the first launch and Rago starts it the way Forge starts an
OptiFine jar from the mods folder. Only publish such builds if you have that permission.

## Controls

| Key | Action |
| --- | --- |
| Right Shift | Open the Rago menu (modules, options, *Edit HUD*); `/rago` in chat does the same |
| F10 | Show / hide the whole Rago HUD |
| C (hold) | Zoom; mouse wheel changes the zoom level while held |
| F12 | Start / stop a 60 s frame capture (saved to `rago/frames-YYYYMMDD-HHMMSS.csv`) |
| F11 | Fullscreen, borderless when the gameplay fix is on |

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

Name tags have their own benchmark: start the game with `-Drago.bench.nametags=60` and enter a
singleplayer world. It adds 60 fake players in front of you, switches the Nametags mod off and on
every 10 seconds and logs `[bench]` lines with the frame time and the time spent drawing names.

`-Drago.bench.frame=true` logs a `[profile]` line every 5 seconds with the frame time, the time Rago
spends per frame on occlusion culling, the HUD, name tags and its tick, and the slowest HUD elements.

## What the numbers mean

| Value | Definition |
| --- | --- |
| FPS | frames ÷ elapsed time over the last 1,000 frames |
| 1% low | 1 ÷ mean of the slowest 1% of those frames |
| p50 / p99 | nearest-rank percentiles of frame time (start of one frame to start of the next) |
| ping (measured) | round trip of one status ping on a separate connection to the server, like the server list |
| ping (server) | the latency the server reports for you in the tab list |
| jitter | standard deviation of the last 60 ping samples |
| CPS | presses of your attack (or use) binding that the game registered in the last second |

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). The bundled In-Game Account Switcher is LGPL-3.0 (see
`libs/ias/`), the bundled PolyPatcher is CC BY-NC-SA 4.0 (see `libs/patcher/`), the Poppins font is under the SIL Open Font License (see `Poppins-OFL.txt` next to it). The voxel hats (by VoxelBear) and angel wings (by playswolrdgames, a port of the Wings mod by pau101) are under CC BY 4.0, see `tools/models/CREDITS.md`.
