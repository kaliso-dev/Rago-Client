package gg.rago.bedwars;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeightLimitsTest {
	@TempDir
	Path dir;

	@Test
	void unknownUntilTheServerRefusesTwice() {
		HeightLimits h = new HeightLimits(dir.resolve("limits.json"));
		h.setMap("hypixel.net/Lighthouse");
		h.kept(90);
		assertEquals(-1, h.limit());
		h.rejected(96);
		assertEquals(-1, h.limit());
		h.rejected(96);
		assertEquals(95, h.limit());
	}

	@Test
	void refusalsBelowAKeptBlockAreNotTheLimit() {
		HeightLimits h = new HeightLimits(dir.resolve("limits.json"));
		h.setMap("m");
		h.kept(100);
		// a protected area near a generator, not the height
		h.rejected(70);
		h.rejected(70);
		assertEquals(-1, h.limit());
	}

	@Test
	void aKeptBlockAboveTheLimitForgetsIt() {
		HeightLimits h = new HeightLimits(dir.resolve("limits.json"));
		h.setMap("m");
		h.rejected(80);
		h.rejected(80);
		assertEquals(79, h.limit());
		h.kept(85);
		assertEquals(-1, h.limit());
	}

	@Test
	void limitsAreKeptPerMapAcrossGames() {
		Path file = dir.resolve("limits.json");
		HeightLimits h = new HeightLimits(file);
		h.setMap("a");
		h.rejected(91);
		h.rejected(91);
		h.setMap("b");
		assertEquals(-1, h.limit());
		HeightLimits later = new HeightLimits(file);
		later.setMap("a");
		assertEquals(90, later.limit());
	}
}
