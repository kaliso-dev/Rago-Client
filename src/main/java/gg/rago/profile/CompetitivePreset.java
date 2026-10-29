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
	/** OptiFine options, under the names of its fields in {@code GameSettings}. */
	public static final Map<String, JsonElement> OPTIFINE;

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

		// OptiFine's own settings (fields it adds to GameSettings); skipped when OptiFine is absent.
		Map<String, JsonElement> x = new LinkedHashMap<>();
		x.put("ofFastMath", new JsonPrimitive(true));
		x.put("ofSmoothFps", new JsonPrimitive(false));
		x.put("ofDynamicFov", new JsonPrimitive(false));
		x.put("ofClouds", new JsonPrimitive(3)); // off
		x.put("ofTrees", new JsonPrimitive(1)); // fast
		x.put("ofSky", new JsonPrimitive(false));
		x.put("ofStars", new JsonPrimitive(false));
		x.put("ofVignette", new JsonPrimitive(1)); // fast = none
		x.put("ofAnimatedExplosion", new JsonPrimitive(false));
		x.put("ofAnimatedFlame", new JsonPrimitive(false));
		x.put("ofAnimatedSmoke", new JsonPrimitive(false));
		x.put("ofVoidParticles", new JsonPrimitive(false));
		x.put("ofWaterParticles", new JsonPrimitive(false));
		x.put("ofPortalParticles", new JsonPrimitive(false));
		x.put("ofDrippingWaterLava", new JsonPrimitive(false));
		x.put("ofFireworkParticles", new JsonPrimitive(false));
		x.put("ofRainSplash", new JsonPrimitive(false));
		// ofPotionParticles stays on: it shows which potions an opponent has.
		OPTIFINE = Collections.unmodifiableMap(x);
	}

	private CompetitivePreset() {
	}
}
