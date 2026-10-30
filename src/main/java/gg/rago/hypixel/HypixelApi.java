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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
	/** The gap while plenty of the key's requests are left (300 per 5 minutes for a personal key). */
	private static final long FAST_GAP_MS = 120L;
	private static final int FAST_WHILE_REMAINING = 60;
	private static final int MAX_QUEUE = 64;
	/** A new key's first answer comes in a few seconds; past this the check is called failed, never left hanging. */
	private static final long CHECK_TIMEOUT_MS = 20_000L;

	/** What is known about a player; {@code level} and {@code star} are -1 when unknown. */
	public static final class Stats {
		public final double level;
		public final int star;
		/** Whether Hypixel knows the player: a name it does not know in a game is a nick. */
		public final boolean found;
		public final int finalKills;
		public final int finalDeaths;
		public final int wins;
		public final int losses;
		public final int bedsBroken;
		/** -1 when the player hides it. */
		public final int winstreak;
		final long time;
		final boolean failed;
		/** Bed Wars quests by id; only filled for players Hypixel knows. */
		Map<String, Quest> quests = Collections.emptyMap();

		Stats(double level, int star, long time, boolean failed) {
			this(level, star, false, 0, 0, 0, 0, 0, -1, time, failed);
		}

		Stats(double level, int star, boolean found, int finalKills, int finalDeaths, int wins, int losses, int bedsBroken,
			int winstreak, long time, boolean failed) {
			this.level = level;
			this.star = star;
			this.found = found;
			this.finalKills = finalKills;
			this.finalDeaths = finalDeaths;
			this.wins = wins;
			this.losses = losses;
			this.bedsBroken = bedsBroken;
			this.winstreak = winstreak;
			this.time = time;
			this.failed = failed;
		}

		/** Bedwars final kills per final death (the kills alone with no deaths). */
		public double fkdr() {
			return finalKills / (double) Math.max(1, finalDeaths);
		}

		public double wlr() {
			return wins / (double) Math.max(1, losses);
		}

		public Map<String, Quest> quests() {
			return quests;
		}

		/** When Hypixel answered, in milliseconds since 1970. */
		public long time() {
			return time;
		}
	}

	/** A player's state for one quest, as the API tells it. */
	public static final class Quest {
		/** When the quest was accepted, or 0 when it is not running. */
		public final long started;
		/** Progress by objective id. */
		public final Map<String, Integer> progress;
		/** When it was last completed, or 0. */
		public final long completed;

		Quest(long started, Map<String, Integer> progress, long completed) {
			this.started = started;
			this.progress = progress;
			this.completed = completed;
		}
	}

	/** CHECKING: a new key was given and its first answer has not come yet. */
	public enum Status { NO_KEY, CHECKING, OK, BAD_KEY, UNREACHABLE }

	private final Path keyFile;
	private final Map<UUID, Stats> cache = new ConcurrentHashMap<>();
	private final LinkedBlockingDeque<UUID> queue = new LinkedBlockingDeque<>();
	private final Map<UUID, Boolean> queued = new ConcurrentHashMap<>();
	private volatile String key;
	private volatile Status status = Status.NO_KEY;
	private volatile long pausedUntil;
	private volatile long gap = GAP_MS;
	private volatile long checkingSince;
	private volatile int requests;
	private volatile int lastCode;
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
		if (status == Status.CHECKING && System.currentTimeMillis() - checkingSince > CHECK_TIMEOUT_MS) {
			checkTimedOut();
		}
		return status;
	}

	/** The new key's check got no answer in time: says why in the log, and lets the key be tried again. */
	private synchronized void checkTimedOut() {
		if (status != Status.CHECKING) {
			return;
		}
		Thread w = worker;
		String where;
		if (w == null) {
			where = "the request thread never started";
		} else if (!w.isAlive()) {
			where = "the request thread had stopped";
		} else {
			StackTraceElement[] at = w.getStackTrace();
			where = "the request thread is at " + Arrays.toString(Arrays.copyOf(at, Math.min(6, at.length)));
		}
		RagoClient.LOG.warn("Hypixel API: no answer about the new key after {} s ({}, {} waiting, paused for {} s)",
			CHECK_TIMEOUT_MS / 1000, where, queue.size(), Math.max(0, pausedUntil - System.currentTimeMillis()) / 1000);
		status = Status.UNREACHABLE;
		startWorker();
	}

	/** For {@code /rago debug}: where the requests stand. */
	public String debugState() {
		Thread w = worker;
		return "status=" + status + " key=" + (key != null) + " thread=" + (w == null ? "none" : w.isAlive() ? "running" : "stopped")
			+ " waiting=" + queue.size() + " known=" + cache.size() + " requests=" + requests + " lastCode=" + lastCode
			+ " paused=" + Math.max(0, pausedUntil - System.currentTimeMillis()) / 1000 + "s";
	}

	/** Whether a key is saved (it is never shown again, only its last characters). */
	public boolean hasKey() {
		return key != null;
	}

	/** The last four characters of the saved key, to tell keys apart, or null. */
	public String keyHint() {
		String k = key;
		return k == null ? null : k.substring(k.length() - 4);
	}

	/**
	 * Stores a new key (only in this file, never in rago.json or profiles), forgets failures, and
	 * tries it at once by asking for {@code probe} (the player's own stats), when given.
	 */
	public void setKey(String k, UUID probe) throws IOException {
		Files.createDirectories(keyFile.getParent());
		Files.write(keyFile, k.getBytes(StandardCharsets.UTF_8));
		key = k;
		cache.values().removeIf(s -> s.failed);
		pausedUntil = 0;
		checkingSince = System.currentTimeMillis();
		status = probe == null ? Status.OK : Status.CHECKING;
		RagoClient.LOG.info("Hypixel API: new key saved (...{}), {}", keyHint(), probe == null ? "not checked (no player id)" : "checking it");
		if (probe != null) {
			refresh(probe);
		}
	}

	/** Removes the saved key. */
	public void clearKey() throws IOException {
		Files.deleteIfExists(keyFile);
		key = null;
		status = Status.NO_KEY;
	}

	/** Asks for a player's stats again now, ahead of the others (the cached ones stay until then). */
	public void refresh(UUID player) {
		if (key == null || status == Status.BAD_KEY) {
			return;
		}
		queue.remove(player);
		queued.put(player, Boolean.TRUE);
		queue.offerFirst(player);
		startWorker();
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

	/** Starts the request thread, or starts it again if it ever stopped. */
	private synchronized void startWorker() {
		if (worker != null && worker.isAlive()) {
			return;
		}
		if (worker != null) {
			RagoClient.LOG.warn("Hypixel API: the request thread had stopped, starting it again");
		}
		worker = new Thread(this::run, "Rago Hypixel API");
		worker.setDaemon(true);
		worker.start();
	}

	private void run() {
		while (true) {
			UUID id;
			try {
				id = queue.take();
				waitForPause();
			} catch (InterruptedException e) {
				RagoClient.LOG.warn("Hypixel API: the request thread was interrupted");
				return;
			}
			try {
				fetch(id);
			} catch (RuntimeException | Error e) {
				// whatever goes wrong with one answer, the thread keeps serving the others
				RagoClient.LOG.warn("Hypixel API request failed", e);
				cache.put(id, new Stats(-1, -1, System.currentTimeMillis(), true));
				if (status == Status.CHECKING) {
					status = Status.UNREACHABLE;
				}
			} finally {
				queued.remove(id);
			}
			try {
				Thread.sleep(gap);
			} catch (InterruptedException e) {
				RagoClient.LOG.warn("Hypixel API: the request thread was interrupted");
				return;
			}
		}
	}

	/** Sleeps while Hypixel asked to slow down, in short steps so that a new key ends the pause at once. */
	private void waitForPause() throws InterruptedException {
		long wait;
		while ((wait = pausedUntil - System.currentTimeMillis()) > 0) {
			Thread.sleep(Math.min(wait, 250));
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
			requests++;
			lastCode = code;
			String remaining = c.getHeaderField("RateLimit-Remaining");
			gap = remaining != null && remaining.matches("\\d{1,5}") && Integer.parseInt(remaining) > FAST_WHILE_REMAINING ? FAST_GAP_MS : GAP_MS;
			if (code == 403) {
				status = Status.BAD_KEY;
				RagoClient.LOG.warn("Hypixel API: the key was refused (expired or wrong)");
				return;
			}
			if (code == 429) {
				if (status == Status.CHECKING) {
					accepted(); // only a valid key is counted against a limit
				}
				String reset = c.getHeaderField("RateLimit-Reset");
				pausedUntil = now + 1000L * (reset != null && reset.matches("\\d{1,4}") ? Integer.parseInt(reset) : 60);
				queue.offerLast(id);
				return;
			}
			if (code != 200) {
				if (status == Status.CHECKING) {
					accepted(); // the key was accepted, the player was not
				}
				cache.put(id, new Stats(-1, -1, now, true));
				return;
			}
			try (InputStream in = c.getInputStream()) {
				JsonElement json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
				cache.put(id, parse(json, now));
				if (status != Status.OK) {
					accepted();
				}
			}
		} catch (IOException e) {
			if (status != Status.UNREACHABLE) {
				RagoClient.LOG.warn("Hypixel API unreachable: {}", e.toString());
				if (e instanceof javax.net.ssl.SSLException) {
					RagoClient.LOG.warn("Hypixel API: the secure connection failed; Java {} may be too old to trust Hypixel's certificate", System.getProperty("java.version"));
				}
			}
			status = Status.UNREACHABLE;
			cache.put(id, new Stats(-1, -1, now, true));
		} finally {
			if (c != null) {
				c.disconnect();
			}
		}
	}

	private void accepted() {
		if (status == Status.CHECKING) {
			RagoClient.LOG.info("Hypixel API: the key works");
		}
		status = Status.OK;
	}

	public static Stats parse(JsonElement json, long now) {
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
		int fk = 0;
		int fd = 0;
		int wins = 0;
		int losses = 0;
		int beds = 0;
		int ws = -1;
		if (p.has("stats") && p.get("stats").isJsonObject() && p.getAsJsonObject("stats").has("Bedwars")
			&& p.getAsJsonObject("stats").get("Bedwars").isJsonObject()) {
			JsonObject b = p.getAsJsonObject("stats").getAsJsonObject("Bedwars");
			fk = number(b, "final_kills_bedwars", 0);
			fd = number(b, "final_deaths_bedwars", 0);
			wins = number(b, "wins_bedwars", 0);
			losses = number(b, "losses_bedwars", 0);
			beds = number(b, "beds_broken_bedwars", 0);
			ws = number(b, "winstreak", -1);
		}
		Stats stats = new Stats(networkLevel(exp), star, true, fk, fd, wins, losses, beds, ws, now, false);
		if (p.has("quests") && p.get("quests").isJsonObject()) {
			stats.quests = quests(p.getAsJsonObject("quests"));
		}
		return stats;
	}

	/** The Bed Wars entries of a player's "quests" object. */
	static Map<String, Quest> quests(JsonObject all) {
		Map<String, Quest> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : all.entrySet()) {
			if (!e.getKey().startsWith("bedwars_") || !e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject q = e.getValue().getAsJsonObject();
			long started = 0;
			Map<String, Integer> progress = new HashMap<>();
			if (q.has("active") && q.get("active").isJsonObject()) {
				JsonObject active = q.getAsJsonObject("active");
				started = time(active.get("started"));
				if (active.has("objectives") && active.get("objectives").isJsonObject()) {
					for (Map.Entry<String, JsonElement> o : active.getAsJsonObject("objectives").entrySet()) {
						if (o.getValue().isJsonPrimitive() && o.getValue().getAsJsonPrimitive().isNumber()) {
							progress.put(o.getKey(), o.getValue().getAsInt());
						}
					}
				}
			}
			long completed = 0;
			if (q.has("completions") && q.get("completions").isJsonArray()) {
				for (JsonElement c : q.getAsJsonArray("completions")) {
					if (c.isJsonObject()) {
						completed = Math.max(completed, time(c.getAsJsonObject().get("time")));
					}
				}
			}
			out.put(e.getKey(), new Quest(started, progress, completed));
		}
		return out;
	}

	private static long time(JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0;
	}

	private static int number(JsonObject o, String key, int fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : fallback;
	}

	/** Hypixel's network level for an amount of network experience (1.0 at 0 exp). */
	static double networkLevel(double exp) {
		return (Math.sqrt(exp + 15312.5) - 125 / Math.sqrt(2)) / (25 * Math.sqrt(2));
	}
}
