package gg.rago.fixes;

import gg.rago.optimize.FontBatch;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/**
 * What a wide unicode character measures with OptiFine. Characters outside the font's ASCII page
 * are drawn from the unicode glyph pages, where each glyph has its first and last column (0 to 15)
 * and is drawn (last + 1 - first) / 2 + 1 units wide, which is also what the game measures.
 * OptiFine (its getCharWidthFloat, behind getCharWidth, getStringWidth and trimStringToWidth)
 * measures any glyph that goes past column 7 as if it filled all 16 columns: 9 units, so Chinese,
 * Japanese, Korean and other wide characters measured wider than they are drawn. PolyPatcher
 * measures them as the game does, and so do the internal fixes. Only measuring changes (centring,
 * wrapping, widths of boxes), not the text drawn. Called as the game measures text, possibly before
 * Rago has started (the loading screen), so it needs nothing but its switch.
 * <p>
 * PolyPatcher also changes one glyph size: U+0D9E (Sinhala kantaja naasikyaya), columns 1 to 11
 * in the game's table, gets columns 0 to 14, so it is drawn from a wider part of its glyph page and
 * measures 8.
 */
public final class GlyphWidths {
	/** U+0D9E's entry in the glyph sizes: first column in the high 4 bits, last column in the low 4. */
	static final int GLYPH = 0x0d9e;
	static final byte GAME_SIZE = 0x1b;
	static final byte FIXED_SIZE = 0x0e;

	private static volatile BooleanSupplier on;
	private static Field glyphWidth;
	private static boolean glyphWidthMissing;

	private GlyphWidths() {
	}

	public static void setSwitch(BooleanSupplier setting) {
		on = setting;
	}

	/** The last column up to which OptiFine measures a glyph as drawn: 7, or 15 (every glyph) with the fix. */
	public static int wideGlyphLimit() {
		BooleanSupplier s = on;
		return s != null && s.getAsBoolean() ? 15 : 7;
	}

	/**
	 * Puts U+0D9E's glyph size as the fix has it in the font's table, or back as the game read it;
	 * a size a resource pack gave is left alone. Checked every tick, as the font is made before Rago
	 * starts.
	 */
	public static void syncGlyph(FontRenderer fr, boolean fixed) {
		byte[] sizes = sizes(fr);
		if (sizes != null) {
			byte size = glyphSize(sizes[GLYPH], fixed);
			if (size != sizes[GLYPH]) {
				sizes[GLYPH] = size;
				FontBatch.fontChanged(); // widths kept with the other size are dropped
			}
		}
	}

	/** The size U+0D9E should have now, from the one the table holds. */
	static byte glyphSize(byte current, boolean fixed) {
		if (fixed && current == GAME_SIZE) {
			return FIXED_SIZE;
		}
		if (!fixed && current == FIXED_SIZE) {
			return GAME_SIZE;
		}
		return current;
	}

	private static byte[] sizes(FontRenderer fr) {
		if (fr == null || glyphWidthMissing) {
			return null;
		}
		try {
			if (glyphWidth == null) {
				glyphWidth = ReflectionHelper.findField(FontRenderer.class, "field_78287_e", "glyphWidth");
			}
			byte[] sizes = (byte[]) glyphWidth.get(fr);
			return sizes != null && sizes.length > GLYPH ? sizes : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			glyphWidthMissing = true;
			return null;
		}
	}
}
