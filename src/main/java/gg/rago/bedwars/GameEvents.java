package gg.rago.bedwars;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Bed Wars game events (generator upgrades, beds gone, sudden death, game end) and when they
 * come. Hypixel's sidebar shows only the next one with its countdown ("Diamond II in 5:37"); that
 * line fixes how long the game has run, and the events after it follow the usual schedule.
 */
public final class GameEvents {
	public enum Kind {
		DIAMOND, EMERALD, BED_GONE, SUDDEN_DEATH, GAME_END
	}

	public static final class Event {
		public final Kind kind;
		/** The generator tier the event brings (2 or 3), or 0. */
		public final int tier;
		/** Seconds after the game starts. */
		public final int at;

		Event(Kind kind, int tier, int at) {
			this.kind = kind;
			this.tier = tier;
			this.at = at;
		}
	}

	/** Hypixel's usual schedule: a generator upgrade every 6 minutes, then the beds, then the end. */
	public static final Event[] SCHEDULE = {
		new Event(Kind.DIAMOND, 2, 6 * 60),
		new Event(Kind.EMERALD, 2, 12 * 60),
		new Event(Kind.DIAMOND, 3, 18 * 60),
		new Event(Kind.EMERALD, 3, 24 * 60),
		new Event(Kind.BED_GONE, 0, 30 * 60),
		new Event(Kind.SUDDEN_DEATH, 0, 40 * 60),
		new Event(Kind.GAME_END, 0, 50 * 60),
	};

	/** A countdown at the end of a sidebar line: "5:37", "12:05". */
	private static final Pattern COUNTDOWN = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*$");
	private static final Pattern TIER = Pattern.compile("\\b(IV|III|II|I|[1-4])\\b");
	private static final Pattern BED = word("beds?|lits?|betten?|camas?|letti|letto");
	private static final Pattern END = word("end|ende|fin|fine");
	private static final Pattern MARKS = Pattern.compile("\\p{M}");

	private GameEvents() {
	}

	/** Whether a sidebar line ends with a countdown, the cheap test before {@link #read}. */
	public static boolean hasCountdown(String line) {
		return line != null && COUNTDOWN.matcher(line).find();
	}

	/** What a sidebar line announces: the event (null when unknown) and the seconds left. */
	public static final class Reading {
		public final Event event;
		public final int left;
		/** The line's words before the countdown, as Hypixel wrote them. */
		public final String label;

		Reading(Event event, int left, String label) {
			this.event = event;
			this.left = left;
			this.label = label;
		}

		/** Seconds since the game started, or -1 when the event is not one of the schedule's. */
		public int elapsed() {
			return event == null ? -1 : Math.max(0, event.at - left);
		}
	}

	/**
	 * Reads a sidebar line without colours: "Diamond II in 5:37" (or "Diamant II dans 5:37") gives
	 * Diamond II and 337 seconds. A line that ends with a countdown but names no known event keeps
	 * its words, so it can still be shown. Returns null for a line with no countdown.
	 */
	public static Reading read(String line) {
		if (line == null) {
			return null;
		}
		Matcher m = COUNTDOWN.matcher(line);
		if (!m.find()) {
			return null;
		}
		int left = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
		String label = line.substring(0, m.start()).trim();
		String words = plain(label);
		Kind kind = kind(words);
		if (kind == null) {
			return label.isEmpty() ? null : new Reading(null, left, label);
		}
		int tier = 0;
		if (kind == Kind.DIAMOND || kind == Kind.EMERALD) {
			Matcher t = TIER.matcher(label);
			tier = t.find() ? tierOf(t.group(1)) : 0;
		}
		return new Reading(find(kind, tier), left, label);
	}

	/** The schedule's event of this kind (and tier, when known: the first one otherwise). */
	static Event find(Kind kind, int tier) {
		for (Event e : SCHEDULE) {
			if (e.kind == kind && (tier == 0 || e.tier == tier)) {
				return e;
			}
		}
		return null;
	}

	private static Kind kind(String w) {
		if (w.contains("diamond") || w.contains("diamant")) {
			return Kind.DIAMOND;
		}
		if (w.contains("emerald") || w.contains("emeraude") || w.contains("smaragd") || w.contains("esmeralda") || w.contains("smeraldo")) {
			return Kind.EMERALD;
		}
		if (w.contains("sudden") || w.contains("subit") || w.contains("subita") || w.contains("plotzlich") || w.contains("improvvisa")) {
			return Kind.SUDDEN_DEATH;
		}
		if (BED.matcher(w).find()) {
			return Kind.BED_GONE;
		}
		if (END.matcher(w).find()) {
			return Kind.GAME_END;
		}
		return null;
	}

	/** One of {@code words} standing on its own, not inside a longer word. */
	private static Pattern word(String words) {
		return Pattern.compile("(^|[^\\p{L}])(" + words + ")($|[^\\p{L}])");
	}

	/** Lower case without accents: "Émeraude" reads "emeraude". */
	private static String plain(String s) {
		return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
	}

	private static int tierOf(String s) {
		switch (s) {
			case "I":
			case "1":
				return 1;
			case "II":
			case "2":
				return 2;
			case "III":
			case "3":
				return 3;
			default:
				return 4;
		}
	}

	/** "II" for 2. */
	public static String roman(int n) {
		return n == 1 ? "I" : n == 2 ? "II" : n == 3 ? "III" : n == 4 ? "IV" : String.valueOf(n);
	}

	/** m:ss, as the sidebar shows it. */
	public static String clock(int seconds) {
		int s = Math.max(0, seconds);
		return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
	}
}
