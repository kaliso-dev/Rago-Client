package gg.rago.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.IntUnaryOperator;

/**
 * Whether the Minecraft font's characters 32 to 126 are all a whole number of units wide, as
 * vanilla's always are. OptiFine keeps a second, fractional width for each character (HD fonts,
 * and the custom widths a resource pack can set) and draws with it; Rago's own copies of the
 * game's text drawing ({@link VanillaText}, {@link LabelBatch}) use whole widths, so with such a
 * font they leave the text to the game. Checked again every second, as a resource pack can change
 * the font at any time.
 */
public final class FontWidths {
	private static final long RECHECK_NS = 1_000_000_000L;

	private static Field fractional;
	private static boolean lookedUp;
	private static boolean checked;
	private static long checkedAt;
	private static boolean whole = true;

	private FontWidths() {
	}

	/** True when the game's font draws characters 32 to 126 at whole widths (always without OptiFine). */
	public static boolean whole() {
		long now = System.nanoTime();
		if (!checked || now - checkedAt >= RECHECK_NS) {
			checked = true;
			checkedAt = now;
			FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
			whole = fr == null || whole(fractional(fr), c -> fr.getCharWidth((char) c));
		}
		return whole;
	}

	/** True when every width of characters 32 to 126 in {@code fractional} (null: none kept) is the whole width the font reports. */
	static boolean whole(float[] fractional, IntUnaryOperator width) {
		if (fractional == null) {
			return true;
		}
		for (int c = 32; c <= 126; c++) {
			if (fractional[c] != (float) width.applyAsInt(c)) {
				return false;
			}
		}
		return true;
	}

	/** OptiFine's fractional widths: the font's only float[] of 256 entries (vanilla's font has none). */
	private static float[] fractional(FontRenderer fr) {
		try {
			if (!lookedUp) {
				lookedUp = true;
				for (Field f : FontRenderer.class.getDeclaredFields()) {
					if (!Modifier.isStatic(f.getModifiers()) && f.getType() == float[].class) {
						f.setAccessible(true);
						float[] a = (float[]) f.get(fr);
						if (a != null && a.length == 256) {
							fractional = f;
							break;
						}
					}
				}
			}
			return fractional == null ? null : (float[]) fractional.get(fr);
		} catch (IllegalAccessException | RuntimeException e) {
			fractional = null;
			return null;
		}
	}
}
