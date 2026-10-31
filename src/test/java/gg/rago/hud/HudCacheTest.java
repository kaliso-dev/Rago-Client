package gg.rago.hud;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudCacheTest {
	private static boolean overlap(float[] r, int i, int j) {
		return r[i * 4] < r[j * 4 + 2] && r[j * 4] < r[i * 4 + 2] && r[i * 4 + 1] < r[j * 4 + 3] && r[j * 4 + 1] < r[i * 4 + 3];
	}

	private static boolean covers(float[] r, int count, float x, float y) {
		for (int i = 0; i < count; i++) {
			if (x >= r[i * 4] && x < r[i * 4 + 2] && y >= r[i * 4 + 1] && y < r[i * 4 + 3]) {
				return true;
			}
		}
		return false;
	}

	@Test
	void separateAreasStayAsTheyAre() {
		float[] r = {0, 0, 10, 10, 20, 0, 30, 10, 0, 20, 10, 30};
		assertEquals(3, HudCache.merge(r, 3));
		assertEquals(20, r[4], 0);
	}

	@Test
	void areasSharingOnlyAnEdgeAreNotMerged() {
		// drawn side by side, each pixel is still laid over once
		float[] r = {0, 0, 10, 10, 10, 0, 20, 10};
		assertEquals(2, HudCache.merge(r, 2));
	}

	@Test
	void overlappingAreasBecomeOneThatCoversBoth() {
		float[] r = {0, 0, 10, 10, 5, 5, 15, 15};
		assertEquals(1, HudCache.merge(r, 2));
		assertEquals(0, r[0], 0);
		assertEquals(0, r[1], 0);
		assertEquals(15, r[2], 0);
		assertEquals(15, r[3], 0);
	}

	@Test
	void aMergedAreaThatNowOverlapsAnotherIsMergedAgain() {
		// a and b do not touch; c joins them, and the joined area reaches d
		float[] r = {0, 0, 10, 10, 20, 0, 30, 10, 8, 2, 22, 4, 25, 9, 40, 40};
		assertEquals(1, HudCache.merge(r, 4));
		assertEquals(40, r[2], 0);
		assertEquals(40, r[3], 0);
	}

	@Test
	void randomAreasEndUpApartAndStillCoverEveryPixel() {
		Random random = new Random(7);
		for (int run = 0; run < 200; run++) {
			int n = 1 + random.nextInt(32);
			float[] before = new float[n * 4];
			for (int i = 0; i < n; i++) {
				float x = random.nextInt(100);
				float y = random.nextInt(100);
				before[i * 4] = x;
				before[i * 4 + 1] = y;
				before[i * 4 + 2] = x + 1 + random.nextInt(20);
				before[i * 4 + 3] = y + 1 + random.nextInt(20);
			}
			float[] r = before.clone();
			int count = HudCache.merge(r, n);
			assertTrue(count >= 1 && count <= n);
			for (int i = 0; i < count; i++) {
				for (int j = i + 1; j < count; j++) {
					assertFalse(overlap(r, i, j), "run " + run + ": areas " + i + " and " + j + " overlap");
				}
			}
			for (int i = 0; i < n; i++) {
				for (float x = before[i * 4]; x < before[i * 4 + 2]; x++) {
					for (float y = before[i * 4 + 1]; y < before[i * 4 + 3]; y++) {
						assertTrue(covers(r, count, x + 0.5f, y + 0.5f), "run " + run + ": pixel " + x + "," + y + " lost");
					}
				}
			}
		}
	}
}
