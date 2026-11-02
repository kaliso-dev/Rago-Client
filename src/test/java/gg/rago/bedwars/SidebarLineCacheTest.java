package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A sidebar line is read once and the reading reused: it must say what reading it each time would. */
class SidebarLineCacheTest {
	private static final String[] LINES = {
		"§cR §fRed: §a✔", "§9B §fBlue: §a2", "§aG §fGreen: §c✘",
		"§eY §fYellow: §70", "Diamond II in §a4:12", "Players: §a7/8", "", "§ewww.hypixel.net",
		"Kills: §a3", "Emerald II in §a0:05",
	};

	@Test
	void theCachedReadingIsTheDirectOne() {
		for (String raw : LINES) {
			BedwarsGame.Line line = BedwarsGame.line(raw);
			String text = BedwarsGame.clean(raw);
			assertEquals(text, line.text, raw);
			assertEquals(BedwarsGame.bed(text), line.bed, raw);
			assertEquals(BedwarsGame.bed(text) != null && BedwarsGame.alive(text), line.alive, raw);
			assertEquals(GameEvents.hasCountdown(text), line.countdown, raw);
			assertEquals(BedwarsGame.waitingLine(text), line.waiting, raw);
		}
	}

	@Test
	void aLineIsReadOnce() {
		String raw = "§cR §fRed: §a✔";
		assertSame(BedwarsGame.line(raw), BedwarsGame.line(new String(raw.toCharArray())));
		assertSame(BedwarsGame.line(null), BedwarsGame.line(""));
	}
}
