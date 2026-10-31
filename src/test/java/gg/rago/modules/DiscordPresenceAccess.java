package gg.rago.modules;

/** Lets tests in other packages reach DiscordPresence's package-private helpers. */
public final class DiscordPresenceAccess {
	private DiscordPresenceAccess() {
	}

	public static String server(String ip, boolean address) {
		return DiscordPresence.server(ip, address);
	}
}
