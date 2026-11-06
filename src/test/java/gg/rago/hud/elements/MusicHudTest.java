package gg.rago.hud.elements;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicHudTest {
	@Test
	void timesReadLikeAPlayer() {
		assertEquals("0:00", MusicHud.clock(0));
		assertEquals("1:05", MusicHud.clock(65));
		assertEquals("12:40", MusicHud.clock(760));
	}
}
