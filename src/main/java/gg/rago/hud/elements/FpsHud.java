package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import gg.rago.perf.FrameStats;
import gg.rago.perf.FrameTimer;

import java.util.Locale;

/** Average FPS over the last 1,000 frames, optionally with the 1% low. */
public final class FpsHud extends TextHudElement {
	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	private final FrameTimer frames;
	private final BooleanOption showLow = addOption(new BooleanOption("show_one_percent_low", true));

	public FpsHud(FrameTimer frames) {
		super("fps", FeatureClass.INFORMATIONAL, true, Anchor.TOP_LEFT, 4, 4);
		this.frames = frames;
	}

	@Override
	protected String label(boolean after) {
		return after ? "FPS" : "FPS";
	}

	private final Shown shown = new Shown();

	@Override
	protected String value(boolean placeholder) {
		FrameStats stats = frames.stats();
		if (!placeholder && stats == FrameStats.EMPTY) {
			return "--";
		}
		double avg = placeholder ? 860 : stats.avgFps;
		double low = placeholder ? 225 : stats.onePercentLowFps;
		boolean withLow = showLow.get();
		if (!shown.same(Shown.key(avg, 0), withLow ? Shown.key(low, 0) : -1, 0)) {
			shown.set(withLow ? Shown.fixed(avg, 0) + " \u00b7 " + Shown.fixed(low, 0) + " low" : Shown.fixed(avg, 0));
		}
		return shown.text();
	}
}
