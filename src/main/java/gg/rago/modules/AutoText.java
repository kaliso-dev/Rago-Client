package gg.rago.modules;

import gg.rago.config.StringOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/**
 * Message hotkeys: a key that types one of your messages in the chat for you ("gl hf", "/l bedwars",
 * your Discord...). Each press sends it once, as if typed; nothing is sent without a press, and a
 * message can go at most once every two seconds. The keys are set in Controls, unbound at first.
 */
public final class AutoText extends Module {
	public static final int SLOTS = 4;
	private static final long COOLDOWN_MS = 2000;

	private final StringOption[] messages = new StringOption[SLOTS];
	private final KeyBinding[] keys = new KeyBinding[SLOTS];
	private long lastSent;

	public AutoText() {
		super("auto_text", FeatureClass.INPUT, false);
		for (int i = 0; i < SLOTS; i++) {
			messages[i] = addOption(new StringOption("auto_text_" + (i + 1), i == 0 ? "gl hf" : "", 100));
			keys[i] = new KeyBinding("key.rago.auto_text_" + (i + 1), 0, "key.categories.rago");
		}
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	public KeyBinding[] keys() {
		return keys;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		for (int i = 0; i < SLOTS; i++) {
			while (keys[i].isPressed()) {
				String text = messages[i].get().trim();
				long now = System.currentTimeMillis();
				if (!text.isEmpty() && mc.thePlayer != null && mc.currentScreen == null && now - lastSent >= COOLDOWN_MS) {
					lastSent = now;
					mc.thePlayer.sendChatMessage(text);
				}
			}
		}
	}
}
