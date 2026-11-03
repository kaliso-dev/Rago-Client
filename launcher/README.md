# Rago launcher

The launcher installs and starts Minecraft 1.8.9 with Forge and Rago, signed in with the player's own Microsoft account.

- `core/` is the engine. It has no window and is tested on its own.
- `app/` is the window, built with Tauri: a Rust program with an HTML page in `app/ui/` (no framework, no build step). CI builds the Windows installer (`rago-launcher-windows` artifact).

To build the window on Linux, install the WebKitGTK headers first (`libwebkit2gtk-4.1-dev`, `libsoup-3.0-dev`), then run `cargo run -p rago-launcher`.

## Where each file comes from

| What | From | Checked by |
| --- | --- | --- |
| Minecraft 1.8.9: game, libraries, natives, sounds and textures, log4j configuration | Mojang's servers (`piston-meta`, `libraries.minecraft.net`, `resources.download.minecraft.net`) | Mojang's SHA-1 |
| Forge 1.8.9-11.15.1.2318 and its libraries | `maven.minecraftforge.net` and `libraries.minecraft.net` | SHA-1 and size pinned in `core/data/forge-1.8.9.json` |
| Java 8 (Eclipse Temurin JRE) | Adoptium API, then GitHub | SHA-256 from Adoptium |
| Rago | Latest release of this repository | SHA-256 that GitHub publishes for the asset |

Neither the repository nor the launcher ever contains Minecraft or its assets. OptiFine is not distributed.

A file is kept only when its size and hash match. Files already on disk are checked again and skipped. Everything lives in one folder:

- `libraries/`
- `assets/`
- `versions/1.8.9/`
- `runtime/`
- `game/`: options, worlds, resource packs and `mods/`

## Signing in

Sign-in uses the authorization code flow with PKCE:

1. The browser opens Microsoft's own sign-in page.
2. Microsoft sends the code back to `http://localhost:<port>`.
3. The launcher exchanges it, then goes through Xbox Live, XSTS and Minecraft services.

The Azure app ID is set at build time with `RAGO_MS_CLIENT_ID`. Mojang must approve the app before Minecraft services accept it. Until then, sign-in stops at "Mojang n'a pas encore autorisé l'application Rago".

The refresh token must go in the system's credential store. It is never written to a plain file and never sent to Rago's servers.

## Tests

```
cd launcher
cargo test
```

The end-to-end test downloads everything for real (about 230 MB), then starts the game with a test profile until Forge has loaded Rago:

```
RAGO_E2E_DIR=/tmp/rago RAGO_E2E_JAR=../build/libs/rago-<version>+1.8.9.jar DISPLAY=:99 \
  cargo test --test end_to_end -- --ignored --nocapture
```

## Regenerating the Forge lock

The lock is `core/data/forge-1.8.9.json`. To rebuild it:

1. Read `install_profile.json` from the Forge 1.8.9 installer.
2. Keep the libraries the client needs (`clientreq` not `false`).
3. Download each one, with a `User-Agent` header (the Forge maven refuses requests without one).
4. Write its URL, path, SHA-1 and size into the lock.
