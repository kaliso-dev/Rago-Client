package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.discord.DiscordClient;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;

import java.util.Locale;

/**
 * Discord Rich Presence: your Discord profile shows "Playing Rago Client" with where you play
 * (Hypixel, Minemen Club and the other big PvP networks by name; other servers only as
 * "Multiplayer" unless you choose to show their address), what you do there (a Bed Wars game, its
 * waiting room or the lobby) and for how long. Talks only to the Discord app on this computer.
 */
public final class DiscordPresence extends Module {
	/**
	 * The Rago Client application in Discord's developer portal: its name is what friends read
	 * ("Playing Rago Client"), its art asset "rago" is the picture. Rago shows nothing on Discord
	 * while it is not set.
	 */
	public static final String APP_ID = System.getProperty("rago.discord.app", "");

	/** Networks shown by name, by the end of their address. */
	private static final String[][] NETWORKS = {
		{"hypixel.net", "Hypixel"},
		{"minemen.club", "Minemen Club"},
		{"pvp.land", "PvP Land"},
		{"blocksmc.com", "BlocksMC"},
		{"pika-network.net", "Pika Network"},
		{"jartexnetwork.com", "Jartex Network"},
		{"bedwarspractice.club", "Bedwars Practice"},
		{"mush.com.br", "Mush"},
		{"universocraft.com", "UniversoCraft"},
	};

	private final BooleanOption showServer = addOption(new BooleanOption("discord_server", true));
	private final BooleanOption showAddress = addOption(new BooleanOption("discord_address", false));
	private final BooleanOption showGame = addOption(new BooleanOption("discord_game", true));
	private final BooleanOption showTime = addOption(new BooleanOption("discord_time", true));
	private final DiscordClient client = new DiscordClient(APP_ID);
	private final String version;
	private int ticks;
	private String shown;
	private long since;

	public DiscordPresence(String version) {
		super("discord", FeatureClass.INFORMATIONAL, true);
		this.version = version;
		showAddress.showIf(showServer::get);
	}

	/** Whether Rago has a Discord application to show the activity as. */
	public static boolean available() {
		return new DiscordClient(APP_ID).configured();
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	@Override
	protected void onDisable() {
		client.stop();
		shown = null;
	}

	@Override
	public void tick() {
		if (ticks++ % 20 != 0) {
			return;
		}
		client.start(); // does nothing once started; the setting turning it off stops it
		Minecraft mc = Minecraft.getMinecraft();
		String details;
		String state = null;
		if (mc.theWorld == null) {
			details = I18n.format("rago.discord.menus");
		} else if (mc.isSingleplayer()) {
			details = I18n.format("rago.discord.singleplayer");
		} else {
			ServerData server = mc.getCurrentServerData();
			details = showServer.get() ? server(server == null ? null : server.serverIP, showAddress.get()) : null;
			if (details == null) {
				details = I18n.format("rago.discord.multiplayer");
			}
			if (showGame.get() && Hypixel.connected() && BedwarsGame.inBedwars()) {
				details = details + " · Bed Wars";
				state = I18n.format(BedwarsGame.playing() ? "rago.discord.bedwars_game"
					: BedwarsGame.waitingRoom() ? "rago.discord.bedwars_waiting" : "rago.discord.bedwars_lobby");
			}
		}
		String key = details + '|' + state;
		if (!key.equals(shown)) {
			shown = key;
			since = System.currentTimeMillis() / 1000;
		}
		client.want(new DiscordClient.Activity(details, state, showTime.get() ? since : 0, "Rago Client " + version));
	}

	/**
	 * The name shown for a server address: a known network by its name, another server by its
	 * address only when {@code address} is on, otherwise null (shown as "Multiplayer").
	 */
	static String server(String ip, boolean address) {
		if (ip == null || ip.trim().isEmpty()) {
			return null;
		}
		String host = ip.trim().toLowerCase(Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		for (String[] n : NETWORKS) {
			if (host.equals(n[0]) || host.endsWith("." + n[0])) {
				return n[1];
			}
		}
		return address ? host : null;
	}
}
