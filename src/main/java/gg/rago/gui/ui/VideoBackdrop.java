package gg.rago.gui.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * A looping video behind the menus. A clip that comes with Rago is one file of JPEG frames (already
 * scaled down, the loop's end cross-faded into its start so it has no seam); the player's own is a
 * folder of JPEG frames. A background thread decodes the next frames while the render thread
 * uploads the one that is due into a texture. The thread stops a moment after the menu stops
 * drawing the clip, its frames' memory goes with it, and its textures are given back a little
 * later, so nothing runs or stays in game.
 */
final class VideoBackdrop {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final int QUEUE = 3;
	private static final long IDLE_STOP_NS = 2_000_000_000L;
	/** The textures of a clip not drawn for this long are deleted; they are made again when it shows. */
	private static final long RELEASE_NS = 8_000_000_000L;
	/** The player's own video: at most this many frames, this wide (wider frames are read at a half or less). */
	static final int OWN_MAX_FRAMES = 900;
	static final int OWN_MAX_WIDTH = 1280;
	private static final long OWN_MAX_BYTES = 160L << 20;

	private static boolean playing = true;
	private static boolean smooth = true;
	private static final List<VideoBackdrop> ALL = new ArrayList<>();

	/** A clip's frames, read once each time it starts showing. */
	static final class Clip {
		final int width;
		final int height;
		final int fps;
		/** Read every n-th pixel of each frame (1, or more for the player's large frames). */
		final int subsample;
		final byte[][] jpegs;

		Clip(int width, int height, int fps, int subsample, byte[][] jpegs) {
			this.width = width;
			this.height = height;
			this.fps = fps;
			this.subsample = subsample;
			this.jpegs = jpegs;
		}
	}

	private final String name;
	private final Callable<Clip> source;
	private volatile Clip clip;
	private volatile boolean failed;
	private int fps = 24;
	private int frames = 1;
	private float aspect = 16 / 9f;
	private int texture = -1;
	private int texW;
	private int texH;
	/** The frame shown before {@link #texture}'s, faded into it so the clip moves at the menu's frame rate. */
	private int previous = -1;
	private int previousFrame = -1;
	private long uploadedNs;
	private volatile int shownFrame = -1;
	private long startNs;
	private volatile long lastDrawNs;
	private volatile int wanted;
	private Thread decoder;

	/** Decoded frames waiting for upload: slot i holds frame {@code slotFrame[i]}, or -1. */
	private final ByteBuffer[] slots = new ByteBuffer[QUEUE];
	private final int[] slotFrame = {-1, -1, -1};
	private final Object lock = new Object();

	private VideoBackdrop(String name, Callable<Clip> source) {
		this.name = name;
		this.source = source;
		synchronized (ALL) {
			ALL.add(this);
		}
	}

	/** A clip that comes with Rago: {@code /assets/rago/video/<id>.bin}. */
	static VideoBackdrop builtIn(String id) {
		String path = "/assets/rago/video/" + id + ".bin";
		return new VideoBackdrop(id, () -> {
			try (InputStream in = VideoBackdrop.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IOException("missing " + path);
				}
				return read(in);
			}
		});
	}

	/** The player's own video: the JPEG frames in {@code dir}, in name order. */
	static VideoBackdrop folder(File dir) {
		return new VideoBackdrop("own", () -> readFolder(dir));
	}

	/** Whether the clips move; when off the first frame stays as a still picture. */
	static void setPlaying(boolean on) {
		playing = on;
	}

	static boolean isPlaying() {
		return playing;
	}

	/** Whether each frame fades into the next (smooth at any frame rate) or is shown as it comes. */
	static void setSmooth(boolean on) {
		smooth = on;
	}

	/**
	 * The frame before the one {@link #textureId} shows, or -1: drawn first, with the current one
	 * over it at {@link #mix}, it turns the clip's frames into a steady movement.
	 */
	int previousTextureId() {
		return smooth && playing && previousFrame >= 0 ? previous : -1;
	}

	/** How far (0..1) the current frame has faded in over the previous one: one frame's time after it came. */
	float mix() {
		return Math.max(0, Math.min(1, (System.nanoTime() - uploadedNs) * fps / 1e9f));
	}

	float aspect() {
		return aspect;
	}

	boolean failed() {
		return failed;
	}

	/** The texture showing the frame that is due now, or -1 while the clip loads or when it cannot be shown. */
	int textureId() {
		if (failed) {
			return -1;
		}
		long now = System.nanoTime();
		if (now - lastDrawNs > IDLE_STOP_NS) {
			// the menu was away; restart the clock instead of jumping ahead
			startNs = now - (long) (Math.max(0, shownFrame) * 1e9 / fps);
		}
		lastDrawNs = now;
		ensureDecoder();
		Clip c = clip;
		if (c == null) {
			// loading: the last frame shown, if the textures are still there
			return texture < 0 || shownFrame < 0 ? -1 : texture;
		}
		if (texture < 0 || texW != c.width || texH != c.height) {
			deleteTextures();
			texW = c.width;
			texH = c.height;
			texture = frameTexture(texW, texH);
			previous = frameTexture(texW, texH);
		}
		fps = c.fps;
		frames = c.jpegs.length;
		aspect = c.width / (float) c.height;
		int due = playing ? (int) ((now - startNs) * fps / 1_000_000_000L % frames) : 0;
		wanted = due;
		upload(due);
		return shownFrame < 0 ? -1 : texture;
	}

	/** Gives back the textures of clips not drawn for a while (call on the render thread). */
	static void releaseIdle() {
		long now = System.nanoTime();
		synchronized (ALL) {
			for (VideoBackdrop v : ALL) {
				if (v.texture >= 0 && now - v.lastDrawNs > RELEASE_NS) {
					v.deleteTextures();
				}
			}
		}
	}

	/** Forgets the clip for good (the player's own, changed or removed): textures now, frames when its thread ends. */
	void dispose() {
		deleteTextures();
		lastDrawNs = 0;
		synchronized (ALL) {
			ALL.remove(this);
		}
	}

	private void deleteTextures() {
		if (texture >= 0) {
			GlStateManager.deleteTexture(texture);
		}
		if (previous >= 0) {
			GlStateManager.deleteTexture(previous);
		}
		texture = -1;
		previous = -1;
		shownFrame = -1;
		previousFrame = -1;
	}

	private static int frameTexture(int w, int h) {
		int id = GlStateManager.generateTexture();
		GlStateManager.bindTexture(id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		return id;
	}

	/**
	 * Uploads the newest decoded frame that is not ahead of {@code due}, into the texture that held
	 * the older of the two frames kept; the one shown until now becomes the previous frame.
	 */
	private void upload(int due) {
		synchronized (lock) {
			int best = -1;
			int bestAge = Integer.MAX_VALUE;
			for (int i = 0; i < QUEUE; i++) {
				if (slotFrame[i] < 0) {
					continue;
				}
				int age = Math.floorMod(due - slotFrame[i], frames);
				if (age > frames / 2) {
					continue; // decoded ahead of time, keep it for later
				}
				if (age < bestAge) {
					bestAge = age;
					best = i;
				}
			}
			if (best < 0 || slotFrame[best] == shownFrame || slots[best] == null) {
				return;
			}
			int older = previous;
			previous = texture;
			texture = older;
			previousFrame = shownFrame;
			GlStateManager.bindTexture(texture);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, texW, texH, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, slots[best]);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
			// a jump (the menu was away, or the decoder fell behind) is not faded
			int step = Math.floorMod(slotFrame[best] - shownFrame, frames);
			if (shownFrame < 0 || step != 1) {
				previousFrame = -1;
			}
			shownFrame = slotFrame[best];
			uploadedNs = System.nanoTime();
			// frames at or before the one shown are no longer needed
			for (int i = 0; i < QUEUE; i++) {
				if (slotFrame[i] >= 0 && Math.floorMod(shownFrame - slotFrame[i], frames) <= frames / 2) {
					slotFrame[i] = -1;
				}
			}
			lock.notifyAll();
		}
	}

	private void ensureDecoder() {
		if (decoder != null && decoder.isAlive()) {
			return;
		}
		if (shownFrame < 0) {
			startNs = System.nanoTime();
		}
		decoder = new Thread(this::decodeLoop, "Rago menu video " + name);
		decoder.setDaemon(true);
		decoder.setPriority(Thread.NORM_PRIORITY - 1);
		decoder.start();
	}

	private void decodeLoop() {
		ImageReader reader = null;
		try {
			Clip c = source.call();
			int n = c.jpegs.length;
			synchronized (lock) {
				for (int i = 0; i < QUEUE; i++) {
					slotFrame[i] = -1;
					slots[i] = BufferUtils.createByteBuffer(c.width * c.height * 3);
				}
			}
			clip = c;
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
			if (!readers.hasNext()) {
				throw new IOException("no JPEG decoder");
			}
			reader = readers.next();
			BufferedImage target = new BufferedImage(c.width, c.height, BufferedImage.TYPE_3BYTE_BGR);
			ImageReadParam param = reader.getDefaultReadParam();
			param.setDestination(target);
			if (c.subsample > 1) {
				param.setSourceSubsampling(c.subsample, c.subsample, 0, 0);
			}
			byte[] pixels = ((DataBufferByte) target.getRaster().getDataBuffer()).getData();
			int next = wanted % n;
			while (System.nanoTime() - lastDrawNs < IDLE_STOP_NS) {
				if (!playing) {
					next = 0;
					if (shownFrame == 0 || holds(0)) {
						synchronized (lock) {
							lock.wait(50);
						}
						continue;
					}
				} else {
					int due = wanted % n;
					int lead = Math.floorMod(next - due, n);
					if (lead > n / 2) {
						next = due; // fell behind: skip to the frame that is due
					} else if (lead >= QUEUE) {
						synchronized (lock) {
							lock.wait(20);
						}
						continue;
					}
				}
				int slot = freeSlot();
				if (slot < 0) {
					synchronized (lock) {
						lock.wait(20);
					}
					continue;
				}
				reader.setInput(new MemoryCacheImageInputStream(new ByteArrayInputStream(c.jpegs[next])), true, true);
				reader.read(0, param);
				synchronized (lock) {
					ByteBuffer buf = slots[slot];
					buf.clear();
					buf.put(pixels, 0, c.width * c.height * 3);
					buf.flip();
					slotFrame[slot] = next;
				}
				next = (next + 1) % n;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable e) {
			LOG.warn("Menu video {} disabled, falling back to its still picture", name, e);
			failed = true;
		} finally {
			if (reader != null) {
				reader.dispose();
			}
			// the menu is gone: give the memory back, it is read again next time
			clip = null;
			synchronized (lock) {
				for (int i = 0; i < QUEUE; i++) {
					slotFrame[i] = -1;
					slots[i] = null;
				}
			}
		}
	}

	private boolean holds(int frame) {
		synchronized (lock) {
			for (int f : slotFrame) {
				if (f == frame) {
					return true;
				}
			}
		}
		return false;
	}

	private int freeSlot() {
		synchronized (lock) {
			for (int i = 0; i < QUEUE; i++) {
				if (slotFrame[i] < 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Reads a clip file: "RGV1", then width, height, frames a second and frame count (big-endian
	 * ints), each frame's length, then the frames as JPEGs.
	 */
	static Clip read(InputStream raw) throws IOException {
		DataInputStream in = new DataInputStream(raw);
		byte[] magic = new byte[4];
		in.readFully(magic);
		if (!"RGV1".equals(new String(magic, StandardCharsets.US_ASCII))) {
			throw new IOException("not a Rago clip");
		}
		int w = in.readInt();
		int h = in.readInt();
		int fps = in.readInt();
		int n = in.readInt();
		if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || fps <= 0 || fps > 120 || n <= 0 || n > 10_000) {
			throw new IOException("bad clip header");
		}
		int[] lengths = new int[n];
		for (int i = 0; i < n; i++) {
			lengths[i] = in.readInt();
			if (lengths[i] <= 0 || lengths[i] > 4 << 20) {
				throw new IOException("bad frame length");
			}
		}
		byte[][] jpegs = new byte[n][];
		for (int i = 0; i < n; i++) {
			jpegs[i] = new byte[lengths[i]];
			in.readFully(jpegs[i]);
		}
		return new Clip(w, h, fps, 1, jpegs);
	}

	/** The JPEG frames of a folder, in name order, at the size of the first (read smaller when wide). */
	static Clip readFolder(File dir) throws IOException {
		File[] files = dir.listFiles((d, n) -> {
			String lower = n.toLowerCase(Locale.ROOT);
			return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
		});
		if (files == null || files.length == 0) {
			throw new IOException("no JPEG frames in " + dir);
		}
		Arrays.sort(files);
		int fps = ownFps(dir);
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
		if (!readers.hasNext()) {
			throw new IOException("no JPEG decoder");
		}
		ImageReader reader = readers.next();
		try {
			List<byte[]> kept = new ArrayList<>();
			long total = 0;
			int w = -1;
			int h = -1;
			for (File f : files) {
				if (kept.size() >= OWN_MAX_FRAMES || total + f.length() > OWN_MAX_BYTES) {
					break;
				}
				byte[] bytes = Files.readAllBytes(f.toPath());
				int fw;
				int fh;
				try (ImageInputStream s = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
					reader.setInput(s, true, true);
					fw = reader.getWidth(0);
					fh = reader.getHeight(0);
				} catch (IOException e) {
					continue; // not a readable JPEG: left out
				}
				if (w < 0) {
					w = fw;
					h = fh;
				} else if (fw != w || fh != h) {
					continue; // every frame has the first one's size
				}
				kept.add(bytes);
				total += bytes.length;
			}
			if (kept.isEmpty()) {
				throw new IOException("no readable frame in " + dir);
			}
			int sub = subsample(w);
			return new Clip((w + sub - 1) / sub, (h + sub - 1) / sub, fps, sub, kept.toArray(new byte[0][]));
		} finally {
			reader.dispose();
		}
	}

	/** How many pixels to step so a frame {@code width} wide is read at most {@link #OWN_MAX_WIDTH} wide. */
	static int subsample(int width) {
		return Math.max(1, (width + OWN_MAX_WIDTH - 1) / OWN_MAX_WIDTH);
	}

	/** The frames a second of the player's video: the number in fps.txt beside the frames, else 24. */
	static int ownFps(File dir) {
		File f = new File(dir, "fps.txt");
		if (f.isFile()) {
			try {
				int v = Integer.parseInt(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
				if (v >= 1 && v <= 60) {
					return v;
				}
			} catch (IOException | NumberFormatException ignored) {
				// the default below
			}
		}
		return 24;
	}
}
