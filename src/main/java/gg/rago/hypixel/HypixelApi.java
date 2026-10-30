package gg.rago.hypixel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import gg.rago.core.RagoClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;

/**
 * Reads players' network level and Bedwars star from the public Hypixel API with the player's own
 * API key (from developer.hypixel.net). Requests run one at a time on a background thread, spaced to
 * fit what the key has left, and answers are kept two days, on disk too, so a player met again
 * costs nothing. Nothing is sent to the Minecraft server.
 */
public final class HypixelApi {
	private static final String URL_PLAYER = "https://api.hypixel.net/v2/player?uuid=";
	private static final Pattern KEY = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	/** Other players' stats are asked again after this; your own are asked by the quests when needed. */
	private static final long FRESH_MS = 48 * 3_600_000L;
	/** Stats saved on disk are used again for this long after a restart. */
	private static final long KEEP_ON_DISK_MS = FRESH_MS;
	private static final int MAX_ON_DISK = 3000;
	private static final long SAVE_EVERY_MS = 30_000L;
	/** A rate-limit window longer than this is a daily quota: lobby players then get nothing. */
	private static final int SHORT_WINDOW_S = 600;
	private static final long RETRY_MS = 2 * 60_000L;
	/** A failed answer is asked again only after this on a daily quota, where each request counts. */
	private static final long DAILY_RETRY_MS = 30 * 60_000L;
	/** The gap between requests until Hypixel has said how many the key has left. */
	private static final long GAP_MS = 1_000L;
	/** Never closer than this, however many requests the key has left. */
	private static final long MIN_GAP_MS = 120L;
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
		/** Read back from the disk after a restart: no quests in it. */
		boolean saved;

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

		/** Whether these come from an earlier session (they have no quests), not from Hypixel in this one. */
		public boolean saved() {
			return saved;
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
	private final String playerUrl;
	private final Map<UUID, Stats> cache = new ConcurrentHashMap<>();
	private final LinkedBlockingDeque<UUID> queue = new LinkedBlockingDeque<>();
	/** The players waiting, true for those that matter, false for the spare ones (asked only while the key can afford it). */
	private final Map<UUID, Boolean> queued = new ConcurrentHashMap<>();
	private volatile String key;
	private volatile Status status = Status.NO_KEY;
	private volatile long pausedUntil;
	private volatile long gap = GAP_MS;
	/** What Hypixel said last about the key's budget: requests per window, left in this window; -1 until known. */
	private volatile int limit = -1;
	private volatile int remaining = -1;
	private volatile int window = -1;
	/** When the current window ends (System.currentTimeMillis()), or 0 until known. */
	private volatile long resetAt;
	private final Path cacheFile;
	private long savedAt;
	private boolean dirty;
	private volatile long checkingSince;
	private volatile int requests;
	private volatile int lastCode;
	private Thread worker;

	public HypixelApi(Path configDir) {
		// a local stand-in for Hypixel, only in the developer test game
		this(configDir, Boolean.getBoolean("rago.test.hypixel") ? System.getProperty("rago.test.hypixelUrl", URL_PLAYER) : URL_PLAYER);
	}

	/** With another address for Hypixel's player endpoint, for tests. */
	HypixelApi(Path configDir, String playerUrl) {
		this.playerUrl = playerUrl;
		this.keyFile = configDir.resolve("rago-hypixel-key.txt");
		this.cacheFile = configDir.resolve("rago-hypixel-cache.json");
		load();
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
			+ " budget=" + remaining + "/" + limit + " window=" + window + "s gap=" + gap + "ms"
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
		return get(player, false);
	}

	/**
	 * The cached stats, or null. When they are missing or stale they are asked for in the background,
	 * unless {@code spare} (a player who only might matter, such as one in a big lobby) and the key has
	 * used more than half of its requests for now: those are kept for the players of your game.
	 */
	public Stats get(UUID player, boolean spare) {
		Stats s = cache.get(player);
		long now = System.currentTimeMillis();
		boolean stale = s == null || now - s.time > (s.failed ? dailyQuota() ? DAILY_RETRY_MS : RETRY_MS : FRESH_MS);
		if (stale && !spare) {
			queued.replace(player, Boolean.FALSE, Boolean.TRUE); // a spare player waiting now matters
		}
		if (stale && key != null && status != Status.BAD_KEY && !(spare && lowOnRequests()) && queued.putIfAbsent(player, !spare) == null) {
			if (queue.size() >= MAX_QUEUE) {
				// the players seen longest ago make room: they are the least likely to be on screen
				UUID old = queue.pollLast();
				if (old != null) {
					queued.remove(old);
				}
			}
			queue.offerFirst(player); // newest first: the players just seen are the ones on screen
			startWorker();
		}
		return s == null || s.failed ? null : s;
	}

	/**
	 * Whether a player who only might matter must wait: while the key's quota is a daily one (it would
	 * be gone in a few lobbies), once half of a short window's requests are used, and while Hypixel
	 * asks to wait. Checked again when the player's turn comes, once Hypixel has told the budget.
	 */
	private boolean lowOnRequests() {
		int l = limit;
		int r = remaining;
		int w = window;
		return pausedUntil > System.currentTimeMillis() || l >= 0 && r >= 0 && (w > SHORT_WINDOW_S || r * 2 < l);
	}

	/** Seconds until Hypixel takes requests from this key again, after it said there were too many; 0 when it does. */
	public int pausedSeconds() {
		return (int) Math.max(0, (pausedUntil - System.currentTimeMillis() + 999) / 1000);
	}

	/** Whether Hypixel counts this key's requests per day (a developer key: 300 a day), not per few minutes. */
	public boolean dailyQuota() {
		return window > SHORT_WINDOW_S;
	}

	/**
	 * The key's requests left and its requests per window, and the seconds until the window starts
	 * again ({remaining, limit, seconds}), as Hypixel last told them; null before its first answer
	 * since the game started. Once the window is over, the full limit is left.
	 */
	public int[] budget() {
		return budget(System.currentTimeMillis(), remaining, limit, resetAt);
	}

	static int[] budget(long now, int remaining, int limit, long resetAt) {
		if (remaining < 0 || limit < 0) {
			return null;
		}
		if (resetAt > 0 && now >= resetAt) {
			return new int[] {limit, limit, 0};
		}
		return new int[] {Math.min(remaining, limit), limit, resetAt > 0 ? (int) ((resetAt - now + 999) / 1000) : -1};
	}

	/** The key's requests per window as Hypixel told it, or -1 before the first answer. */
	public int limit() {
		return limit;
	}

	/** "18 h 34", "12 min" or "40 s". */
	public static String duration(int seconds) {
		if (seconds >= 3600) {
			return String.format("%d h %02d", seconds / 3600, seconds % 3600 / 60);
		}
		return seconds >= 60 ? seconds / 60 + " min" : seconds + " s";
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
			boolean again = false;
			try {
				if (Boolean.FALSE.equals(queued.get(id)) && lowOnRequests()) {
					continue; // a spare player, and the key cannot afford it now (finally forgets it)
				}
				again = !fetch(id);
			} catch (RuntimeException | Error e) {
				// whatever goes wrong with one answer, the thread keeps serving the others
				RagoClient.LOG.warn("Hypixel API request failed", e);
				cache.put(id, new Stats(-1, -1, System.currentTimeMillis(), true));
				if (status == Status.CHECKING) {
					status = Status.UNREACHABLE;
				}
			} finally {
				if (!again) {
					queued.remove(id);
				}
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

	/** Asks Hypixel for one player; false when the player has to be asked again later (too many requests). */
	private boolean fetch(UUID id) {
		String k = key;
		if (k == null) {
			return true;
		}
		long now = System.currentTimeMillis();
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(playerUrl + id.toString().replace("-", "")).openConnection();
			c.setConnectTimeout(5000);
			c.setReadTimeout(8000);
			c.setRequestProperty("API-Key", k);
			c.setRequestProperty("User-Agent", "Rago Client");
			int code = c.getResponseCode();
			requests++;
			lastCode = code;
			int reset = header(c, "RateLimit-Reset");
			limit = header(c, "RateLimit-Limit");
			remaining = header(c, "RateLimit-Remaining");
			window = reset;
			if (reset >= 0) {
				resetAt = now + 1000L * reset;
			}
			// in a short window, what is left of the key's requests is spread over what is left of it, so
			// the key never runs dry halfway through a lobby; a daily quota is spent only on players that
			// matter (see lowOnRequests), as fast as they come
			gap = reset < 0 || remaining < 0 ? GAP_MS
				: reset > SHORT_WINDOW_S ? MIN_GAP_MS : Math.max(MIN_GAP_MS, 1000L * reset / Math.max(1, remaining));
			if (code == 403) {
				status = Status.BAD_KEY;
				RagoClient.LOG.warn("Hypixel API: the key was refused (expired or wrong)");
				return true;
			}
			if (code == 429) {
				if (status == Status.CHECKING) {
					accepted(); // only a valid key is counted against a limit
				}
				int wait = reset > 0 ? reset : 60;
				pausedUntil = now + 1000L * wait;
				RagoClient.LOG.warn("Hypixel API: too many requests for this key ({} of {} left, {}), waiting {} s",
					Math.max(remaining, 0), limit < 0 ? "?" : String.valueOf(limit), cause(c), wait);
				queue.offerFirst(id); // keeps its place: often your own stats, for the quests
				return false;
			}
			if (code != 200) {
				if (status == Status.CHECKING) {
					accepted(); // the key was accepted, the player was not
				}
				cache.put(id, new Stats(-1, -1, now, true));
				return true;
			}
			try (InputStream in = c.getInputStream()) {
				JsonElement json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
				cache.put(id, parse(json, now));
				if (status != Status.OK) {
					accepted();
				}
			}
			dirty = true;
			if (now - savedAt > SAVE_EVERY_MS) {
				save();
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
		return true;
	}

	/** The stats saved by an earlier session, those less than a day old, so a restart costs no requests. */
	private void load() {
		if (!Files.exists(cacheFile)) {
			return;
		}
		long now = System.currentTimeMillis();
		try (InputStreamReader in = new InputStreamReader(Files.newInputStream(cacheFile), StandardCharsets.UTF_8)) {
			JsonElement json = new JsonParser().parse(in);
			if (!json.isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
				if (!e.getValue().isJsonArray() || e.getValue().getAsJsonArray().size() != 10) {
					continue;
				}
				JsonArray a = e.getValue().getAsJsonArray();
				long time = a.get(9).getAsLong();
				if (now - time < KEEP_ON_DISK_MS && time <= now) {
					Stats st = new Stats(a.get(0).getAsDouble(), a.get(1).getAsInt(), a.get(2).getAsBoolean(),
						a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt(), a.get(6).getAsInt(), a.get(7).getAsInt(),
						a.get(8).getAsInt(), time, false);
					st.saved = true;
					cache.put(UUID.fromString(e.getKey()), st);
				}
			}
			RagoClient.LOG.info("Hypixel API: {} players' stats kept from the last session", cache.size());
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.warn("Could not read {}: {}", cacheFile, e.toString());
		}
	}

	/** Saves the stats Hypixel gave (not the quests), newest first, written whole then moved in place. */
	private synchronized void save() {
		if (!dirty) {
			return;
		}
		dirty = false;
		savedAt = System.currentTimeMillis();
		List<Map.Entry<UUID, Stats>> all = new ArrayList<>(cache.entrySet());
		all.removeIf(e -> e.getValue().failed || savedAt - e.getValue().time > KEEP_ON_DISK_MS);
		all.sort((x, y) -> Long.compare(y.getValue().time, x.getValue().time));
		JsonObject out = new JsonObject();
		for (Map.Entry<UUID, Stats> e : all.subList(0, Math.min(all.size(), MAX_ON_DISK))) {
			Stats st = e.getValue();
			JsonArray a = new JsonArray();
			for (Number n : new Number[] {st.level, st.star}) {
				a.add(new JsonPrimitive(n));
			}
			a.add(new JsonPrimitive(st.found));
			for (Number n : new Number[] {st.finalKills, st.finalDeaths, st.wins, st.losses, st.bedsBroken, st.winstreak, st.time}) {
				a.add(new JsonPrimitive(n));
			}
			out.add(e.getKey().toString(), a);
		}
		try {
			Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
			Files.write(tmp, out.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, cacheFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			RagoClient.LOG.warn("Could not save {}: {}", cacheFile, e.toString());
		}
	}

	private static int header(HttpURLConnection c, String name) {
		String v = c.getHeaderField(name);
		return v != null && v.trim().matches("\\d{1,6}") ? Integer.parseInt(v.trim()) : -1;
	}

	/** Hypixel's reason for a refusal ("Key throttle"...), from the small JSON it answers with. */
	private static String cause(HttpURLConnection c) {
		try (InputStream in = c.getErrorStream()) {
			if (in == null) {
				return "no reason given";
			}
			JsonElement json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			if (json.isJsonObject() && json.getAsJsonObject().has("cause")) {
				String cause = json.getAsJsonObject().get("cause").getAsString();
				boolean global = json.getAsJsonObject().has("global") && json.getAsJsonObject().get("global").getAsBoolean();
				return "\"" + cause + "\"" + (global ? ", for all of Hypixel's API" : "");
			}
			return "no reason given";
		} catch (IOException | RuntimeException e) {
			return "no reason given";
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
