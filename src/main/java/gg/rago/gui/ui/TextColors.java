package gg.rago.gui.ui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * The sixteen colours of Minecraft's § codes, with two palettes for colour blindness. Only five
 * bright team colours change (§c red, §a green, §e yellow, §b aqua, §d pink); blue, white, gold and
 * the greys stay. Each palette keeps every colour close to its name and was chosen so that the eight Bed
 * Wars teams stay apart, as seen through the Machado 2009 simulation of that colour blindness and
 * with normal sight (see TextColorsTest). The game's font takes the palette too, so names in chat, the
 * tab list, the sidebar and above heads change: only what you see, like OptiFine's custom colours.
 */
public final class TextColors {
	public static final int NORMAL = 0;
	/** Red-green colour blindness (deuteranopia and protanopia). */
	public static final int RED_GREEN = 1;
	/** Blue-yellow colour blindness (tritanopia). */
	public static final int BLUE_YELLOW = 2;

	private static final Logger LOG = LogManager.getLogger("Rago");
	/** The codes a palette changes, in the order of the arrays below. */
	static final String CHANGED = "caebd";
	static final int[] RED_GREEN_RGB = {0xE46A33, 0x33F4A6, 0xF5FE3D, 0x6DDFFA, 0xD67FFB};
	static final int[] BLUE_YELLOW_RGB = {0xFD4252, 0x43E53D, 0xFEE124, 0x9DFEE4, 0xE245FB};
	static final int[] VANILLA = new int[16];

	static {
		for (int i = 0; i < 16; i++) {
			int j = (i >> 3 & 1) * 85;
			int r = (i >> 2 & 1) * 170 + j;
			int g = (i >> 1 & 1) * 170 + j;
			int b = (i & 1) * 170 + j;
			if (i == 6) {
				r += 85;
			}
			VANILLA[i] = r << 16 | g << 8 | b;
		}
	}

	private static int palette = NORMAL;
	private static int[] current = VANILLA.clone();
	private static Field colorCode;
	private static FontRenderer appliedTo;
	private static int appliedPalette = -1;

	private TextColors() {
	}

	public static void set(int p) {
		palette = p == RED_GREEN || p == BLUE_YELLOW ? p : NORMAL;
		current = colors(palette);
	}

	public static int palette() {
		return palette;
	}

	/** The RGB of § code {@code index} (0 to 15) in the current palette. */
	public static int rgb(int index) {
		return current[index];
	}

	/** The RGB of § code {@code index} (0 to 15) in {@code palette}, for previews. */
	public static int rgb(int palette, int index) {
		return colors(palette)[index];
	}

	/** The opaque colour of § code {@code code} ('a' for green) in the current palette. */
	public static int argb(char code) {
		return 0xFF000000 | current[Character.digit(code, 16)];
	}

	static int[] colors(int palette) {
		int[] c = VANILLA.clone();
		int[] changed = palette == RED_GREEN ? RED_GREEN_RGB : palette == BLUE_YELLOW ? BLUE_YELLOW_RGB : null;
		if (changed != null) {
			for (int i = 0; i < CHANGED.length(); i++) {
				c[Character.digit(CHANGED.charAt(i), 16)] = changed[i];
			}
		}
		return c;
	}

	/**
	 * Puts the palette into the game's font when it or the palette changed (cheap: called every
	 * tick). The font keeps the sixteen colours, then their shadows at a quarter of each channel.
	 */
	public static void applyTo(FontRenderer font) {
		if (font == null || font == appliedTo && palette == appliedPalette) {
			return;
		}
		appliedTo = font;
		appliedPalette = palette;
		try {
			if (colorCode == null) {
				colorCode = ReflectionHelper.findField(FontRenderer.class, "field_78285_g", "colorCode");
			}
			int[] codes = (int[]) colorCode.get(font);
			for (int i = 0; i < 16 && i + 16 < codes.length; i++) {
				int c = current[i];
				codes[i] = c;
				codes[i + 16] = shadow(c);
			}
			clearTextCaches();
			LOG.info("Text colours: palette {}", palette);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not set the text colours", e);
		}
	}

	/**
	 * Patcher keeps drawn strings with their colours baked in; without this, lines already on screen
	 * (the chat, the tab list) would keep the old palette until they change.
	 */
	private static void clearTextCaches() {
		try {
			Class<?> c = Class.forName("club.sk1er.patcher.util.enhancement.text.EnhancedFontRenderer", false, TextColors.class.getClassLoader());
			Method invalidate = c.getMethod("invalidateAll");
			for (Object renderer : (List<?>) c.getMethod("getInstances").invoke(null)) {
				invalidate.invoke(renderer);
			}
		} catch (ClassNotFoundException e) {
			// no Patcher: nothing is kept
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not clear Patcher's text cache", e);
		}
	}

	/** The game's text shadow: each channel divided by four. */
	static int shadow(int rgb) {
		return (rgb >> 16 & 0xFF) / 4 << 16 | (rgb >> 8 & 0xFF) / 4 << 8 | (rgb & 0xFF) / 4;
	}
}
