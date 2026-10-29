package gg.rago.optimize;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import gg.rago.settings.Choice;
import gg.rago.settings.Setting;
import gg.rago.settings.Settings;
import gg.rago.settings.Slider;
import gg.rago.settings.Toggle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Max performance": sets every row of the Performance page that has a faster value to that value
 * in one click: Rago's own culling and hiding options, Minecraft's video settings and OptiFine's.
 * What each row held before is written to {@code config/rago-preset-backup.json} first, so
 * {@link #undo} can put everything back, even after a restart.
 */
public final class PerformancePreset {
	private static final Logger LOG = LogManager.getLogger("Rago");

	/**
	 * Row id to the value it gets: 1/0 for toggles, the value for sliders, the position in the
	 * list for choices. Rows that are absent (no OptiFine, say) are skipped.
	 */
	private static final Map<String, Integer> VALUES = new LinkedHashMap<>();

	static {
		// Rago: frame rate
		VALUES.put("menu_fps", 60);
		VALUES.put("unfocused_cap", 1);
		VALUES.put("unfocused_fps", 15);
		VALUES.put("minimized_cap", 1);
		VALUES.put("occlusion_culling", 1);
		VALUES.put("cull_tile_entities", 1);
		// Rago: entities
		VALUES.put("entity_distance", 1);
		VALUES.put("player_distance", 64);
		VALUES.put("hostile_distance", 32);
		VALUES.put("passive_distance", 24);
		VALUES.put("other_distance", 24);
		for (String id : new String[] {"hide_stuck_arrows", "hide_item_frames", "hide_paintings", "hide_armor_stands",
			"hide_xp_orbs", "hide_fireworks", "hide_falling_blocks"}) {
			VALUES.put(id, 1);
		}
		// Rago: tile entities and world
		VALUES.put("tile_distance", 24);
		for (String id : new String[] {"hide_skulls", "hide_signs", "hide_enchant_books", "hide_banners",
			"hide_beacon_beams", "hide_end_portals", "hide_grass", "hide_flowers", "hide_dead_bushes", "disable_fog"}) {
			VALUES.put(id, 1);
		}
		VALUES.put("max_particles", 200);
		// Rago: engine
		VALUES.put("fast_world_switch", 1);
		VALUES.put("unstacked_items", 1);
		VALUES.put("skip_gl_errors", 1);
		// Minecraft
		VALUES.put("render_distance", 8);
		VALUES.put("max_framerate", 260);
		VALUES.put("vsync", 0);
		VALUES.put("fancy_graphics", 0);
		VALUES.put("entity_shadows", 0);
		VALUES.put("view_bobbing", 0);
		VALUES.put("smooth_lighting", 0);
		VALUES.put("clouds", 0);
		VALUES.put("particles", 2);
		VALUES.put("mipmap_levels", 0);
		// OptiFine: performance
		for (String id : new String[] {"of_fast_render", "of_fast_math", "of_smart_animations", "of_render_regions",
			"of_lazy_chunks"}) {
			VALUES.put(id, 1);
		}
		for (String id : new String[] {"of_dynamic_updates", "of_smooth_fps", "of_smooth_world", "of_dynamic_fov"}) {
			VALUES.put(id, 0);
		}
		VALUES.put("of_chunk_updates", 0);
		// OptiFine: details (positions in the lists shown on the page)
		VALUES.put("of_clouds", 3);
		VALUES.put("of_trees", 1);
		VALUES.put("of_rain", 3);
		VALUES.put("of_dropped_items", 1);
		VALUES.put("of_vignette", 1);
		VALUES.put("of_better_grass", 0);
		VALUES.put("of_dynamic_lights", 0);
		for (String id : new String[] {"of_sky", "of_stars", "of_sun_moon", "of_show_capes", "of_swamp_colors",
			"of_smooth_biomes", "of_custom_sky"}) {
			VALUES.put(id, 0);
		}
		// OptiFine: animations and particles
		VALUES.put("of_anim_water", 2);
		VALUES.put("of_anim_lava", 2);
		for (String id : new String[] {"of_anim_fire", "of_anim_portal", "of_anim_redstone", "of_anim_explosion",
			"of_anim_flame", "of_anim_smoke", "of_anim_terrain", "of_anim_textures", "of_void_particles",
			"of_water_particles", "of_rain_splash", "of_portal_particles", "of_potion_particles",
			"of_firework_particles", "of_dripping"}) {
			VALUES.put(id, 0);
		}
	}

	private final Settings settings;
	private final Path backupFile;

	public PerformancePreset(Settings settings, Path configDir) {
		this.settings = settings;
		this.backupFile = configDir.resolve("rago-preset-backup.json");
	}

	/** The ids the preset changes, for tests and the page's help text. */
	static Iterable<String> ids() {
		return VALUES.keySet();
	}

	public boolean canUndo() {
		return Files.isRegularFile(backupFile);
	}

	/** Applies the preset and returns how many rows changed. */
	public int apply() {
		JsonObject backup = new JsonObject();
		int changed = 0;
		for (Setting row : settings.rows()) {
			Integer want = VALUES.get(row.id());
			if (want == null) {
				continue;
			}
			int before = read(row);
			if (before == want) {
				continue;
			}
			backup.add(row.id(), new JsonPrimitive(before));
			write(row, want);
			changed++;
		}
		if (changed > 0 && !canUndo()) {
			// keep the oldest backup: applying twice must not overwrite the player's own values
			save(backup);
		}
		return changed;
	}

	/** Puts back every value the preset changed, then forgets the backup. */
	public int undo() {
		JsonObject backup = load();
		if (backup == null) {
			return 0;
		}
		int restored = 0;
		for (Setting row : settings.rows()) {
			if (backup.has(row.id())) {
				write(row, backup.get(row.id()).getAsInt());
				restored++;
			}
		}
		try {
			Files.deleteIfExists(backupFile);
		} catch (IOException e) {
			LOG.warn("Could not delete {}", backupFile, e);
		}
		return restored;
	}

	private static int read(Setting row) {
		if (row instanceof Toggle) {
			return ((Toggle) row).get() ? 1 : 0;
		}
		if (row instanceof Slider) {
			return ((Slider) row).get();
		}
		if (row instanceof Choice) {
			return ((Choice) row).index();
		}
		return Integer.MIN_VALUE;
	}

	private static void write(Setting row, int value) {
		try {
			if (row instanceof Toggle) {
				((Toggle) row).set(value != 0);
			} else if (row instanceof Slider) {
				((Slider) row).set(value);
			} else if (row instanceof Choice) {
				((Choice) row).select(value);
			}
		} catch (RuntimeException e) {
			LOG.warn("Could not set {} to {}", row.id(), value, e);
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
