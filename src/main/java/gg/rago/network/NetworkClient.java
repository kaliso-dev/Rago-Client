package gg.rago.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Talks to a Rago Network server (see server/README.md), one request at a time on a worker thread:
 * signs in the way Minecraft servers check a player (the client tells Mojang it joins a server id the
 * Rago server gave, and the Rago server asks Mojang; the Minecraft token goes to Mojang only), sends
 * the player's presence every minute and asks, in batches, which of the players around use Rago.
 * Everything it learns is kept in memory only. {@link #tick} is called on the game thread and never
 * waits for the network.
 */
public final class NetworkClient {
	/** The signed-in Minecraft account. */
	public interface Account {
		String name();

		UUID id();

		/** Tells Mojang that this account joins {@code serverId} (authlib's joinServer). */
		void joinServer(String serverId) throws Exception;
	}

	/** A player who uses Rago, as the server describes them. */
	public static final class Player {
		public final String role;
		public final Loadout loadout;

		public Player(String role, Loadout loadout) {
			this.role = role;
			this.loadout = loadout;
		}
	}

	/** What is known of a player, until when; {@code player} is null for one who does not show Rago. */
	private static final class Known {
		final Player player;
		final long until;

		Known(Player player, long until) {
			this.player = player;
			this.until = until;
		}
	}

	/** An answer other than 200. */
	static final class StatusException extends IOException {
		final int status;

		StatusException(int status, String error) {
			super("HTTP " + status + (error == null ? "" : " " + error));
			this.status = status;
		}
	}

	public static final String[] ROLES = {"user", "partner", "staff", "creator"};
	static final long PRESENCE_EVERY = 60_000;
	/** A presence sent sooner because the loadout or "show that I use Rago" changed, at most this often. */
	static final long PRESENCE_SOONEST = 10_000;
	static final long LOOKUP_GAP = 5_000;
	/** When nobody new is around, how soon to look again. */
	static final long LOOKUP_IDLE = 1_000;
	/** A Rago player is asked about again after this, so a new cape or hat shows within a minute. */
	static final long KNOWN_FOR = 60_000;
	/** Someone not (yet) on Rago Network is asked about again sooner: a friend who just signed in shows within 30 s. */
	static final long UNKNOWN_FOR = 30_000;
	static final int BATCH = 100;
	static final long FIRST_RETRY = 30_000;
	static final long LAST_RETRY = 15 * 60_000;
	private static final int MAX_KNOWN = 4096;
	private static final int TIMEOUT_MS = 5_000;
	private static final int MAX_ANSWER = 1 << 20;
	/** The challenge a sign-in starts with: the server's random hex. */
	private static final Pattern CHALLENGE = Pattern.compile("^[0-9a-f]{16,64}$");

	private final Executor worker;
	private final LongSupplier clock;
	private final Supplier<String> version;
	private final Consumer<String> log;
	private final ConcurrentHashMap<UUID, Known> known = new ConcurrentHashMap<>();
	private final AtomicBoolean busy = new AtomicBoolean();

	private volatile String base = "";
	private volatile String token;
	private volatile UUID signedIn;
	private volatile String role;
	private volatile int online = -1;
	private volatile long nextSignIn;
	private volatile long nextPresence;
	private volatile long lastPresence = Long.MIN_VALUE / 2;
	private volatile long nextLookup;
	private volatile long retry = FIRST_RETRY;
	private volatile int sentSignature;
	private volatile String problem;

	public NetworkClient(Executor worker, LongSupplier clock, Supplier<String> version, Consumer<String> log) {
		this.worker = worker;
		this.clock = clock;
		this.version = version;
		this.log = log;
	}

	/**
	 * Once per client tick. {@code url} is the server's address, "" for none; {@code signature} changes
	 * whenever the loadout or {@code visible} does, so a change is sent within {@link #PRESENCE_SOONEST};
	 * {@code around} is asked for the players to look up only when a lookup is due.
	 */
	public void tick(String url, Account account, boolean visible, boolean others, int signature, Supplier<Loadout> own,
		Supplier<Collection<UUID>> around) {
		String b = normalize(url);
		if (!b.equals(base)) {
			forget();
			base = b;
		}
		if (b.isEmpty() || account == null || account.id() == null || busy.get()) {
			return;
		}
		long now = clock.getAsLong();
		if (token == null || !account.id().equals(signedIn)) {
			if (now >= nextSignIn) {
				run(() -> signIn(b, account));
			}
			return;
		}
		if (now >= nextPresence || signature != sentSignature && now >= lastPresence + PRESENCE_SOONEST) {
			Loadout loadout = own.get();
			run(() -> presence(b, visible, loadout, signature));
			return;
		}
		if (others && now >= nextLookup) {
			List<UUID> due = due(around.get(), now, account.id());
			if (due.isEmpty()) {
				nextLookup = now + LOOKUP_IDLE;
			} else {
				run(() -> lookup(b, due));
			}
		}
	}

	/** What the server said about {@code id}; null for a player who does not show Rago, or not known yet. */
	public Player player(UUID id) {
		Known k = id == null ? null : known.get(id);
		return k == null ? null : k.player;
	}

	/** Your role once signed in (user, partner, staff or creator), else null. */
	public String role() {
		return token == null ? null : role;
	}

	/** Rago players online, as of the last presence; -1 when not known. */
	public int online() {
		return online;
	}

	public boolean signedIn() {
		return token != null;
	}

	/** The last thing that went wrong, or null. */
	public String problem() {
		return problem;
	}

	/** Forgets the session and every player known: another server, or the feature turned off. */
	public void forget() {
		token = null;
		signedIn = null;
		role = null;
		online = -1;
		known.clear();
		nextSignIn = 0;
		nextPresence = 0;
		nextLookup = 0;
		retry = FIRST_RETRY;
	}

	/**
	 * The server's address without a trailing slash, or "" when it is not one to send anything to:
	 * https only, or http on this computer (a server run for a test).
	 */
	public static String normalize(String url) {
		if (url == null) {
			return "";
		}
		String u = url.trim();
		while (u.endsWith("/")) {
			u = u.substring(0, u.length() - 1);
		}
		if (u.isEmpty()) {
			return "";
		}
		try {
			URL parsed = new URL(u);
			if (parsed.getQuery() != null || parsed.getRef() != null || parsed.getUserInfo() != null || parsed.getHost().isEmpty()) {
				return "";
			}
			String host = parsed.getHost();
			boolean local = host.equals("localhost") || host.equals("127.0.0.1");
			if (parsed.getProtocol().equals("https") || parsed.getProtocol().equals("http") && local) {
				return u;
			}
		} catch (MalformedURLException e) {
			// not an address
		}
		return "";
	}

	/** The players of {@code around} to ask about: real accounts (version 4 UUIDs), not you, not known lately. */
	List<UUID> due(Collection<UUID> around, long now, UUID self) {
		List<UUID> out = new ArrayList<>();
		if (around != null) {
			for (UUID u : around) {
				if (u == null || u.version() != 4 || u.equals(self) || out.contains(u)) {
					continue;
				}
				Known k = known.get(u);
				if (k == null || k.until <= now) {
					out.add(u);
					if (out.size() == BATCH) {
						break;
					}
				}
			}
		}
		if (known.size() > MAX_KNOWN) {
			known.values().removeIf(k -> k.until <= now);
		}
		return out;
	}

	private void run(Runnable job) {
		if (!busy.compareAndSet(false, true)) {
			return;
		}
		try {
			worker.execute(() -> {
				try {
					job.run();
				} finally {
					busy.set(false);
				}
			});
		} catch (RejectedExecutionException e) {
			busy.set(false);
		}
	}

	private void signIn(String b, Account account) {
		long now = clock.getAsLong();
		try {
			JsonObject start = new JsonObject();
			start.addProperty("name", account.name());
			String challenge = string(call(b, "POST", "/v1/auth/start", start, null), "serverId");
			if (!CHALLENGE.matcher(challenge).matches()) {
				throw new IOException("not a sign-in challenge");
			}
			account.joinServer(joinId(challenge));
			JsonObject finish = new JsonObject();
			finish.addProperty("name", account.name());
			finish.addProperty("serverId", challenge);
			JsonObject answer = call(b, "POST", "/v1/auth/finish", finish, null);
			String t = string(answer, "token");
			UUID id = uuid(string(answer, "uuid"));
			if (!account.id().equals(id)) {
				throw new IOException("the server signed in another account");
			}
			if (!b.equals(base)) {
				return;
			}
			role = role(answer);
			signedIn = id;
			token = t;
			retry = FIRST_RETRY;
			nextPresence = now;
			problem(null);
		} catch (Exception e) {
			nextSignIn = now + retry;
			retry = Math.min(retry * 2, LAST_RETRY);
			problem("sign-in: " + e.getMessage());
		}
	}

	private void presence(String b, boolean visible, Loadout loadout, int signature) {
		long now = clock.getAsLong();
		JsonObject body = new JsonObject();
		body.addProperty("visible", visible);
		body.addProperty("version", version.get());
		body.add("cosmetics", loadout.toJson());
		try {
			JsonObject answer = call(b, "POST", "/v1/presence", body, token);
			JsonElement count = answer.get("online");
			if (count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()) {
				online = count.getAsInt();
			}
			sentSignature = signature;
			lastPresence = now;
			nextPresence = now + PRESENCE_EVERY;
			problem(null);
		} catch (StatusException e) {
			failed(e, now);
			nextPresence = now + (e.status == 429 ? PRESENCE_EVERY : FIRST_RETRY);
			lastPresence = now;
		} catch (IOException e) {
			nextPresence = now + FIRST_RETRY;
			lastPresence = now;
			problem("presence: " + e.getMessage());
		}
	}

	private void lookup(String b, List<UUID> due) {
		long now = clock.getAsLong();
		JsonObject body = new JsonObject();
		JsonArray ids = new JsonArray();
		for (UUID u : due) {
			ids.add(new JsonPrimitive(u.toString()));
		}
		body.add("uuids", ids);
		try {
			JsonObject answer = call(b, "POST", "/v1/lookup", body, token);
			JsonElement players = answer.get("players");
			Map<UUID, Player> found = new HashMap<>();
			if (players != null && players.isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : players.getAsJsonObject().entrySet()) {
					UUID id = uuid(e.getKey());
					if (id != null && e.getValue().isJsonObject()) {
						JsonObject p = e.getValue().getAsJsonObject();
						found.put(id, new Player(role(p), Loadout.fromJson(p.get("cosmetics"))));
					}
				}
			}
			if (!b.equals(base)) {
				return;
			}
			for (UUID u : due) {
				Player p = found.get(u);
				known.put(u, new Known(p, now + (p != null ? KNOWN_FOR : UNKNOWN_FOR)));
			}
			nextLookup = now + LOOKUP_GAP;
		} catch (StatusException e) {
			failed(e, now);
			nextLookup = now + (e.status == 429 ? 30_000 : FIRST_RETRY);
		} catch (IOException e) {
			nextLookup = now + FIRST_RETRY;
			problem("lookup: " + e.getMessage());
		}
	}

	/** A signed-in call refused: 401 means the session is over, so sign in again. */
	private void failed(StatusException e, long now) {
		if (e.status == 401) {
			token = null;
			nextSignIn = now;
		}
		problem(e.getMessage());
	}

	private void problem(String what) {
		if (what != null && !what.equals(problem)) {
			log.accept("Rago Network: " + what);
		}
		problem = what;
	}

	private JsonObject call(String b, String method, String path, JsonObject body, String bearer) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(b + path).openConnection();
		if (c instanceof HttpsURLConnection) {
			((HttpsURLConnection) c).setSSLSocketFactory(NetworkTls.factory());
		}
		try {
			c.setConnectTimeout(TIMEOUT_MS);
			c.setReadTimeout(TIMEOUT_MS);
			c.setUseCaches(false);
			c.setInstanceFollowRedirects(false);
			c.setRequestMethod(method);
			c.setRequestProperty("User-Agent", "Rago/" + version.get());
			c.setRequestProperty("Accept", "application/json");
			if (bearer != null) {
				c.setRequestProperty("Authorization", "Bearer " + bearer);
			}
			if (body != null) {
				byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
				c.setDoOutput(true);
				c.setRequestProperty("Content-Type", "application/json");
				c.setFixedLengthStreamingMode(bytes.length);
				try (OutputStream out = c.getOutputStream()) {
					out.write(bytes);
				}
			}
			int status = c.getResponseCode();
			JsonObject answer = read(status >= 400 ? c.getErrorStream() : c.getInputStream());
			if (status != 200) {
				JsonElement error = answer.get("error");
				throw new StatusException(status, error != null && error.isJsonPrimitive() ? error.getAsString() : null);
			}
			return answer;
		} finally {
			c.disconnect();
		}
	}

	private static JsonObject read(InputStream in) throws IOException {
		if (in == null) {
			return new JsonObject();
		}
		try (InputStream stream = in) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = stream.read(buf)) > 0) {
				bytes.write(buf, 0, n);
				if (bytes.size() > MAX_ANSWER) {
					throw new IOException("answer too long");
				}
			}
			JsonElement json = new JsonParser().parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
			return json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
		} catch (RuntimeException e) {
			throw new IOException("not JSON");
		}
	}

	private static String string(JsonObject o, String key) throws IOException {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
			throw new IOException("no " + key + " in the answer");
		}
		return e.getAsString();
	}

	/** The role given, if it is one Rago knows; else user. */
	private static String role(JsonObject o) {
		JsonElement e = o.get("role");
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			for (String r : ROLES) {
				if (r.equals(e.getAsString())) {
					return r;
				}
			}
		}
		return "user";
	}

	/**
	 * The server id given to Mojang for a sign-in challenge: the SHA-1 of "rago-network:" and the
	 * challenge, written as Minecraft writes its own (two's complement hex). A Minecraft server asks
	 * for the hash of its own key and a secret instead, so a Rago Network server, even a hostile one,
	 * cannot use your sign-in to join a Minecraft server as you: it would need a SHA-1 preimage.
	 */
	static String joinId(String challenge) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(("rago-network:" + challenge).getBytes(StandardCharsets.UTF_8));
			return new BigInteger(digest).toString(16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A UUID with or without dashes; null if it is not one. */
	static UUID uuid(String s) {
		if (s == null) {
			return null;
		}
		String hex = s.replace("-", "");
		if (hex.length() != 32) {
			return null;
		}
		try {
			long hi = Long.parseUnsignedLong(hex.substring(0, 16), 16);
			long lo = Long.parseUnsignedLong(hex.substring(16), 16);
			return new UUID(hi, lo);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
