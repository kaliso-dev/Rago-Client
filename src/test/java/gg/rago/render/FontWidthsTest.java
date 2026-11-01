package gg.rago.render;

import org.junit.jupiter.api.Test;

import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontWidthsTest {
	/** Widths shaped like the default font's: 4 for a space, 6 for most letters, 2 for 'i'. */
	private static final IntUnaryOperator VANILLA = c -> c == ' ' ? 4 : c == 'i' ? 2 : 6;

	private static float[] same() {
		float[] w = new float[256];
		for (int c = 0; c < 256; c++) {
			w[c] = VANILLA.applyAsInt(c);
		}
		return w;
	}

	@Test
	void withoutOptiFineTheWidthsAreWhole() {
		assertTrue(FontWidths.whole(null, VANILLA));
	}

	@Test
	void optiFineWidthsThatMatchTheWholeOnesAreWhole() {
		assertTrue(FontWidths.whole(same(), VANILLA));
	}

	@Test
	void anHdFontWithAFractionalWidthIsNot() {
		float[] w = same();
		w['a'] = 5.5f;
		assertFalse(FontWidths.whole(w, VANILLA));
	}

	@Test
	void onlyTheAsciiPageCounts() {
		float[] w = same();
		w[200] = 3.25f; // outside 32 to 126: never drawn by Rago's copies
		w[10] = 0.5f;
		assertTrue(FontWidths.whole(w, VANILLA));
	}
}
