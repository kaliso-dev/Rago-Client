package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamAliveTest {
	@Test
	void aTeamIsInTheGameWhileItHasABedOrPlayers() {
		assertTrue(BedwarsGame.alive("R Red: ✔"));
		assertTrue(BedwarsGame.alive("B Bleu: 2"));
		assertFalse(BedwarsGame.alive("G Green: 0"));
		assertFalse(BedwarsGame.alive("Y Yellow: ✘"));
		assertFalse(BedwarsGame.alive("Diamond II in 0:17"));
	}
}
