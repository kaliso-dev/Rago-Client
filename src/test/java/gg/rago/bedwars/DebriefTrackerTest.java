package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebriefTrackerTest {
	private static final String ME = "RagoTest";
	private static final Set<String> TAB = new HashSet<>(Arrays.asList(ME, "Steve", "Alex_99", "Notch"));

	@Test
	void yourKillsAreTold() {
		assertEquals(Debrief.Kind.KILL, DebriefTracker.ownKill("Steve was killed by RagoTest.", ME, TAB));
		assertEquals(Debrief.Kind.KILL, DebriefTracker.ownKill("Alex_99 was knocked into the void by RagoTest.", ME, TAB));
		assertEquals(Debrief.Kind.FINAL_KILL, DebriefTracker.ownKill("Steve was killed by RagoTest. FINAL KILL!", ME, TAB));
		assertEquals(Debrief.Kind.FINAL_KILL, DebriefTracker.ownKill("Steve a été tué par RagoTest. ÉLIMINATION FINALE !", ME, TAB));
		assertNull(DebriefTracker.ownKill("Steve was killed by Notch.", ME, TAB), "someone else's kill");
		assertNull(DebriefTracker.ownKill("Steve was killed by RagoTest2.", ME, TAB), "a longer name");
		assertNull(DebriefTracker.ownKill("Steve: nice one RagoTest.", ME, TAB), "chat");
		assertNull(DebriefTracker.ownKill("[MVP+] Steve: gg RagoTest!", ME, TAB), "chat with a rank");
		assertNull(DebriefTracker.ownKill("Herobrine was killed by RagoTest.", ME, TAB), "the victim is not in the game");
		assertNull(DebriefTracker.ownKill("RagoTest was killed by Steve.", ME, TAB), "your own death");
		assertNull(DebriefTracker.ownKill("Steve purchased Sharpened Swords", ME, TAB));
	}

	@Test
	void yourDeathsAreTold() {
		assertEquals(Debrief.Kind.DEATH, DebriefTracker.ownDeath("RagoTest was killed by Steve.", ME));
		assertEquals(Debrief.Kind.DEATH, DebriefTracker.ownDeath("RagoTest fell into the void.", ME));
		assertEquals(Debrief.Kind.FINAL_DEATH, DebriefTracker.ownDeath("RagoTest was shot by Alex_99. FINAL KILL!", ME));
		assertNull(DebriefTracker.ownDeath("RagoTest purchased Sharpened Swords.", ME), "a purchase");
		assertNull(DebriefTracker.ownDeath("RagoTest: gg.", ME), "your chat");
		assertNull(DebriefTracker.ownDeath("RagoTest2 was killed by Steve.", ME), "a longer name");
		assertTrue(DebriefTracker.isVoid("RagoTest fell into the void."));
		assertTrue(DebriefTracker.isVoid("RagoTest est tombé dans le vide."));
		assertFalse(DebriefTracker.isVoid("RagoTest was killed by Avoidance."));
		assertEquals("Steve", DebriefTracker.killer("RagoTest was killed by Steve.", ME, TAB));
		assertEquals("Alex_99", DebriefTracker.killer("RagoTest was knocked into the void by Alex_99. FINAL KILL!", ME, TAB));
		assertEquals("", DebriefTracker.killer("RagoTest fell into the void.", ME, TAB));
	}

	@Test
	void aWholeGame() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.sidebar(Arrays.asList("Map: Lighthouse", "Players: 16/16"));
		long t0 = 1_700_000_000_000L;
		tracker.start(ME, t0, 0, 4);
		tracker.teamSize(3);
		tracker.teamSize(4);
		tracker.ownBed(true, t0 + 1_000);
		tracker.chat("RagoTest purchased Sharpened Swords", ME, TAB, t0 + 155_000);
		tracker.chat("Steve was killed by RagoTest.", ME, TAB, t0 + 200_000);
		tracker.chat("BED DESTRUCTION > Blue Bed was destroyed by RagoTest!", ME, TAB, t0 + 300_000);
		tracker.chat("Steve was killed by RagoTest. FINAL KILL!", ME, TAB, t0 + 320_000);
		tracker.chat("RagoTest fell into the void.", ME, TAB, t0 + 400_000);
		tracker.chat("Notch: gg RagoTest.", ME, TAB, t0 + 410_000);
		tracker.ownBed(false, t0 + 600_000);
		tracker.ownBed(false, t0 + 601_000);
		tracker.chat("RagoTest was killed by Alex_99. FINAL KILL!", ME, TAB, t0 + 700_000);
		Debrief d = tracker.end(Debrief.Result.ELIMINATED, t0 + 705_000);
		assertFalse(tracker.inGame());
		assertEquals("Lighthouse", d.map());
		assertEquals("fours", d.mode());
		assertEquals(705, d.seconds());
		assertEquals(1, d.kills());
		assertEquals(1, d.finalKills());
		assertEquals(1, d.beds());
		assertEquals(2, d.deaths());
		assertEquals(1, d.voidDeaths());
		assertEquals("Alex_99", d.finalKiller());
		assertTrue(d.bedSeen());
		assertEquals(1, d.count(Debrief.Kind.BED_LOST), "the bed falls once");
		assertEquals(600, d.first(Debrief.Kind.BED_LOST).at);
		assertEquals(155, d.first(Debrief.Kind.UPGRADE).at);
		assertEquals("Sharpened Swords", d.first(Debrief.Kind.UPGRADE).detail);
		assertEquals("Steve", d.first(Debrief.Kind.FINAL_KILL).detail);
	}

	@Test
	void hypixelsOwnCountsWinOverTheChat() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.start(ME, 0, 0, 4);
		tracker.chat("Steve was killed by RagoTest.", ME, TAB, 10_000);
		tracker.sidebar(Arrays.asList("Kills: 3", "Final Kills: 2", "Beds Broken: 1"));
		Debrief d = tracker.end(Debrief.Result.WIN, 600_000);
		assertEquals(3, d.kills());
		assertEquals(2, d.finalKills());
		assertEquals(1, d.beds());
		assertEquals(1, d.count(Debrief.Kind.KILL), "the timeline keeps what the chat said");
	}

	@Test
	void aGameJoinedLateCountsFromItsStart() {
		DebriefTracker tracker = new DebriefTracker();
		long now = 1_000_000;
		tracker.start(ME, now, 300, 8);
		tracker.chat("Steve was killed by RagoTest.", ME, TAB, now + 10_000);
		Debrief d = tracker.end(Debrief.Result.LOSS, now + 60_000);
		assertEquals(360, d.seconds());
		assertEquals(310, d.first(Debrief.Kind.KILL).at);
		assertEquals("", d.mode(), "no team size seen");
	}

	@Test
	void nothingIsFollowedOutsideAGame() {
		DebriefTracker tracker = new DebriefTracker();
		tracker.chat("Steve was killed by RagoTest.", ME, TAB, 0);
		tracker.ownBed(false, 0);
		tracker.sidebar(Collections.singletonList("Kills: 3"));
		assertNull(tracker.end(Debrief.Result.LEFT, 0));
	}

	@Test
	void theGamesKillLeaderboard() {
		Set<String> tab = new HashSet<>(Arrays.asList(ME, "Steve", "Alex_99", "Notch", "Jeb_"));
		java.util.Map<String, Character> colors = new java.util.HashMap<>();
		colors.put("Alex_99", '9');
		colors.put(ME, 'c');
		DebriefTracker tracker = new DebriefTracker();
		long t0 = 1_700_000_000_000L;
		tracker.start(ME, t0, 0, 4);
		tracker.teams('c', 16);
		tracker.chat("Steve was killed by Alex_99.", ME, tab, colors, t0 + 10_000);
		tracker.chat("Notch was knocked into the void by Alex_99. FINAL KILL!", ME, tab, colors, t0 + 20_000);
		tracker.chat("Jeb_ a été tué par Alex_99. ÉLIMINATION FINALE !", ME, tab, colors, t0 + 25_000);
		tracker.chat("Steve was killed by RagoTest.", ME, tab, colors, t0 + 30_000);
		tracker.chat("Steve fell into the void.", ME, tab, colors, t0 + 35_000);
		tracker.chat("Steve: Alex_99 is hacking.", ME, tab, colors, t0 + 36_000);
		tracker.chat("Notch purchased Sharpened Swords", ME, tab, colors, t0 + 37_000);
		tracker.chat("RagoTest was killed by Jeb_.", ME, tab, colors, t0 + 40_000);
		Debrief d = tracker.current();
		assertEquals(5, d.version(), "each kill draws the picture again");
		java.util.List<Debrief.Killer> board = d.board();
		assertEquals("Alex_99", board.get(0).name);
		assertEquals(3, board.get(0).kills());
		assertEquals(2, board.get(0).finals());
		assertEquals('9', board.get(0).color());
		assertEquals(ME, board.get(1).name, "you before the others with as many kills");
		assertEquals('c', board.get(1).color());
		assertEquals(2, d.rank());
		assertFalse(d.firstBlood(), "Alex_99 killed first");
		assertEquals(16, d.players());

		// the sidebar says more than the chat did: your count is the larger one
		tracker.sidebar(Arrays.asList("Kills: 3", "Final Kills: 1"));
		assertEquals(4, d.board().get(0).kills());
		assertEquals(ME, d.board().get(0).name);
		assertEquals(1, d.rank());

		// after your final death the others' kills still count, then Hypixel's top killers
		Debrief over = tracker.end(Debrief.Result.ELIMINATED, t0 + 60_000);
		assertTrue(DebriefTracker.countKill(over, "Notch was killed by Alex_99. FINAL KILL!", tab, colors));
		assertEquals(4, over.board().get(0).kills(), "a tie: you stay first");
		assertTrue(DebriefTracker.topKiller(over, "1st Killer - [MVP+] Alex_99 - 7"));
		assertTrue(DebriefTracker.topKiller(over, "2nd Killer - RagoTest - 4"));
		assertTrue(DebriefTracker.topKiller(over, "3e Tueur - [VIP] Jeb_ - 2"));
		assertFalse(DebriefTracker.topKiller(over, "Alex_99: 1st Killer - me - 99"));
		assertFalse(DebriefTracker.topKiller(over, "Bed Wars"));
		assertEquals("Alex_99", over.board().get(0).name);
		assertEquals(7, over.board().get(0).kills());
		assertEquals(2, over.rank());
	}

	@Test
	void firstBloodIsTheGamesFirstKill() {
		DebriefTracker tracker = new DebriefTracker();
		long t0 = 1_700_000_000_000L;
		tracker.start(ME, t0, 0, 4);
		tracker.chat("Steve was killed by RagoTest.", ME, TAB, t0 + 10_000);
		tracker.chat("Notch was killed by Alex_99.", ME, TAB, t0 + 20_000);
		assertTrue(tracker.current().firstBlood());
		assertEquals(1, tracker.current().rank());
	}

	@Test
	void careerBeforeAndAfter() {
		DebriefTracker tracker = new DebriefTracker();
		long t0 = 1_700_000_000_000L;
		tracker.start(ME, t0, 0, 4);
		tracker.statsBefore(new Debrief.Career(312, 1_512_800, 400, 100, 50, 20, 90, 30, 900, 500, 70, 3, "§a[VIP]"));
		Debrief d = tracker.end(Debrief.Result.WIN, t0 + 600_000);
		assertEquals(4.0, d.fkdrBefore(), 1e-9);
		assertEquals(312, d.starBefore());
		assertEquals(-1, d.xpGained(), "not saved by Hypixel yet");
		DebriefTracker.statsAfter(d, new Debrief.Career(313, 1_513_370, 403, 100, 51, 20, 90, 30, 904, 500, 71, 4, "§a[VIP]"));
		assertEquals(570, d.xpGained());
		assertEquals(4.03, d.fkdrAfter(), 1e-9);
		assertEquals(3.0, d.careerAfter().bblr(), 1e-9);
		assertEquals(900 / 70.0, d.careerBefore().perGame(900), 1e-9);
		assertEquals(600 / 70.0, d.careerBefore().deathsPerGame(), 1e-9);
		assertEquals(1, d.version());
	}
}
