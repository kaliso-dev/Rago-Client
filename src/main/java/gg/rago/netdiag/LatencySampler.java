package gg.rago.netdiag;

/**
 * Rolling window of server-reported latency samples (milliseconds).
 *
 * <p>The value comes from the player's own tab-list entry, which the server fills in from
 * its keep-alive round trips. Rago sends nothing to measure it. How often the server
 * refreshes it, and whether it smooths it, is up to the server.
 */
public final class LatencySampler {
	public static final int WINDOW = 60;

	private final int[] samples = new int[WINDOW];
	private int next;
	private int size;
	private int latest = -1;

	/** Adds a sample; negative values mean "unknown" (not connected, singleplayer) and clear the window. */
	public void add(int pingMs) {
		if (pingMs < 0) {
			reset();
			return;
		}
		latest = pingMs;
		samples[next] = pingMs;
		next = (next + 1) % WINDOW;
		if (size < WINDOW) {
			size++;
		}
	}

	public void reset() {
		latest = -1;
		next = 0;
		size = 0;
	}

	public int latest() {
		return latest;
	}

	public int size() {
		return size;
	}

	/** Population standard deviation of the window, or 0 with fewer than two samples. */
	public double jitterMs() {
		if (size < 2) {
			return 0;
		}
		double mean = 0;
		for (int i = 0; i < size; i++) {
			mean += samples[i];
		}
		mean /= size;
		double var = 0;
		for (int i = 0; i < size; i++) {
			double d = samples[i] - mean;
			var += d * d;
		}
		return Math.sqrt(var / size);
	}
}
