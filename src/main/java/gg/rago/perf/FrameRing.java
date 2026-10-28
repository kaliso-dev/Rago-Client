package gg.rago.perf;

/**
 * Fixed-size ring buffer of frame durations. No allocation after construction, so it
 * can be written from the render loop every frame.
 */
public final class FrameRing {
	private final long[] values;
	private int next;
	private int size;

	public FrameRing(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.values = new long[capacity];
	}

	public void add(long value) {
		values[next] = value;
		next = (next + 1) % values.length;
		if (size < values.length) {
			size++;
		}
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return values.length;
	}

	/** Copies the stored values, oldest first, into {@code out}; returns how many were copied. */
	public int copyTo(long[] out) {
		int start = (next - size + values.length) % values.length;
		for (int i = 0; i < size; i++) {
			out[i] = values[(start + i) % values.length];
		}
		return size;
	}

	public void clear() {
		next = 0;
		size = 0;
	}
}
