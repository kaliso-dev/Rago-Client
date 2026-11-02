package gg.rago.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatcherFullbrightTest {
	/** Stands in for Patcher's settings class: the same public fields and methods Rago uses. */
	public static final class FakeConfig {
		public static FakeConfig INSTANCE;
		public static boolean fullbright;
		int dirty;
		int writes;

		public void markDirty() {
			dirty++;
		}

		public void writeData() {
			writes++;
		}
	}

	private static final String FAKE = FakeConfig.class.getName();

	@TempDir
	Path dir;

	@BeforeEach
	void reset() {
		FakeConfig.INSTANCE = new FakeConfig();
		FakeConfig.fullbright = true;
	}

	@Test
	void turnsItOffAndSavesOnce() {
		assertTrue(PatcherFullbright.offOnce(dir, FAKE));
		assertFalse(FakeConfig.fullbright);
		assertEquals(1, FakeConfig.INSTANCE.dirty);
		assertEquals(1, FakeConfig.INSTANCE.writes);
		assertTrue(Files.exists(dir.resolve(PatcherFullbright.MARKER)));

		// the player turns it back on: Rago leaves it on from then on
		FakeConfig.fullbright = true;
		assertFalse(PatcherFullbright.offOnce(dir, FAKE));
		assertTrue(FakeConfig.fullbright);
		assertEquals(1, FakeConfig.INSTANCE.writes);
	}

	@Test
	void alreadyOffIsLeftAlone() {
		FakeConfig.fullbright = false;
		assertFalse(PatcherFullbright.offOnce(dir, FAKE));
		assertEquals(0, FakeConfig.INSTANCE.writes);
		assertTrue(Files.exists(dir.resolve(PatcherFullbright.MARKER)));
	}

	@Test
	void withoutPatcherNothingIsMarked() {
		assertFalse(PatcherFullbright.offOnce(dir, "club.sk1er.patcher.config.NoSuchConfig"));
		assertFalse(Files.exists(dir.resolve(PatcherFullbright.MARKER)));
	}

	@Test
	void settingsNotReadYetAreTriedAgainLater() {
		FakeConfig.INSTANCE = null;
		assertFalse(PatcherFullbright.offOnce(dir, FAKE));
		assertTrue(FakeConfig.fullbright);
		assertFalse(Files.exists(dir.resolve(PatcherFullbright.MARKER)));
	}
}
