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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebriefCardTest {
	private static final String ME = "RagoTest";
	private static final Set<String> TAB = new HashSet<>(Arrays.asList(ME, "Steve", "Alex_99", "Notch", "Jeb_", "Technoblade"));
	private static final long T0 = 1_761_920_000_000L;

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

	/** Minecraft's own pictures, from the game's jar on the test classpath, with Rago's starter skin. */
	private static CardArt art() throws IOException {
		BufferedImage skin = ImageIO.read(DebriefCardTest.class.getResourceAsStream("/assets/rago/skins/qrago.png"));
		return CardArt.load(p -> DebriefCardTest.class.getResourceAsStream("/assets/minecraft/" + p), skin, false);
	}

	private static Map<String, Character> colors() {
		Map<String, Character> c = new HashMap<>();
		c.put(ME, 'c');
		c.put("Steve", '9');
		c.put("Notch", 'a');
		c.put("Alex_99", 'e');
		c.put("Jeb_", '9');
		c.put("Technoblade", 'a');
		return c;
	}

	/** A great win, with the Hypixel API before and after (a star gained). */
	private static Debrief bigGame() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.sidebar(Arrays.asList("Map: Lighthouse"));
		tracker.start(ME, T0, 0, 4);
		tracker.teamSize(4);
		tracker.teams('c', 16);
		tracker.statsBefore(new Debrief.Career(312, 1_512_800, 4210, 1000, 1500, 430, 2800, 1200, 9000, 7000, 1930, 12, "§b[MVP§c+§b]"));
		tracker.ownBed(true, T0 + 1_000);
		Map<String, Character> c = colors();
		String[] lines = {"Jeb_ was killed by Technoblade.", "RagoTest purchased Sharpened Swords", "Steve was killed by RagoTest.",
			"Notch was killed by RagoTest.", "Alex_99 was shot by RagoTest.", "BED DESTRUCTION > Blue Bed was destroyed by RagoTest!",
			"Steve was killed by RagoTest. FINAL KILL!", "RagoTest fell into the void.", "Jeb_ was killed by RagoTest. FINAL KILL!",
			"Notch was killed by RagoTest. FINAL KILL!", "BED DESTRUCTION > Green Bed was destroyed by RagoTest!",
			"Notch was killed by Technoblade.", "Alex_99 was killed by RagoTest. FINAL KILL!", "Technoblade was killed by RagoTest. FINAL KILL!"};
		int[] at = {60, 155, 200, 204, 209, 300, 320, 400, 730, 733, 760, 800, 1100, 1105};
		for (int i = 0; i < lines.length; i++) {
			tracker.chat(lines[i], ME, TAB, c, T0 + at[i] * 1000L);
		}
		tracker.ownBed(false, T0 + 840_000);
		Debrief d = tracker.end(Debrief.Result.WIN, T0 + 1_122_000);
		DebriefTracker.topKiller(d, "2nd Killer - [VIP] Technoblade - 3");
		DebriefTracker.statsAfter(d, new Debrief.Career(313, 1_513_370, 4215, 1000, 1501, 430, 2802, 1200, 9004, 7001, 1931, 13, "§b[MVP§c+§b]"));
		return d;
	}

	/** Final-killed early, a 2,100+ star and a long name with a long rank; no stats after. */
	private static Debrief eliminated() {
		String me = "xX_LongName_1234";
		Set<String> tab = new HashSet<>(Arrays.asList(me, "Steve", "Alex_99"));
		DebriefTracker tracker = new DebriefTracker();
		tracker.start(me, T0, 0, 8);
		tracker.teamSize(2);
		tracker.statsBefore(new Debrief.Career(2153, 10_480_000, 30000, 5000, 9000, 3000, 12000, 4000, 40000, 20000, 12000, -1, "§6[MVP§2++§6]"));
		tracker.chat("Steve was killed by Alex_99.", me, tab, T0 + 50_000);
		tracker.chat(me + " was knocked into the void by Alex_99. FINAL KILL!", me, tab, T0 + 95_000);
		return tracker.end(Debrief.Result.ELIMINATED, T0 + 96_000);
	}

	/** A loss without the Hypixel API. */
	private static Debrief noApi() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.start(ME, T0, 0, 8);
		tracker.teamSize(1);
		tracker.ownBed(true, T0 + 1_000);
		tracker.chat("Steve was killed by RagoTest.", ME, TAB, T0 + 100_000);
		tracker.ownBed(false, T0 + 300_000);
		tracker.chat("RagoTest was knocked into the void by Alex_99. FINAL KILL!", ME, TAB, T0 + 395_000);
		return tracker.end(Debrief.Result.LOSS, T0 + 396_000);
	}

	/** A full 4v4v4v4 game: sixteen players, most of them killing, a star gained on the way. */
	private static Debrief fullGame() {
		String[][] teams = {{"c", ME, "Kaliso", "Pyrozz", "Nyxen"}, {"9", "Vortexx", "Zeyrox", "Lumi_", "Solaar"},
			{"a", "Frostbyte", "Kenzo_", "Drakkon", "Milo"}, {"e", "Shadowz", "Tytan", "Ozzy_", "Nova"}};
		Map<String, Character> c = new HashMap<>();
		for (String[] team : teams) {
			for (int i = 1; i < team.length; i++) {
				c.put(team[i], team[0].charAt(0));
			}
		}
		Set<String> tab = c.keySet();
		DebriefTracker tracker = new DebriefTracker();
		tracker.sidebar(Arrays.asList("Map: Archway"));
		tracker.start(ME, T0, 0, 4);
		tracker.teamSize(4);
		tracker.teams('c', 16);
		tracker.statsBefore(new Debrief.Career(412, 1_999_700, 9800, 2400, 2100, 1300, 4200, 1900, 14000, 9000, 3400, 7, "§6[MVP§c++§6]"));
		tracker.ownBed(true, T0 + 1_000);
		Object[][] lines = {{40, "Nova was killed by RagoTest."}, {52, "Milo was killed by Shadowz."}, {66, "Kaliso was killed by Vortexx."},
			{71, "Vortexx was killed by RagoTest."}, {75, "Zeyrox was knocked into the void by RagoTest."}, {88, "Lumi_ was shot by Pyrozz."},
			{120, "RagoTest was killed by Shadowz."}, {150, "Ozzy_ was killed by Frostbyte."}, {190, "Tytan was killed by Kenzo_."},
			{240, "Kenzo_ was killed by Shadowz."}, {260, "Kaliso was killed by Shadowz."}, {300, "BED DESTRUCTION > Blue Bed was destroyed by RagoTest!"},
			{330, "Solaar was killed by RagoTest. FINAL KILL!"}, {336, "Vortexx was killed by RagoTest. FINAL KILL!"},
			{349, "Zeyrox was knocked into the void by Pyrozz. FINAL KILL!"}, {352, "Lumi_ was killed by Nyxen. FINAL KILL!"},
			{380, "Nyxen was knocked into the void by Shadowz."}, {420, "BED DESTRUCTION > Green Bed was destroyed by Shadowz!"},
			{450, "Pyrozz was killed by Shadowz."}, {470, "Frostbyte was killed by Shadowz. FINAL KILL!"}, {500, "Pyrozz was killed by Tytan."},
			{520, "RagoTest fell into the void."}, {560, "Nyxen was killed by Drakkon."}, {600, "BED DESTRUCTION > Yellow Bed was destroyed by Kaliso!"},
			{640, "Drakkon was killed by RagoTest. FINAL KILL!"}, {655, "Kenzo_ was knocked into the void by RagoTest. FINAL KILL!"},
			{662, "Milo was killed by RagoTest. FINAL KILL!"}, {780, "Shadowz was killed by RagoTest. FINAL KILL!"},
			{790, "Tytan was killed by Kaliso. FINAL KILL!"}, {800, "Ozzy_ was shot by Pyrozz. FINAL KILL!"},
			{812, "Nova was killed by RagoTest. FINAL KILL!"}};
		for (Object[] l : lines) {
			tracker.chat((String) l[1], ME, tab, c, T0 + (Integer) l[0] * 1000L);
		}
		Debrief d = tracker.end(Debrief.Result.WIN, T0 + 815_000);
		DebriefTracker.topKiller(d, "1st Killer - [MVP++] RagoTest - 10");
		DebriefTracker.topKiller(d, "2nd Killer - Shadowz - 7");
		DebriefTracker.topKiller(d, "3rd Killer - [VIP] Pyrozz - 3");
		DebriefTracker.statsAfter(d, new Debrief.Career(413, 2_000_250, 9807, 2400, 2101, 1300, 4201, 1900, 14003, 9002, 3401, 8, "§6[MVP§c++§6]"));
		return d;
	}

	private static Debrief nothing() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.start(ME, T0, 0, 2);
		return tracker.end(Debrief.Result.LEFT, T0 + 120_000);
	}

	@Test
	void picturesAreDrawnInBothLanguages() throws IOException {
		File out = new File("build/debrief-cards");
		out.mkdirs();
		CardArt art = art();
		assertNotNull(art.font, "Minecraft's font is read from the game's jar");
		assertNotNull(art.icons);
		assertNotNull(art.item("diamond_sword"));
		assertNotNull(art.wool('c'));
		Object[][] langs = {{"en_US", Locale.US}, {"fr_FR", Locale.FRANCE}};
		Debrief[] games = {bigGame(), eliminated(), noApi(), nothing(), fullGame()};
		for (Object[] l : langs) {
			DebriefCard.Text t = lang((String) l[0], (Locale) l[1]);
			for (int i = 0; i < games.length; i++) {
				CardArt a = i == 1 ? art.withSkin(null, true) : art;
				DebriefCard.Session session = i == 4 ? new DebriefCard.Session(3, 2, 15) : new DebriefCard.Session(i + 1, 1, 6);
				BufferedImage img = DebriefCard.render(games[i], session, t, (Locale) l[1], i != 1, a, i == 2 ? "Nicked" : null);
				assertEquals(DebriefCard.W, img.getWidth());
				assertEquals(DebriefCard.H, img.getHeight());
				// something was drawn over the background, in both columns
				assertNotEquals(img.getRGB(200, 380), img.getRGB(200, 860));
				assertNotEquals(img.getRGB(1310, 250), img.getRGB(10, 450));
				ImageIO.write(img, "png", new File(out, l[0] + "-" + i + ".png"));
			}
			// without the game's pictures (Rago's fonts only)
			BufferedImage plain = DebriefCard.render(games[0], new DebriefCard.Session(1, 1, 6), t, (Locale) l[1], true);
			ImageIO.write(plain, "png", new File(out, l[0] + "-plain.png"));
		}
	}

	@Test
	void theOtherStylesAreDrawnInBothLanguages() throws IOException {
		File out = new File("build/debrief-cards");
		out.mkdirs();
		CardArt art = art();
		assertNotNull(art.glint, "the enchanted glint is read from the game's jar");
		Object[][] langs = {{"en_US", Locale.US}, {"fr_FR", Locale.FRANCE}};
		// a session: a loss without the API, then two wins with it
		List<Debrief> session = Arrays.asList(noApi(), bigGame(), fullGame());
		List<Debrief> others = Arrays.asList(eliminated(), nothing());
		for (Object[] l : langs) {
			DebriefCard.Text t = lang((String) l[0], (Locale) l[1]);
			Locale locale = (Locale) l[1];
			for (DebriefCard.Style style : DebriefCard.Style.values()) {
				for (int i = 0; i < session.size(); i++) {
					BufferedImage img = DebriefCard.render(style, session, i, t, locale, true, art, null);
					assertEquals(style.width, img.getWidth());
					assertEquals(style.height, img.getHeight());
					if (style != DebriefCard.Style.CLASSIC) {
						ImageIO.write(img, "png", new File(out, l[0] + "-" + style.id + "-" + i + ".png"));
					}
				}
				for (int i = 0; i < others.size(); i++) {
					BufferedImage img = DebriefCard.render(style, others, i, t, locale, i == 0, art.withSkin(null, true), null);
					assertEquals(style.width, img.getWidth());
					if (style != DebriefCard.Style.CLASSIC) {
						ImageIO.write(img, "png", new File(out, l[0] + "-" + style.id + "-other" + i + ".png"));
					}
				}
				// without the game's pictures (Rago's fonts only)
				BufferedImage plain = DebriefCard.render(style, session, 2, t, locale, true, null, null);
				ImageIO.write(plain, "png", new File(out, l[0] + "-" + style.id + "-plain.png"));
			}
		}
	}

	@Test
	void cardsAreRatedFromTheStats() {
		Debrief.Career pro = new Debrief.Career(1200, 6_000_000, 60000, 5000, 9000, 1500, 20000, 4000, 80000, 30000, 10500, 20, "");
		Debrief.Career mid = new Debrief.Career(413, 2_000_250, 9807, 2400, 2100, 1300, 4200, 1900, 14000, 9000, 3400, 8, "");
		Debrief.Career fresh = new Debrief.Career(12, 40_000, 40, 90, 10, 40, 15, 45, 120, 200, 50, 0, "");
		Debrief game = fullGame();
		assertTrue(CollectorCard.overall(pro, game) > CollectorCard.overall(mid, game));
		assertTrue(CollectorCard.overall(mid, game) > CollectorCard.overall(fresh, game));
		assertTrue(CollectorCard.overall(pro, game) <= 99 && CollectorCard.overall(fresh, game) >= 1);
		assertEquals(CollectorCard.Rarity.LEGENDARY, CollectorCard.rarity(1200));
		assertEquals(CollectorCard.Rarity.RARE, CollectorCard.rarity(413));
		assertEquals(CollectorCard.Rarity.COMMON, CollectorCard.rarity(12));
		assertEquals(CollectorCard.Rarity.COMMON, CollectorCard.rarity(-1));
		assertEquals("finisher", CollectorCard.talent(pro).key);
		assertEquals("finisher", CollectorCard.talent(mid).key);
		assertEquals("all_rounder", CollectorCard.talent(fresh).key);
		assertEquals("no_api", CollectorCard.talent(null).key);
	}

	@Test
	void theSessionEarnsATitle() {
		SessionCard.Recap recap = new SessionCard.Recap(Arrays.asList(noApi(), bigGame(), fullGame()));
		assertEquals(3, recap.games);
		assertEquals(2, recap.wins);
		assertEquals(2, recap.bestRun);
		assertEquals(5 + 7 + 0, recap.finals - noApi().finalKills());
		assertEquals("final_machine", recap.title()[0]);
		SessionCard.Recap lost = new SessionCard.Recap(Arrays.asList(noApi()));
		assertEquals("warming_up", lost.title()[0]);
		SessionCard.Recap won = new SessionCard.Recap(Arrays.asList(bigGame(), fullGame()));
		assertEquals("unbeaten", won.title()[0]);
	}

	@Test
	void gradesFollowTheGame() {
		assertEquals("S+", DebriefCard.grade(bigGame()));
		assertEquals("D", DebriefCard.grade(eliminated()));
		assertEquals("D", DebriefCard.grade(noApi()));
		assertEquals("D", DebriefCard.grade(nothing()));
	}

	@Test
	void countsAreRatedAgainstYourAverage() {
		assertEquals(DebriefCard.Rating.ELITE, DebriefCard.rate(5, 2.2, 1, 2, 4));
		assertEquals(DebriefCard.Rating.GOOD, DebriefCard.rate(3, 2.2, 1, 2, 4));
		assertEquals(DebriefCard.Rating.AVERAGE, DebriefCard.rate(2, 2.2, 1, 2, 4));
		assertEquals(DebriefCard.Rating.POOR, DebriefCard.rate(1, 2.2, 1, 2, 4));
		// a small average: one more is good, not elite
		assertEquals(DebriefCard.Rating.GOOD, DebriefCard.rate(2, 0.8, 1, 2, 4));
		assertEquals(DebriefCard.Rating.ELITE, DebriefCard.rate(3, 0.8, 1, 2, 4));
		// without the API: fixed steps
		assertEquals(DebriefCard.Rating.POOR, DebriefCard.rate(0, Double.NaN, 1, 2, 4));
		assertEquals(DebriefCard.Rating.ELITE, DebriefCard.rate(4, Double.NaN, 1, 2, 4));
		assertEquals(DebriefCard.Rating.ELITE, DebriefCard.rateDeaths(0, 4.1, 600));
		assertEquals(DebriefCard.Rating.GOOD, DebriefCard.rateDeaths(0, 4.1, 60), "no death in a minute is no feat");
		assertEquals(DebriefCard.Rating.GOOD, DebriefCard.rateDeaths(2, 4.1, 600));
		assertEquals(DebriefCard.Rating.AVERAGE, DebriefCard.rateDeaths(5, 4.1, 600));
		assertEquals(DebriefCard.Rating.POOR, DebriefCard.rateDeaths(6, 4.1, 600));
		assertEquals(DebriefCard.Rating.POOR, DebriefCard.rateDeaths(5, Double.NaN, 600));
	}

	@Test
	void theBestHighlightsFirst() {
		List<String> keys = new ArrayList<>();
		for (DebriefCard.Badge b : DebriefCard.badges(bigGame())) {
			keys.add(b.key);
		}
		assertEquals(Arrays.asList("level_up", "mvp", "spree", "final_boss"), keys);
		assertEquals(3, DebriefCard.spree(bigGame(), 30), "three kills within 9 seconds");
		assertTrue(DebriefCard.badges(nothing()).isEmpty());
		String[] nemesis = DebriefCard.most(eliminated(), Debrief.Kind.DEATH, Debrief.Kind.FINAL_DEATH);
		assertEquals(null, nemesis, "killed once: no nemesis");
		String[] victim = DebriefCard.most(bigGame(), Debrief.Kind.KILL, Debrief.Kind.FINAL_KILL);
		assertEquals("Steve", victim[0]);
		assertEquals("2", victim[1]);
	}

	@Test
	void rankColoursAndText() {
		assertEquals('b', DebriefCard.firstColor("§b[MVP§c+§b]", '7'));
		assertEquals('7', DebriefCard.firstColor("", '7'));
		assertEquals("[MVP+]", DebriefCard.plain("§b[MVP§c+§b]"));
		assertEquals(0x55FF55, DebriefCard.ratioColor(2.5));
		assertEquals(0xAAAAAA, DebriefCard.ratioColor(0.4));
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
