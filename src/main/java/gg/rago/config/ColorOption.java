package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** ARGB colour stored as "#AARRGGBB". */
public final class ColorOption extends Option<Integer> {
	/** Colours offered by the menu's colour button, in order. */
	public static final int[] PRESETS = {
		0xFFFFFFFF, 0xFFAAAAAA, 0xFF55FFFF, 0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF5555, 0xFFFF55FF, 0xFF5555FF
	};

	/** The opacity this colour always has, or -1 when it can be set. */
	private int fixedAlpha = -1;

	public ColorOption(String key, int defaultArgb) {
		super(key, defaultArgb);
	}

	/** Any colour, always at this opacity (0 to 255): the menu shows no opacity bar for it. */
	public ColorOption fixedAlpha(int alpha) {
		fixedAlpha = alpha;
		set(get());
		makeCurrentDefault();
		return this;
	}

	public boolean hasFixedAlpha() {
		return fixedAlpha >= 0;
	}

	@Override
	protected Integer sanitize(Integer argb) {
		return fixedAlpha >= 0 ? fixedAlpha << 24 | argb & 0xFFFFFF : argb;
	}

	/** Moves to the next preset after the current colour (or the first preset). */
	public void cycle() {
		int current = get();
		for (int i = 0; i < PRESETS.length; i++) {
			if (PRESETS[i] == current) {
				set(PRESETS[(i + 1) % PRESETS.length]);
				return;
			}
		}
		set(PRESETS[0]);
	}

	public static String format(int argb) {
		return String.format(Locale.ROOT, "#%08X", argb);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(format(get()));
	}

	@Override
	protected Integer parse(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
			return null;
		}
		String s = json.getAsString();
		if (!s.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
			return null;
		}
		long v = Long.parseLong(s.substring(1), 16);
		return s.length() == 7 ? (int) (0xFF000000L | v) : (int) v;
	}
}
