package gg.rago.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.core.RagoClient;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The music playing on this PC, from Windows' media controls (Spotify, Deezer, a browser...), read by
 * a small PowerShell script ({@code assets/rago/music/rago-music.ps1}) in its own process at low
 * priority: it writes a JSON line when something changes, this class reads it on its own thread and
 * keeps the latest {@link Track}. Nothing of it runs on the game's thread but reading that field and
 * uploading a new cover (128 x 128) once per track. Commands (play/pause, next, previous) go the other
 * way, one a line; when the game closes, the script's input ends and it stops.
 *
 * <p>{@code -Drago.test.music=<command>} runs another command instead, on any system (tests).
 */
public final class MediaSession {
	/** The cover's size once scaled, in pixels. */
	public static final int COVER = 128;
	private static final long RETRY_MS = 30_000;
	private static final int MAX_STARTS = 5;

	/** What is playing; {@link #NONE} when nothing is. */
	public static final class Track {
		public final String title;
		public final String artist;
		public final boolean playing;
		/** Seconds into the track when this was read, and the track's length (0: not given). */
		public final double position;
		public final double duration;
		/** When this was read (System.nanoTime). */
		public final long readAt;
		public final int coverId;
		/** The cover's file, "" for none. */
		final String cover;

		Track(String title, String artist, boolean playing, double position, double duration, long readAt, int coverId,
			  String cover) {
			this.title = title;
			this.artist = artist;
			this.playing = playing;
			this.position = position;
			this.duration = duration;
			this.readAt = readAt;
			this.coverId = coverId;
			this.cover = cover;
		}

		/** Seconds into the track now: moved on by itself while it plays. */
		public double positionAt(long now) {
			double p = playing ? position + (now - readAt) / 1e9 : position;
			return duration > 0 ? Math.min(p, duration) : p;
		}
	}

	public static final Track NONE = new Track("", "", false, 0, 0, 0, 0, "");

	private volatile Track track = NONE;
	/** A decoded cover waiting to be uploaded on the game's thread, and its id. */
	private volatile int[] coverPixels;
	private volatile int coverPixelsId = -1;
	private volatile boolean unavailable;

	private Process process;
	private Writer commands;
	private Thread reader;
	private int starts;
	private long lastStart;

	/** Whether this system can show the music (Windows, or a test command). */
	public static boolean supported() {
		return testCommand() != null || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	private static String testCommand() {
		String c = System.getProperty("rago.test.music");
		return c == null || c.trim().isEmpty() ? null : c;
	}

	public Track track() {
		return track;
	}

	/** True once the script could not start or kept failing: the HUD says so. */
	public boolean unavailable() {
		return unavailable;
	}

	/** Takes the cover decoded since the last call, if any (game thread). */
	public int[] takeCover(int[] idOut) {
		int[] pixels = coverPixels;
		if (pixels == null) {
			return null;
		}
		coverPixels = null;
		idOut[0] = coverPixelsId;
		return pixels;
	}

	/** Starts the script if it is not running (again after a crash, at most every 30 s and 5 times). */
	public synchronized void ensureRunning(File folder) {
		if (process != null && process.isAlive()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (starts >= MAX_STARTS || now - lastStart < RETRY_MS && starts > 0 || !supported()) {
			if (starts >= MAX_STARTS) {
				unavailable = true;
			}
			return;
		}
		starts++;
		lastStart = now;
		try {
			folder.mkdirs();
			List<String> cmd = new ArrayList<>();
			String test = testCommand();
			if (test != null) {
				for (String part : test.trim().split("\\s+")) {
					cmd.add(part);
				}
				cmd.add(folder.getAbsolutePath());
			} else {
				File script = new File(folder, "rago-music.ps1");
				extractScript(script);
				cmd.add("powershell.exe");
				cmd.add("-NoProfile");
				cmd.add("-NonInteractive");
				cmd.add("-ExecutionPolicy");
				cmd.add("Bypass");
				cmd.add("-WindowStyle");
				cmd.add("Hidden");
				cmd.add("-File");
				cmd.add(script.getAbsolutePath());
				cmd.add(folder.getAbsolutePath());
			}
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectError(ProcessBuilder.Redirect.to(new File(folder, "helper.log")));
			Process p = pb.start();
			process = p;
			commands = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
			reader = new Thread(() -> read(p), "Rago music");
			reader.setDaemon(true);
			reader.setPriority(Thread.MIN_PRIORITY);
			reader.start();
			unavailable = false;
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.warn("Music HUD: the media reader could not start ({})", e.toString());
			process = null;
			unavailable = starts >= MAX_STARTS;
		}
	}

	/** Stops the script; the next {@link #ensureRunning} starts it again. */
	public synchronized void stop() {
		Process p = process;
		process = null;
		if (p != null) {
			try {
				commands.close();
			} catch (IOException ignored) {
				// it is going away anyway
			}
			p.destroy();
		}
		track = NONE;
		starts = 0;
	}

	/** Sends "toggle", "next" or "previous"; nothing happens when the script is not running. */
	public synchronized void send(String command) {
		if (process == null || commands == null) {
			return;
		}
		try {
			commands.write(command + "\n");
			commands.flush();
		} catch (IOException e) {
			// the script has stopped: ensureRunning starts it again
		}
	}

	private static void extractScript(File target) throws IOException {
		try (InputStream in = MediaSession.class.getResourceAsStream("/assets/rago/music/rago-music.ps1")) {
			if (in == null) {
				throw new IOException("rago-music.ps1 is missing from the jar");
			}
			File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
			Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// --- the script's thread ---

	private void read(Process p) {
		int lastCover = -1;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				if (!line.isEmpty() && line.charAt(0) == '﻿') {
					line = line.substring(1);
				}
				Track t = parse(line, System.nanoTime());
				if (t == null) {
					continue;
				}
				if (t != NONE && t.coverId != lastCover) {
					lastCover = t.coverId;
					coverPixels = null;
					int[] pixels = t.cover.isEmpty() ? null : decode(new File(t.cover));
					coverPixelsId = t.coverId;
					coverPixels = pixels != null ? pixels : new int[0];
				}
				track = t;
			}
		} catch (IOException | RuntimeException e) {
			// the script stopped or wrote something else: it is started again
		} finally {
			synchronized (this) {
				if (process == p) {
					track = NONE;
				}
			}
		}
	}

	private static final JsonParser JSON = new JsonParser();

	/** A track from one of the script's lines, {@link #NONE} for nothing playing, null for anything else. */
	static Track parse(String line, long now) {
		JsonElement e;
		try {
			e = JSON.parse(line);
		} catch (RuntimeException bad) {
			return null;
		}
		if (!e.isJsonObject()) {
			return null;
		}
		JsonObject o = e.getAsJsonObject();
		if (o.has("none") || o.has("error")) {
			return NONE;
		}
		String title = string(o, "title");
		if (title.isEmpty()) {
			return NONE;
		}
		return new Track(title, string(o, "artist"), o.has("playing") && o.get("playing").getAsBoolean(),
			number(o, "position"), number(o, "duration"), now, (int) number(o, "coverId"), string(o, "cover"));
	}

	private static String string(JsonObject o, String key) {
		JsonElement v = o.get(key);
		return v == null || v.isJsonNull() ? "" : v.getAsString();
	}

	private static double number(JsonObject o, String key) {
		JsonElement v = o.get(key);
		try {
			return v == null || v.isJsonNull() ? 0 : v.getAsDouble();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** The cover scaled to {@link #COVER} square (cropped to a square first), as ARGB, or null. */
	static int[] decode(File file) {
		try {
			BufferedImage src = ImageIO.read(file);
			if (src == null) {
				return null;
			}
			int side = Math.min(src.getWidth(), src.getHeight());
			BufferedImage square = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
			BufferedImage out = new BufferedImage(COVER, COVER, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(square, 0, 0, COVER, COVER, null);
			g.dispose();
			return out.getRGB(0, 0, COVER, COVER, null, 0, COVER);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
