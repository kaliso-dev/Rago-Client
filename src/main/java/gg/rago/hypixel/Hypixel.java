package gg.rago.hypixel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;
import java.util.UUID;

/** Small facts about being on Hypixel. */
public final class Hypixel {
	private Hypixel() {
	}

	/** Whether the current server is Hypixel, from the address typed into the server list. */
	public static boolean connected() {
		Minecraft mc = Minecraft.getMinecraft();
		if (gg.rago.debug.FakeBedwars.ON && mc.isIntegratedServerRunning()) {
			return true;
		}
		ServerData server = mc.getCurrentServerData();
		if (server == null || mc.isIntegratedServerRunning() || server.serverIP == null) {
			return false;
		}
		// asked many times a frame (every name tag): the address is only read again when it changes
		if (server.serverIP != checkedIp) {
			checkedIp = server.serverIP;
			String ip = checkedIp.toLowerCase(Locale.ROOT);
			int colon = ip.indexOf(':');
			String host = colon >= 0 ? ip.substring(0, colon) : ip;
			checkedHypixel = host.equals("hypixel.net") || host.endsWith(".hypixel.net");
		}
		return checkedHypixel;
	}

	private static String checkedIp;
	private static boolean checkedHypixel;

	/** Hypixel's NPCs and the bots it adds to games have version 2 UUIDs: never players. */
	public static boolean npc(UUID id) {
		return id == null || id.version() == 2;
	}

	/** Real accounts have version 4 UUIDs; Hypixel's NPCs and nicked players do not. */
	public static boolean realPlayer(UUID id) {
		return id != null && id.version() == 4;
	}

	/** A Bedwars star in Hypixel's colours for its prestige, such as "[312✫]". */
	public static String bedwarsStar(int star) {
		String text = "[" + star + "✫]";
		if (star >= 1000) {
			String rainbow = "c6eabd5";
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < text.length(); i++) {
				b.append('§').append(rainbow.charAt(i % rainbow.length())).append(text.charAt(i));
			}
			return b.toString();
		}
		String colors = "7f6b234d95";
		return "§" + colors.charAt(Math.max(0, star) / 100) + text;
	}
}
