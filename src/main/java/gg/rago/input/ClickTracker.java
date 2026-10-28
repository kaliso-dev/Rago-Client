package gg.rago.input;

/**
 * Counts presses in a sliding one-second window. Fed from {@code KeyBindingMixin}, which
 * only observes the press events vanilla already handles; nothing is changed or sent.
 * All methods run on the client thread.
 */
public final class ClickTracker {
	public static final long WINDOW_NS = 1_000_000_000L;
	/** More presses than this in one second are not humanly possible; older ones are overwritten. */
	private static final int CAPACITY = 64;

	private final long[] times = new long[CAPACITY];
	private int head;
	private int size;

	public void press(long nowNs) {
		times[head] = nowNs;
		head = (head + 1) % CAPACITY;
		if (size < CAPACITY) {
			size++;
		}
	}

	/** Presses within the last second before {@code nowNs}. */
	public int cps(long nowNs) {
		int count = 0;
		for (int i = 0; i < size; i++) {
			long t = times[(head - 1 - i + CAPACITY) % CAPACITY];
			if (nowNs - t >= WINDOW_NS) {
				break;
			}
			count++;
		}
		return count;
	}

	public void clear() {
		size = 0;
	}
}
