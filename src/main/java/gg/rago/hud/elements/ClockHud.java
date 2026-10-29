package gg.rago.hud.elements;

import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** The time on your computer's clock. */
public final class ClockHud extends TextHudElement {
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a");

	private final IntOption format = addOption(new IntOption("time_format", 0, 0, 1));

	public ClockHud() {
		super("clock", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 70);
	}

	@Override
	protected String value(boolean placeholder) {
		return LocalTime.now().format(format.get() == 1 ? H12 : H24);
	}
}
