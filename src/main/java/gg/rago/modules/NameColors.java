package gg.rago.modules;

import java.awt.Color;

/**
 * The colours a Rago player can give their name (catalogue "name:..."): plain colours, gradients along
 * the name, and colours that move along it. Drawn letter by letter by {@link RagoBadge} on name tags
 * and in the tab list, out of Bed Wars games.
 */
public final class NameColors {
	/** How long the moving colours take to go along the name, in milliseconds. */
	private static final long MOVE_MS = 2600;
	/** The rainbow's colours go round in this long. */
	private static final long RAINBOW_MS = 3200;

	private NameColors() {
	}

	/** The colours (RGB) the name goes through, null for an id that is not a name colour. */
	static int[] stops(String id) {
		switch (id) {
			case "mint":
				return new int[] {0x7CF5C8};
			case "sky":
				return new int[] {0x7CC8FF};
			case "peach":
				return new int[] {0xFFB38A};
			case "lilac":
				return new int[] {0xC9A7FF};
			case "lemon":
				return new int[] {0xFFF07A};
			case "rago_red":
				return new int[] {0xFF3B3B};
			case "sakura":
				return new int[] {0xFF9ECF};
			case "sunset":
				return new int[] {0xFF8A3D, 0xFF3E9E};
			case "ocean":
				return new int[] {0x3DD9FF, 0x3D6BFF};
			case "toxic":
				return new int[] {0xB6FF3D, 0x3DFF8A};
			case "aurora":
				return new int[] {0x3DFFB0, 0x7A5CFF, 0xFF5CC8};
			case "inferno":
				return new int[] {0xFFDB3D, 0xFF3D1F};
			case "gold_rush":
				return new int[] {0xFFF1B0, 0xE0A526};
			case "rainbow":
				// the hues are worked out in shade()
				return new int[] {0xFF0000, 0x00FF00};
			case RagoBadge.Style.CREATOR:
				return new int[] {RagoBadge.CREATOR_LIGHT, RagoBadge.CREATOR_DEEP};
			default:
				return null;
		}
	}

	/** Whether the colours move along the name. */
	static boolean moving(String id) {
		switch (id) {
			case "aurora":
			case "inferno":
			case "gold_rush":
			case "rainbow":
			case RagoBadge.Style.CREATOR:
				return true;
			default:
				return false;
		}
	}

	/**
	 * The colour (RGB) of letter {@code i} of an {@code n} letter name: a plain colour, a gradient from
	 * the first letter to the last, or (moving) colours going along the name and back.
	 */
	static int shade(int[] stops, boolean moving, int i, int n, long time) {
		if (stops.length == 1) {
			return stops[0];
		}
		if (stops.length == 2 && stops[0] == 0xFF0000 && stops[1] == 0x00FF00) {
			float hue = (float) i / Math.max(1, n) * 0.6f - (float) (time % RAINBOW_MS) / RAINBOW_MS;
			return Color.HSBtoRGB(hue - (float) Math.floor(hue), 0.62f, 1f) & 0xFFFFFF;
		}
		float t;
		if (moving) {
			t = (float) i / Math.max(1, n) * 0.75f - (float) (time % MOVE_MS) / MOVE_MS;
			t -= (float) Math.floor(t);
			t = 1 - Math.abs(2 * t - 1);
		} else {
			t = n <= 1 ? 0 : (float) i / (n - 1);
		}
		float at = t * (stops.length - 1);
		int k = Math.min(stops.length - 2, (int) at);
		return RagoBadge.mix(stops[k], stops[k + 1], at - k);
	}
}
