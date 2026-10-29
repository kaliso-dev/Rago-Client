package gg.rago.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionCullerTest {
	/** A wall filling x = 5 from y 0 to 10, every z. */
	private static final OcclusionCuller.Blocks WALL = (x, y, z) -> x == 5 && y >= 0 && y < 10;

	@Test
	void wallBlocksTheRay() {
		assertFalse(OcclusionCuller.rayClear(0.5, 1.6, 0.5, 9.5, 1.5, 3.5, WALL));
	}

	@Test
	void rayOverTheWallIsClear() {
		assertTrue(OcclusionCuller.rayClear(0.5, 1.6, 0.5, 9.5, 30, 0.5, (x, y, z) -> x == 5 && y >= 0 && y < 5));
	}

	@Test
	void sameSideIsClear() {
		assertTrue(OcclusionCuller.rayClear(0.5, 1.6, 0.5, 4.5, 1.5, -6.2, WALL));
	}

	@Test
	void startAndEndBlocksAreNotChecked() {
		OcclusionCuller.Blocks all = (x, y, z) -> true;
		assertTrue(OcclusionCuller.rayClear(0.5, 0.5, 0.5, 0.9, 0.2, 0.7, all));
		assertTrue(OcclusionCuller.rayClear(0.5, 0.5, 0.5, 1.5, 0.5, 0.5, all));
		assertFalse(OcclusionCuller.rayClear(0.5, 0.5, 0.5, 2.5, 0.5, 0.5, all));
	}

	@Test
	void negativeDirectionsWork() {
		assertFalse(OcclusionCuller.rayClear(9.5, 1.5, 9.5, -3.5, 1.5, -2.5, WALL));
		assertTrue(OcclusionCuller.rayClear(9.5, 1.5, 9.5, 6.5, 2.5, -20.5, WALL));
	}

	@Test
	void diagonalPastACornerIsClear() {
		// a single block at (1, 0, 1): the ray from (0.5, 0.5, 0.5) to (2.5, 0.5, 0.2) passes beside it
		OcclusionCuller.Blocks one = (x, y, z) -> x == 1 && y == 0 && z == 1;
		assertTrue(OcclusionCuller.rayClear(0.5, 0.5, 0.5, 2.5, 0.5, 0.2, one));
		assertFalse(OcclusionCuller.rayClear(0.5, 0.5, 1.5, 2.5, 0.5, 1.5, one));
	}
}
