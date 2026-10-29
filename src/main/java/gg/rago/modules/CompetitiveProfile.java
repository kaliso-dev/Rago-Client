package gg.rago.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.profile.CompetitivePreset;
import gg.rago.profile.ProfileBackup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * One switch for PvP rendering settings: fast graphics, minimal particles, no clouds,
 * shadows, vignette or dynamic FOV, uncapped FPS. Turning it on saves the player's own
 * values to {@code config/rago-profile-backup.json}; turning it off puts them back.
 *
 * <p>With OptiFine installed, its own settings are included too. They are fields OptiFine adds to
 * {@code GameSettings} and are not obfuscated, so they are reached by name; vanilla fields are
 * accessed directly because their names change in the obfuscated game.
 */
public final class CompetitiveProfile extends Module {
	/** False while the config is loading, so restoring the saved on/off state changes nothing. */
	private boolean ready;
	private final Path backupFile;

	public CompetitiveProfile(Path configDir) {
		super("competitive_profile", FeatureClass.VISUAL, false);
		this.backupFile = configDir.resolve("rago-profile-backup.json");
	}

	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	/** Called once the config is loaded; from then on toggling applies or restores. */
	public void markReady() {
		ready = true;
	}

	@Override
	protected void onEnable() {
		if (!ready) {
			return;
		}
		try {
			JsonObject backup = ProfileBackup.read(backupFile);
			boolean haveBackup = backup != null;

			JsonObject vanilla = readVanilla();
			JsonObject vanillaOld = ProfileBackup.apply(vanilla, CompetitivePreset.VANILLA);
			JsonObject optifine = readOptiFine();
			JsonObject optifineOld = ProfileBackup.apply(optifine, present(CompetitivePreset.OPTIFINE));
			writeOptiFine(optifine);
			writeVanilla(vanilla);

			if (!haveBackup) {
				backup = new JsonObject();
				backup.add("vanilla", vanillaOld);
				backup.add("optifine", optifineOld);
				ProfileBackup.write(backupFile, backup);
			}
			RagoClient.LOG.info("Competitive profile applied");
		} catch (Exception e) {
			RagoClient.LOG.error("Could not apply the competitive profile", e);
		}
	}

	@Override
	protected void onDisable() {
		if (!ready) {
			return;
		}
		try {
			JsonObject backup = ProfileBackup.read(backupFile);
			if (backup == null) {
				return;
			}
			if (backup.has("optifine")) {
				JsonObject optifine = readOptiFine();
				ProfileBackup.restore(optifine, backup.getAsJsonObject("optifine"));
				writeOptiFine(optifine);
			}
			if (backup.has("vanilla")) {
				JsonObject vanilla = readVanilla();
				ProfileBackup.restore(vanilla, backup.getAsJsonObject("vanilla"));
				writeVanilla(vanilla);
			}
			Files.deleteIfExists(backupFile);
			RagoClient.LOG.info("Competitive profile removed, previous settings restored");
		} catch (Exception e) {
			RagoClient.LOG.error("Could not restore settings from {}", backupFile, e);
		}
	}

	private static JsonObject readVanilla() {
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		JsonObject j = new JsonObject();
		j.addProperty("fancy_graphics", o.fancyGraphics);
		j.addProperty("particles", o.particleSetting);
		j.addProperty("ambient_occlusion", o.ambientOcclusion);
		j.addProperty("clouds", o.clouds);
		j.addProperty("entity_shadows", o.entityShadows);
		j.addProperty("fps_limit", o.limitFramerate);
		j.addProperty("vsync", o.enableVsync);
		j.addProperty("snooper", o.snooperEnabled);
		return j;
	}

	private static void writeVanilla(JsonObject j) {
		Minecraft mc = Minecraft.getMinecraft();
		GameSettings o = mc.gameSettings;
		o.fancyGraphics = j.get("fancy_graphics").getAsBoolean();
		o.particleSetting = j.get("particles").getAsInt();
		o.ambientOcclusion = j.get("ambient_occlusion").getAsInt();
		o.clouds = j.get("clouds").getAsInt();
		o.entityShadows = j.get("entity_shadows").getAsBoolean();
		o.limitFramerate = j.get("fps_limit").getAsInt();
		o.enableVsync = j.get("vsync").getAsBoolean();
		// a backup from before this setting was part of the profile has none
		if (j.has("snooper")) {
			o.snooperEnabled = j.get("snooper").getAsBoolean();
		}
		Display.setVSyncEnabled(o.enableVsync);
		if (mc.renderGlobal != null) {
			mc.renderGlobal.loadRenderers();
		}
		// With OptiFine this also writes optionsof.txt
		o.saveOptions();
	}

	/** The preset entries whose OptiFine field exists in this game; none without OptiFine. */
	private static Map<String, JsonElement> present(Map<String, JsonElement> preset) {
		Map<String, JsonElement> found = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : preset.entrySet()) {
			if (field(e.getKey()) != null) {
				found.put(e.getKey(), e.getValue());
			}
		}
		return found;
	}

	private static Field field(String name) {
		try {
			Field f = GameSettings.class.getField(name);
			Class<?> t = f.getType();
			return t == boolean.class || t == int.class || t == float.class ? f : null;
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	private static JsonObject readOptiFine() throws IllegalAccessException {
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		JsonObject j = new JsonObject();
		for (String name : CompetitivePreset.OPTIFINE.keySet()) {
			Field f = field(name);
			if (f == null) {
				continue;
			}
			Class<?> t = f.getType();
			if (t == boolean.class) {
				j.addProperty(name, f.getBoolean(o));
			} else if (t == int.class) {
				j.addProperty(name, f.getInt(o));
			} else {
				j.addProperty(name, f.getFloat(o));
			}
		}
		return j;
	}

	private static void writeOptiFine(JsonObject j) throws IllegalAccessException {
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		for (Map.Entry<String, JsonElement> e : j.entrySet()) {
			Field f = field(e.getKey());
			if (f == null || !e.getValue().isJsonPrimitive()) {
				continue;
			}
			JsonPrimitive v = e.getValue().getAsJsonPrimitive();
			Class<?> t = f.getType();
			if (t == boolean.class) {
				f.setBoolean(o, v.getAsBoolean());
			} else if (t == int.class) {
				f.setInt(o, v.getAsInt());
			} else {
				f.setFloat(o, v.getAsFloat());
			}
		}
	}
}
