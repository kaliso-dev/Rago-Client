package gg.rago.bedwars;

import com.google.gson.JsonParser;
import gg.rago.hypixel.HypixelApi;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestsTest {
	private static final long NOW = ZonedDateTime.of(2026, 10, 29, 15, 0, 0, 0, ZoneId.of("America/New_York")).toInstant().toEpochMilli();
	private static final long HOUR = 3600_000L;

	private static Quests.Quest quest(String id) {
		for (Quests.Quest q : Quests.ALL) {
			if (q.id.equals(id)) {
				return q;
			}
		}
		throw new IllegalArgumentException(id);
	}

	private static HypixelApi.Stats stats(String quests, long time) {
		return HypixelApi.parse(new JsonParser().parse("{\"player\":{\"quests\":{" + quests + "}}}"), time);
	}

	@Test
	void readsProgressAndCountsLive() {
		Quests q = new Quests();
		q.update(stats("\"bedwars_daily_final_killer\":{\"active\":{\"started\":" + (NOW - HOUR)
			+ ",\"objectives\":{\"bedwars_daily_final_killer\":7}}},"
			+ "\"bedwars_daily_win\":{\"completions\":[{\"time\":" + (NOW - 2 * HOUR) + "}]},"
			+ "\"bedwars_daily_bed_breaker\":{\"completions\":[{\"time\":" + (NOW - 24 * HOUR) + "}]}", NOW), NOW);
		Quests.Quest finals = quest("bedwars_daily_final_killer");
		assertEquals(Quests.State.RUNNING, q.state(finals, NOW));
		assertEquals(7, q.progress(finals, NOW));
		q.count(Quests.Kind.FINAL_KILL);
		q.count(Quests.Kind.FINAL_KILL);
		assertEquals(9, q.progress(finals, NOW));
		// done today, and done yesterday (so not done today)
		assertEquals(Quests.State.DONE, q.state(quest("bedwars_daily_win"), NOW));
		assertEquals(Quests.State.NOT_STARTED, q.state(quest("bedwars_daily_bed_breaker"), NOW));
		assertEquals(Quests.State.NOT_STARTED, q.state(quest("bedwars_weekly_final_killer"), NOW));
	}

	@Test
	void keepsLiveCountWhenTheApiIsBehind() {
		Quests q = new Quests();
		String started = "\"started\":" + (NOW - HOUR);
		q.update(stats("\"bedwars_weekly_final_killer\":{\"active\":{" + started + ",\"objectives\":{\"bedwars_weekly_final_killer\":40}}}", NOW), NOW);
		for (int i = 0; i < 5; i++) {
			q.count(Quests.Kind.FINAL_KILL);
		}
		Quests.Quest weekly = quest("bedwars_weekly_final_killer");
		assertEquals(45, q.progress(weekly, NOW));
		// Hypixel has not saved the game yet: still 45, not back to 40
		q.update(stats("\"bedwars_weekly_final_killer\":{\"active\":{" + started + ",\"objectives\":{\"bedwars_weekly_final_killer\":40}}}", NOW + 1), NOW + 1);
		assertEquals(45, q.progress(weekly, NOW + 1));
		// now it has, and one more kill since
		q.update(stats("\"bedwars_weekly_final_killer\":{\"active\":{" + started + ",\"objectives\":{\"bedwars_weekly_final_killer\":45}}}", NOW + 2), NOW + 2);
		q.count(Quests.Kind.FINAL_KILL);
		assertEquals(46, q.progress(weekly, NOW + 2));
	}

	@Test
	void yesterdaysDailyQuestHasExpired() {
		Quests q = new Quests();
		q.update(stats("\"bedwars_daily_final_killer\":{\"active\":{\"started\":" + (NOW - 20 * HOUR)
			+ ",\"objectives\":{\"bedwars_daily_final_killer\":7}}}", NOW), NOW);
		assertEquals(Quests.State.NOT_STARTED, q.state(quest("bedwars_daily_final_killer"), NOW));
	}

	@Test
	void recognisesOwnFinalKillsAndBeds() {
		assertTrue(Quests.isOwnFinalKill("Steve was killed by Rago_Player. FINAL KILL!", "Rago_Player"));
		assertTrue(Quests.isOwnFinalKill("Steve was knocked into the void by Rago_Player. FINAL KILL!", "Rago_Player"));
		assertTrue(Quests.isOwnFinalKill("Steve was spooked by Rago_Player's ghost. FINAL KILL!", "Rago_Player"));
		assertFalse(Quests.isOwnFinalKill("Rago_Player was killed by Steve. FINAL KILL!", "Rago_Player"));
		assertFalse(Quests.isOwnFinalKill("Steve was killed by Rago_Player2. FINAL KILL!", "Rago_Player"));
		assertFalse(Quests.isOwnFinalKill("Steve was killed by Rago_Player.", "Rago_Player"));
		assertTrue(Quests.isOwnBed("BED DESTRUCTION > Red Bed was destroyed by Rago_Player!", "Rago_Player"));
		assertFalse(Quests.isOwnBed("BED DESTRUCTION > Your Bed was destroyed by Steve!", "Rago_Player"));
	}
}
