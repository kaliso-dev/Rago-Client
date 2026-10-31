package gg.rago.hud.elements;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import gg.rago.perf.FrameStats;
import gg.rago.perf.FrameTimer;


/** Typical (p50) and worst-1% (p99) frame time in milliseconds. */
public final class FrameTimeHud extends TextHudElement {
	/** The text shown, made again only when it changes. */
	private final Shown shown = new Shown();
	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	private final FrameTimer frames;

	public FrameTimeHud(FrameTimer frames) {
		super("frame_time", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 15);
		this.frames = frames;
	}

	@Override
	protected String label(boolean after) {
		return after ? "ms" : "Frame";
	}

	@Override
	protected String value(boolean placeholder) {
		FrameStats s = frames.stats();
		double p50 = placeholder ? 1.16 : s.p50Ms;
		double p99 = placeholder ? 3.31 : s.p99Ms;
		if (!shown.same(Shown.key(p50, 2), Shown.key(p99, 2), 0)) {
			shown.set(Shown.fixed(p50, 2) + " \u00b7 p99 " + Shown.fixed(p99, 2));
		}
		return shown.text();
	}
}
