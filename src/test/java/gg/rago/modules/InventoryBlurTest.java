package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBlurTest {
	@Test
	void theBlurFadesInOverTheChosenTime() {
		assertEquals(0f, InventoryBlur.fadeIn(0, 150));
		assertEquals(1f, InventoryBlur.fadeIn(150_000_000L, 150));
		assertEquals(1f, InventoryBlur.fadeIn(2_000_000_000L, 150));
		float half = InventoryBlur.fadeIn(75_000_000L, 150);
		assertTrue(half > 0.5f && half < 1f, "eased: past half way at half the time, " + half);
		// no fade: at once
		assertEquals(1f, InventoryBlur.fadeIn(0, 0));
	}

	@Test
	void theGaussianKernelAddsUpToOneAndFitsTheShader() {
		for (float sigma : new float[] {0.1f, 0.5f, 1f, 2.5f, 4f, 8.3f, 10f, 40f}) {
			float[][] k = InventoryBlur.kernel(sigma);
			float[] offsets = k[0];
			float[] weights = k[1];
			assertTrue(offsets.length <= InventoryBlur.MAX_TAPS, "taps for " + sigma);
			assertEquals(offsets.length, weights.length);
			assertEquals(0f, offsets[0]);
			float sum = weights[0];
			for (int i = 1; i < weights.length; i++) {
				sum += 2 * weights[i];
				assertTrue(offsets[i] > offsets[i - 1], "offsets grow for " + sigma);
				assertTrue(weights[i] > 0, "weight " + i + " for " + sigma);
			}
			assertEquals(1f, sum, 1e-4f, "sum for " + sigma);
		}
		// a pair of neighbours read as one tap sits between them, nearer the heavier one
		float[][] k = InventoryBlur.kernel(3f);
		assertTrue(k[0][1] > 1f && k[0][1] < 1.5f, "first pair at " + k[0][1]);
	}

	@Test
	void theBlurIsRemadeAtTheChosenRate() {
		assertFalse(InventoryBlur.remakeDue(10_000_000_000L, "frozen"));
		assertFalse(InventoryBlur.remakeDue(20_000_000L, "30"));
		assertTrue(InventoryBlur.remakeDue(34_000_000L, "30"));
		assertFalse(InventoryBlur.remakeDue(10_000_000L, "60"));
		assertTrue(InventoryBlur.remakeDue(17_000_000L, "60"));
	}

	@Test
	void theRadiusIsInPixelsOfA1080pScreen() {
		assertEquals(6f, InventoryBlur.sigmaPixels(18, 1080), 1e-4f);
		assertEquals(12f, InventoryBlur.sigmaPixels(18, 2160), 1e-4f);
		assertEquals(4f, InventoryBlur.sigmaPixels(18, 720), 1e-4f);
	}

	@Test
	void theShadeKeepsItsColourAndScalesItsOpacity() {
		// vanilla's two gradient colours
		assertEquals(0xC0101010, InventoryBlur.shaded(0xC0101010, 100));
		assertEquals(0x00101010, InventoryBlur.shaded(0xD0101010, 0));
		assertEquals(0x60101010, InventoryBlur.shaded(0xC0101010, 50));
		assertEquals(0x68101010, InventoryBlur.shaded(0xD0101010, 50));
	}
}
