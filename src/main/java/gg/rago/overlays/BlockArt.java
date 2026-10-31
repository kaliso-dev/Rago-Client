package gg.rago.overlays;

/**
 * Wool and stained clay textures drawn by Rago, in code (no image file, nothing taken from a pack):
 * a clean colour per dye in one of a few styles. Each block is drawn at the size of the texture it
 * replaces, so it fits any pack resolution. Clay is a little darker and duller than wool of the same
 * colour, as in the game, so the two stay apart in Bed Wars where both are bought.
 */
public final class BlockArt {
	/** How a block is drawn. */
	public enum Style {
		/** One colour, no texture: the cleanest look, blocks of a bridge merge into one surface. */
		FLAT,
		/** A darker frame with a light line inside it: each block stands out, handy to count a bridge. */
		OUTLINE,
		/** A raised tile: light top and left edges, dark bottom and right ones, a soft gradient. */
		BEVEL,
		/** Fine knit stripes in the colour: wool that still looks like wool, without the noise. */
		KNIT
	}

	/** The sixteen dyes in the game's order (block metadata 0 to 15), as in texture names. */
	public static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
	/** Rago's wool colours: the game's dyes, cleaner and a little brighter for the Bed Wars teams. */
	static final int[] WOOL = {0xEDEFF1, 0xEF7A1A, 0xBE48B5, 0x3DB0DB, 0xF6C82E, 0x6DBE22, 0xEE8DB0, 0x464C52,
		0x9A9C9A, 0x179DA8, 0x7B2FB0, 0x3446B8, 0x7A4C2C, 0x56731E, 0xC22F2A, 0x1B1C21};
	/**
	 * The § colour of the Bed Wars team that builds with each dye (white §f, yellow §e, green §a on lime,
	 * pink §d, gray §8, aqua §b on cyan, blue §9, red §c), or -1: these follow the colour-blind palettes.
	 */
	static final int[] TEAM_CODE = {0xf, -1, -1, -1, 0xe, 0xa, 0xd, 0x8, -1, 0xb, -1, 0x9, -1, -1, 0xc, -1};
	/** Clay against wool of the same dye: lightness and colourfulness kept. */
	static final double CLAY_LIGHTNESS = 0.86;
	static final double CLAY_CHROMA = 0.88;

	private BlockArt() {
	}

	/** The § colour code (0 to 15) of the Bed Wars team that builds with {@code dye}, or -1. */
	public static int teamCode(int dye) {
		return TEAM_CODE[dye];
	}

	/** The index of a dye by its texture name, or -1. */
	public static int dye(String name) {
		for (int i = 0; i < DYES.length; i++) {
			if (DYES[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The colour of {@code dye} as a block: Rago's own, or for a team dye with a colour-blind
	 * palette ({@code teamRgb} not -1, the palette's § colour) that colour's hue and colourfulness at
	 * the lightness of Rago's own, so it still reads as a block and not as bright text.
	 */
	public static int color(int dye, boolean clay, int teamRgb) {
		double[] lab = Oklab.fromRgb(WOOL[dye]);
		if (teamRgb >= 0 && TEAM_CODE[dye] >= 0) {
			double[] team = Oklab.fromRgb(teamRgb);
			// the palette's own colourfulness, at least the wool's: dull blocks are hard to tell apart
			double c = Math.max(Math.hypot(team[1], team[2]), Math.hypot(lab[1], lab[2]));
			double h = Math.atan2(team[2], team[1]);
			lab = new double[] {lab[0], c * Math.cos(h), c * Math.sin(h)};
		}
		if (clay) {
			lab = new double[] {lab[0] * CLAY_LIGHTNESS, lab[1] * CLAY_CHROMA, lab[2] * CLAY_CHROMA};
		}
		return Oklab.toRgb(lab);
	}

	/** The block {@code size} pixels wide and high (ARGB, rows from the top), in {@code rgb}. */
	public static int[] pixels(Style style, int rgb, int size, boolean clay) {
		int[] out = new int[size * size];
		double[] base = Oklab.fromRgb(rgb);
		// one game pixel of a 16x texture, in this texture's pixels
		int px = Math.max(1, Math.round(size / 16f));
		boolean dark = base[0] < 0.36;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				double dl;
				switch (style) {
					case OUTLINE:
						dl = outline(x, y, size, px, dark, clay);
						break;
					case BEVEL:
						dl = bevel(x, y, size, px, dark);
						break;
					case KNIT:
						dl = knit(x, y, size, px, clay);
						break;
					default:
						dl = 0;
				}
				out[y * size + x] = 0xFF000000 | shade(base, dl);
			}
		}
		return out;
	}

	/** Lightness change of a pixel of the frame: edge darker (lighter on a dark block), a light line inside. */
	private static double outline(int x, int y, int size, int px, boolean dark, boolean clay) {
		int edge = Math.min(Math.min(x, y), Math.min(size - 1 - x, size - 1 - y)) / px;
		if (edge == 0) {
			return dark ? 0.13 : -0.16;
		}
		if (edge == 1) {
			return clay ? -0.03 : 0.05;
		}
		return 0;
	}

	private static double bevel(int x, int y, int size, int px, boolean dark) {
		int left = x / px;
		int top = y / px;
		int right = (size - 1 - x) / px;
		int bottom = (size - 1 - y) / px;
		double light = dark ? 0.16 : 0.11;
		double shadow = dark ? -0.05 : -0.13;
		double gradient = 0.025 - 0.05 * y / (double) (size - 1);
		// the nearest edge wins; corners split on the diagonal like a real bevel
		int nearest = Math.min(Math.min(left, top), Math.min(right, bottom));
		if (nearest > 1) {
			return gradient;
		}
		double strength = nearest == 0 ? 1 : 0.5;
		if (top == nearest && top <= right || left == nearest && left <= bottom) {
			return light * strength + gradient;
		}
		return shadow * strength + gradient;
	}

	/** Stripes like rows of stitches: every other game pixel row a shade lighter, offset in pairs. */
	private static double knit(int x, int y, int size, int px, boolean clay) {
		int gx = x / px;
		int gy = y / px;
		if (clay) {
			// clay keeps a smooth face with a fine frame, so it is never mistaken for wool
			int edge = Math.min(Math.min(x, y), Math.min(size - 1 - x, size - 1 - y)) / px;
			return edge == 0 ? -0.07 : 0;
		}
		boolean stitch = ((gx + (gy / 2)) & 1) == 0;
		return (gy & 1) == 0 ? (stitch ? 0.025 : 0) : (stitch ? -0.012 : -0.032);
	}

	/** {@code base} (OKLab) with its lightness moved by {@code dl}, as RGB. */
	private static int shade(double[] base, double dl) {
		if (dl == 0) {
			return Oklab.toRgb(base);
		}
		double l = Math.max(0, Math.min(1, base[0] + dl));
		// keep the colour from going grey when darkened a lot or washing out when lightened
		double k = l <= 0 ? 0 : Math.min(1.15, l / Math.max(0.05, base[0]));
		double scale = dl > 0 ? 1 - Math.min(0.35, dl * 1.5) : Math.min(1, 0.6 + 0.4 * k);
		return Oklab.toRgb(new double[] {l, base[1] * scale, base[2] * scale});
	}

	/** Björn Ottosson's OKLab, with sRGB in and out; enough of it for shading. */
	static final class Oklab {
		private Oklab() {
		}

		static double[] fromRgb(int rgb) {
			double r = linear((rgb >> 16 & 0xFF) / 255.0);
			double g = linear((rgb >> 8 & 0xFF) / 255.0);
			double b = linear((rgb & 0xFF) / 255.0);
			double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
			double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
			double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
			return new double[] {
				0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
				1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
				0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
		}

		static int toRgb(double[] lab) {
			double l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2];
			double m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2];
			double s = lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2];
			l = l * l * l;
			m = m * m * m;
			s = s * s * s;
			int r = channel(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
			int g = channel(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
			int b = channel(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
			return r << 16 | g << 8 | b;
		}

		private static double linear(double c) {
			return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
		}

		private static int channel(double linear) {
			double c = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(Math.max(0, linear), 1 / 2.4) - 0.055;
			return (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
		}
	}
}
