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
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * The looping video behind the main menu. The clip ships as numbered JPEG frames (already scaled
 * down, lightly blurred and cross-faded so the loop has no seam); a background thread decodes the
 * next frames while the render thread uploads the one that is due into a single texture. The
 * thread stops a moment after the menu stops drawing, so nothing runs in game.
 */
final class VideoBackdrop {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final String DIR = "/assets/rago/video/menu/";
	private static final int WIDTH = 960;
	private static final int HEIGHT = 540;
	private static final int FRAMES = 288;
	private static final int FPS = 30;
	private static final int QUEUE = 3;
	private static final long IDLE_STOP_NS = 2_000_000_000L;

	private static boolean playing = true;
	private static boolean failed;
	private static int texture = -1;
	/** The frame shown before {@link #texture}'s, faded into it so the clip moves at the menu's frame rate. */
	private static int previous = -1;
	private static int previousFrame = -1;
	private static long uploadedNs;
	private static boolean smooth = true;
	private static volatile int shownFrame = -1;
	private static long startNs;
	private static volatile long lastDrawNs;
	private static volatile int wanted;
	private static Thread decoder;

	/** Decoded frames waiting for upload: slot i holds frame {@code slotFrame[i]}, or -1. */
	private static final ByteBuffer[] slots = new ByteBuffer[QUEUE];
	private static final int[] slotFrame = new int[QUEUE];
	private static final Object LOCK = new Object();
	private static byte[][] jpegs;

	private VideoBackdrop() {
	}

	/** Whether the clip moves; when off the first frame stays as a still picture. */
	static void setPlaying(boolean on) {
		playing = on;
	}

	/** Whether each frame fades into the next (smooth at any frame rate) or is shown as it comes. */
	static void setSmooth(boolean on) {
		smooth = on;
	}

	/**
	 * The frame before the one {@link #textureId} shows, or -1: drawn first, with the current one
	 * over it at {@link #mix}, it turns the clip's 30 frames a second into a steady movement.
	 */
	static int previousTextureId() {
		return smooth && playing && previousFrame >= 0 ? previous : -1;
	}

	/** How far (0..1) the current frame has faded in over the previous one: one frame's time after it came. */
	static float mix() {
		return Math.max(0, Math.min(1, (System.nanoTime() - uploadedNs) * FPS / 1e9f));
	}

	static float aspect() {
		return WIDTH / (float) HEIGHT;
	}

	/** The texture showing the frame that is due now, or -1 when the video cannot be shown. */
	static int textureId() {
		if (failed) {
			return -1;
		}
		long now = System.nanoTime();
		if (now - lastDrawNs > IDLE_STOP_NS) {
			// the menu was away; restart the clock instead of jumping ahead
			startNs = now - (long) (Math.max(0, shownFrame) * 1e9 / FPS);
		}
		lastDrawNs = now;
		if (texture < 0) {
			texture = frameTexture();
			previous = frameTexture();
		}
		int due = playing ? (int) ((now - startNs) * FPS / 1_000_000_000L % FRAMES) : 0;
		wanted = due;
		ensureDecoder();
		upload(due);
		return shownFrame < 0 ? -1 : texture;
	}

	private static int frameTexture() {
		int id = GlStateManager.generateTexture();
		GlStateManager.bindTexture(id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, WIDTH, HEIGHT, 0, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		return id;
	}

	/**
	 * Uploads the newest decoded frame that is not ahead of {@code due}, into the texture that held
	 * the older of the two frames kept; the one shown until now becomes the previous frame.
	 */
	private static void upload(int due) {
		int best = -1;
		synchronized (LOCK) {
			int bestAge = Integer.MAX_VALUE;
			for (int i = 0; i < QUEUE; i++) {
				if (slotFrame[i] < 0) {
					continue;
				}
				int age = Math.floorMod(due - slotFrame[i], FRAMES);
				if (age > FRAMES / 2) {
					continue; // decoded ahead of time, keep it for later
				}
				if (age < bestAge) {
					bestAge = age;
					best = i;
				}
			}
			if (best < 0 || slotFrame[best] == shownFrame) {
				return;
			}
			int older = previous;
			previous = texture;
			texture = older;
			previousFrame = shownFrame;
			GlStateManager.bindTexture(texture);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, WIDTH, HEIGHT, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, slots[best]);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
			// a jump (the menu was away, or the decoder fell behind) is not faded
			int step = Math.floorMod(slotFrame[best] - shownFrame, FRAMES);
			if (shownFrame < 0 || step != 1) {
				previousFrame = -1;
			}
			shownFrame = slotFrame[best];
			uploadedNs = System.nanoTime();
			// frames at or before the one shown are no longer needed
			for (int i = 0; i < QUEUE; i++) {
				if (slotFrame[i] >= 0 && Math.floorMod(shownFrame - slotFrame[i], FRAMES) <= FRAMES / 2) {
					slotFrame[i] = -1;
				}
			}
			LOCK.notifyAll();
		}
	}

	private static void ensureDecoder() {
		if (decoder != null && decoder.isAlive()) {
			return;
		}
		synchronized (LOCK) {
			for (int i = 0; i < QUEUE; i++) {
				slotFrame[i] = -1;
				if (slots[i] == null) {
					slots[i] = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 3);
				}
			}
		}
		if (shownFrame < 0) {
			startNs = System.nanoTime();
		}
		decoder = new Thread(VideoBackdrop::decodeLoop, "Rago menu video");
		decoder.setDaemon(true);
		decoder.setPriority(Thread.NORM_PRIORITY - 1);
		decoder.start();
	}

	private static void decodeLoop() {
		ImageReader reader = null;
		try {
			if (jpegs == null) {
				jpegs = load();
			}
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
			if (!readers.hasNext()) {
				throw new IOException("no JPEG decoder");
			}
			reader = readers.next();
			BufferedImage target = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
			ImageReadParam param = reader.getDefaultReadParam();
			param.setDestination(target);
			byte[] pixels = ((DataBufferByte) target.getRaster().getDataBuffer()).getData();
			int next = wanted;
			while (System.nanoTime() - lastDrawNs < IDLE_STOP_NS) {
				if (!playing) {
					next = 0;
					if (shownFrame == 0 || holds(0)) {
						synchronized (LOCK) {
							LOCK.wait(50);
						}
						continue;
					}
				} else {
					int due = wanted;
					int lead = Math.floorMod(next - due, FRAMES);
					if (lead > FRAMES / 2) {
						next = due; // fell behind: skip to the frame that is due
					} else if (lead >= QUEUE) {
						synchronized (LOCK) {
							LOCK.wait(20);
						}
						continue;
					}
				}
				int slot = freeSlot();
				if (slot < 0) {
					synchronized (LOCK) {
						LOCK.wait(20);
					}
					continue;
				}
				reader.setInput(new MemoryCacheImageInputStream(new ByteArrayInputStream(jpegs[next])), true, true);
				reader.read(0, param);
				synchronized (LOCK) {
					ByteBuffer buf = slots[slot];
					buf.clear();
					buf.put(pixels, 0, WIDTH * HEIGHT * 3);
					buf.flip();
					slotFrame[slot] = next;
				}
				next = (next + 1) % FRAMES;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable e) {
			LOG.warn("Menu video disabled, falling back to the still backdrop", e);
			failed = true;
		} finally {
			if (reader != null) {
				reader.dispose();
			}
			// the menu is gone: give the memory back, it is read again next time
			jpegs = null;
			synchronized (LOCK) {
				for (int i = 0; i < QUEUE; i++) {
					slotFrame[i] = -1;
					slots[i] = null;
				}
			}
		}
	}

	private static boolean holds(int frame) {
		synchronized (LOCK) {
			for (int f : slotFrame) {
				if (f == frame) {
					return true;
				}
			}
		}
		return false;
	}

	private static int freeSlot() {
		synchronized (LOCK) {
			for (int i = 0; i < QUEUE; i++) {
				if (slotFrame[i] < 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/** Reads every frame's JPEG bytes once (about 9 MB), so decoding never touches the jar again. */
	private static byte[][] load() throws IOException {
		byte[][] out = new byte[FRAMES][];
		byte[] buf = new byte[64 * 1024];
		for (int i = 0; i < FRAMES; i++) {
			String name = DIR + String.format("%04d.jpg", i + 1);
			try (InputStream in = VideoBackdrop.class.getResourceAsStream(name)) {
				if (in == null) {
					throw new IOException("missing " + name);
				}
				ByteArrayOutputStream bytes = new ByteArrayOutputStream(48 * 1024);
				int n;
				while ((n = in.read(buf)) > 0) {
					bytes.write(buf, 0, n);
				}
				out[i] = bytes.toByteArray();
			}
		}
		return out;
	}
}
