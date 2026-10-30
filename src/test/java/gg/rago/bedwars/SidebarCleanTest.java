package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarCleanTest {
	@Test
	void coloursAndHypixelsHiddenCharactersAreRemoved() {
		// an emoji (two chars) or a symbol between the parts of a line, wherever it sits
		assertEquals("R Red: ✔ YOU", BedwarsGame.clean("§cR §fRed: §a✔🎂§7 YOU"));
		assertEquals("Y Yellow: ✔", BedwarsGame.clean("§eY §fYellow: ⚽§a✔"));
		assertEquals("B Blue: ✘", BedwarsGame.clean("👾§9B §fBlue: §c✘"));
		assertEquals("Diamond II in 5:37", BedwarsGame.clean("§fDiamond II in 🎉§a5:37"));
		assertEquals("BED WARS", BedwarsGame.clean("§e§lBED WARS"));
	}

	@Test
	void cleanedLinesAreReadAsTeamsAndCountdowns() {
		assertEquals(Boolean.TRUE, BedwarsGame.bed(BedwarsGame.clean("§eY §fYellow: ⚽§a✔")));
		assertEquals(Boolean.FALSE, BedwarsGame.bed(BedwarsGame.clean("👾§9B §fBlue: §c✘")));
		assertTrue(BedwarsGame.alive(BedwarsGame.clean("§aG §fGreen: 🎂§f3")));
		assertEquals(337, GameEvents.read(BedwarsGame.clean("§fDiamond II in 🎉§a5:37")).left);
		assertTrue(BedwarsGame.waitingLine(BedwarsGame.clean("Players: §a5/16🎂")));
	}

	@Test
	void plainLinesAreLeftAsTheyAre() {
		assertEquals("Joueurs : 5/16", BedwarsGame.clean("Joueurs : 5/16"));
		assertEquals("Émeraude II dans 3:05", BedwarsGame.clean("§fÉmeraude II dans §a3:05"));
	}
}
