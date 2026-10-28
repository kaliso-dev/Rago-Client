package gg.rago.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The values the competitive profile sets. Only rendering settings: nothing here changes what
 * the player sends to a server.
 */
public final class CompetitivePreset {
	/** Vanilla options, under Rago's own keys (see {@code CompetitiveProfile}). */
	public static final Map<String, JsonElement> VANILLA;
	/** Argentum Extras options, under the keys of {@code config/argentum-extras.json}. */
	public static final Map<String, JsonElement> EXTRAS;

	static {
		Map<String, JsonElement> v = new LinkedHashMap<>();
		v.put("fancy_graphics", new JsonPrimitive(false));
		v.put("particles", new JsonPrimitive(2)); // minimal
		v.put("ambient_occlusion", new JsonPrimitive(0));
		v.put("clouds", new JsonPrimitive(0)); // off
		v.put("entity_shadows", new JsonPrimitive(false));
		v.put("fps_limit", new JsonPrimitive(260)); // the slider's "Unlimited"
		v.put("vsync", new JsonPrimitive(false));
		VANILLA = Collections.unmodifiableMap(v);

		Map<String, JsonElement> x = new LinkedHashMap<>();
		x.put("explosion_particles", new JsonPrimitive(false));
		x.put("portal_particles", new JsonPrimitive(false));
		x.put("smoke_and_flame_particles", new JsonPrimitive(false));
		x.put("redstone_particles", new JsonPrimitive(false));
		x.put("water_particles", new JsonPrimitive(false));
		x.put("miscellaneous_particles", new JsonPrimitive(false));
		// spell_particles stay on: they show which potions an opponent has.
		x.put("dynamic_fov_strength", new JsonPrimitive(0));
		x.put("entity_shadow_distance", new JsonPrimitive(0));
		x.put("player_ears", new JsonPrimitive(false));
		x.put("stuck_arrows", new JsonPrimitive(false));
		x.put("stars", new JsonPrimitive(false));
		x.put("vignette_strength", new JsonPrimitive(0));
		x.put("weather_density", new JsonPrimitive(0));
		x.put("leaf_quality", new JsonPrimitive("FAST"));
		EXTRAS = Collections.unmodifiableMap(x);
	}

	private CompetitivePreset() {
	}
}
