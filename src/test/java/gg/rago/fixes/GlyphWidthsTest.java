package gg.rago.fixes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlyphWidthsTest {
	@AfterEach
	void noSwitch() {
		GlyphWidths.setSwitch(null);
	}

	@Test
	void wideGlyphsAreMeasuredAsDrawnWithTheFix() {
		assertEquals(7, GlyphWidths.wideGlyphLimit(), "OptiFine's measure before Rago starts");
		GlyphWidths.setSwitch(() -> true);
		assertEquals(15, GlyphWidths.wideGlyphLimit());
		GlyphWidths.setSwitch(() -> false);
		assertEquals(7, GlyphWidths.wideGlyphLimit());
	}

	@Test
	void theSinhalaGlyphGetsItsWiderColumnsAndBack() {
		assertEquals(0x0e, GlyphWidths.glyphSize((byte) 0x1b, true));
		assertEquals(0x0e, GlyphWidths.glyphSize((byte) 0x0e, true));
		assertEquals(0x1b, GlyphWidths.glyphSize((byte) 0x0e, false));
		assertEquals(0x1b, GlyphWidths.glyphSize((byte) 0x1b, false));
		assertEquals(0x2c, GlyphWidths.glyphSize((byte) 0x2c, true), "a size from a resource pack stays");
		assertEquals(0x2c, GlyphWidths.glyphSize((byte) 0x2c, false));
	}
}
