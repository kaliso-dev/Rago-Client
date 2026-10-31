package gg.rago.gui;

import gg.rago.bedwars.Debrief;
import gg.rago.bedwars.DebriefTracker;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DebriefCardTest {
	private static final String ME = "RagoTest";
	private static final Set<String> TAB = new HashSet<>(Arrays.asList(ME, "Steve", "Alex_99", "Notch", "Jeb_"));

	/** The game's texts from a language file; a missing key fails the test. */
	private static DebriefCard.Text lang(String file, Locale locale) throws IOException {
		Properties p = new Properties();
		try (InputStream in = DebriefCardTest.class.getResourceAsStream("/assets/rago/lang/" + file + ".lang")) {
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return (key, args) -> {
			String v = p.getProperty(key);
			if (v == null) {
				throw new AssertionError(file + " has no " + key);
			}
			return String.format(locale, v, args);
		};
	}

	private static Debrief bigGame() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.sidebar(Arrays.asList("Map: Lighthouse"));
		long t0 = 1_761_920_000_000L;
		tracker.start(ME, t0, 0, 4);
		tracker.teamSize(4);
		tracker.statsBefore(2.3125, 312);
		tracker.ownBed(true, t0 + 1_000);
		tracker.chat("RagoTest purchased Sharpened Swords", ME, TAB, t0 + 155_000);
		tracker.chat("Steve was killed by RagoTest.", ME, TAB, t0 + 200_000);
		tracker.chat("Notch was killed by RagoTest.", ME, TAB, t0 + 204_000);
		tracker.chat("BED DESTRUCTION > Blue Bed was destroyed by RagoTest!", ME, TAB, t0 + 300_000);
		tracker.chat("Steve was killed by RagoTest. FINAL KILL!", ME, TAB, t0 + 320_000);
		tracker.chat("RagoTest fell into the void.", ME, TAB, t0 + 400_000);
		tracker.chat("RagoTest purchased Iron Forge", ME, TAB, t0 + 420_000);
		tracker.chat("Jeb_ was killed by RagoTest. FINAL KILL!", ME, TAB, t0 + 730_000);
		tracker.chat("Notch was killed by RagoTest. FINAL KILL!", ME, TAB, t0 + 733_000);
		tracker.ownBed(false, t0 + 840_000);
		tracker.chat("Alex_99 was killed by RagoTest. FINAL KILL!", ME, TAB, t0 + 1_100_000);
		Debrief d = tracker.end(Debrief.Result.WIN, t0 + 1_122_000);
		DebriefTracker.statsAfter(d, 2.3412);
		return d;
	}

	private static Debrief longUpgrade() {
		DebriefTracker tracker = new DebriefTracker();
		long t0 = 1_761_920_000_000L;
		tracker.start(ME, t0, 0, 4);
		tracker.teamSize(2);
		tracker.statsBefore(1.25, 40);
		tracker.ownBed(true, t0 + 1_000);
		tracker.chat("RagoTest purchased Counter-Offensive Trap", ME, TAB, t0 + 754_000);
		tracker.chat("Steve was killed by RagoTest. FINAL KILL!", ME, TAB, t0 + 800_000);
		tracker.chat("RagoTest was killed by Alex_99. FINAL KILL!", ME, TAB, t0 + 900_000);
		return tracker.end(Debrief.Result.ELIMINATED, t0 + 905_000);
	}

	private static Debrief shortLoss() {
		DebriefTracker tracker = new DebriefTracker();
		long t0 = 1_761_920_000_000L;
		tracker.start(ME, t0, 0, 8);
		tracker.teamSize(1);
		tracker.chat("RagoTest was knocked into the void by Alex_99. FINAL KILL!", ME, TAB, t0 + 95_000);
		return tracker.end(Debrief.Result.ELIMINATED, t0 + 96_000);
	}

	private static Debrief nothing() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.start(ME, 1_761_920_000_000L, 0, 2);
		return tracker.end(Debrief.Result.LEFT, 1_761_920_000_000L + 120_000);
	}

	@Test
	void picturesAreDrawnInBothLanguages() throws IOException {
		File out = new File("build/debrief-cards");
		out.mkdirs();
		Object[][] langs = {{"en_US", Locale.US}, {"fr_FR", Locale.FRANCE}};
		Debrief[] games = {bigGame(), shortLoss(), nothing(), longUpgrade()};
		for (Object[] l : langs) {
			DebriefCard.Text t = lang((String) l[0], (Locale) l[1]);
			for (int i = 0; i < games.length; i++) {
				BufferedImage img = DebriefCard.render(games[i], new DebriefCard.Session(i + 1, 1, 6), t, (Locale) l[1], i != 1);
				assertEquals(DebriefCard.W, img.getWidth());
				assertEquals(DebriefCard.H, img.getHeight());
				// something was drawn over the background
				assertNotEquals(img.getRGB(DebriefCard.W / 2, 250), img.getRGB(DebriefCard.W / 2, 700));
				ImageIO.write(img, "png", new File(out, l[0] + "-" + i + ".png"));
			}
		}
	}

	@Test
	void longValuesTakeTheRoomShortOnesLeave() {
		float[] cols = DebriefCard.share(new float[] {330, 200, 60, 60}, 1072);
		assertEquals(330, cols[0], 0.01);
		assertEquals(200, cols[1], 0.01);
		assertEquals(60, cols[2], 0.01);
		float[] tight = DebriefCard.share(new float[] {900, 800, 60, 60}, 1072);
		assertEquals(60, tight[2], 0.01);
		assertEquals(476, tight[0], 0.01, "the two long ones share what is left");
		assertEquals(476, tight[1], 0.01);
	}

	@Test
	void countsAgreeWithTheLanguage() throws IOException {
		DebriefCard.Text fr = lang("fr_FR", Locale.FRANCE);
		DebriefCard.Text en = lang("en_US", Locale.US);
		assertEquals("0 victoire", DebriefCard.plural(fr, Locale.FRANCE, "rago.debrief.wins", 0));
		assertEquals("1 kill final", DebriefCard.plural(fr, Locale.FRANCE, "rago.debrief.finals", 1));
		assertEquals("2 parties", DebriefCard.plural(fr, Locale.FRANCE, "rago.debrief.games", 2));
		assertEquals("0 wins", DebriefCard.plural(en, Locale.US, "rago.debrief.wins", 0));
		assertEquals("1 game", DebriefCard.plural(en, Locale.US, "rago.debrief.games", 1));
	}
}
