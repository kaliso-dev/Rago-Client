package gg.rago.hypixel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.core.RagoClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;

/**
 * Reads players' network level and Bedwars star from the public Hypixel API with the player's own
 * API key (from developer.hypixel.net). Requests run one at a time on a background thread, at most
 * one per second, and answers are kept for ten minutes, so a lobby full of players costs one
 * request per player. Nothing is sent to the Minecraft server.
 */
public final class HypixelApi {
	private static final String URL_PLAYER = "https://api.hypixel.net/v2/player?uuid=";
	private static final Pattern KEY = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	private static final long FRESH_MS = 10 * 60_000L;
	private static final long RETRY_MS = 2 * 60_000L;
	private static final long GAP_MS = 1_000L;
	private static final int MAX_QUEUE = 64;

	/** What is known about a player; {@code level} and {@code star} are -1 when unknown. */
	public static final class Stats {
		public final double level;
		public final int star;
		final long time;
		final boolean failed;

		Stats(double level, int star, long time, boolean failed) {
			this.level = level;
			this.star = star;
			this.time = time;
			this.failed = failed;
		}
	}

	public enum Status { NO_KEY, OK, BAD_KEY, UNREACHABLE }

	private final Path keyFile;
	private final Map<UUID, Stats> cache = new ConcurrentHashMap<>();
	private final LinkedBlockingDeque<UUID> queue = new LinkedBlockingDeque<>();
	private final Map<UUID, Boolean> queued = new ConcurrentHashMap<>();
	private volatile String key;
	private volatile Status status = Status.NO_KEY;
	private volatile long pausedUntil;
	private Thread worker;

	public HypixelApi(Path configDir) {
		this.keyFile = configDir.resolve("rago-hypixel-key.txt");
		try {
			if (Files.exists(keyFile)) {
				String k = new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim();
				if (isKey(k)) {
					key = k;
					status = Status.OK;
				}
			}
		} catch (IOException e) {
			RagoClient.LOG.warn("Could not read {}", keyFile, e);
		}
	}

	public static boolean isKey(String s) {
		return s != null && KEY.matcher(s).matches();
	}

	public Status status() {
		return status;
	}

	/** Stores a new key (only in this file, never in rago.json or profiles) and forgets failures. */
	public void setKey(String k) throws IOException {
		Files.createDirectories(keyFile.getParent());
		Files.write(keyFile, k.getBytes(StandardCharsets.UTF_8));
		key = k;
		status = Status.OK;
		cache.values().removeIf(s -> s.failed);
		pausedUntil = 0;
	}

	/** The cached stats, or null; asks for them in the background when missing or stale. */
	public Stats get(UUID player) {
		Stats s = cache.get(player);
		long now = System.currentTimeMillis();
		boolean stale = s == null || now - s.time > (s.failed ? RETRY_MS : FRESH_MS);
		if (stale && key != null && status != Status.BAD_KEY && queue.size() < MAX_QUEUE && queued.putIfAbsent(player, Boolean.TRUE) == null) {
			queue.offerFirst(player); // newest first: the players just seen are the ones on screen
			startWorker();
		}
		return s == null || s.failed ? null : s;
	}

	private synchronized void startWorker() {
		if (worker != null) {
			return;
		}
		worker = new Thread(this::run, "Rago Hypixel API");
		worker.setDaemon(true);
		worker.start();
	}

	private void run() {
		while (true) {
			try {
				UUID id = queue.take();
				long wait = Math.max(pausedUntil - System.currentTimeMillis(), 0);
				if (wait > 0) {
					Thread.sleep(wait);
				}
				fetch(id);
				queued.remove(id);
				Thread.sleep(GAP_MS);
			} catch (InterruptedException e) {
				return;
			} catch (RuntimeException e) {
				RagoClient.LOG.warn("Hypixel API request failed", e);
			}
		}
	}

	private void fetch(UUID id) {
		String k = key;
		if (k == null) {
			return;
		}
		long now = System.currentTimeMillis();
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(URL_PLAYER + id.toString().replace("-", "")).openConnection();
			c.setConnectTimeout(5000);
			c.setReadTimeout(8000);
			c.setRequestProperty("API-Key", k);
			c.setRequestProperty("User-Agent", "Rago Client");
			int code = c.getResponseCode();
			if (code == 403) {
				status = Status.BAD_KEY;
				RagoClient.LOG.warn("Hypixel API: the key was refused (expired or wrong)");
				return;
			}
			if (code == 429) {
				String reset = c.getHeaderField("RateLimit-Reset");
				pausedUntil = now + 1000L * (reset != null && reset.matches("\\d{1,4}") ? Integer.parseInt(reset) : 60);
				queue.offerLast(id);
				return;
			}
			if (code != 200) {
				cache.put(id, new Stats(-1, -1, now, true));
				return;
			}
			try (InputStream in = c.getInputStream()) {
				JsonElement json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
				cache.put(id, parse(json, now));
				status = Status.OK;
			}
		} catch (IOException e) {
			if (status != Status.UNREACHABLE) {
				RagoClient.LOG.warn("Hypixel API unreachable: {}", e.toString());
			}
			status = Status.UNREACHABLE;
			cache.put(id, new Stats(-1, -1, now, true));
		} finally {
			if (c != null) {
				c.disconnect();
			}
		}
	}

	static Stats parse(JsonElement json, long now) {
		if (!json.isJsonObject() || !json.getAsJsonObject().has("player") || !json.getAsJsonObject().get("player").isJsonObject()) {
			// the player never joined Hypixel, or is nicked
			return new Stats(-1, -1, now, false);
		}
		JsonObject p = json.getAsJsonObject().getAsJsonObject("player");
		double exp = p.has("networkExp") && p.get("networkExp").isJsonPrimitive() ? p.get("networkExp").getAsDouble() : 0;
		int star = -1;
		if (p.has("achievements") && p.get("achievements").isJsonObject()) {
			JsonObject a = p.getAsJsonObject("achievements");
			if (a.has("bedwars_level") && a.get("bedwars_level").isJsonPrimitive()) {
				star = a.get("bedwars_level").getAsInt();
			}
		}
		return new Stats(networkLevel(exp), star, now, false);
	}

	/** Hypixel's network level for an amount of network experience (1.0 at 0 exp). */
	static double networkLevel(double exp) {
		return (Math.sqrt(exp + 15312.5) - 125 / Math.sqrt(2)) / (25 * Math.sqrt(2));
	}
}
