package gg.rago.gui.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Small animation helpers for the menus. Values glide towards a target over time, frame-rate
 * independent. When menu animations are off every value jumps straight to its target.
 */
public final class Anim {
	private static boolean enabled = true;
	private static final Map<Object, Value> VALUES = new HashMap<>();

	private static final class Value {
		float v;
		long t;

		Value(float v, long t) {
			this.v = v;
			this.t = t;
		}
	}

	private Anim() {
	}

	public static void setEnabled(boolean on) {
		enabled = on;
	}

	public static boolean enabled() {
		return enabled;
	}

	/**
	 * Moves the value stored under {@code key} towards {@code target} and returns it. It covers
	 * about 95% of the distance in {@code ms} milliseconds. New keys start at {@code target}.
	 */
	public static float to(Object key, float target, float ms) {
		long now = System.nanoTime();
		Value v = VALUES.get(key);
		if (v == null || !enabled) {
			VALUES.put(key, new Value(target, now));
			return target;
		}
		float dt = (now - v.t) / 1_000_000f;
		v.t = now;
		float k = 1f - (float) Math.exp(-3.0 * Math.min(dt, 250f) / Math.max(1f, ms));
		v.v += (target - v.v) * k;
		if (Math.abs(target - v.v) < 0.001f) {
			v.v = target;
		}
		return v.v;
	}

	/** Like {@link #to} but starting from {@code from} the first time the key is seen. */
	public static float from(Object key, float from, float target, float ms) {
		if (!VALUES.containsKey(key) && enabled) {
			VALUES.put(key, new Value(from, System.nanoTime()));
		}
		return to(key, target, ms);
	}

	/** Forgets all values, e.g. when a screen closes. */
	public static void clear() {
		VALUES.clear();
	}

	/** Eased progress 0..1 of an animation that started at {@code startNs} and lasts {@code ms}. */
	public static float progress(long startNs, float ms) {
		if (!enabled) {
			return 1f;
		}
		float t = (System.nanoTime() - startNs) / 1_000_000f / ms;
		return easeOut(Math.max(0, Math.min(1, t)));
	}

	public static float easeOut(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
