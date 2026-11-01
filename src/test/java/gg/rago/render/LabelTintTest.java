package gg.rago.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Colours of floating texts shaded by the light map, as the game's texture unit multiplies them. */
class LabelTintTest {
	@Test
	void whiteTextTakesTheLightMapColour() {
		assertEquals(0xFFFCFCFC, LabelBatch.tinted(0xFFFFFFFF, 0xFFFCFCFC));
		assertEquals(0x20808080, LabelBatch.tinted(0x20FFFFFF, 0xFF808080));
	}

	@Test
	void fullLightLeavesColoursAndAlphaAlone() {
		assertEquals(0xFF55FFFF, LabelBatch.tinted(0xFF55FFFF, 0xFFFFFFFF));
		assertEquals(0x3F000000, LabelBatch.tinted(0x3F000000, 0xFF102030));
	}

	@Test
	void eachChannelIsMultipliedOnItsOwn() {
		// yellow text (FFFF55) under a blue-ish light map colour (4060FF)
		assertEquals(0xFF406055, LabelBatch.tinted(0xFFFFFF55, 0xFF4060FF));
		assertEquals(0xFF203055, LabelBatch.tinted(0xFF808055, 0xFF4060FF));
	}
}
