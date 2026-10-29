# PolyPatcher (bundled)

`PolyPatcher-1.8.9-forge-1.10.4.jar` is **PolyPatcher** 1.10.4 for Forge 1.8.9, the Polyfrost
fork of Sk1er's **Patcher**, shipped unmodified inside Rago's jar. It is not stored in this
repository: the build downloads it from Modrinth and checks its SHA-512 before bundling it.

- License: Creative Commons Attribution-NonCommercial-ShareAlike 4.0 (`LICENSE.md`). Rago builds
  that contain it may only be shared non-commercially, with this notice.
- Authors: Sk1er LLC and Polyfrost, with the contributors listed in its repository.
- Source code: https://github.com/Polyfrost/PolyPatcher
- Download: https://modrinth.com/mod/patcher

PolyPatcher downloads OneConfig, Polyfrost's settings library, the first time the game starts.
Its settings open with `/patcher`. If a player installs Patcher or PolyPatcher in their own mods
folder, that copy is used and the bundled one is skipped. Build with `-PnoPatcher` to leave it out.
