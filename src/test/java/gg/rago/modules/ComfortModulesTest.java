package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComfortModulesTest {
	@Test
	void crossArmsAreSymmetricAroundCentrePixel() {
		int[][] r = Crosshair.rects(Crosshair.STYLE_CROSS, 4, 2, 1, 100, 50);
		assertEquals(4, r.length);
		assertArrayEquals(new int[]{100, 44, 101, 48}, r[0]); // up: rows 44..47, gap rows 48..49
		assertArrayEquals(new int[]{100, 53, 101, 57}, r[1]); // down: gap rows 51..52
		assertArrayEquals(new int[]{94, 50, 98, 51}, r[2]);   // left
		assertArrayEquals(new int[]{103, 50, 107, 51}, r[3]); // right
	}

	@Test
	void evenThicknessStaysOnTheCentre() {
		int[] dot = Crosshair.rects(Crosshair.STYLE_DOT, 4, 0, 2, 10, 10)[0];
		assertArrayEquals(new int[]{10, 10, 12, 12}, dot);
		int[] dot3 = Crosshair.rects(Crosshair.STYLE_DOT, 4, 0, 3, 10, 10)[0];
		assertArrayEquals(new int[]{9, 9, 12, 12}, dot3);
	}

	@Test
	void tStyleHasNoTopArm() {
		for (int[] rect : Crosshair.rects(Crosshair.STYLE_T, 4, 2, 1, 100, 50)) {
			assertTrue(rect[1] >= 50, "no rectangle above the centre");
		}
	}

	@Test
	void zoomApproachesTargetAndSnaps() {
		float z = 1f;
		for (int i = 0; i < 100; i++) {
			z = Zoom.approach(z, 4f, 1f / 60);
		}
		assertEquals(4f, z);
		assertEquals(1f, Zoom.approach(1f, 1f, 0.016f));
		assertTrue(Zoom.approach(1f, 4f, 0.016f) > 1f);
	}

	@Test
	void zoomClamp() {
		assertEquals(1.5f, Zoom.clampZoom(1.1f));
		assertEquals(40f, Zoom.clampZoom(100f));
		assertEquals(5f, Zoom.clampZoom(5f));
	}
}
