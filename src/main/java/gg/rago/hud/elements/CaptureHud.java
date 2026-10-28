package gg.rago.hud.elements;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import gg.rago.perf.FrameTimer;

import java.util.function.Supplier;

/** Shows "REC 12s / 60s" while a frame capture runs, then the save result for a few seconds. */
public final class CaptureHud extends TextHudElement {
	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	private static final long STATUS_VISIBLE_NS = 5_000_000_000L;

	private final FrameTimer frames;
	private final Supplier<String> status;
	private final Supplier<Long> statusTimeNs;

	public CaptureHud(FrameTimer frames, Supplier<String> status, Supplier<Long> statusTimeNs) {
		super("capture", FeatureClass.INFORMATIONAL, true, Anchor.TOP_RIGHT, -4, 4, 0xFFFF5555);
		this.frames = frames;
		this.status = status;
		this.statusTimeNs = statusTimeNs;
	}

	@Override
	public boolean hasContent() {
		return frames.isCapturing() || (!status.get().isEmpty() && System.nanoTime() - statusTimeNs.get() < STATUS_VISIBLE_NS);
	}

	@Override
	protected String text(boolean placeholder) {
		if (placeholder) {
			return "REC 12s / 60s";
		}
		if (frames.isCapturing()) {
			return "REC " + frames.capture().elapsedNs(System.nanoTime()) / 1_000_000_000L + "s / 60s";
		}
		return status.get();
	}
}
