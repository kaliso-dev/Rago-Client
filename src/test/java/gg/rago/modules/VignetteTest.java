package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VignetteTest {
	@Test
	void aBiggerVignetteStartsCloserToTheCentre() {
		float previous = 2;
		for (int size = 0; size <= 100; size += 10) {
			float[] f = Vignette.fade(size, 80);
			assertTrue(f[0] < previous, "size " + size);
			assertTrue(f[1] > f[0]);
			previous = f[0];
		}
	}

	@Test
	void aSofterVignetteFadesOverMore() {
		float[] hard = Vignette.fade(50, 0);
		float[] soft = Vignette.fade(50, 100);
		assertEquals(hard[0], soft[0], 1e-6);
		assertTrue(soft[1] - soft[0] > 3 * (hard[1] - hard[0]));
	}

	@Test
	void theDefaultIsDiscreet() {
		// size 50, softness 80, intensity 45 %: the middle of each edge a few percent dark, the corners under half
		float[] f = Vignette.fade(50, 80);
		float edge = Vignette.curve((float) ((Math.sqrt(0.5) - f[0]) / (f[1] - f[0]))) * 0.45f;
		float corner = Vignette.curve((1 - f[0]) / (f[1] - f[0])) * 0.45f;
		assertTrue(edge > 0.05f && edge < 0.2f, "edge " + edge);
		assertTrue(corner > 0.4f && corner <= 0.45f, "corner " + corner);
		assertEquals(0, Vignette.curve(-1), 0);
		assertEquals(1, Vignette.curve(2), 0);
	}

	@Test
	void theRingsCoverTheScreenFromWhereTheFadeStarts() {
		float[] r = Vignette.rings(0.55f, 0.8f);
		assertEquals(0.55f, r[0], 1e-6);
		assertEquals(1, r[r.length - 1], 1e-6, "out to the corners");
		for (int i = 1; i < r.length; i++) {
			assertTrue(r[i] > r[i - 1]);
		}
		// a fade ending past the corners stops at them
		float[] wide = Vignette.rings(0.3f, 1.05f);
		assertEquals(1, wide[wide.length - 1], 1e-6);
	}
}
