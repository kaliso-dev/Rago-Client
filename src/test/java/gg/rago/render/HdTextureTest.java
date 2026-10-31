package gg.rago.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The mipmaps of the HD capes. */
class HdTextureTest {
	@Test
	void halvesDownToMinecraftsCapeSize() {
		assertEquals(5, HdTexture.levels(2048, 1024));
		assertEquals(4, HdTexture.levels(1024, 512));
		assertEquals(0, HdTexture.levels(64, 32));
		assertEquals(0, HdTexture.levels(100, 50));
		assertEquals(1, HdTexture.levels(130, 66));
	}

	@Test
	void eachLevelAveragesFourPixels() {
		int[] px = {0xFF000000, 0xFF0000FF, 0xFFFF0000, 0xFF00FF00, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
		int[][] mips = HdTexture.mipmaps(px, 4, 2, 1);
		assertEquals(2, mips[1].length);
		// black, blue and two whites; red, green and two whites
		assertEquals(0xFF8080BF, mips[1][0]);
		assertEquals(0xFFBFBF80, mips[1][1]);
	}

	/** See-through pixels count for the alpha but not the colour: no dark fringe around a cut-out. */
	@Test
	void clearPixelsDoNotDarkenTheColour() {
		assertEquals(0x80FF0000, HdTexture.average(0xFFFF0000, 0xFFFF0000, 0x00000000, 0x00000000));
		assertEquals(0, HdTexture.average(0, 0, 0, 0));
	}
}
