package gg.rago.optimize;

import gg.rago.fixes.GlyphWidths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * PolyPatcher's Optimized Font Renderer and Cache Font Data, for the game's font (FontRenderer,
 * with or without OptiFine).
 * <p>
 * <b>Optimized Font Renderer.</b> The font sends every character to the graphics card on its own,
 * in immediate mode (about ten calls per character). Here the characters of a string go into one
 * vertex array, drawn in one call when the string is done, or before anything that must come
 * after them (another texture page, an underline or strikethrough); the same glyphs, positions,
 * texture coordinates and colours, in the same order. The colour the font leaves set is set again
 * after the draw, as without it. Not done with OptiFine's shaders, nor for a mod's own font
 * renderer (a subclass). Lookups the font does for each character (its glyph page, the lower case
 * copy of the string for each § code) are made without searching or copying, with the same
 * results. And where OptiFine lays text out otherwise than the game, the text is laid out as
 * PolyPatcher lays it, measured on its output: each character moves the next one on by its width
 * cut to whole units (the game's rule; OptiFine keeps the fraction), bold adds one unit (OptiFine
 * adds half a unit to a unicode glyph, or its HD font's bold offset), and a no-break space is as
 * wide as a space (OptiFine draws it as wide as the page's 160th glyph). Only drawing changes, not
 * what text measures.
 * <p>
 * <b>Cache Font Data.</b> The width of a string, measured each time it is drawn centred, in a box,
 * a name tag or the tab list, is kept the first time; kept widths are dropped when the font
 * changes (resource packs, Unicode font, the unicode width fix, the optimized font renderer).
 */
public final class FontBatch {
	/** The font's glyph page: the characters of textures/font/ascii.png, in order. */
	static final String PAGE = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";
	/** Each character's place in {@link #PAGE} (its first one), or -1. */
	private static final short[] PLACE = new short[65536];
	/** Widths kept at most; all are dropped beyond it. */
	static final int MAX_WIDTHS = 8192;

	private static volatile BooleanSupplier setting = () -> false;
	private static volatile BooleanSupplier cacheSetting = () -> false;

	private static final WorldRenderer BUFFER = new WorldRenderer(16384);
	private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
	private static boolean active;
	private static boolean open;
	private static ResourceLocation texture;
	private static float red = 1;
	private static float green = 1;
	private static float blue = 1;
	private static float alpha = 1;
	private static int r = 255;
	private static int g = 255;
	private static int b = 255;
	private static int a = 255;
	private static int mode;
	private static int vertices;
	private static final float[] STRIP = new float[8 * 5];
	private static float u;
	private static float v;

	private static String lowerOf;
	private static Locale lowerLocale;
	private static String lower;

	private static final HashMap<String, Integer> WIDTHS = new HashMap<>();
	private static FontRenderer widthsFont;
	private static boolean widthsUnicode;
	private static boolean widthsCut;
	private static int widthsLimit;
	private static int widthsGeneration;
	private static volatile int fontGeneration;
	private static volatile Thread clientThread;

	static {
		java.util.Arrays.fill(PLACE, (short) -1);
		for (int i = PAGE.length() - 1; i >= 0; i--) {
			PLACE[PAGE.charAt(i)] = (short) i;
		}
	}

	private FontBatch() {
	}

	public static void setSwitch(BooleanSupplier optimized, BooleanSupplier cache) {
		setting = optimized;
		cacheSetting = cache;
	}

	private static boolean on() {
		return setting.getAsBoolean();
	}

	/**
	 * Whether this is the game's thread, found the first time the font is used on it (Forge's loading
	 * screen draws its own font on another thread, which never batches nor keeps widths).
	 */
	private static boolean onClientThread() {
		Thread current = Thread.currentThread();
		Thread t = clientThread;
		if (t == null) {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null || !mc.isCallingFromMinecraftThread()) {
				return false;
			}
			clientThread = t = current;
		}
		return current == t;
	}

	/** For tests: the thread taken for the game's. */
	static void clientThreadForTests(Thread thread) {
		clientThread = thread;
	}

	/** Whether the characters being drawn go into the batch: one is open on this thread. */
	private static boolean batching() {
		return active && Thread.currentThread() == clientThread;
	}

	// ---- layout where OptiFine differs ----------------------------------------------------------

	/** doDraw, with OptiFine: how far the next character moves on (OptiFine: the exact width). */
	public static float advance(float width) {
		return on() ? (float) (int) width : width;
	}

	/** renderStringAtPos, with OptiFine: what bold adds to a character's width (OptiFine: 0.5 for a unicode glyph). */
	public static float boldAdvance(float offset) {
		return on() ? 1.0f : offset;
	}

	/**
	 * getStringWidth, with OptiFine: the sum of the characters' widths as a whole number (OptiFine
	 * rounds it; cut, as PolyPatcher does, a text is never measured wider than it is drawn).
	 */
	public static int measured(float width) {
		return on() ? (int) width : Math.round(width);
	}

	/** renderChar, with OptiFine: the width a space or no-break space is drawn with (OptiFine: a no-break space, the 160th glyph's). */
	public static int spaceWidthIndex(int ch) {
		return ch == 160 && on() ? 32 : ch;
	}

	// ---- lookups ----------------------------------------------------------------------------------

	/** String.indexOf(char) in the font's methods: for the glyph page, a table instead of a search. */
	public static int indexOf(String s, int ch) {
		return s == PAGE && ch >= 0 && ch < 65536 && on() ? PLACE[ch] : s.indexOf(ch);
	}

	/** String.toLowerCase(Locale) in the font's methods: the copy made for the last string, made once. */
	public static String lower(String s, Locale locale) {
		if (!on() || !onClientThread()) {
			return s.toLowerCase(locale);
		}
		if (s != lowerOf || locale != lowerLocale) {
			lower = s.toLowerCase(locale);
			lowerOf = s;
			lowerLocale = locale;
		}
		return lower;
	}

	// ---- one draw per string ---------------------------------------------------------------------

	/** renderStringAtPos, first: the characters of this string go into one draw. */
	public static void start(FontRenderer font) {
		if (!onClientThread()) {
			return;
		}
		if (active) {
			finish();
		}
		active = on() && font != null && font.getClass() == FontRenderer.class && !OptiFineFields.shadersOn();
	}

	/** renderStringAtPos, last: the string's characters drawn. */
	public static void finish() {
		if (Thread.currentThread() == clientThread) {
			flush();
			active = false;
		}
	}

	/** setColor: the colour of the characters that follow. */
	public static void color(float rf, float gf, float bf, float af) {
		if (!onClientThread()) {
			return;
		}
		red = rf;
		green = gf;
		blue = bf;
		alpha = af;
		r = channel(rf);
		g = channel(gf);
		b = channel(bf);
		a = channel(af);
	}

	static int channel(float f) {
		int c = (int) (f * 255.0f + 0.5f);
		return c < 0 ? 0 : Math.min(c, 255);
	}

	/** bindTexture: the characters so far are drawn before the font's texture changes. */
	public static void texture(ResourceLocation location) {
		if (Thread.currentThread() != clientThread) {
			return;
		}
		if (open && location != texture && (location == null || !location.equals(texture))) {
			flush();
		}
		texture = location;
	}

	/** GL11.glBegin in renderDefaultChar and renderUnicodeChar. */
	public static void begin(int glMode) {
		if (!batching()) {
			GL11.glBegin(glMode);
			return;
		}
		mode = glMode;
		vertices = 0;
	}

	/** GL11.glTexCoord2f in renderDefaultChar and renderUnicodeChar. */
	public static void tex(float s, float t) {
		if (!batching()) {
			GL11.glTexCoord2f(s, t);
			return;
		}
		u = s;
		v = t;
	}

	/** GL11.glVertex3f in renderDefaultChar and renderUnicodeChar. */
	public static void vertex(float x, float y, float z) {
		if (!batching()) {
			GL11.glVertex3f(x, y, z);
			return;
		}
		if (vertices < 8) {
			int i = vertices * 5;
			STRIP[i] = x;
			STRIP[i + 1] = y;
			STRIP[i + 2] = z;
			STRIP[i + 3] = u;
			STRIP[i + 4] = v;
		}
		vertices++;
	}

	/** GL11.glEnd in renderDefaultChar and renderUnicodeChar: the glyph joins the batch. */
	public static void end() {
		if (!batching()) {
			GL11.glEnd();
			return;
		}
		int n = vertices;
		if (mode == GL11.GL_TRIANGLE_STRIP && n >= 3 && n <= 8) {
			if (!open) {
				BUFFER.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
				open = true;
			}
			// the strip's own triangles, turned as the strip turns them
			for (int i = 0; i + 2 < n; i++) {
				if ((i & 1) == 0) {
					put(i);
					put(i + 1);
				} else {
					put(i + 1);
					put(i);
				}
				put(i + 2);
			}
		} else if (n > 0) {
			// any other shape is drawn as it came, after the characters before it
			flush();
			GL11.glBegin(mode);
			for (int i = 0; i < Math.min(n, 8); i++) {
				GL11.glTexCoord2f(STRIP[i * 5 + 3], STRIP[i * 5 + 4]);
				GL11.glVertex3f(STRIP[i * 5], STRIP[i * 5 + 1], STRIP[i * 5 + 2]);
			}
			GL11.glEnd();
		}
		vertices = 0;
	}

	private static void put(int i) {
		int k = i * 5;
		BUFFER.pos(STRIP[k], STRIP[k + 1], STRIP[k + 2]).tex(STRIP[k + 3], STRIP[k + 4]).color(r, g, b, a).endVertex();
	}

	/** Draws the characters gathered so far; doDraw calls it before an underline or strikethrough. */
	public static void flush() {
		if (!open || Thread.currentThread() != clientThread) {
			return;
		}
		open = false;
		BUFFER.finishDrawing();
		UPLOADER.draw(BUFFER);
		GlStateManager.color(red, green, blue, alpha); // as the font left it, the array draw having reset it
	}

	// ---- Cache Font Data -------------------------------------------------------------------------

	/** FontRenderer.onResourceManagerReload, and a change to the glyph sizes: kept widths are dropped. */
	public static void fontChanged() {
		fontGeneration++;
	}

	/** getStringWidth, after the nick hider: the width kept for this text, or -1 to measure it. */
	public static int keptWidth(FontRenderer font, String text) {
		if (text == null || !cacheSetting.getAsBoolean() || !onClientThread()) {
			return -1;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || font != mc.fontRendererObj) {
			return -1;
		}
		boolean unicode = font.getUnicodeFlag();
		boolean cut = on();
		int limit = GlyphWidths.wideGlyphLimit();
		int generation = fontGeneration;
		if (font != widthsFont || unicode != widthsUnicode || cut != widthsCut || limit != widthsLimit || generation != widthsGeneration) {
			WIDTHS.clear();
			widthsFont = font;
			widthsUnicode = unicode;
			widthsCut = cut;
			widthsLimit = limit;
			widthsGeneration = generation;
			return -1;
		}
		Integer w = WIDTHS.get(text);
		return w != null ? w : -1;
	}

	/** getStringWidth's result, kept for the next time (for the font {@link #keptWidth} last checked). */
	public static int keepWidth(int width, FontRenderer font, String text) {
		if (text != null && font == widthsFont && width >= 0 && cacheSetting.getAsBoolean() && Thread.currentThread() == clientThread
			&& font.getUnicodeFlag() == widthsUnicode && fontGeneration == widthsGeneration) {
			if (WIDTHS.size() >= MAX_WIDTHS) {
				WIDTHS.clear();
			}
			WIDTHS.put(text, width);
		}
		return width;
	}

	/** For tests: how many widths are kept. */
	static int keptWidths() {
		return WIDTHS.size();
	}
}
