package gg.rago.hud.elements;

import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import gg.rago.perf.FrameStats;
import gg.rago.perf.FrameTimer;

import java.util.Locale;

/** Typical (p50) and worst-1% (p99) frame time in milliseconds. */
public final class FrameTimeHud extends TextHudElement {
	private final FrameTimer frames;

	public FrameTimeHud(FrameTimer frames) {
		super("frame_time", FeatureClass.INFORMATIONAL, true, Anchor.TOP_LEFT, 4, 15);
		this.frames = frames;
	}

	@Override
	protected String text(boolean placeholder) {
		FrameStats s = frames.stats();
		double p50 = placeholder ? 1.16 : s.p50Ms;
		double p99 = placeholder ? 3.31 : s.p99Ms;
		return String.format(Locale.ROOT, "frame %.2f ms  p99 %.2f ms", p50, p99);
	}
}
