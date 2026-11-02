package gg.rago.hypixel;

/**
 * Hypixel's Bed Wars stars from Bed Wars experience. Every prestige (100 stars) costs 487,000
 * experience: its first four stars 500, 1,000, 2,000 and 3,500, each of the 96 others 5,000.
 */
public final class BedwarsLevel {
	public static final long PRESTIGE_XP = 487_000;
	private static final int[] FIRST_STARS = {500, 1000, 2000, 3500};
	private static final int STAR = 5000;

	private BedwarsLevel() {
	}

	/** The experience the star after {@code star} costs. */
	public static int cost(int star) {
		int inPrestige = Math.floorMod(star, 100);
		return inPrestige < FIRST_STARS.length ? FIRST_STARS[inPrestige] : STAR;
	}

	/** The star reached with {@code xp} experience. */
	public static int star(long xp) {
		return (int) split(xp)[0];
	}

	/** The experience gathered towards the next star. */
	public static long into(long xp) {
		return split(xp)[1];
	}

	/** The experience still needed for the next star. */
	public static long toNext(long xp) {
		long[] s = split(xp);
		return cost((int) s[0]) - s[1];
	}

	/** How far into the current star, 0 to 1. */
	public static double progress(long xp) {
		long[] s = split(xp);
		return s[1] / (double) cost((int) s[0]);
	}

	/** {star, experience into it}. */
	private static long[] split(long xp) {
		long x = Math.max(0, xp);
		long prestiges = x / PRESTIGE_XP;
		long rest = x % PRESTIGE_XP;
		int star = 0;
		while (star < 99 && rest >= cost(star)) {
			rest -= cost(star);
			star++;
		}
		return new long[] {prestiges * 100 + star, rest};
	}
}
