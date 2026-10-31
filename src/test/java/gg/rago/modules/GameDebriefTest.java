package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDebriefTest {
	@Test
	void theLineWaitsForHypixelsEndOfGameLines() {
		assertFalse(GameDebrief.readyToSay(0, 0), "not as the game ends");
		assertFalse(GameDebrief.readyToSay(900, 900), "a second at least");
		assertTrue(GameDebrief.readyToSay(1_000, 1_000), "a second after the end, and a second of quiet");
		assertFalse(GameDebrief.readyToSay(2_500, 300), "rewards and experience still coming");
		assertTrue(GameDebrief.readyToSay(3_600, 1_100), "once they stop");
		assertTrue(GameDebrief.readyToSay(8_000, 0), "eight seconds at most, even in a busy chat");
	}

	@Test
	void aGameLeftBeforeItsEndGetsNoLine() {
		assertTrue(GameDebrief.announced(gg.rago.bedwars.Debrief.Result.WIN));
		assertTrue(GameDebrief.announced(gg.rago.bedwars.Debrief.Result.LOSS));
		assertTrue(GameDebrief.announced(gg.rago.bedwars.Debrief.Result.ELIMINATED), "at your final death");
		assertFalse(GameDebrief.announced(gg.rago.bedwars.Debrief.Result.LEFT), "it would show in the next game or on joining");
	}
}
