package gg.rago.debug;

import gg.rago.core.RagoClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Developer sampling, active only with {@code -Drago.bench.sample=<seconds>}: looks at the render
 * thread's stack every 2 ms and logs, every {@code seconds}, the methods it was found in most
 * often: once as the innermost Minecraft, Forge or mod method (where the time goes), once for any
 * method on the stack (what the time is spent under). Calls into OpenGL count for the method that
 * made them, so their weight shows next to the Java code around them.
 */
public final class StackSampler implements Runnable {
	private static final int TOP = 40;
	private final Thread target;
	private final long windowMs;
	private final Map<String, int[]> self = new HashMap<>();
	private final Map<String, int[]> total = new HashMap<>();
	private int samples;

	private StackSampler(Thread target, long windowMs) {
		this.target = target;
		this.windowMs = windowMs;
	}

	/** Starts sampling the calling thread when the property is set. */
	public static void startIfAsked() {
		int seconds = Integer.getInteger("rago.bench.sample", 0);
		if (seconds <= 0) {
			return;
		}
		Thread t = new Thread(new StackSampler(Thread.currentThread(), seconds * 1000L), "Rago stack sampler");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void run() {
		long windowStart = System.currentTimeMillis();
		while (true) {
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				return;
			}
			StackTraceElement[] stack = target.getStackTrace();
			if (stack.length == 0) {
				continue;
			}
			samples++;
			boolean selfDone = false;
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (StackTraceElement e : stack) {
				String m = e.getClassName() + "." + e.getMethodName();
				if (!selfDone && !m.startsWith("org.lwjgl.") && !m.startsWith("java.") && !m.startsWith("sun.")) {
					self.computeIfAbsent(m, k -> new int[1])[0]++;
					selfDone = true;
				}
				if (seen.add(m)) {
					total.computeIfAbsent(m, k -> new int[1])[0]++;
				}
			}
			long now = System.currentTimeMillis();
			if (now - windowStart >= windowMs) {
				log("self", self);
				log("total", total);
				self.clear();
				total.clear();
				samples = 0;
				windowStart = now;
			}
		}
	}

	private void log(String kind, Map<String, int[]> counts) {
		StringBuilder b = new StringBuilder("[sample] ").append(kind).append(" (").append(samples).append(" samples)");
		counts.entrySet().stream().sorted((x, y) -> Integer.compare(y.getValue()[0], x.getValue()[0])).limit(TOP)
			.forEach(e -> b.append(String.format(java.util.Locale.ROOT, "\n  %5.1f%% %s", 100.0 * e.getValue()[0] / samples, e.getKey())));
		RagoClient.LOG.info(b.toString());
	}
}
