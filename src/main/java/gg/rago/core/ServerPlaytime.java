package gg.rago.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How long the player has played on each server, for the home screen's Fast Play button, which
 * joins the one played the most (Hypixel until another one has more time). Counted in seconds
 * while in a world on a server; kept in {@code config/rago-servers.json}, nothing else is sent or
 * stored.
 */
public final class ServerPlaytime {
	public static final String DEFAULT_ADDRESS = "mc.hypixel.net";
	public static final String DEFAULT_NAME = "Hypixel";
	/** Written at most once a minute while playing, and when leaving a server. */
	private static final int SAVE_EVERY_SECONDS = 60;

	private static final Map<String, Long> SECONDS = new HashMap<>();
	private static final Map<String, String> NAMES = new HashMap<>();
	private static Path file;
	private static int ticks;
	private static int unsaved;

	private ServerPlaytime() {
	}

	static void load(Path configDir) {
		file = configDir.resolve("rago-servers.json");
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonObject root = new Gson().fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), JsonObject.class);
			if (root == null || !root.has("servers")) {
				return;
			}
			for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("servers").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				SECONDS.put(e.getKey(), o.has("seconds") ? o.get("seconds").getAsLong() : 0);
				if (o.has("name")) {
					NAMES.put(e.getKey(), o.get("name").getAsString());
				}
			}
		} catch (RuntimeException | IOException e) {
			RagoClient.LOG.warn("Could not read {}", file, e);
		}
	}

	/** Counts a second every 20 ticks spent in a world on a server. */
	static void tick(Minecraft mc) {
		ServerData server = mc.getCurrentServerData();
		if (mc.theWorld == null || server == null || mc.isSingleplayer() || server.serverIP == null) {
			if (unsaved > 0) {
				save();
			}
			ticks = 0;
			return;
		}
		if (++ticks < 20) {
			return;
		}
		ticks = 0;
		String key = key(server.serverIP);
		if (key.isEmpty()) {
			return;
		}
		SECONDS.merge(key, 1L, Long::sum);
		String name = server.serverName;
		if (name != null && !name.trim().isEmpty() && !"Minecraft Server".equals(name)) {
			NAMES.put(key, name.trim());
		}
		if (++unsaved >= SAVE_EVERY_SECONDS) {
			save();
		}
	}

	/** The address played the most, or Hypixel's. */
	public static String favourite() {
		String best = DEFAULT_ADDRESS;
		long most = 0;
		for (Map.Entry<String, Long> e : SECONDS.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	/** A name to show for an address: Hypixel, Minemen Club, the player's own name for it, or the address. */
	public static String name(String address) {
		if (address.endsWith("hypixel.net")) {
			return DEFAULT_NAME;
		}
		if (address.endsWith("minemen.club")) {
			return "Minemen Club";
		}
		String own = NAMES.get(address);
		return own != null ? own : address;
	}

	/** One key per server: lower case, without the default port; every Hypixel address is mc.hypixel.net. */
	static String key(String address) {
		String a = address.trim().toLowerCase(Locale.ROOT);
		if (a.endsWith(":25565")) {
			a = a.substring(0, a.length() - ":25565".length());
		}
		while (a.endsWith(".")) {
			a = a.substring(0, a.length() - 1);
		}
		if (a.equals("hypixel.net") || a.endsWith(".hypixel.net")) {
			return DEFAULT_ADDRESS;
		}
		return a;
	}

	private static void save() {
		unsaved = 0;
		if (file == null) {
			return;
		}
		JsonObject servers = new JsonObject();
		for (Map.Entry<String, Long> e : SECONDS.entrySet()) {
			JsonObject o = new JsonObject();
			o.addProperty("seconds", e.getValue());
			String name = NAMES.get(e.getKey());
			if (name != null) {
				o.addProperty("name", name);
			}
			servers.add(e.getKey(), o);
		}
		JsonObject root = new JsonObject();
		root.add("servers", servers);
		try {
			Files.write(file, new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			RagoClient.LOG.warn("Could not write {}", file, e);
		}
	}
}
