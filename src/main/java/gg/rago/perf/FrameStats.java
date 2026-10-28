package gg.rago.perf;

import java.util.Arrays;

/**
 * Summary statistics over a set of frame durations (nanoseconds).
 *
 * <p>Definitions used everywhere in Rago, so numbers from the overlay, the CSV and
 * {@code bench/analyse.py} mean the same thing:
 * <ul>
 *   <li>average FPS = frame count / total elapsed time (not the mean of per-frame FPS)</li>
 *   <li>percentiles use the nearest-rank method on frame times</li>
 *   <li>1% low FPS = 1 / mean of the slowest 1% of frame times (at least one frame)</li>
 * </ul>
 * Pure Java with no Minecraft types so it can be unit tested without the game.
 */
public final class FrameStats {
	public final int count;
	public final double avgFps;
	public final double p50Ms;
	public final double p99Ms;
	public final double maxMs;
	public final double onePercentLowFps;

	private FrameStats(int count, double avgFps, double p50Ms, double p99Ms, double maxMs, double onePercentLowFps) {
		this.count = count;
		this.avgFps = avgFps;
		this.p50Ms = p50Ms;
		this.p99Ms = p99Ms;
		this.maxMs = maxMs;
		this.onePercentLowFps = onePercentLowFps;
	}

	public static final FrameStats EMPTY = new FrameStats(0, 0, 0, 0, 0, 0);

	/**
	 * Computes statistics for the first {@code count} values of {@code framesNs}.
	 * {@code scratch} must hold at least {@code count} values; it is overwritten and
	 * lets the caller avoid allocating every time the overlay refreshes.
	 */
	public static FrameStats compute(long[] framesNs, int count, long[] scratch) {
		if (count <= 0) {
			return EMPTY;
		}
		System.arraycopy(framesNs, 0, scratch, 0, count);
		Arrays.sort(scratch, 0, count);

		long total = 0;
		for (int i = 0; i < count; i++) {
			total += scratch[i];
		}

		int slowest = Math.max(1, count / 100);
		long slowTotal = 0;
		for (int i = count - slowest; i < count; i++) {
			slowTotal += scratch[i];
		}

		return new FrameStats(
			count,
			total > 0 ? count * 1e9 / total : 0,
			percentile(scratch, count, 0.50) / 1e6,
			percentile(scratch, count, 0.99) / 1e6,
			scratch[count - 1] / 1e6,
			slowTotal > 0 ? slowest * 1e9 / slowTotal : 0
		);
	}

	/** Nearest-rank percentile of an ascending-sorted array. */
	static long percentile(long[] sorted, int count, double p) {
		int rank = (int) Math.ceil(p * count);
		return sorted[Math.min(count, Math.max(1, rank)) - 1];
	}
}
