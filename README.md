# Rago Client

Competitive Minecraft 1.8.9 client for Hypixel Bedwars and Minemen Club, built as a
[Minecraft Forge](https://files.minecraftforge.net) 1.8.9 mod so it runs next to OptiFine.

**Current version: 1.4.16.** What it contains:

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
  - Cosmetics (off by default, seen only by you): a hat (top hat, crown, halo) and wings (dragon,
    with flapping), each with its colour
  - resource packs screen: every pack as a card with its icon, description and the textures it
    changes (sword, pearl, golden apple, wool...), search by typing, your own folders to sort packs,
    priority arrows; the vanilla screen stays one click away
  - a warning in chat when the separate "Rago NoHitDelay" test mod is loaded: its NoUseDelay lets go
    of right click before the server has finished the bite, so golden apples are cancelled on servers
  - the HUD works out each element's text once per frame instead of several times, and no longer
    filters the module list every frame
- new in 1.3:
  - Cosmetics redone in 3D: voxel hats (top hat, wizard hat, caps) imported under CC BY 4.0
    (credits in `tools/models/CREDITS.md`), Rago's own cap and beanie with the logo, dragon wings
    extruded into voxels; each
    model is compiled once into a display list, so thousands of voxels are one draw call. Hat and
    wings size, logo colour, flapping speed
  - the Rago cape in six HD designs: Rago, Midnight, Flame, Ocean, Royal, Frost
  - name tags keep vanilla's size and opacity: only vanilla's faint copy shows behind blocks, the
    name stays fully opaque and the background at vanilla's quarter opacity; only their colours
    can change
  - Rago Logo: the Rago logo before your own name in the tab list and on your name tag in F5 (only
    on your screen: there is no Rago server, so other Rago players are not marked)
  - Bed Wars stats: the FKDR is coloured by steps (grey under 1, green from 1, dark green from 5,
    yellow from 8, orange from 15, red from 20); what the numbers mean is explained on the mod's
    page in the menu instead of at the top of the tab list
  - when OneConfig's menu (Patcher) uses the same key as Rago's (Right Shift), it is moved to Right
    Control once, with a chat message
  - Patcher's Chat Delay works again next to Rago's chat look: Rago leaves the game's chat text call
    where Patcher looks for it
  - Bed Wars stats in the tab list as numbers only: `[star] Name FKDR/Danger`; the game-start alert
    waits 3 seconds for every player to be in a team, then for their stats
  - Anti-sniper (in Bed Wars Stats): enemies with a name alt and cheat accounts often carry, few
    final kills for a high FKDR, or (off by default) chainmail and an iron sword in the first
    minute are marked `[S]` in the tab list and named in the alert, with a sound
  - Anti Armor: your own armour is not drawn, so your skin shows in F5 (only on your screen)
  - Auto Messages also sees the end of a game from the sidebar (one team left, or yours out), and
    says so in your own chat before sending the GG
  - FKDR colours: grey under 1, green from 1, dark green from 5, red from 10, dark red from 20;
    danger 1 to 5: grey, green, dark green, red, dark red
  - small dragon wings in Rago's red and black
  - Clean Motion Blur: a ready-made blur on every frame (the trail keeps 70 % of itself, laid over
    the image at 80 %), as an option of Motion Blur
  - dances rewritten: eased moves, the body sinking on the beat, turning and leaning from the hips,
    easing in from your pose; a dance no longer leaves its pose on other players
  - `/rago debug`: writes what happens to your clicks, slow frames and the Auto Messages state to
    `logs/latest.log`, to find a delay or a message that does not go
  - the competitive profile also turns off the usage snooper and, with OptiFine, turns on Smart
    Animations, Render Regions and Fast Render
  - Event Timers HUD (Bed Wars): the time until Diamond II, Emerald II, Diamond III, Emerald III,
    the beds going, sudden death and the game end; the next event's time comes from the
    scoreboard, the ones after it follow Hypixel's usual schedule
  - Upgrades HUD (Bed Wars): your team's Sharpness, Protection, Haste, Feather Falling, heal pool,
    forge and queued traps, the next trap first; levels are read from your gear, purchases and traps
    from the chat and titles
  - Title Mod: the size of titles and subtitles (20% to 200%), long titles shrunk to fit the
    screen, or no titles at all
  - Bed Wars stats: the danger level shows as a tier, S (elite, dark red), A (light red), B (dark
    green), C (light green), D (grey), and is shown after the FKDR by default
  - hats that go around the head (caps, top hat, wizard hat, beanie) are drawn a little bigger, so
    they sit over the skin's hat layer instead of cutting through it
  - faster name tags with many players (a name is worked out again only when its team changes, the
    rounded outline's angles once, the vertices handed over in one copy), all hitboxes in one draw
    call, and fewer graphics driver queries for HUD text, the HUD cache and Motion Blur
  - Low Latency (Performance, off by default): the idea behind NVIDIA Reflex, done in Rago's own
    code with GL fences. Before reading the mouse for a new frame, the game waits until the graphics
    card has caught up (at most one frame behind in Balanced, none in Strict), so what you see comes
    from fresher input; it can lower the frame rate, Strict more than Balanced
  - a new Hypixel API key can no longer stay on "Checking..." forever: the answer comes within
    20 seconds, the request thread starts again if it ever stopped, and the log says what happened
  - `/rago debug` also logs each mouse button and hotbar key the game reads, and the state at the
    moment the game runs a left or right click; and every 5 seconds what Bed Wars Stats and Bed
    Wars Quests see (server, sidebar, players with stats, the API requests and their last answer)
  - Hypixel API requests follow the key's budget, which can be a daily quota: your own stats and
    your game's players come first; players of a big lobby and Level Head outside a game only get
    spare requests (half of a short window, none on a daily quota); in a short window what is left
    is spread over it; a player refused for "too many requests" keeps its place; other players'
    stats are kept 6 hours, and saved on disk for a day so a restart costs no requests; your quests
    are asked again every 15 minutes in a lobby (was every minute) and after chest menus only; Bed
    Wars Quests says how long Hypixel asks to wait ("18 h 34")
  - a developer key from developer.hypixel.net has 300 requests a day: when they are used up, the
    API key card in the menu, Bed Wars Quests and a chat message from Bed Wars Stats say so and
    when they come back, instead of a green "Key OK" and empty stats
  - the Rago cape, the Rago cap and beanie, the small dragon wings and the Rago shield come
    in nine neon colours (red, orange, yellow, green, cyan, blue, purple, pink, white), picked with
    swatches in the Locker; the red of the texture is recoloured in game with its shading kept
  - the mushroom hats and the angel and butterfly wings are gone; a saved choice of one of them
    falls back to none
  - the Locker starts with qRago's skin (added once; removing it is up to you)
  - home screen: "Made by Rago" at the bottom, a softer glow behind the logo, slightly wider buttons
  - fewer Hypixel API requests: other players' stats are kept two days (on disk too); your quests are
    asked again every 30 minutes in a lobby and after the quest master's menu only (not every chest
    menu); a game's waiting room is recognised from its sidebar ("Players: 5/16"), and any other
    place with more than 16 players is a lobby whose players only get spare requests
  - Nick Hider, for streaming: your name is replaced by a nick of your choice (with & colour codes)
    in the chat, the tab list, name tags, the scoreboard and Rago's menus, and your skin by Steve,
    Alex or a locker skin (mask button on a locker card) in F5 and in the tab list; only on your
    screen
  - the Rago cap's neon colour now colours its front logo and a snapback strap at the back; the
    separate neon cap is gone (a saved choice of it falls back to none)
  - neon cosmetics (Rago cap and beanie logos, small dragon wings, Rago shield) get the same faint
    bloom as the Rago cape, in every colour; one switch in the Locker turns it off
  - qRago's skin ships inside the jar, so it is in every locker even without a connection to
    Mojang's servers (added once, not worn)
  - the Rago logo next to your name (tab list and F5) no longer turns into another icon after a
    resource pack change
  - on a daily-quota key, your quests are asked again at most every 20 minutes after games, and a
    failed request waits 30 minutes before the next try
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
  - Bed Wars Stats: a red danger sign before strong players (FKDR 10+ or 300 stars+, both
    settings) and nicks, a legend at the top of the tab list saying what each number means, and at
    the start of a game a chat message listing the enemy teams to target, in their colour, with
    their strong and nicked players
  - Hypixel in other languages: games are recognised from the sidebar's team lines, and the Fast
    Shop reads prices in English, French, German, Spanish, Italian, Portuguese, Dutch and Polish
  - Hit Particles: the multiplier goes by tenths (x1.0 to x5.0) and adds single particles, not
    whole extra bursts
  - the shield lies flat against the outside of the left forearm, face outward, and is bigger;
    the wings start higher on the back
  - dragon wings with scales and bones, and smaller dragon wings like Lunar Client's
  - Emotes: eight dances and gestures on a wheel (B): floss, dab, wave, disco, zombie, helicopter,
    clap, macarena; the camera turns to face you, and only you see them
  - Message Hotkeys (input class, off by default): up to four keys that each type one of your
    messages in the chat, at most once every two seconds
  - Auto Messages (auto-chat class, off by default, Hypixel only): GG and a message of yours when a
    game ends, a message when you kill someone (final kills only by default, at least 15 seconds
    apart), and `/tip all` every 15 minutes; at most one message every 1.25 seconds. In a game they
    go to everyone: `/shout` in team modes, `/ac` in solo
  - HUD cache: at high frame rates Rago's HUD is drawn 144 times per second (a setting) into an
    image reused in between, drawn over black and white so translucent backgrounds stay exact
  - Bed Wars Stats: each number in the tab list is named ("FKDR 1.3", "Danger 2/5"), and Hypixel's
    NPCs, bots without a team and spectators are no longer taken for nicks
  - Hit Particles from x0.1 to x3.0, applied to every critical and sharpness burst
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
  - motion blur costs nothing while it has no trail to draw (standing still in the "when turning"
    modes, or zooming), and reads the frame straight from the game's framebuffer instead of copying
    the screen when the game draws into one (OptiFine's Fast Render draws to the screen, so the copy
    stays there)
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

Every module declares a policy class (visual, informational, input, auto chat, packet). Input and
auto-chat modules are off by default, and packet-class modules are refused at registration: Rago
does not ship them.

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
2. Put `rago-1.4.16+1.8.9.jar` in `.minecraft/mods` (with Prism Launcher or another launcher
   that makes instances, in that instance's `minecraft/mods` folder). If your Rago build bundles OptiFine, that is
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
`libs/ias/`), the bundled PolyPatcher is CC BY-NC-SA 4.0 (see `libs/patcher/`), the Poppins font is under the SIL Open Font License (see `Poppins-OFL.txt` next to it). The voxel hats (by VoxelBear) are under CC BY 4.0, see `tools/models/CREDITS.md`.
