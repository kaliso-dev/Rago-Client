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
	private static long frames;
	private static long windowStart;

	private FrameProfile() {
	}

	private static final java.util.Map<String, long[]> ELEMENTS = new java.util.HashMap<>();

	public static void element(String id, long ns) {
		ELEMENTS.computeIfAbsent(id, k -> new long[1])[0] += ns;
	}

	public static void add(int slot, long ns) {
		TOTAL[slot] += ns;
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
			b.append(String.format(Locale.ROOT, ", %s %.1f us", NAMES[i], TOTAL[i] / 1000.0 / frames));
			TOTAL[i] = 0;
		}
		final long n = frames;
		ELEMENTS.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(8)
			.forEach(e -> b.append(String.format(Locale.ROOT, " | %s %.1f", e.getKey(), e.getValue()[0] / 1000.0 / n)));
		ELEMENTS.clear();
		RagoClient.LOG.info(b.toString());
		frames = 0;
		windowStart = now;
	}
}
