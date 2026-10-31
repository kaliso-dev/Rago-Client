package gg.rago.debug;

import gg.rago.core.RagoClient;

import java.util.Locale;

/**
 * Developer timing, active only with {@code -Drago.bench.frame=true}: logs every 5 seconds how
 * many microseconds per frame Rago's own per-frame work takes on the render thread.
 */
public final class FrameProfile {
	public static final boolean ON = Boolean.getBoolean("rago.bench.frame");
	public static final int CULLING = 0;
	public static final int HUD = 1;
	public static final int NAMETAGS = 2;
	public static final int TICK = 3;
	/** Working out the name tags (and the lines above them) as each player is drawn. */
	public static final int NAMES_QUEUED = 4;
	public static final int HITBOXES = 5;
	private static final String[] NAMES = {"culling", "hud", "nametags", "tick", "names", "hitboxes"};
	private static final long[] TOTAL = new long[NAMES.length];
	/** Bytes allocated on the client thread inside each slot (see {@link #start}). */
	private static final long[] BYTES = new long[NAMES.length];
	private static final long[] STARTED = new long[8];
	private static int depth;
	private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();
	/** What reading the counter itself allocates, taken off every reading. */
	private static final long READ_COST = readCost();
	private static long frames;
	private static long windowStart;

	private FrameProfile() {
	}

	/** Per element: time in ns, then the bytes it allocated (-1 when not measured). */
	private static final java.util.Map<String, long[]> ELEMENTS = new java.util.HashMap<>();
	private static final java.util.Map<String, String> TICK_LABELS = new java.util.HashMap<>();

	public static void element(String id, long ns) {
		element(id, ns, -1);
	}

	/** Time and, when {@code bytes} is not negative, the bytes allocated by one element this frame. */
	public static void element(String id, long ns, long bytes) {
		long[] e = ELEMENTS.computeIfAbsent(id, k -> new long[] {0, -1});
		e[0] += ns;
		if (bytes >= 0) {
			e[1] = Math.max(0, e[1]) + bytes;
		}
	}

	/** The bytes allocated so far on this thread, to pass to {@link #bytesSince}. */
	public static long bytes() {
		return allocated();
	}

	/** The bytes allocated on this thread since {@code start} (from {@link #bytes}), less the cost of reading. */
	public static long bytesSince(long start) {
		return Math.max(0, allocated() - start - READ_COST);
	}

	/** "id tick", made once per module so measuring does not allocate what it measures. */
	public static String tickLabel(String id) {
		return TICK_LABELS.computeIfAbsent(id, k -> k + " tick");
	}

	/** The start of a measured slot: the time, and (kept here) the bytes allocated so far. */
	public static long start() {
		if (depth < STARTED.length) {
			STARTED[depth] = allocated();
		}
		depth++;
		return System.nanoTime();
	}

	public static void add(int slot, long ns) {
		TOTAL[slot] += ns;
		if (depth > 0) {
			depth--;
			if (depth < STARTED.length) {
				BYTES[slot] += Math.max(0, allocated() - STARTED[depth] - READ_COST);
			}
		}
	}

	private static long allocated() {
		if (THREADS instanceof com.sun.management.ThreadMXBean) {
			return ((com.sun.management.ThreadMXBean) THREADS).getThreadAllocatedBytes(Thread.currentThread().getId());
		}
		return 0;
	}

	private static long readCost() {
		long min = Long.MAX_VALUE;
		for (int i = 0; i < 20; i++) {
			long a = allocated();
			long b = allocated();
			min = Math.min(min, b - a);
		}
		return Math.max(0, min);
	}

	/** Called once per frame. */
	public static void frame() {
		long now = System.nanoTime();
		if (windowStart == 0) {
			windowStart = now;
			return;
		}
		frames++;
		if (now - windowStart < 5_000_000_000L) {
			return;
		}
		StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "[profile] %.2f ms/frame", (now - windowStart) / 1e6 / frames));
		for (int i = 0; i < NAMES.length; i++) {
			b.append(String.format(Locale.ROOT, ", %s %.1f us %d B", NAMES[i], TOTAL[i] / 1000.0 / frames, BYTES[i] / frames));
			TOTAL[i] = 0;
			BYTES[i] = 0;
		}
		final long n = frames;
		ELEMENTS.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(24)
			.forEach(e -> {
				b.append(String.format(Locale.ROOT, " | %s %.1f", e.getKey(), e.getValue()[0] / 1000.0 / n));
				if (e.getValue()[1] >= 0) {
					b.append(' ').append(e.getValue()[1] / n).append('B');
				}
			});
		ELEMENTS.clear();
		RagoClient.LOG.info(b.toString());
		frames = 0;
		windowStart = now;
	}
}
