package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitingRoomTest {
	@Test
	void theWaitingRoomCountsItsPlayers() {
		assertTrue(BedwarsGame.waitingLine("Players: 5/8"));
		assertTrue(BedwarsGame.waitingLine("Joueurs : 11/16 "));
		assertTrue(BedwarsGame.waitingLine("Players: 12/12"));
	}

	@Test
	void theLobbySidebarIsNoWaitingRoom() {
		assertFalse(BedwarsGame.waitingLine("Progress: 2.5k/5k"));
		assertFalse(BedwarsGame.waitingLine("Progress: 1,208/5,000"));
		assertFalse(BedwarsGame.waitingLine("10/30/26 m123AB"));
		assertFalse(BedwarsGame.waitingLine("Players: 40/80"));
		assertFalse(BedwarsGame.waitingLine("Diamond II in 5:37"));
	}
}
