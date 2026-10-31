package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.gui.ui.TextColors;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import gg.rago.netdiag.LatencySampler;
import gg.rago.netdiag.StatusPinger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;

/**
 * Ping to the current server. "Measured" times a fresh round trip on a separate status connection
 * every few seconds, like the server list; "Server" shows the value the server puts in the tab
 * list, which large networks refresh only now and then. Hidden in singleplayer and menus.
 */
public final class PingHud extends TextHudElement {
	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	private final LatencySampler serverPing;
	private final StatusPinger pinger = new StatusPinger();
	private final ChoiceOption source = addOption(new ChoiceOption("ping_source", "measured", "measured", "server"));
	private final IntOption interval = addOption(new IntOption("ping_interval", 10, 3, 60));
	private final BooleanOption showJitter = addOption(new BooleanOption("show_jitter", false));
	private final BooleanOption colorByPing = addOption(new BooleanOption("color_by_ping", true));

	public PingHud(LatencySampler serverPing) {
		super("ping", FeatureClass.INFORMATIONAL, true, Anchor.TOP_LEFT, 4, 26);
		this.serverPing = serverPing;
		interval.showIf(() -> source.is("measured"));
	}

	private LatencySampler active() {
		return source.is("measured") ? pinger.samples() : serverPing;
	}

	@Override
	public void tick() {
		if (!source.is("measured")) {
			pinger.tick(null, null, 0);
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		NetHandlerPlayClient handler = mc.getNetHandler();
		ServerData data = mc.getCurrentServerData();
		SocketAddress remote = handler == null || mc.isIntegratedServerRunning() ? null : handler.getNetworkManager().getRemoteAddress();
		if (!(remote instanceof InetSocketAddress) || data == null) {
			pinger.tick(null, null, 0);
			return;
		}
		String host = data.serverIP;
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}
		pinger.tick((InetSocketAddress) remote, host, interval.get());
	}

	@Override
	protected void onDisable() {
		pinger.tick(null, null, 0);
	}

	@Override
	public boolean hasContent() {
		return active().latest() >= 0;
	}

	@Override
	protected String label(boolean after) {
		return after ? "ms" : "Ping";
	}

	private final Shown shown = new Shown();

	@Override
	protected String value(boolean placeholder) {
		int ms = placeholder ? 42 : active().latest();
		if (!showJitter.get()) {
			return Shown.of(ms);
		}
		double jitter = placeholder ? 3.5 : active().jitterMs();
		if (!shown.same(ms, Shown.key(jitter, 1), 0)) {
			shown.set(ms + " ±" + Shown.fixed(jitter, 1));
		}
		return shown.text();
	}

	@Override
	protected int valueColor(boolean placeholder) {
		if (!colorByPing.get()) {
			return super.valueColor(placeholder);
		}
		int ms = placeholder ? 42 : active().latest();
		int alpha = textColor.get() & 0xFF000000;
		return alpha | 0xFFFFFF & (ms < 80 ? TextColors.argb('a') : ms < 150 ? TextColors.argb('e') : ms < 250 ? TextColors.argb('6') : TextColors.argb('c'));
	}
}
