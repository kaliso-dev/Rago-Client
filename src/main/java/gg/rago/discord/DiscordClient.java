package gg.rago.discord;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Objects;
import java.util.UUID;

/**
 * Shows what you are doing on your Discord profile (Rich Presence), on a thread of its own so the
 * game never waits for Discord. It finds the Discord app when it starts, sends the activity when it
 * changes (not more than once every few seconds, as Discord asks), and clears it when stopped. Only
 * the activity Rago writes is sent, to the Discord app on this computer, which shows it to your
 * Discord friends.
 */
public final class DiscordClient {
	private static final Logger LOG = LogManager.getLogger("Rago");
	/** Looking for the Discord app again, when it is closed. */
	static final long RETRY_MS = 20_000;
	/** Discord takes five activity updates per twenty seconds. */
	static final long MIN_GAP_MS = 4_000;
	/** After Discord refused the application (a wrong id): no point asking again soon. */
	static final long REFUSED_RETRY_MS = 10 * 60_000;

	/** What the profile shows. Two activities are equal when they show the same thing. */
	public static final class Activity {
		final String details;
		final String state;
		/** Epoch seconds the time shown counts from, or 0 for none. */
		final long start;
		final String imageText;

		public Activity(String details, String state, long start, String imageText) {
			this.details = details;
			this.state = state;
			this.start = start;
			this.imageText = imageText;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Activity)) {
				return false;
			}
			Activity a = (Activity) o;
			return start == a.start && Objects.equals(details, a.details) && Objects.equals(state, a.state) && Objects.equals(imageText, a.imageText);
		}

		@Override
		public int hashCode() {
			return Objects.hash(details, state, start, imageText);
		}

		@Override
		public String toString() {
			return details + " / " + state;
		}
	}

	private final String appId;
	private volatile Activity wanted;
	private volatile boolean running;
	private Thread thread;

	public DiscordClient(String appId) {
		this.appId = appId == null ? "" : appId.trim();
	}

	/** Whether there is a Discord application to show the activity as. */
	public boolean configured() {
		return appId.matches("\\d{15,22}");
	}

	public synchronized void start() {
		if (!configured() || running) {
			return;
		}
		running = true;
		thread = new Thread(this::run, "Rago Discord");
		thread.setDaemon(true);
		thread.start();
	}

	/** Stops and clears the activity (closing the connection clears it in Discord). */
	public synchronized void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
	}

	/** The activity to show; sent when it changes. Null shows nothing but "Rago Client". */
	public void want(Activity activity) {
		wanted = activity;
	}

	private void run() {
		DiscordIpc ipc = null;
		Activity sent = null;
		boolean sentOnce = false;
		long lastSend = 0;
		long retryAt = 0;
		String pid = pid();
		try {
			while (running) {
				long now = System.currentTimeMillis();
				if (ipc == null && now >= retryAt) {
					ipc = DiscordIpc.open();
					if (ipc != null) {
						long refused = handshake(ipc);
						if (refused > 0) {
							ipc.close();
							ipc = null;
							retryAt = now + refused;
						} else {
							sentOnce = false;
						}
					} else {
						retryAt = now + RETRY_MS;
					}
				}
				Activity w = wanted;
				if (ipc != null && (!sentOnce || !Objects.equals(w, sent)) && now - lastSend >= MIN_GAP_MS) {
					try {
						ipc.send(DiscordIpc.FRAME, setActivity(pid, w, UUID.randomUUID().toString()));
						reply(ipc);
						sent = w;
						sentOnce = true;
						lastSend = now;
					} catch (IOException e) {
						LOG.info("Discord: connection closed ({})", e.getMessage());
						ipc.close();
						ipc = null;
						retryAt = now + RETRY_MS;
					}
				}
				Thread.sleep(500);
			}
		} catch (InterruptedException e) {
			// stopped
		} finally {
			if (ipc != null) {
				ipc.close();
			}
		}
	}

	/** Says hello as the application; returns 0 when Discord is ready, or how long to wait before trying again. */
	private long handshake(DiscordIpc ipc) {
		try {
			JsonObject hello = new JsonObject();
			hello.addProperty("v", 1);
			hello.addProperty("client_id", appId);
			ipc.send(DiscordIpc.HANDSHAKE, hello.toString());
			int[] op = new int[1];
			String json = ipc.read(op);
			if (op[0] == DiscordIpc.FRAME && json.contains("\"READY\"")) {
				LOG.info("Discord: connected");
				return 0;
			}
			LOG.warn("Discord refused Rago's application: {}", json);
			return REFUSED_RETRY_MS;
		} catch (IOException | RuntimeException e) {
			return RETRY_MS;
		}
	}

	/** Reads frames until the answer to the last command, answering Discord's pings on the way. */
	private static void reply(DiscordIpc ipc) throws IOException {
		int[] op = new int[1];
		for (int i = 0; i < 8; i++) {
			String json = ipc.read(op);
			if (op[0] == DiscordIpc.PING) {
				ipc.send(DiscordIpc.PONG, json);
			} else if (op[0] == DiscordIpc.CLOSE) {
				throw new IOException("Discord closed: " + json);
			} else {
				if (json.contains("\"ERROR\"")) {
					LOG.warn("Discord: {}", json);
				}
				return;
			}
		}
	}

	/** The SET_ACTIVITY command for {@code activity} (null clears it). */
	static String setActivity(String pid, Activity activity, String nonce) {
		JsonObject args = new JsonObject();
		args.addProperty("pid", Long.parseLong(pid));
		if (activity == null) {
			args.add("activity", JsonNull.INSTANCE);
		} else {
			JsonObject a = new JsonObject();
			if (activity.details != null && !activity.details.isEmpty()) {
				a.addProperty("details", limit(activity.details));
			}
			if (activity.state != null && !activity.state.isEmpty()) {
				a.addProperty("state", limit(activity.state));
			}
			if (activity.start > 0) {
				JsonObject t = new JsonObject();
				t.addProperty("start", activity.start);
				a.add("timestamps", t);
			}
			JsonObject assets = new JsonObject();
			assets.addProperty("large_image", "rago");
			if (activity.imageText != null && !activity.imageText.isEmpty()) {
				assets.addProperty("large_text", limit(activity.imageText));
			}
			a.add("assets", assets);
			a.addProperty("instance", false);
			args.add("activity", a);
		}
		JsonObject cmd = new JsonObject();
		cmd.addProperty("cmd", "SET_ACTIVITY");
		cmd.add("args", args);
		cmd.addProperty("nonce", nonce);
		return cmd.toString();
	}

	/** Discord takes 2 to 128 characters per line. */
	static String limit(String s) {
		String t = s.length() > 128 ? s.substring(0, 127) + "…" : s;
		return t.length() < 2 ? t + " " : t;
	}

	private static String pid() {
		String name = ManagementFactory.getRuntimeMXBean().getName();
		int at = name.indexOf('@');
		String pid = at > 0 ? name.substring(0, at) : name;
		return pid.matches("\\d+") ? pid : "0";
	}

	/** For tests: Discord's answer as a JSON object. */
	static JsonObject parse(String json) {
		return new JsonParser().parse(json).getAsJsonObject();
	}
}
