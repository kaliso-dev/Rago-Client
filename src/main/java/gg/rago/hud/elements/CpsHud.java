package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import gg.rago.input.InputState;

/** Attack-button presses in the last second, optionally with the use button. */
public final class CpsHud extends TextHudElement {
	private final InputState input;
	private final BooleanOption showRight = addOption(new BooleanOption("show_right_click", true));

	public CpsHud(InputState input) {
		super("cps", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 37);
		this.input = input;
	}

	@Override
	protected String text(boolean placeholder) {
		long now = System.nanoTime();
		int left = placeholder ? 9 : input.attackCps(now);
		if (!showRight.get()) {
			return left + " CPS";
		}
		int right = placeholder ? 4 : input.useCps(now);
		return left + " | " + right + " CPS";
	}
}
