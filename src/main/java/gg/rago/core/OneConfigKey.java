package gg.rago.core;

import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * OneConfig (the settings menu that Patcher and other Polyfrost mods use) opens on Right Shift by
 * default, the same key as Rago's menu, so one press opened both. When the two keys are the same,
 * OneConfig's is moved to Right Control and saved in OneConfig's own settings; it can be set back
 * in OneConfig's Preferences, and nothing is changed when the keys already differ.
 */
final class OneConfigKey {
	static final int NEW_KEY = Keyboard.KEY_RCONTROL;

	private OneConfigKey() {
	}

	/** Moves OneConfig's menu key off {@code ragoKey}; true when it was moved. */
	static boolean moveOff(int ragoKey) {
		try {
			Class<?> prefs = Class.forName("cc.polyfrost.oneconfig.internal.config.Preferences");
			Field field = prefs.getField("oneConfigKeyBind");
			Object bind = field.get(null);
			if (bind == null) {
				return false;
			}
			List<?> keys = (List<?>) bind.getClass().getMethod("getKeyBinds").invoke(bind);
			if (keys.size() != 1 || !Integer.valueOf(ragoKey).equals(keys.get(0))) {
				return false;
			}
			bind.getClass().getMethod("clearKeys").invoke(bind);
			bind.getClass().getMethod("addKey", int.class).invoke(bind, NEW_KEY);
			Object instance = prefs.getMethod("getInstance").invoke(null);
			if (instance != null) {
				Method save = instance.getClass().getMethod("save");
				save.invoke(instance);
			}
			RagoClient.LOG.info("OneConfig's menu key was the same as Rago's; moved to Right Control");
			return true;
		} catch (ClassNotFoundException e) {
			return false; // no OneConfig
		} catch (ReflectiveOperationException | RuntimeException e) {
			RagoClient.LOG.warn("Could not check OneConfig's menu key", e);
			return false;
		}
	}
}
