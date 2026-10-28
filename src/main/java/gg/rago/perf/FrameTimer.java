package gg.rago.perf;

/**
 * Receives the start and end of every frame from {@code MinecraftMixin} and keeps the
 * last {@link #WINDOW} frame intervals for the overlay, plus an optional capture.
 * All methods run on the render thread.
 */
public final class FrameTimer {
	public static final int WINDOW = 1000;
	private static final long REFRESH_NS = 500_000_000L;

	private final FrameRing ring = new FrameRing(WINDOW);
	private final long[] copy = new long[WINDOW];
	private final long[] scratch = new long[WINDOW];

	private long lastStartNs = -1;
	private long frameStartNs;
	private long lastRefreshNs;
	private FrameStats stats = FrameStats.EMPTY;
	private FrameCapture capture;

	public void startFrame(long nowNs) {
		frameStartNs = nowNs;
	}

	/** Returns a capture that just finished (full or timed out) so the caller can save it, else null. */
	public FrameCapture endFrame(long nowNs) {
		long workNs = nowNs - frameStartNs;
		FrameCapture finished = null;
		if (lastStartNs >= 0) {
			long intervalNs = frameStartNs - lastStartNs;
			ring.add(intervalNs);
			if (capture != null && !capture.record(nowNs, intervalNs, workNs)) {
				finished = capture;
				capture = null;
			}
		}
		lastStartNs = frameStartNs;

		if (nowNs - lastRefreshNs >= REFRESH_NS) {
			lastRefreshNs = nowNs;
			int n = ring.copyTo(copy);
			stats = FrameStats.compute(copy, n, scratch);
		}
		return finished;
	}

	public FrameStats stats() {
		return stats;
	}

	public boolean isCapturing() {
		return capture != null;
	}

	public FrameCapture capture() {
		return capture;
	}

	public void startCapture(FrameCapture newCapture) {
		capture = newCapture;
	}

	/** Stops the running capture early and returns it, or null if none was running. */
	public FrameCapture stopCapture() {
		FrameCapture c = capture;
		capture = null;
		return c;
	}
}
