package gg.rago.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeonColorsTest {
	private static float hue(int argb) {
		return Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null)[0] * 360;
	}

	@Test
	void ragoRedTakesTheChosenHueAndKeepsItsShade() {
		int blue = NeonColors.recolor(0xFFF2392C, NeonColors.hue("blue"));
		assertEquals(222, hue(blue), 3);
		assertEquals(0xF2, Math.max(blue >> 16 & 0xFF, Math.max(blue >> 8 & 0xFF, blue & 0xFF)), 1);
		int green = NeonColors.recolor(0xFFF2392C, NeonColors.hue("green"));
		assertEquals(129, hue(green), 3);
	}

	@Test
	void theDarkRedOfTheWingsFollowsAndBlackStays() {
		int dark = NeonColors.recolor(0xFF150C0D, NeonColors.hue("cyan"));
		assertEquals(180, hue(dark), 10);
		assertEquals(0xFF0F0F12, NeonColors.recolor(0xFF0F0F12, NeonColors.hue("cyan")));
		assertEquals(0x00000000, NeonColors.recolor(0x00000000, NeonColors.hue("cyan")));
	}

	@Test
	void whiteIsAlmostGreyAndRedIsUntouched() {
		int white = NeonColors.recolor(0xFFF2392C, NeonColors.hue("white"));
		int r = white >> 16 & 0xFF;
		int b = white & 0xFF;
		assertTrue(r > 0xF0 && b > 0xE0, Integer.toHexString(white));
		assertEquals(0xFFF2392C, NeonColors.recolor(0xFFF2392C, NeonColors.hue("red")));
		assertEquals(0xFFF2392C, NeonColors.swatch("red"));
	}

	@Test
	void theGlowKeepsTheBrightNeonAndAFaintHaloAroundIt() {
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(7, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, 0xFFF2392C);
		for (int x = 1; x < 6; x++) {
			img.setRGB(x, 0, 0xFF0F0F12);
		}
		NeonColors.glow(img, NeonColors.hue("blue"));
		assertEquals(0x80, img.getRGB(0, 0) >>> 24);
		assertEquals(222, hue(img.getRGB(0, 0)), 3);
		assertEquals(0x48, img.getRGB(1, 0) >>> 24);
		assertEquals(0x22, img.getRGB(2, 0) >>> 24);
		assertEquals(0, img.getRGB(3, 0) >>> 24);
		assertEquals(0, img.getRGB(6, 0) >>> 24);
	}
}
