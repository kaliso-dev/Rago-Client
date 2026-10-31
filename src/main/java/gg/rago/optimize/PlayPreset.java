package gg.rago.optimize;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import gg.rago.config.ConfigStore;
import gg.rago.config.Option;
import gg.rago.core.Module;
import gg.rago.core.ModuleRegistry;
import gg.rago.settings.Setting;
import gg.rago.settings.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-click setups that turn mods on or off, set some of their options and set Performance page
 * rows together: "Ranked Bedwars" (the information a Bedwars player needs, light visuals, fast
 * settings) and "Quality" (better-looking world). Mods a preset does not name keep their state.
 * Before the first preset is applied, every mod and every row a preset touches is saved to
 * {@code config/rago-play-preset-backup.json}, so {@link #undo} puts things back.
 */
public final class PlayPreset {
	private static final Logger LOG = LogManager.getLogger("Rago");

	public enum Kind {
		RANKED_BEDWARS, QUALITY,
		/** The welcome guide's choices: the mods for what you play, without touching the video settings. */
		BEDWARS, PVP, BOTH
	}

	/** What a Hypixel Bed Wars player wants on screen. */
	private static final String[] BEDWARS_MODS = {"fps", "ping", "cps", "keystrokes", "armor", "potions", "bedwars_resources",
		"height_limit", "block_counter", "tab_ping", "tab_list", "old_animations", "hit_color", "zoom", "low_fire",
		"hide_achievements", "no_hurt_cam", "upgrades_hud", "event_timers", "trap_notifier", "tnt_timer", "game_debrief", "quickplay"};
	/** What a 1.8 PvP player (Minemen Club, duels, practice) wants on screen. */
	private static final String[] PVP_MODS = {"fps", "ping", "cps", "keystrokes", "armor", "potions", "combo", "reach",
		"tab_ping", "old_animations", "hit_color", "zoom", "low_fire", "hide_achievements", "no_hurt_cam"};

	private static final class Values {
		final Map<String, Boolean> modules = new LinkedHashMap<>();
		final Map<String, JsonObject> options = new LinkedHashMap<>();
		final Map<String, Integer> rows = new LinkedHashMap<>();

		Values on(String... ids) {
			for (String id : ids) {
				modules.put(id, true);
			}
			return this;
		}

		Values off(String... ids) {
			for (String id : ids) {
				modules.put(id, false);
			}
			return this;
		}

		Values option(String module, String key, Object value) {
			JsonObject o = options.computeIfAbsent(module, k -> new JsonObject());
			o.add(key, value instanceof Boolean ? new JsonPrimitive((Boolean) value)
				: value instanceof Number ? new JsonPrimitive((Number) value) : new JsonPrimitive(value.toString()));
			return this;
		}

		Values row(String id, int value) {
			rows.put(id, value);
			return this;
		}
	}

	private static final Map<Kind, Values> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put(Kind.RANKED_BEDWARS, new Values()
			.on("fps", "ping", "cps", "keystrokes", "armor", "potions", "bedwars_resources", "height_limit",
				"block_counter", "tab_ping", "tab_list", "old_animations", "hit_color", "zoom", "low_fire",
				"hide_achievements", "no_hurt_cam")
			.off("motion_blur", "color_grading", "weather_changer", "time_changer")
			.option("tab_list", "tab_background", 20)
			.row("render_distance", 8)
			.row("max_framerate", 260)
			.row("vsync", 0)
			.row("fancy_graphics", 0)
			.row("entity_shadows", 0)
			.row("view_bobbing", 0)
			.row("smooth_lighting", 0)
			.row("clouds", 0)
			.row("particles", 1)
			.row("mipmap_levels", 0)
			.row("occlusion_culling", 1)
			.row("cull_tile_entities", 1)
			.row("unfocused_cap", 1)
			.row("disable_fog", 1)
			.row("fast_world_switch", 1)
			.row("of_fast_render", 1)
			.row("of_fast_math", 1)
			.row("of_smooth_fps", 0)
			.row("of_smooth_world", 0)
			.row("of_clouds", 3)
			.row("of_dynamic_lights", 0)
			.row("of_vignette", 1));
		PRESETS.put(Kind.BEDWARS, new Values().on(BEDWARS_MODS));
		PRESETS.put(Kind.PVP, new Values().on(PVP_MODS));
		java.util.Set<String> both = new java.util.LinkedHashSet<>(java.util.Arrays.asList(BEDWARS_MODS));
		both.addAll(java.util.Arrays.asList(PVP_MODS));
		PRESETS.put(Kind.BOTH, new Values().on(both.toArray(new String[0])));
		PRESETS.put(Kind.QUALITY, new Values()
			.on("color_grading", "old_animations")
			.off("motion_blur")
			.option("color_grading", "grading_saturation", 115)
			.option("color_grading", "grading_vibrance", 20)
			.option("color_grading", "grading_contrast", 105)
			.option("color_grading", "grading_sharpen", 15)
			.row("render_distance", 12)
			.row("fancy_graphics", 1)
			.row("entity_shadows", 1)
			.row("smooth_lighting", 2)
			.row("clouds", 2)
			.row("particles", 0)
			.row("mipmap_levels", 4)
			.row("disable_fog", 0)
			.row("particle_collisions", 1)
			.row("of_fast_render", 1)
			.row("of_fast_math", 1));
	}

	private final Settings settings;
	private final ModuleRegistry modules;
	private final Path backupFile;

	public PlayPreset(Settings settings, ModuleRegistry modules, Path configDir) {
		this.settings = settings;
		this.modules = modules;
		this.backupFile = configDir.resolve("rago-play-preset-backup.json");
	}

	public boolean canUndo() {
		return Files.isRegularFile(backupFile);
	}

	/** Applies a preset and returns how many mods and rows changed. */
	public int apply(Kind kind) {
		if (!canUndo()) {
			save(backup());
		}
		Values v = PRESETS.get(kind);
		int changed = 0;
		for (Map.Entry<String, Boolean> e : v.modules.entrySet()) {
			Module m = modules.get(e.getKey()).orElse(null);
			if (m != null && m.isEnabled() != e.getValue()) {
				m.setEnabled(e.getValue());
				changed++;
			}
		}
		for (Map.Entry<String, JsonObject> e : v.options.entrySet()) {
			Module m = modules.get(e.getKey()).orElse(null);
			if (m == null) {
				continue;
			}
			for (Option<?> option : m.options()) {
				JsonElement value = e.getValue().get(option.key());
				if (value != null) {
					option.fromJson(value);
					changed++;
				}
			}
		}
		for (Setting row : settings.rows()) {
			Integer want = v.rows.get(row.id());
			if (want != null && PerformancePreset.read(row) != want) {
				PerformancePreset.write(row, want);
				changed++;
			}
		}
		return changed;
	}

	/** The mods and every row any preset touches, as they are now. */
	private JsonObject backup() {
		JsonObject b = new JsonObject();
		b.add("modules", ConfigStore.modulesToJson(modules));
		JsonObject rows = new JsonObject();
		for (Setting row : settings.rows()) {
			for (Values v : PRESETS.values()) {
				if (v.rows.containsKey(row.id())) {
					rows.addProperty(row.id(), PerformancePreset.read(row));
					break;
				}
			}
		}
		b.add("rows", rows);
		return b;
	}

	/** Puts back the mods and rows saved before the first preset, then forgets the backup. */
	public void undo() {
		JsonObject b = load();
		if (b == null) {
			return;
		}
		if (b.has("modules") && b.get("modules").isJsonObject()) {
			ConfigStore.modulesFromJson(modules, b.getAsJsonObject("modules"));
		}
		JsonObject rows = b.has("rows") && b.get("rows").isJsonObject() ? b.getAsJsonObject("rows") : new JsonObject();
		for (Setting row : settings.rows()) {
			if (rows.has(row.id())) {
				PerformancePreset.write(row, rows.get(row.id()).getAsInt());
			}
		}
		try {
			Files.deleteIfExists(backupFile);
		} catch (IOException e) {
			LOG.warn("Could not delete {}", backupFile, e);
		}
	}

	private void save(JsonObject backup) {
		try {
			Files.createDirectories(backupFile.getParent());
			Files.write(backupFile, new Gson().toJson(backup).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			LOG.warn("Could not save {}", backupFile, e);
		}
	}

	private JsonObject load() {
		try {
			if (!canUndo()) {
				return null;
			}
			return new Gson().fromJson(new String(Files.readAllBytes(backupFile), StandardCharsets.UTF_8), JsonObject.class);
		} catch (Exception e) {
			LOG.warn("Could not read {}", backupFile, e);
			return null;
		}
	}
}
