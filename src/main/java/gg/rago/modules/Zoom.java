package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.settings.KeyBinding;

/**
 * Narrows the field of view while the zoom key is held, like OptiFine's zoom. The mouse wheel
 * changes the zoom level instead of the hotbar slot while zooming. Purely visual.
 */
public final class Zoom extends Module {
	private static final float SMOOTH_PER_SECOND = 14f;

	private final KeyBinding key;
	private final IntOption factor = addOption(new IntOption("zoom_factor", 4, 2, 10));
	private final BooleanOption smooth = addOption(new BooleanOption("smooth_zoom", true));
	private final BooleanOption scroll = addOption(new BooleanOption("scroll_to_zoom", true));

	/** Zoom level adjusted with the wheel during the current hold; reset to the option on release. */
	private float target = 1f;
	private float current = 1f;
	private float scrolled;
	private long lastNs;

	public Zoom(KeyBinding key) {
		super("zoom", FeatureClass.VISUAL, true);
		this.key = key;
	}

	public boolean isZooming() {
		return isEnabled() && key.isKeyDown();
	}

	/** Called with the FOV the world is about to be rendered with; returns the FOV to use. */
	public float apply(float fov) {
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0 : Math.min(0.1f, (now - lastNs) / 1e9f);
		lastNs = now;
		if (isZooming()) {
			target = scrolled > 0 ? scrolled : factor.get();
		} else {
			target = 1f;
			scrolled = 0;
		}
		current = smooth.get() ? approach(current, target, dt) : target;
		return current <= 1.0001f ? fov : fov / current;
	}

	static float approach(float current, float target, float dt) {
		float t = 1f - (float) Math.exp(-SMOOTH_PER_SECOND * dt);
		float next = current + (target - current) * t;
		return Math.abs(next - target) < 0.01f ? target : next;
	}

	/** Returns true when the wheel was used for zooming, so the hotbar should not scroll. */
	public boolean onScroll(int direction) {
		if (!isZooming() || !scroll.get()) {
			return false;
		}
		float base = scrolled > 0 ? scrolled : factor.get();
		scrolled = clampZoom(base * (direction > 0 ? 1.25f : 0.8f));
		return true;
	}

	static float clampZoom(float z) {
		return Math.max(1.5f, Math.min(40f, z));
	}
}
