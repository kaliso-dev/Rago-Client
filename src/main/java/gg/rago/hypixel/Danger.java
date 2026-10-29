package gg.rago.hypixel;

/**
 * Rago's own 1 to 5 danger level for a Bed Wars player, from their final kill/death ratio (which
 * weighs most) and their star. 1 is a newcomer, 5 a player to deal with first.
 */
public final class Danger {
	/** Chat colour codes by level, 1 to 5: grey, green, yellow, gold, red. */
	private static final char[] COLORS = {'7', '7', 'a', 'e', '6', 'c'};

	private Danger() {
	}

	public static int fkdrScore(double fkdr) {
		return fkdr < 1 ? 1 : fkdr < 2 ? 2 : fkdr < 4 ? 3 : fkdr < 8 ? 4 : 5;
	}

	public static int starScore(int star) {
		return star < 100 ? 1 : star < 300 ? 2 : star < 600 ? 3 : star < 1000 ? 4 : 5;
	}

	public static int level(double fkdr, int star) {
		int l = Math.round(0.65f * fkdrScore(fkdr) + 0.35f * starScore(Math.max(0, star)));
		return Math.max(1, Math.min(5, l));
	}

	public static int level(HypixelApi.Stats s) {
		return level(s.fkdr(), s.star);
	}

	/** "§c" and the like, for a level (or for {@link #fkdrScore}). */
	public static String color(int level) {
		return "§" + COLORS[Math.max(0, Math.min(5, level))];
	}

	/** A ratio as Bed Wars players write it: one decimal, none from 100. */
	public static String ratio(double r) {
		return r >= 100 ? Integer.toString((int) r) : String.format(java.util.Locale.ROOT, "%.1f", r);
	}
}
