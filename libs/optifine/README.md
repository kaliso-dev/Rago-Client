# OptiFine (optional, not in git)

Put `OptiFine_1.8.9_HD_U_M5.jar` in this folder before `gradlew build` to ship it inside the Rago
jar. Forge extracts it to `.minecraft/mods/1.8.9/` on the first launch and loads it like a normal
mod, so players do not install OptiFine themselves.

OptiFine's licence does not allow redistribution without permission from its author (sp614x).
Only bundle it in builds you have that permission for. The jar is ignored by git and is never
committed to this repository.
