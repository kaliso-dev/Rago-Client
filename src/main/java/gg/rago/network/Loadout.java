package gg.rago.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The cosmetics a player shows, as Rago Network passes them from one client to another: the fields
 * of the server's cosmetics.json, with the same choices, ranges and defaults (checked by
 * CosmeticsWhitelistTest). Read from the server with {@link #fromJson}, where a field that is missing
 * or not allowed falls back to its default, as the server itself does.
 */
public final class Loadout {
	public static final String[] CAPES = {"none", "rago", "black_cat", "pixel_cat", "night_cat", "qrago", "primal", "mfga"};
	public static final String[] HATS = {"none", "rago_cap", "rago_beanie", "top_hat", "wizard_hat", "crown", "halo"};
	public static final String[] WINGS = {"none", "dragon", "dragon_small"};
	public static final String[] SHIELDS = {"none", "rago", "crimson", "gold", "frost", "void"};
	/** The neon colours, as {@code gg.rago.render.NeonColors.IDS}. */
	public static final String[] NEON = {"red", "orange", "yellow", "green", "cyan", "blue", "purple", "pink", "white"};
	public static final int HAT_SCALE_MIN = 70;
	public static final int HAT_SCALE_MAX = 150;
	public static final int WINGS_SCALE_MIN = 50;
	public static final int WINGS_SCALE_MAX = 160;
	public static final int FLAP_SPEED_MIN = 25;
	public static final int FLAP_SPEED_MAX = 300;
	public static final int SHIELD_SCALE_MIN = 50;
	public static final int SHIELD_SCALE_MAX = 150;
	/** A colour left at white: the piece's own colours. */
	public static final int NO_TINT = 0xFFFFFFFF;

	public static final Loadout NONE = new Loadout("none", "red", false, "none", "red", NO_TINT, 100, "none", "red", NO_TINT, 100,
		true, 100, "none", "red", 100, true);

	public final String cape;
	public final String capeNeon;
	/** Whether the Rago cape is shown over the player's Minecraft or OptiFine cape (else only when they have none). */
	public final boolean capeOverOwn;
	public final String hat;
	public final String hatNeon;
	public final int hatColor;
	public final int hatScale;
	public final String wings;
	public final String wingsNeon;
	public final int wingsColor;
	public final int wingsScale;
	public final boolean wingsFlap;
	public final int wingsFlapSpeed;
	public final String shield;
	public final String shieldNeon;
	public final int shieldScale;
	public final boolean neonGlow;

	public Loadout(String cape, String capeNeon, boolean capeOverOwn, String hat, String hatNeon, int hatColor, int hatScale,
		String wings, String wingsNeon, int wingsColor, int wingsScale, boolean wingsFlap, int wingsFlapSpeed, String shield,
		String shieldNeon, int shieldScale, boolean neonGlow) {
		this.cape = cape;
		this.capeNeon = capeNeon;
		this.capeOverOwn = capeOverOwn;
		this.hat = hat;
		this.hatNeon = hatNeon;
		this.hatColor = hatColor;
		this.hatScale = hatScale;
		this.wings = wings;
		this.wingsNeon = wingsNeon;
		this.wingsColor = wingsColor;
		this.wingsScale = wingsScale;
		this.wingsFlap = wingsFlap;
		this.wingsFlapSpeed = wingsFlapSpeed;
		this.shield = shield;
		this.shieldNeon = shieldNeon;
		this.shieldScale = shieldScale;
		this.neonGlow = neonGlow;
	}

	/** Whether this shows nothing at all. */
	public boolean empty() {
		return cape.equals("none") && hat.equals("none") && wings.equals("none") && shield.equals("none");
	}

	/** As the server takes it: colours as #RRGGBB (their alpha is not passed on). */
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("cape", cape);
		o.addProperty("cape_neon", capeNeon);
		o.addProperty("cape_over_own", capeOverOwn);
		o.addProperty("hat", hat);
		o.addProperty("hat_neon", hatNeon);
		o.addProperty("hat_color", hex(hatColor));
		o.addProperty("hat_scale", hatScale);
		o.addProperty("wings", wings);
		o.addProperty("wings_neon", wingsNeon);
		o.addProperty("wings_color", hex(wingsColor));
		o.addProperty("wings_scale", wingsScale);
		o.addProperty("wings_flap", wingsFlap);
		o.addProperty("wings_flap_speed", wingsFlapSpeed);
		o.addProperty("shield", shield);
		o.addProperty("shield_neon", shieldNeon);
		o.addProperty("shield_scale", shieldScale);
		o.addProperty("neon_glow", neonGlow);
		return o;
	}

	/** A loadout as the server sent it; anything missing or not allowed is left at its default. */
	public static Loadout fromJson(JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			return NONE;
		}
		JsonObject o = json.getAsJsonObject();
		return new Loadout(
			choice(o, "cape", CAPES), choice(o, "cape_neon", NEON), bool(o, "cape_over_own", false),
			choice(o, "hat", HATS), choice(o, "hat_neon", NEON), color(o, "hat_color"),
			integer(o, "hat_scale", HAT_SCALE_MIN, HAT_SCALE_MAX),
			choice(o, "wings", WINGS), choice(o, "wings_neon", NEON), color(o, "wings_color"),
			integer(o, "wings_scale", WINGS_SCALE_MIN, WINGS_SCALE_MAX), bool(o, "wings_flap", true),
			integer(o, "wings_flap_speed", FLAP_SPEED_MIN, FLAP_SPEED_MAX),
			choice(o, "shield", SHIELDS), choice(o, "shield_neon", NEON),
			integer(o, "shield_scale", SHIELD_SCALE_MIN, SHIELD_SCALE_MAX), bool(o, "neon_glow", true));
	}

	static String hex(int argb) {
		return String.format("#%06X", argb & 0xFFFFFF);
	}

	private static JsonPrimitive primitive(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsJsonPrimitive() : null;
	}

	/** The value if it is one of {@code values}, else the first of them (the default). */
	private static String choice(JsonObject o, String key, String[] values) {
		JsonPrimitive p = primitive(o, key);
		if (p != null && p.isString()) {
			String s = p.getAsString();
			for (String v : values) {
				if (v.equals(s)) {
					return v;
				}
			}
		}
		return values[0];
	}

	/** A whole number between {@code min} and {@code max}, else 100 (every size's default). */
	private static int integer(JsonObject o, String key, int min, int max) {
		JsonPrimitive p = primitive(o, key);
		if (p != null && p.isNumber()) {
			double d = p.getAsDouble();
			if (d == Math.rint(d) && d >= min && d <= max) {
				return (int) d;
			}
		}
		return 100;
	}

	/** true or false, else the switch's default. */
	private static boolean bool(JsonObject o, String key, boolean fallback) {
		JsonPrimitive p = primitive(o, key);
		return p == null || !p.isBoolean() ? fallback : p.getAsBoolean();
	}

	/** #RRGGBB as an opaque colour, else white. */
	private static int color(JsonObject o, String key) {
		JsonPrimitive p = primitive(o, key);
		if (p != null && p.isString()) {
			String s = p.getAsString();
			if (s.length() == 7 && s.charAt(0) == '#') {
				int rgb = 0;
				for (int i = 1; i < 7; i++) {
					int digit = Character.digit(s.charAt(i), 16);
					if (digit < 0) {
						return NO_TINT;
					}
					rgb = rgb << 4 | digit;
				}
				return 0xFF000000 | rgb;
			}
		}
		return NO_TINT;
	}
}
