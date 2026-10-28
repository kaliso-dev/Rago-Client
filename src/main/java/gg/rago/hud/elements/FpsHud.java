package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import gg.rago.perf.FrameStats;
import gg.rago.perf.FrameTimer;

import java.util.Locale;

/** Average FPS over the last 1,000 frames, optionally with the 1% low. */
public final class FpsHud extends TextHudElement {
	private final FrameTimer frames;
	private final BooleanOption showLow = addOption(new BooleanOption("show_one_percent_low", true));

	public FpsHud(FrameTimer frames) {
		super("fps", FeatureClass.INFORMATIONAL, true, Anchor.TOP_LEFT, 4, 4);
		this.frames = frames;
	}

	@Override
	protected String text(boolean placeholder) {
		double avg = placeholder ? 860 : frames.stats().avgFps;
		double low = placeholder ? 225 : frames.stats().onePercentLowFps;
		if (!placeholder && frames.stats() == FrameStats.EMPTY) {
			return "-- FPS";
		}
		return showLow.get()
			? String.format(Locale.ROOT, "%.0f FPS  1%% low %.0f", avg, low)
			: String.format(Locale.ROOT, "%.0f FPS", avg);
	}
}
