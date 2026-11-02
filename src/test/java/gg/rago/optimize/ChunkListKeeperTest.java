package gg.rago.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkListKeeperTest {
	private static final int FULL_12 = 25 * 25;

	@Test
	void theListIsKeptWhileNothingChanges() {
		ChunkListKeeper k = new ChunkListKeeper();
		Object world = new Object();
		assertFalse(k.same(world, 3, -4, 12, 0), "the first list is built");
		assertTrue(k.same(world, 3, -4, 12, FULL_12));
		assertTrue(k.same(world, 3, -4, 12, FULL_12));
	}

	@Test
	void anotherChunkDistanceOrWorldBuildsItAgain() {
		ChunkListKeeper k = new ChunkListKeeper();
		Object world = new Object();
		k.same(world, 0, 0, 12, 0);
		assertFalse(k.same(world, 1, 0, 12, FULL_12), "moved east");
		assertTrue(k.same(world, 1, 0, 12, FULL_12));
		assertFalse(k.same(world, 1, 1, 12, FULL_12), "moved south");
		assertFalse(k.same(world, 1, 1, 8, FULL_12), "render distance lowered");
		assertTrue(k.same(world, 1, 1, 8, 17 * 17));
		assertFalse(k.same(new Object(), 1, 1, 8, 17 * 17), "another world");
	}

	@Test
	void aListChangedByAnythingElseIsBuiltAgain() {
		ChunkListKeeper k = new ChunkListKeeper();
		Object world = new Object();
		k.same(world, 0, 0, 12, 0);
		assertFalse(k.same(world, 0, 0, 12, FULL_12 - 1));
		assertTrue(k.same(world, 0, 0, 12, FULL_12));
	}

	@Test
	void afterForgettingTheListIsBuiltAgain() {
		ChunkListKeeper k = new ChunkListKeeper();
		Object world = new Object();
		k.same(world, 0, 0, 12, 0);
		k.forget();
		assertFalse(k.same(world, 0, 0, 12, FULL_12));
		assertTrue(k.same(world, 0, 0, 12, FULL_12));
	}
}
