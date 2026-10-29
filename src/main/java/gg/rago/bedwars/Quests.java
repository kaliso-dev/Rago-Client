package gg.rago.bedwars;

import gg.rago.hypixel.HypixelApi;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Your Hypixel Bed Wars daily and weekly quests: the progress the Hypixel API last gave, plus what
 * Rago has seen happen since in the chat and titles (your final kills, the beds you broke, games
 * won and played), so the numbers move during the game instead of after it.
 */
public final class Quests {
	/** What a quest counts, and how Rago sees it happen; NONE is only known from the API. */
	public enum Kind { FINAL_KILL, BED, WIN, GAME, NONE }

	/** A Bed Wars quest, as listed by the API's resources/quests. */
	public static final class Quest {
		public final String id;
		public final boolean weekly;
		public final int target;
		public final Kind kind;

		Quest(String id, boolean weekly, int target, Kind kind) {
			this.id = id;
			this.weekly = weekly;
			this.target = target;
			this.kind = kind;
		}
	}

	public static final Quest[] ALL = {
		new Quest("bedwars_daily_win", false, 1, Kind.WIN),
		new Quest("bedwars_daily_one_more", false, 3, Kind.GAME),
		new Quest("bedwars_daily_bed_breaker", false, 3, Kind.BED),
		new Quest("bedwars_daily_final_killer", false, 15, Kind.FINAL_KILL),
		new Quest("bedwars_weekly_bed_elims", true, 25, Kind.BED),
		new Quest("bedwars_weekly_dream_win", true, 10, Kind.NONE),
		new Quest("bedwars_weekly_challenges_win", true, 5, Kind.NONE),
		new Quest("bedwars_weekly_final_killer", true, 150, Kind.FINAL_KILL),
	};

	/** Hypixel's daily quests start again at midnight, New York time. */
	private static final ZoneId HYPIXEL_TIME = ZoneId.of("America/New_York");
	private static final long WEEK_MS = 7 * 24 * 3600_000L;

	/** Where a quest stands. */
	public enum State { NOT_STARTED, RUNNING, DONE }

	private HypixelApi.Stats base;
	private final Map<Kind, Integer> since = new EnumMap<>(Kind.class);
	/** The progress shown before the last API answer, kept when the API is behind; by quest, with its start. */
	private final Map<String, long[]> floors = new HashMap<>();

	/** A new answer from the API; what was counted until then is now in its numbers (or kept as a floor). */
	public void update(HypixelApi.Stats stats, long now) {
		if (stats == null || stats == base) {
			return;
		}
		if (base != null) {
			for (Quest q : ALL) {
				HypixelApi.Quest old = base.quests().get(q.id);
				if (old != null && old.started > 0) {
					floors.put(q.id, new long[] {old.started, progress(q, now)});
				}
			}
		}
		base = stats;
		since.clear();
	}

	public boolean known() {
		return base != null;
	}

	public void count(Kind kind) {
		since.merge(kind, 1, Integer::sum);
	}

	public State state(Quest q, long now) {
		HypixelApi.Quest s = base == null ? null : base.quests().get(q.id);
		if (s == null) {
			return State.NOT_STARTED;
		}
		long reset = q.weekly ? now - WEEK_MS : lastDailyReset(now);
		if (s.started > 0 && (q.weekly || s.started >= reset)) {
			return progress(q, now) >= q.target ? State.DONE : State.RUNNING;
		}
		return s.completed >= reset ? State.DONE : State.NOT_STARTED;
	}

	/** How far along a running quest is, at most its target. */
	public int progress(Quest q, long now) {
		HypixelApi.Quest s = base == null ? null : base.quests().get(q.id);
		if (s == null || s.started == 0) {
			return 0;
		}
		int api = 0;
		for (int v : s.progress.values()) {
			api = Math.max(api, v); // each Bed Wars quest has one objective (its id does not always match)
		}
		int n = api + (q.kind == Kind.NONE ? 0 : since.getOrDefault(q.kind, 0));
		long[] floor = floors.get(q.id);
		if (floor != null && floor[0] == s.started) {
			n = (int) Math.max(n, floor[1]);
		}
		return Math.min(n, q.target);
	}

	static long lastDailyReset(long now) {
		ZonedDateTime t = Instant.ofEpochMilli(now).atZone(HYPIXEL_TIME);
		return t.toLocalDate().atStartOfDay(HYPIXEL_TIME).toInstant().toEpochMilli();
	}

	// ---- what the chat says ---------------------------------------------------------------------

	/** "Victim was killed by You. FINAL KILL!": a final kill of yours, with the colours removed. */
	public static boolean isOwnFinalKill(String line, String name) {
		if (!line.endsWith("FINAL KILL!") || line.startsWith(name + " ")) {
			return false;
		}
		return mentions(line.substring(line.indexOf(' ') + 1), name);
	}

	/** "BED DESTRUCTION > Red Bed was destroyed by You!": a bed you broke. */
	public static boolean isOwnBed(String line, String name) {
		return line.startsWith("BED DESTRUCTION") && mentions(line, name);
	}

	private static boolean mentions(String text, String name) {
		return Pattern.compile("(^|[^A-Za-z0-9_])" + Pattern.quote(name) + "($|[^A-Za-z0-9_])").matcher(text).find();
	}
}
