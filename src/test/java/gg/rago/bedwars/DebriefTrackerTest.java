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
}
