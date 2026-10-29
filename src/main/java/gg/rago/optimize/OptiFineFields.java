package gg.rago.optimize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.client.FMLClientHandler;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes OptiFine's settings. OptiFine adds them to {@link GameSettings} as public
 * {@code ofXxx} fields whose names are not obfuscated, so plain reflection works. A field that is
 * missing or has another type than expected is reported as absent and its row is hidden.
 */
public final class OptiFineFields {
	private static final Map<String, Field> CACHE = new HashMap<>();

	private OptiFineFields() {
	}

	public static boolean installed() {
		return FMLClientHandler.instance().hasOptifine();
	}

	private static Field field(String name, Class<?> type) {
		if (!installed()) {
			return null;
		}
		String key = name + ":" + type.getName();
		if (CACHE.containsKey(key)) {
			return CACHE.get(key);
		}
		Field f = null;
		try {
			Field found = GameSettings.class.getField(name);
			if (found.getType() == type) {
				f = found;
			}
		} catch (NoSuchFieldException ignored) {
			// not in this OptiFine version
		}
		CACHE.put(key, f);
		return f;
	}

	public static boolean hasBool(String name) {
		return field(name, boolean.class) != null;
	}

	public static boolean hasInt(String name) {
		return field(name, int.class) != null;
	}

	public static boolean getBool(String name) {
		try {
			Field f = field(name, boolean.class);
			return f != null && f.getBoolean(Minecraft.getMinecraft().gameSettings);
		} catch (IllegalAccessException e) {
			return false;
		}
	}

	public static void setBool(String name, boolean value) {
		try {
			Field f = field(name, boolean.class);
			if (f != null) {
				f.setBoolean(Minecraft.getMinecraft().gameSettings, value);
			}
		} catch (IllegalAccessException ignored) {
			// public field; cannot happen
		}
	}

	public static int getInt(String name) {
		try {
			Field f = field(name, int.class);
			return f == null ? 0 : f.getInt(Minecraft.getMinecraft().gameSettings);
		} catch (IllegalAccessException e) {
			return 0;
		}
	}

	public static void setInt(String name, int value) {
		try {
			Field f = field(name, int.class);
			if (f != null) {
				f.setInt(Minecraft.getMinecraft().gameSettings, value);
			}
		} catch (IllegalAccessException ignored) {
			// public field; cannot happen
		}
	}

	/** Lists every OptiFine field with its type, for the log. */
	public static String describe() {
		StringBuilder sb = new StringBuilder();
		for (Field f : GameSettings.class.getFields()) {
			if (f.getName().startsWith("of")) {
				sb.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(' ');
			}
		}
		return sb.toString().trim();
	}
}
