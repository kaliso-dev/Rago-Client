package gg.rago.core;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Patcher's Fullbright, on by default in Patcher, shows every block at full light and stops the
 * game from working out light at all. Rago starts with it off, like other PvP clients: the game
 * lights the world itself, with the bundled Phosphor keeping that cheap, and the player who wants
 * everything lit turns on Rago's Fullbright mod or Patcher's own setting ({@code /patcher}). It is
 * turned off once per installation, after Patcher has read its settings and before any world is
 * loaded, and whatever the player chooses afterwards is kept.
 */
final class PatcherFullbright {
	static final String MARKER = "rago-patcher-fullbright.txt";
	/** Patcher's settings class, the same in PolyPatcher (OneConfig) and Sk1er's Patcher (Vigilance). */
	static final String PATCHER_CONFIG = "club.sk1er.patcher.config.PatcherConfig";

	private PatcherFullbright() {
	}

	/**
	 * Turns Patcher's Fullbright off unless this was done before ({@code MARKER} in
	 * {@code configDir}); true when it was on and is now off and saved.
	 */
	static boolean offOnce(Path configDir, String configClass) {
		Path marker = configDir.resolve(MARKER);
		if (Files.exists(marker)) {
			return false;
		}
		try {
			Class<?> config = Class.forName(configClass);
			Object instance = config.getField("INSTANCE").get(null);
			if (instance == null) {
				return false; // settings not read yet: tried again on the next launch
			}
			Field fullbright = config.getField("fullbright");
			boolean wasOn = fullbright.getBoolean(null);
			if (wasOn) {
				fullbright.setBoolean(null, false);
				instance.getClass().getMethod("markDirty").invoke(instance);
				instance.getClass().getMethod("writeData").invoke(instance);
			}
			Files.write(marker, ("Patcher's Fullbright was turned off once by Rago" + (wasOn ? "" : " (it was already off)")
				+ ". Delete this file to have it turned off again on the next launch.\n").getBytes(StandardCharsets.UTF_8));
			if (wasOn) {
				RagoClient.LOG.info("Turned Patcher's Fullbright off (once); the game lights the world itself");
			}
			return wasOn;
		} catch (ClassNotFoundException e) {
			return false; // no Patcher
		} catch (ReflectiveOperationException | RuntimeException | IOException e) {
			RagoClient.LOG.warn("Could not turn Patcher's Fullbright off", e);
			return false;
		}
	}
}
