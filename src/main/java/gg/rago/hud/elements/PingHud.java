package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import gg.rago.netdiag.LatencySampler;

import java.util.Locale;

/** Server-reported ping from the tab list. Hidden when there is no server (singleplayer, menus). */
public final class PingHud extends TextHudElement {
	private final LatencySampler ping;
	private final BooleanOption showJitter = addOption(new BooleanOption("show_jitter", true));

	public PingHud(LatencySampler ping) {
		super("ping", FeatureClass.INFORMATIONAL, true, Anchor.TOP_LEFT, 4, 26);
		this.ping = ping;
	}

	@Override
	public boolean hasContent() {
		return ping.latest() >= 0;
	}

	@Override
	protected String text(boolean placeholder) {
		int ms = placeholder ? 42 : ping.latest();
		double jitter = placeholder ? 3.5 : ping.jitterMs();
		return showJitter.get()
			? String.format(Locale.ROOT, "%d ms  jitter %.1f", ms, jitter)
			: ms + " ms";
	}
}
