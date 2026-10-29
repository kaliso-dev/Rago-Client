package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEventsTest {
	@Test
	void readsTheNextEventAndTheGameClock() {
		GameEvents.Reading r = GameEvents.read("Diamond II in 5:37");
		assertEquals(GameEvents.Kind.DIAMOND, r.event.kind);
		assertEquals(2, r.event.tier);
		assertEquals(337, r.left);
		assertEquals(23, r.elapsed());
		r = GameEvents.read("Emerald III in 0:05");
		assertEquals(GameEvents.Kind.EMERALD, r.event.kind);
		assertEquals(3, r.event.tier);
		assertEquals(24 * 60 - 5, r.elapsed());
		assertEquals(GameEvents.Kind.BED_GONE, GameEvents.read("Bed Gone in 4:59").event.kind);
		assertEquals(GameEvents.Kind.SUDDEN_DEATH, GameEvents.read("Sudden Death in 9:59").event.kind);
		assertEquals(GameEvents.Kind.GAME_END, GameEvents.read("Game End in 9:59").event.kind);
	}

	@Test
	void readsOtherLanguages() {
		GameEvents.Reading r = GameEvents.read("Émeraude II dans 11:00");
		assertEquals(GameEvents.Kind.EMERALD, r.event.kind);
		assertEquals(2, r.event.tier);
		assertEquals(GameEvents.Kind.DIAMOND, GameEvents.read("Diamant III dans 1:00").event.kind);
		assertEquals(GameEvents.Kind.BED_GONE, GameEvents.read("Destruction des lits dans 2:00").event.kind);
		assertEquals(GameEvents.Kind.SUDDEN_DEATH, GameEvents.read("Mort subite dans 3:00").event.kind);
		assertEquals(GameEvents.Kind.GAME_END, GameEvents.read("Fin de la partie dans 4:00").event.kind);
	}

	@Test
	void keepsUnknownCountdownsAndSkipsOtherLines() {
		GameEvents.Reading r = GameEvents.read("Dragon Spawn in 1:30");
		assertNull(r.event);
		assertEquals("Dragon Spawn in", r.label);
		assertEquals(90, r.left);
		assertEquals(-1, r.elapsed());
		assertNull(GameEvents.read("R Red: ✓"));
		assertNull(GameEvents.read("www.hypixel.net"));
		assertFalse(GameEvents.hasCountdown("Kills: 3"));
		assertTrue(GameEvents.hasCountdown("Diamond II in 5:59"));
		// "Splendid" holds "end" but not as a word
		assertNull(GameEvents.read("Splendid in 1:00").event);
	}

	@Test
	void formatsTimes() {
		assertEquals("5:07", GameEvents.clock(307));
		assertEquals("0:00", GameEvents.clock(-3));
		assertEquals("III", GameEvents.roman(3));
	}
}
