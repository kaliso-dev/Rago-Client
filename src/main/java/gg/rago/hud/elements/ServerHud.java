package gg.rago.hud.elements;

import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** The address of the server you are playing on. Hidden in singleplayer. */
public final class ServerHud extends TextHudElement {
	public ServerHud() {
		super("server_address", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 103);
	}

	@Override
	public boolean hasContent() {
		return address() != null;
	}

	private static String address() {
		Minecraft mc = Minecraft.getMinecraft();
		ServerData s = mc.getCurrentServerData();
		return s == null || mc.isIntegratedServerRunning() ? null : s.serverIP;
	}

	@Override
	protected String text(boolean placeholder) {
		String a = address();
		return a == null ? "mc.hypixel.net" : a;
	}
}
