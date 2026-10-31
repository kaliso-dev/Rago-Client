package gg.rago.hud;

import java.util.Locale;

/**
 * The text of a HUD value, made again only when what it shows changes. Values are asked for every
 * frame, and making their text allocates every time (String.format makes a formatter and reads its
 * pattern on each call), while what they show changes a few times a second at most.
 *
 * <pre>
 * if (!shown.same(ms, jitterTenths, flags)) {
 *     shown.set(...);
 * }
 * return shown.text();
 * </pre>
 */
public final class Shown {
	private static final long[] POW10 = {1, 10, 100, 1000, 10_000, 100_000, 1_000_000};
	/** The text of 0 to 1999, made once each: counters, ping, CPS, FPS and durability are numbers like these. */
	private static final String[] SMALL = new String[2000];

	private long a;
	private long b;
	private long c;
	private String text;

	/**
	 * Whether the text kept was made for these keys (numbers that change exactly when the text
	 * would). When it was not, the keys are kept and the caller makes the text with {@link #set}.
	 */
	public boolean same(long a, long b, long c) {
		if (text != null && a == this.a && b == this.b && c == this.c) {
			return true;
		}
		this.a = a;
		this.b = b;
		this.c = c;
		text = null;
		return false;
	}

	public String set(String text) {
		this.text = text;
		return text;
	}

	public String text() {
		return text;
	}

	/** Integer.toString, without making a new string for 0 to 1999. */
	public static String of(int n) {
		if (n >= 0 && n < SMALL.length) {
			String s = SMALL[n];
			if (s == null) {
				s = Integer.toString(n);
				SMALL[n] = s;
			}
			return s;
		}
		return Integer.toString(n);
	}

	/** {@code v} as shown with {@code decimals} digits after the point, as a key for {@link #same}. */
	public static long key(double v, int decimals) {
		return Math.round(v * POW10[clamp(decimals)]);
	}

	/**
	 * {@code v} with {@code decimals} digits after the point (0 to 6), rounded half up like
	 * {@code %.nf} in {@link Locale#ROOT}, without a formatter. A value that rounds to zero reads
	 * "0.0", never "-0.0".
	 */
	public static String fixed(double v, int decimals) {
		int d = clamp(decimals);
		if (Double.isNaN(v) || Double.isInfinite(v) || Math.abs(v) >= 1e12) {
			return String.format(Locale.ROOT, "%." + d + "f", v);
		}
		long scale = POW10[d];
		long n = Math.round(Math.abs(v) * scale);
		StringBuilder out = new StringBuilder(20);
		if (v < 0 && n != 0) {
			out.append('-');
		}
		out.append(n / scale);
		if (d > 0) {
			out.append('.');
			long frac = n % scale;
			for (long p = scale / 10; p > 0; p /= 10) {
				out.append((char) ('0' + frac / p % 10));
			}
		}
		return out.toString();
	}

	private static int clamp(int decimals) {
		return Math.max(0, Math.min(POW10.length - 1, decimals));
	}
}
