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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

/**
 * One switch for PvP rendering settings: fast graphics, minimal particles, no clouds,
 * shadows, vignette or dynamic FOV, uncapped FPS. Turning it on saves the player's own
 * values to {@code config/rago-profile-backup.json}; turning it off puts them back.
 *
 * <p>Argentum Extras options are written to its JSON file and, when the mod is loaded,
 * set on its live config object so they apply without a restart.
 */
public final class CompetitiveProfile extends Module {
	private static final String EXTRAS_CLASS = "dev.rdh.argentum.extras.ArgentumExtras";

	/** False while the config is loading, so restoring the saved on/off state changes nothing. */
	private boolean ready;

	public CompetitiveProfile() {
		super("competitive_profile", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	/** Called once the config is loaded; from then on toggling applies or restores. */
	public void markReady() {
		ready = true;
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	protected void onEnable() {
		if (!ready) {
			return;
		}
		Path backupFile = configDir().resolve("rago-profile-backup.json");
		try {
			JsonObject backup = ProfileBackup.read(backupFile);
			boolean haveBackup = backup != null;
			if (!haveBackup) {
				backup = new JsonObject();
			}

			JsonObject vanilla = readVanilla();
			JsonObject vanillaOld = ProfileBackup.apply(vanilla, CompetitivePreset.VANILLA);
			writeVanilla(vanilla);

			Path extrasFile = configDir().resolve("argentum-extras.json");
			JsonObject extras = ProfileBackup.read(extrasFile);
			JsonObject extrasOld = null;
			if (extras != null) {
				extrasOld = ProfileBackup.apply(extras, CompetitivePreset.EXTRAS);
				ProfileBackup.write(extrasFile, extras);
				applyExtrasLive(CompetitivePreset.EXTRAS);
			}

			if (!haveBackup) {
				backup.add("vanilla", vanillaOld);
				if (extrasOld != null) {
					backup.add("extras", extrasOld);
				}
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
		Path backupFile = configDir().resolve("rago-profile-backup.json");
		try {
			JsonObject backup = ProfileBackup.read(backupFile);
			if (backup == null) {
				return;
			}
			if (backup.has("vanilla")) {
				JsonObject vanilla = readVanilla();
				ProfileBackup.restore(vanilla, backup.getAsJsonObject("vanilla"));
				writeVanilla(vanilla);
			}
			Path extrasFile = configDir().resolve("argentum-extras.json");
			JsonObject extras = ProfileBackup.read(extrasFile);
			if (backup.has("extras") && extras != null) {
				JsonObject old = backup.getAsJsonObject("extras");
				ProfileBackup.restore(extras, old);
				ProfileBackup.write(extrasFile, extras);
				applyExtrasLive(old.entrySet().stream()
					.filter(e -> !e.getValue().isJsonNull())
					.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			}
			java.nio.file.Files.deleteIfExists(backupFile);
			RagoClient.LOG.info("Competitive profile removed, previous settings restored");
		} catch (Exception e) {
			RagoClient.LOG.error("Could not restore settings from {}", backupFile, e);
		}
	}

	private static JsonObject readVanilla() {
		GameOptions o = Minecraft.getInstance().options;
		JsonObject j = new JsonObject();
		j.addProperty("fancy_graphics", o.fancyGraphics);
		j.addProperty("particles", o.particles);
		j.addProperty("ambient_occlusion", o.ambientOcclusion);
		j.addProperty("clouds", o.cloudRenderMode);
		// Feather names the entity shadows switch renderClouds in 1.8.9.
		j.addProperty("entity_shadows", o.renderClouds);
		j.addProperty("fps_limit", o.fpsLimit);
		j.addProperty("vsync", o.vsync);
		return j;
	}

	private static void writeVanilla(JsonObject j) {
		Minecraft mc = Minecraft.getInstance();
		GameOptions o = mc.options;
		o.fancyGraphics = j.get("fancy_graphics").getAsBoolean();
		o.particles = j.get("particles").getAsInt();
		o.ambientOcclusion = j.get("ambient_occlusion").getAsInt();
		o.cloudRenderMode = j.get("clouds").getAsInt();
		o.renderClouds = j.get("entity_shadows").getAsBoolean();
		o.fpsLimit = j.get("fps_limit").getAsInt();
		o.vsync = j.get("vsync").getAsBoolean();
		Display.setVSyncEnabled(o.vsync);
		if (mc.worldRenderer != null) {
			mc.worldRenderer.reload();
		}
		o.save();
	}

	/** Sets Extras' public config fields; does nothing when Extras is not installed. */
	private static void applyExtrasLive(Map<String, JsonElement> values) {
		if (!FabricLoader.getInstance().isModLoaded("argentum-extras")) {
			return;
		}
		try {
			Object config = Class.forName(EXTRAS_CLASS).getField("CONFIG").get(null);
			if (config == null) {
				return;
			}
			for (Map.Entry<String, JsonElement> e : values.entrySet()) {
				Field f;
				try {
					f = config.getClass().getField(ProfileBackup.camelCase(e.getKey()));
				} catch (NoSuchFieldException missing) {
					continue;
				}
				JsonPrimitive v = e.getValue().getAsJsonPrimitive();
				Class<?> t = f.getType();
				if (t == boolean.class) {
					f.setBoolean(config, v.getAsBoolean());
				} else if (t == int.class) {
					f.setInt(config, v.getAsInt());
				} else if (t == float.class) {
					f.setFloat(config, v.getAsFloat());
				} else if (t.isEnum()) {
					f.set(config, enumValue(t, v.getAsString()));
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			RagoClient.LOG.warn("Argentum Extras settings saved to file but not applied live", e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumValue(Class<?> type, String name) {
		return Enum.valueOf((Class) type, name);
	}
}
