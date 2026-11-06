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

	@Test
	void buttonsUnderTheTitle() {
		// 170 wide with the cover: text from 39, 127 wide, three 16 px buttons 6 apart centred from 72
		assertEquals(0, MusicHud.buttonAt(72, 16, true, 170));
		assertEquals(0, MusicHud.buttonAt(87.9f, 31.9f, true, 170));
		assertEquals(-1, MusicHud.buttonAt(90, 20, true, 170));
		assertEquals(1, MusicHud.buttonAt(100, 24, true, 170));
		assertEquals(2, MusicHud.buttonAt(131, 24, true, 170));
		assertEquals(-1, MusicHud.buttonAt(100, 10, true, 170));
		assertEquals(-1, MusicHud.buttonAt(100, 32, true, 170));
		// without the cover the text starts at 4: buttons from 4 + (162 - 60) / 2 = 55
		assertEquals(0, MusicHud.buttonAt(55, 20, false, 170));
		assertEquals(-1, MusicHud.buttonAt(72, 20, false, 170));
	}
}
