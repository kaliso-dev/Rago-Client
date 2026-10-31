package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import gg.rago.input.InputState;

/** Attack-button presses in the last second, optionally with the use button. */
public final class CpsHud extends TextHudElement {
	@Override
	public Category category() {
		return Category.PVP;
	}

	private final InputState input;
	private final BooleanOption showRight = addOption(new BooleanOption("show_right_click", true));

	public CpsHud(InputState input) {
		super("cps", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 37);
		this.input = input;
	}

	@Override
	protected String label(boolean after) {
		return after ? "CPS" : "CPS";
	}

	private final Shown shown = new Shown();

	@Override
	protected String value(boolean placeholder) {
		long now = System.nanoTime();
		int left = placeholder ? 9 : input.attackCps(now);
		if (!showRight.get()) {
			return Shown.of(left);
		}
		int right = placeholder ? 4 : input.useCps(now);
		if (!shown.same(left, right, 0)) {
			shown.set(left + " | " + right);
		}
		return shown.text();
	}
}
