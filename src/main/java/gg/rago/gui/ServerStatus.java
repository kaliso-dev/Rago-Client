package gg.rago.gui;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The servers on the home screen: their icon, players online and ping, asked with the same status
 * request as the multiplayer list (at most once a minute), and joining them.
 */
final class ServerStatus {
	/** Asked again after a minute. */
	private static final long REFRESH_NS = 60_000_000_000L;

	static final ServerStatus HYPIXEL = new ServerStatus("hypixel", "Hypixel", "mc.hypixel.net");
	static final ServerStatus MINEMEN = new ServerStatus("minemen", "Minemen Club", "eu.minemen.club");
	private static final ServerStatus[] ALL = {HYPIXEL, MINEMEN};

	private static final OldServerPinger PINGER = new OldServerPinger();
	private static final ExecutorService PINGS = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Rago server status");
		t.setDaemon(true);
		return t;
	});

	final String id;
	final String name;
	final String address;
	private final ServerData data;
	private long askedNs;
	private boolean asked;
	private volatile boolean failed;
	private String iconData;
	private ResourceLocation icon;

	private ServerStatus(String id, String name, String address) {
		this.id = id;
		this.name = name;
		this.address = address;
		this.data = new ServerData(name, address, false);
	}

	/** Asks every server for its status unless it answered less than a minute ago. */
	static void refresh() {
		long now = System.nanoTime();
		for (ServerStatus s : ALL) {
			if (s.asked && now - s.askedNs < REFRESH_NS) {
				continue;
			}
			s.asked = true;
			s.askedNs = now;
			s.failed = false;
			// unknown until the server answers (the field starts at 0, and stays there if the address cannot be found)
			s.data.pingToServer = -1;
			PINGS.execute(() -> {
				try {
					PINGER.ping(s.data);
				} catch (Exception | LinkageError e) {
					s.failed = true;
				}
			});
		}
	}

	/** Reads the answers; call once a tick while the home screen is open. */
	static void tick() {
		PINGER.pingPendingNetworks();
		for (ServerStatus s : ALL) {
			s.updateIcon();
		}
	}

	/** Drops the requests still waiting (the home screen closed); they are asked again next time. */
	static void cancel() {
		PINGER.clearPendingNetworks();
		for (ServerStatus s : ALL) {
			if (s.ping() < 0) {
				s.asked = false;
			}
		}
	}

	/** The ping in milliseconds, or -1 while unknown. */
	long ping() {
		return offline() ? -1 : data.pingToServer;
	}

	/** Players online, or -1 while unknown. */
	int players() {
		String info = data.populationInfo;
		if (info == null) {
			return -1;
		}
		String plain = EnumChatFormatting.getTextWithoutFormattingCodes(info);
		int slash = plain.indexOf('/');
		try {
			return Integer.parseInt((slash >= 0 ? plain.substring(0, slash) : plain).trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Players online written for the player's language ("45 312"), or null while unknown. */
	String playersText() {
		int n = players();
		return n < 0 ? null : NumberFormat.getIntegerInstance().format(n);
	}

	/** Whether the server could not be reached. */
	boolean offline() {
		return failed || data.serverMOTD != null && data.serverMOTD.contains("Can't connect");
	}

	/** The server's own icon, or null before it answered (or when it has none). */
	ResourceLocation icon() {
		return icon;
	}

	private void updateIcon() {
		String now = data.getBase64EncodedIconData();
		if (now == null || now.equals(iconData)) {
			return;
		}
		iconData = now;
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(now)));
			if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
				return;
			}
			Minecraft mc = Minecraft.getMinecraft();
			if (icon != null) {
				mc.getTextureManager().deleteTexture(icon);
			}
			icon = new ResourceLocation("rago", "servers/" + id);
			mc.getTextureManager().loadTexture(icon, new DynamicTexture(image));
		} catch (Exception e) {
			RagoClient.LOG.debug("Unreadable icon from {}", address, e);
		}
	}

	/** Joins the server, with the player's own list entry (and its resource pack choice) when it has one. */
	void join(GuiScreen parent) {
		Minecraft mc = Minecraft.getMinecraft();
		ServerData target = new ServerData(name, address, false);
		try {
			ServerList list = new ServerList(mc);
			for (int i = 0; i < list.countServers(); i++) {
				ServerData d = list.getServerData(i);
				if (d.serverIP != null && d.serverIP.trim().equalsIgnoreCase(address)) {
					target = d;
					break;
				}
			}
		} catch (RuntimeException e) {
			RagoClient.LOG.debug("No server list", e);
		}
		mc.displayGuiScreen(new GuiConnecting(parent, mc, target));
	}
}
