# Phosphor Legacy Forge (bundled)

`Phosphor-Legacy-Forge-1.8.9-7.jar` is **Phosphor Legacy Forge** version 7 for Forge 1.8.9, a
rewrite of the game's light engine, shipped unmodified inside Rago's jar. It is not stored in this
repository: the build downloads it from Modrinth (where it is published as `phosphor-universal.jar`)
and checks its SHA-512 before bundling it.

- License: GNU General Public License version 3 (`LICENSE.txt`).
- Authors: JellySquid (Phosphor) and HowardZHY (the Forge 1.8.9 port).
- Source code: https://github.com/HowardZHY/Phosphor-Legacy-Forge
- Download: https://modrinth.com/mod/phosphorlegacyforge

It is packed in `META-INF/rago/bundled/`, outside `ContainedDeps`, so Forge does not extract it to
the mods folder. On every launch Rago copies it to `rago/bundled/` in the game folder when the copy
there is missing or different, and starts it itself (`gg.rago.asm.BundledPhosphor`), the way Forge
starts a jar from the mods folder. If a player installs Phosphor in their own mods folder, that copy
is used and the bundled one is not started. Build with `-PnoPhosphor` to leave it out.
