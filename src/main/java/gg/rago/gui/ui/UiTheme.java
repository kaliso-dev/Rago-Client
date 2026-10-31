package gg.rago.gui.ui;

/**
 * The look of Rago's menus, applied to every colour {@link Ui} draws. Dark is the design as drawn;
 * OLED black takes the grey surfaces down to black (the background becomes pure black, the cards
 * keep their steps between them) and draws panels solid instead of glass; High contrast does the
 * same, lifts grey text and icons towards white ({@link #text}) and makes the faint white borders
 * stronger. Colours (the accent, green, team colours) and pictures keep theirs, and the HUD and the
 * game itself are never touched.
 */
public final class UiTheme {
	public static final int DARK = 0;
	public static final int OLED = 1;
	public static final int CONTRAST = 2;

	/** A colour whose channels differ by at most this much is a grey. */
	static final int NEUTRAL = 16;
	/** Greys up to this are surfaces (backgrounds, panels, cards, fields). */
	static final int SURFACE_TOP = 0x40;
	/** The darkest surface of the design (the background), which OLED makes black. */
	static final int DARKEST = 0x0B;
	/** Greys from here up to {@link #TEXT_TOP} are text and icons, lifted in High contrast. */
	static final int TEXT_BOTTOM = 0x58;
	static final int TEXT_TOP = 0xE8;
	static final int WHITE = 0xF4;
	static final float LIFT = 0.5f;
	/** Faint white (borders, hovers) is this much stronger in High contrast, up to {@link #BORDER_MAX}. */
	static final float BORDER_BOOST = 2.5f;
	static final int BORDER_MAX = 0x80;

	private static int current = DARK;

	private UiTheme() {
	}

	public static void set(int theme) {
		current = theme == OLED || theme == CONTRAST ? theme : DARK;
	}

	public static int current() {
		return current;
	}

	/** A shape's colour (fills, gradients, pictures) under the current theme. */
	public static int apply(int argb) {
		return current == DARK ? argb : apply(argb, current, false);
	}

	/** A line's colour (borders, outlines, separators) under the current theme. */
	public static int line(int argb) {
		return current == DARK ? argb : apply(argb, current, true);
	}

	/** A text or icon colour under the current theme. */
	public static int text(int argb) {
		return current == CONTRAST ? text(argb, current) : argb;
	}

	/** Whether panels are drawn as glass (the blurred game behind them): only in Dark. */
	public static boolean glass() {
		return current == DARK;
	}

	static int apply(int argb, int theme) {
		return apply(argb, theme, false);
	}

	/**
	 * Faint white is made stronger only in lines: behind text, a stronger white fill would take
	 * the contrast away from the text instead of adding to it.
	 */
	static int apply(int argb, int theme, boolean line) {
		if (theme != OLED && theme != CONTRAST) {
			return argb;
		}
		int a = argb >>> 24;
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		if (max - min > NEUTRAL) {
			return argb;
		}
		if (max <= SURFACE_TOP) {
			return a << 24 | deepen(r) << 16 | deepen(g) << 8 | deepen(b);
		}
		if (line && theme == CONTRAST && a < 0x80 && min >= 0xE0) {
			// faint white over the panels: borders, hover and selected tints
			return Math.min(BORDER_MAX, Math.round(a * BORDER_BOOST)) << 24 | argb & 0xFFFFFF;
		}
		return argb;
	}

	static int text(int argb, int theme) {
		if (theme != CONTRAST || argb >>> 24 < 0x80) {
			return argb;
		}
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		if (max - min > NEUTRAL || max < TEXT_BOTTOM || max >= TEXT_TOP) {
			return argb;
		}
		return argb & 0xFF000000 | lift(r) << 16 | lift(g) << 8 | lift(b);
	}

	/** A surface channel: the background goes to black, {@link #SURFACE_TOP} stays where it is. */
	static int deepen(int c) {
		return c <= DARKEST ? 0 : Math.round((c - DARKEST) * (float) SURFACE_TOP / (SURFACE_TOP - DARKEST));
	}

	static int lift(int c) {
		return c + Math.round((WHITE - c) * LIFT);
	}
}
