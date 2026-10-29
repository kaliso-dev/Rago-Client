package gg.rago.optimize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/**
 * Changes Minecraft and OptiFine video settings through {@link GameSettings#setOptionValue} and
 * {@link GameSettings#setOptionFloatValue}, the same calls the video settings screen makes, so the
 * game (and OptiFine) apply their own side effects such as reloading chunks.
 */
public final class GameOptions {
	private GameOptions() {
	}

	/** Looks up an option by enum name; OptiFine adds its own constants. Null when absent. */
	public static GameSettings.Options option(String name) {
		for (GameSettings.Options o : GameSettings.Options.values()) {
			if (o.name().equals(name)) {
				return o;
			}
		}
		return null;
	}

	private static GameSettings settings() {
		return Minecraft.getMinecraft().gameSettings;
	}

	/** Advances a cycling option by one step, as one click on its button would. */
	public static void step(GameSettings.Options option) {
		settings().setOptionValue(option, 1);
		settings().saveOptions();
	}

	public static void setFloat(GameSettings.Options option, float value) {
		settings().setOptionFloatValue(option, value);
		settings().saveOptions();
	}

	public static float getFloat(GameSettings.Options option) {
		return settings().getOptionFloatValue(option);
	}

	public static float max(GameSettings.Options option) {
		return option.getValueMax();
	}
}
