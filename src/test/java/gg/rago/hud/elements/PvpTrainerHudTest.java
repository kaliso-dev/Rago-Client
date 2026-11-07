package gg.rago.hud.elements;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PvpTrainerHudTest {
	private static final long FAR = Long.MIN_VALUE / 2;

	@Test
	void aJumpOnTheKnockbacksTickIsPerfect() {
		assertEquals(0, PvpTrainerHud.score(100, 100, 100));
	}

	@Test
	void aJumpBeforeTheKnockbackIsEarlyByItsTicks() {
		assertEquals(-1, PvpTrainerHud.score(100, 99, 99));
		assertEquals(-3, PvpTrainerHud.score(100, 97, 97));
	}

	@Test
	void aPressInTheAirAfterTheKnockbackIsLate() {
		assertEquals(1, PvpTrainerHud.score(100, FAR, 101));
		assertEquals(3, PvpTrainerHud.score(100, 60, 103));
	}

	@Test
	void nothingCloseIsNoJump() {
		assertEquals(PvpTrainerHud.NO_JUMP, PvpTrainerHud.score(100, 90, 90));
		assertEquals(PvpTrainerHud.NO_JUMP, PvpTrainerHud.score(100, FAR, FAR));
		assertEquals(PvpTrainerHud.NO_JUMP, PvpTrainerHud.score(100, FAR, 104));
	}

	@Test
	void barsAndColoursFollowTheScore() {
		assertEquals(PvpTrainerHud.barHeight(0) > PvpTrainerHud.barHeight(1), true);
		assertEquals(PvpTrainerHud.barHeight(-1), PvpTrainerHud.barHeight(1));
		assertEquals(PvpTrainerHud.color(2), PvpTrainerHud.color(-3));
	}
}
