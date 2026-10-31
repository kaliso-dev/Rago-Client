package gg.rago.hud.elements;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;

/** Java memory in use out of the maximum the game may use. */
public final class MemoryHud extends TextHudElement {
	/** The text shown, made again only when it changes. */
	private final Shown shown = new Shown();
	public MemoryHud() {
		super("memory", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 81);
	}

	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	@Override
	protected String label(boolean after) {
		return after ? "RAM" : "RAM";
	}

	@Override
	protected String value(boolean placeholder) {
		Runtime rt = Runtime.getRuntime();
		long max = rt.maxMemory() / (1024 * 1024);
		long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
		if (!shown.same(used, max, 0)) {
			shown.set(used * 100 / Math.max(1, max) + "% \u00b7 " + used + "/" + max + " MB");
		}
		return shown.text();
	}
}
