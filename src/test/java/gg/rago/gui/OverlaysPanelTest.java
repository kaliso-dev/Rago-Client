package gg.rago.gui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlaysPanelTest {
	@Test
	void roundedCorners() {
		int w = 40;
		int h = 20;
		int[] px = new int[w * h];
		Arrays.fill(px, 0xFF336699);
		OverlaysPanel.round(px, w, h, 8);
		assertEquals(0, px[0] >>> 24, "top left corner");
		assertEquals(0, px[w - 1] >>> 24, "top right corner");
		assertEquals(0, px[(h - 1) * w] >>> 24, "bottom left corner");
		assertEquals(0, px[h * w - 1] >>> 24, "bottom right corner");
		assertEquals(0xFF, px[10 * w + 20] >>> 24, "middle");
		assertEquals(0xFF, px[8 * w + 8] >>> 24, "inside the curve");
		assertEquals(0x336699, px[0] & 0xFFFFFF, "colour kept");
	}

	@Test
	void packNames() {
		assertEquals("Wool Overlay", OverlaysPanel.plain("§aWool §lOverlay.zip"));
		assertEquals("Sky", OverlaysPanel.plain("Sky"));
	}

	@Test
	void gameSkyIsBlueAboveAndPaleAtTheHorizon() {
		int[] sky = OverlaysPanel.gameSky(64, 36);
		int top = sky[0];
		int low = sky[35 * 64];
		assertTrue((top & 0xFF) > (top >> 16 & 0xFF), "blue above");
		assertTrue((low >> 16 & 0xFF) > (top >> 16 & 0xFF), "paler lower down");
	}
}
