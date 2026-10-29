package gg.rago.optimize;

import gg.rago.settings.Setting;
import gg.rago.settings.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

/**
 * Settings-page rows that mirror Minecraft's video settings and, when OptiFine is installed, its
 * quality, performance, detail and animation settings. Values stay in the game's own files.
 */
public final class VideoRows {
	private static final String MC = "rago.section.minecraft";
	private static final String OF = "rago.section.optifine";
	private static final String OF_DETAILS = "rago.section.optifine_details";
	private static final String OF_ANIM = "rago.section.optifine_animations";
	private static final String OF_PARTICLES = "rago.section.optifine_particles";

	private VideoRows() {
	}

	public static void register(Settings s) {
		Setting.Tab p = Setting.Tab.PERFORMANCE;
		Minecraft mc = Minecraft.getMinecraft();
		GameSettings.Options renderDistance = GameOptions.option("RENDER_DISTANCE");
		GameSettings.Options framerate = GameOptions.option("FRAMERATE_LIMIT");
		s.slider(p, MC, "render_distance", 2, 32, 1, v -> I18n.format("rago.value.chunks", v),
			() -> mc.gameSettings.renderDistanceChunks,
			v -> GameOptions.setFloat(renderDistance, Math.min(v, (int) GameOptions.max(renderDistance))));
		s.slider(p, MC, "max_framerate", 10, 260, 10, v -> v >= 260 ? I18n.format("rago.value.unlimited") : v + " FPS",
			() -> mc.gameSettings.limitFramerate, v -> GameOptions.setFloat(framerate, v));
		vanillaToggle(s, "vsync", "ENABLE_VSYNC", () -> mc.gameSettings.enableVsync);
		vanillaToggle(s, "fancy_graphics", "GRAPHICS", () -> mc.gameSettings.fancyGraphics);
		vanillaToggle(s, "entity_shadows", "ENTITY_SHADOWS", () -> mc.gameSettings.entityShadows);
		vanillaToggle(s, "view_bobbing", "VIEW_BOBBING", () -> mc.gameSettings.viewBobbing);
		if (!OptiFineFields.installed()) {
			cycle(s, MC, "smooth_lighting", "AMBIENT_OCCLUSION", () -> mc.gameSettings.ambientOcclusion,
				new int[] {0, 1, 2}, "options.ao.off", "options.ao.min", "options.ao.max");
			cycle(s, MC, "clouds", "RENDER_CLOUDS", () -> mc.gameSettings.clouds,
				new int[] {0, 1, 2}, "options.off", "options.clouds.fast", "options.clouds.fancy");
		}
		cycle(s, MC, "particles", "PARTICLES", () -> mc.gameSettings.particleSetting,
			new int[] {0, 1, 2}, "options.particles.all", "options.particles.decreased", "options.particles.minimal");
		GameSettings.Options mipmap = GameOptions.option("MIPMAP_LEVELS");
		s.slider(p, MC, "mipmap_levels", 0, 4, 1, v -> v == 0 ? I18n.format("options.off") : Integer.toString(v),
			() -> mc.gameSettings.mipmapLevels, v -> GameOptions.setFloat(mipmap, v));

		if (!OptiFineFields.installed()) {
			return;
		}
		s.button(p, OF, "optifine_screen", () -> mc.displayGuiScreen(new GuiVideoSettings(mc.currentScreen, mc.gameSettings)));
		ofToggle(s, OF, "of_fast_render", "ofFastRender", "FAST_RENDER");
		ofToggle(s, OF, "of_fast_math", "ofFastMath", "FAST_MATH");
		ofToggle(s, OF, "of_smart_animations", "ofSmartAnimations", "SMART_ANIMATIONS");
		ofToggle(s, OF, "of_render_regions", "ofRenderRegions", "RENDER_REGIONS");
		ofToggle(s, OF, "of_lazy_chunks", "ofLazyChunkLoading", "LAZY_CHUNK_LOADING");
		ofToggle(s, OF, "of_dynamic_updates", "ofChunkUpdatesDynamic", "CHUNK_UPDATES_DYNAMIC");
		ofToggle(s, OF, "of_smooth_fps", "ofSmoothFps", "SMOOTH_FPS");
		ofToggle(s, OF, "of_smooth_world", "ofSmoothWorld", "SMOOTH_WORLD");
		ofToggle(s, OF, "of_dynamic_fov", "ofDynamicFov", "DYNAMIC_FOV");
		ofToggle(s, OF, "of_show_fps", "ofShowFps", "SHOW_FPS");
		ofCycle(s, OF, "of_chunk_updates", "ofChunkUpdates", "CHUNK_UPDATES", new int[] {1, 2, 3, 4, 5}, "1", "2", "3", "4", "5");

		String[] dff = {"rago.value.default", "rago.value.fast", "rago.value.fancy", "options.off"};
		ofCycle(s, OF_DETAILS, "of_clouds", "ofClouds", "CLOUDS", new int[] {0, 1, 2, 3}, dff);
		ofCycle(s, OF_DETAILS, "of_trees", "ofTrees", "TREES", new int[] {0, 1, 2, 4},
			"rago.value.default", "rago.value.fast", "rago.value.fancy", "rago.value.smart");
		ofCycle(s, OF_DETAILS, "of_rain", "ofRain", "RAIN", new int[] {0, 1, 2, 3}, dff);
		ofCycle(s, OF_DETAILS, "of_dropped_items", "ofDroppedItems", "DROPPED_ITEMS", new int[] {0, 1, 2},
			"rago.value.default", "rago.value.fast", "rago.value.fancy");
		ofCycle(s, OF_DETAILS, "of_vignette", "ofVignette", "VIGNETTE", new int[] {0, 1, 2},
			"rago.value.default", "rago.value.fast", "rago.value.fancy");
		ofCycle(s, OF_DETAILS, "of_better_grass", "ofBetterGrass", "BETTER_GRASS", new int[] {3, 1, 2},
			"options.off", "rago.value.fast", "rago.value.fancy");
		ofCycle(s, OF_DETAILS, "of_dynamic_lights", "ofDynamicLights", "DYNAMIC_LIGHTS", new int[] {3, 1, 2},
			"options.off", "rago.value.fast", "rago.value.fancy");
		ofToggle(s, OF_DETAILS, "of_sky", "ofSky", "SKY");
		ofToggle(s, OF_DETAILS, "of_stars", "ofStars", "STARS");
		ofToggle(s, OF_DETAILS, "of_sun_moon", "ofSunMoon", "SUN_MOON");
		ofToggle(s, OF_DETAILS, "of_show_capes", "ofShowCapes", "SHOW_CAPES");
		ofToggle(s, OF_DETAILS, "of_swamp_colors", "ofSwampColors", "SWAMP_COLORS");
		ofToggle(s, OF_DETAILS, "of_smooth_biomes", "ofSmoothBiomes", "SMOOTH_BIOMES");
		ofToggle(s, OF_DETAILS, "of_custom_sky", "ofCustomSky", "CUSTOM_SKY");

		String[] onDynOff = {"options.on", "rago.value.dynamic", "options.off"};
		ofCycle(s, OF_ANIM, "of_anim_water", "ofAnimatedWater", "ANIMATED_WATER", new int[] {0, 1, 2}, onDynOff);
		ofCycle(s, OF_ANIM, "of_anim_lava", "ofAnimatedLava", "ANIMATED_LAVA", new int[] {0, 1, 2}, onDynOff);
		ofToggle(s, OF_ANIM, "of_anim_fire", "ofAnimatedFire", "ANIMATED_FIRE");
		ofToggle(s, OF_ANIM, "of_anim_portal", "ofAnimatedPortal", "ANIMATED_PORTAL");
		ofToggle(s, OF_ANIM, "of_anim_redstone", "ofAnimatedRedstone", "ANIMATED_REDSTONE");
		ofToggle(s, OF_ANIM, "of_anim_explosion", "ofAnimatedExplosion", "ANIMATED_EXPLOSION");
		ofToggle(s, OF_ANIM, "of_anim_flame", "ofAnimatedFlame", "ANIMATED_FLAME");
		ofToggle(s, OF_ANIM, "of_anim_smoke", "ofAnimatedSmoke", "ANIMATED_SMOKE");
		ofToggle(s, OF_ANIM, "of_anim_terrain", "ofAnimatedTerrain", "ANIMATED_TERRAIN");
		ofToggle(s, OF_ANIM, "of_anim_textures", "ofAnimatedTextures", "ANIMATED_TEXTURES");

		ofToggle(s, OF_PARTICLES, "of_void_particles", "ofVoidParticles", "VOID_PARTICLES");
		ofToggle(s, OF_PARTICLES, "of_water_particles", "ofWaterParticles", "WATER_PARTICLES");
		ofToggle(s, OF_PARTICLES, "of_rain_splash", "ofRainSplash", "RAIN_SPLASH");
		ofToggle(s, OF_PARTICLES, "of_portal_particles", "ofPortalParticles", "PORTAL_PARTICLES");
		ofToggle(s, OF_PARTICLES, "of_potion_particles", "ofPotionParticles", "POTION_PARTICLES");
		ofToggle(s, OF_PARTICLES, "of_firework_particles", "ofFireworkParticles", "FIREWORK_PARTICLES");
		ofToggle(s, OF_PARTICLES, "of_dripping", "ofDrippingWaterLava", "DRIPPING_WATER_LAVA");
	}

	private interface IntGetter {
		int get();
	}

	private interface BoolGetter {
		boolean get();
	}

	private static void vanillaToggle(Settings s, String id, String optionName, BoolGetter get) {
		GameSettings.Options o = GameOptions.option(optionName);
		if (o == null) {
			return;
		}
		s.toggle(Setting.Tab.PERFORMANCE, MC, id, get::get, v -> {
			if (get.get() != v) {
				GameOptions.step(o);
			}
		});
	}

	/** A cycling option shown with ‹ ›; values are the stored ints in the order shown. */
	private static void cycle(Settings s, String section, String id, String optionName, IntGetter get, int[] values, String... keys) {
		GameSettings.Options o = GameOptions.option(optionName);
		if (o == null) {
			return;
		}
		s.choice(Setting.Tab.PERFORMANCE, section, id, () -> indexOf(values, get.get()), i -> stepUntil(o, get, values[i]), keys);
	}

	private static void ofToggle(Settings s, String section, String id, String field, String optionName) {
		GameSettings.Options o = GameOptions.option(optionName);
		if (o == null || !OptiFineFields.hasBool(field)) {
			return;
		}
		s.toggle(Setting.Tab.PERFORMANCE, section, id, () -> OptiFineFields.getBool(field), v -> {
			if (OptiFineFields.getBool(field) != v) {
				GameOptions.step(o);
			}
		});
	}

	private static void ofCycle(Settings s, String section, String id, String field, String optionName, int[] values, String... keys) {
		GameSettings.Options o = GameOptions.option(optionName);
		if (o == null || values == null || !OptiFineFields.hasInt(field)) {
			return;
		}
		cycle(s, section, id, optionName, () -> OptiFineFields.getInt(field), values, keys);
	}

	private static int indexOf(int[] values, int v) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == v) {
				return i;
			}
		}
		return 0;
	}

	/** Clicks the option until it holds the wanted value, at most a full cycle plus one. */
	private static void stepUntil(GameSettings.Options o, IntGetter get, int want) {
		for (int i = 0; i < 8 && get.get() != want; i++) {
			GameOptions.step(o);
		}
	}
}
