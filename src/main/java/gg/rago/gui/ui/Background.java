package gg.rago.gui.ui;

import gg.rago.render.StaticTexture;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * The menus' background, chosen on the home screen: the night desert video; a neon grid, a starry
 * night or plain dark with soft lights, drawn and moved by Rago as the menu is drawn (no video to
 * decode, nothing to load: a few hundred shapes in a handful of draw calls); or an image of the
 * player's own (config/rago-background.png or .jpg). Changing it fades the new one in over the old one.
 */
public final class Background {
	public static final int NIGHT_DESERT = 0;
	public static final int NEON = 1;
	public static final int STARS = 2;
	public static final int DARK = 3;
	public static final int OWN_IMAGE = 4;
	/** The names of the backgrounds, in the order of their ids. */
	public static final String[] KEYS = {"rago.value.bg_night_desert", "rago.value.bg_neon", "rago.value.bg_stars",
		"rago.value.bg_dark", "rago.value.bg_own"};
	/** The names the player's own image may have, in the config folder. */
	public static final String[] OWN_NAMES = {"rago-background.png", "rago-background.jpg", "rago-background.jpeg"};
	/** Own images are scaled down to this width at most: the background is blurred and darkened anyway. */
	private static final int OWN_MAX_WIDTH = 1920;
	private static final float FADE_MS = 450;

	private static final Logger LOG = LogManager.getLogger("Rago");

	private static int current = NIGHT_DESERT;
	private static int previous = -1;
	private static long changedNs;
	private static Path configDir;

	/** The player's image: its texture once loaded, the file and its date it was read from. */
	private static StaticTexture own;
	private static float ownAspect = 16 / 9f;
	private static File ownFile;
	private static long ownModified;
	private static long ownCheckedNs;
	private static volatile BufferedImage ownLoaded;
	private static volatile boolean ownLoading;

	private Background() {
	}

	/** Where the player's own image is looked for. */
	public static void setConfigDir(Path dir) {
		configDir = dir;
	}

	/** The background shown, without fading (the saved choice, when the game starts). */
	public static void init(int id) {
		current = clamp(id);
		previous = -1;
	}

	/** Changes the background, fading the new one in. */
	public static void set(int id) {
		id = clamp(id);
		if (id == current) {
			return;
		}
		previous = current;
		current = id;
		changedNs = System.nanoTime();
		if (id == OWN_IMAGE) {
			ownCheckedNs = 0;
		}
	}

	public static int get() {
		return current;
	}

	private static int clamp(int id) {
		return id < 0 || id >= KEYS.length ? NIGHT_DESERT : id;
	}

	/** The player's own image file, or null when there is none. */
	public static File ownFile() {
		if (configDir == null) {
			return null;
		}
		for (String name : OWN_NAMES) {
			File f = configDir.resolve(name).toFile();
			if (f.isFile()) {
				return f;
			}
		}
		return null;
	}

	/** Whether the player's own image is there to show (looked for again every few seconds). */
	public static boolean hasOwnImage() {
		checkOwn();
		return own != null || ownLoading || ownLoaded != null;
	}

	/**
	 * Covers the screen, moved by ({@code dx}, {@code dy}) and zoomed by {@code zoom} for the
	 * parallax, darkened by {@code dim} (0..1); while a change fades, the new background is drawn
	 * over the old one.
	 */
	static void drawScreen(float w, float h, float dx, float dy, float zoom, float dim) {
		float fade = previous >= 0 ? Math.min(1, (System.nanoTime() - changedNs) / (FADE_MS * 1_000_000f)) : 1;
		if (fade < 1) {
			draw(previous, 0, 0, w, h, dx, dy, zoom, 1, dim);
			draw(current, 0, 0, w, h, dx, dy, zoom, Anim.easeOut(fade), dim);
		} else {
			previous = -1;
			draw(current, 0, 0, w, h, dx, dy, zoom, 1, dim);
		}
	}

	/**
	 * Draws background {@code id} covering the box (x, y, w, h) at {@code alpha}; what lies outside
	 * the box is drawn too, so a box smaller than the screen needs a {@link Ui#scissor}.
	 */
	public static void draw(int id, float x, float y, float w, float h, float dx, float dy, float zoom, float alpha) {
		draw(id, x, y, w, h, dx, dy, zoom, alpha, 0);
	}

	/**
	 * {@link #draw} darkened by {@code dim}: the video and the image are drawn darker (their colour
	 * multiplied, no extra layer over the whole screen), the drawn ones get a dark layer.
	 */
	private static void draw(int id, float x, float y, float w, float h, float dx, float dy, float zoom, float alpha, float dim) {
		int shade = Math.round(255 * (1 - Math.max(0, Math.min(1, dim))));
		int argb = Ui.alpha(0xFF000000 | shade << 16 | shade << 8 | shade, alpha);
		double t = System.nanoTime() / 1e9;
		// the drawn backgrounds move, and the image drifts slowly, while the menu video is allowed to move
		boolean drift = VideoBackdrop.isPlaying() && Anim.enabled();
		if (!drift) {
			t = 0;
		}
		switch (id) {
			case NIGHT_DESERT: {
				int video = VideoBackdrop.textureId();
				if (video < 0) {
					// until its first frame is ready (or when it cannot play)
					dark(x, y, w, h, alpha, t);
					break;
				}
				float[] box = cover(VideoBackdrop.aspect(), x, y, w, h, dx, dy, zoom);
				int before = VideoBackdrop.previousTextureId();
				float mix = before >= 0 ? VideoBackdrop.mix() : 1;
				if (mix < 1) {
					// the frame before, with the current one fading in over it: smooth at any frame rate
					Ui.texturedQuad(before, box[0], box[1], box[2], box[3], argb);
					Ui.texturedQuad(video, box[0], box[1], box[2], box[3], Ui.alpha(argb, mix * alpha));
				} else {
					Ui.texturedQuad(video, box[0], box[1], box[2], box[3], argb);
				}
				return;
			}
			case OWN_IMAGE:
				if (ownTexture() >= 0) {
					image(own.getGlTextureId(), ownAspect, x, y, w, h, dx, dy, zoom, argb, t);
					return;
				}
				dark(x, y, w, h, alpha, t);
				break;
			case NEON:
				neon(x, y, w, h, dx, alpha, t);
				break;
			case STARS:
				stars(x, y, w, h, dx, dy, alpha, t);
				break;
			default:
				dark(x, y, w, h, alpha, t);
		}
		if (dim > 0) {
			Ui.rect(x, y, w, h, Ui.alpha(0xFF050406, dim * alpha));
		}
	}

	/** A picture slowly moving and zooming in and out (by a few percent), for some life. */
	private static void image(int texture, float aspect, float x, float y, float w, float h, float dx, float dy, float zoom,
		int argb, double t) {
		zoom *= 1.04f + 0.025f * (float) Math.sin(t * 0.07);
		dx += (float) Math.sin(t * 0.045) * w * 0.012f;
		dy += (float) Math.cos(t * 0.034) * h * 0.01f;
		float[] box = cover(aspect, x, y, w, h, dx, dy, zoom);
		Ui.texturedQuad(texture, box[0], box[1], box[2], box[3], argb);
	}

	/** Plain dark with soft lights, Rago's red and a cold blue, drifting slowly. */
	private static void dark(float x, float y, float w, float h, float alpha, double t) {
		Ui.rect(x, y, w, h, Ui.alpha(0xFF0A090C, alpha));
		float a = (float) Math.sin(t * 0.11);
		float b = (float) Math.cos(t * 0.083);
		float big = Math.max(w, h);
		WorldRenderer r = Ui.beginGlows();
		Ui.batchGlow(r, x + w * (0.22f + 0.05f * a), y + h * (0.28f + 0.04f * b), big * 0.62f, Ui.alpha(0xFFE0262F, 0.20f * alpha));
		Ui.batchGlow(r, x + w * (0.82f - 0.04f * b), y + h * (0.78f + 0.05f * a), big * 0.55f, Ui.alpha(0xFF2A4BD8, 0.15f * alpha));
		Ui.batchGlow(r, x + w * 0.5f, y + h * 0.5f, big * 0.4f, Ui.alpha(0xFF3A1F4A, 0.10f * alpha));
		Ui.endBatch();
	}

	// ---- neon grid --------------------------------------------------------------------------------

	/** Lines of the grid going away (each side of the middle one) and across. */
	private static final int NEON_COLUMNS = 14;
	private static final int NEON_ROWS = 16;

	/**
	 * A grid of Rago-red light on dark ground running towards a glowing horizon, under a dusk sky:
	 * the lines across come slowly closer; the vanishing point follows the mouse a little.
	 */
	private static void neon(float x, float y, float w, float h, float dx, float alpha, double t) {
		float horizon = y + h * 0.6f;
		float cx = x + w / 2 + dx * 1.5f;
		float big = Math.max(w, h);
		float u = w / 1280f;
		Ui.gradient(x, y, w, horizon - y, Ui.alpha(0xFF050409, alpha), Ui.alpha(0xFF1C0712, alpha));
		Ui.gradient(x, horizon, w, y + h - horizon, Ui.alpha(0xFF0C0509, alpha), Ui.alpha(0xFF050306, alpha));

		WorldRenderer r = Ui.beginGlows();
		// the sun behind the horizon and the haze over it
		Ui.batchGlow(r, cx, horizon, h * 0.5f, Ui.alpha(0xFFE0262F, 0.6f * alpha));
		Ui.batchGlow(r, cx, horizon, big * 0.6f, Ui.alpha(0xFF8A1D4F, 0.3f * alpha));
		Ui.endBatch();

		float bottom = y + h;
		float depth = bottom - horizon;
		r = Ui.beginShapes();
		// lines going away, meeting at the vanishing point
		float spread = w * 0.16f;
		for (int i = -NEON_COLUMNS; i <= NEON_COLUMNS; i++) {
			float xb = cx + i * spread;
			float xt = cx + i * spread * 0.035f;
			float half = 1.5f * u;
			Ui.batchQuad(r, xt - half * 0.2f, horizon, xt + half * 0.2f, horizon, xb + half, bottom, xb - half, bottom,
				Ui.alpha(0xFFE0262F, 0.1f * alpha), Ui.alpha(0xFFFF4A55, 0.9f * alpha));
		}
		// lines across, closer as time goes
		float scroll = (float) (t * 0.35 % 1);
		for (int k = 0; k < NEON_ROWS; k++) {
			float z = 1 + (k + 1 - scroll) * 0.9f;
			float ly = horizon + depth / z;
			float near = 1 / z;
			float half = (0.5f + 1.8f * near) * u;
			int c = Ui.alpha(0xFFFF3B47, (0.12f + 0.85f * near) * alpha);
			Ui.batchQuad(r, x, ly - half, x + w, ly - half, x + w, ly + half, x, ly + half, c, c);
		}
		// the horizon, bright
		float hl = 1.2f * u;
		Ui.batchQuad(r, x, horizon - hl, x + w, horizon - hl, x + w, horizon + hl, x, horizon + hl,
			Ui.alpha(0xFFFF6A72, 0.9f * alpha), Ui.alpha(0xFFFF6A72, 0.9f * alpha));
		Ui.endBatch();
		// the ground fades into the dark at the bottom, the sky glows a little at the horizon
		Ui.gradient(x, bottom - depth * 0.3f, w, depth * 0.3f, 0x00050306, Ui.alpha(0xFF050306, 0.7f * alpha));
		Ui.gradient(x, horizon - h * 0.14f, w, h * 0.14f, 0x00E0262F, Ui.alpha(0xFFE0262F, 0.16f * alpha));
	}

	// ---- starry night -----------------------------------------------------------------------------

	private static final int STAR_COUNT = 170;
	/** Each star: x, y (0..1 of the box), size, twinkle speed, phase and how far it is (parallax). */
	private static final float[] STAR = new float[STAR_COUNT * 6];

	static {
		long seed = 0x5EED_5A60L;
		for (int i = 0; i < STAR.length; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			STAR[i] = (seed >>> 40) / (float) (1 << 24);
		}
	}

	/**
	 * Deep blue night with a red and violet glow, and stars twinkling and drifting slowly, the near
	 * ones faster (parallax with the mouse too); now and then a shooting star.
	 */
	private static void stars(float x, float y, float w, float h, float dx, float dy, float alpha, double t) {
		Ui.gradient(x, y, w, h, Ui.alpha(0xFF03040A, alpha), Ui.alpha(0xFF0D0A19, alpha));
		float big = Math.max(w, h);
		float u = w / 1280f;
		float a = (float) Math.sin(t * 0.05);
		WorldRenderer r = Ui.beginGlows();
		Ui.batchGlow(r, x + w * (0.28f + 0.04f * a), y + h * 0.36f, big * 0.48f, Ui.alpha(0xFF5B2A86, 0.26f * alpha));
		Ui.batchGlow(r, x + w * (0.78f - 0.03f * a), y + h * 0.66f, big * 0.42f, Ui.alpha(0xFFE0262F, 0.14f * alpha));
		Ui.batchGlow(r, x + w * 0.6f, y + h * 0.12f, big * 0.3f, Ui.alpha(0xFF2A4BD8, 0.12f * alpha));
		for (int i = 0; i < STAR_COUNT; i++) {
			int o = i * 6;
			float near = 0.3f + 0.7f * STAR[o + 5];
			float sx = (float) ((STAR[o] - t * 0.0035 * near) % 1);
			if (sx < 0) {
				sx += 1;
			}
			float px = x + sx * w + dx * near * 0.6f;
			float py = y + STAR[o + 1] * h + dy * near * 0.6f;
			float size = (0.9f + 2.1f * STAR[o + 2] * STAR[o + 2]) * near * u;
			float twinkle = 0.55f + 0.45f * (float) Math.sin(t * (0.6 + 2.2 * STAR[o + 3]) + STAR[o + 4] * 6.28);
			Ui.batchGlow(r, px, py, size * 2.6f, Ui.alpha(0xFFDDE4FF, twinkle * (0.35f + 0.65f * near) * alpha));
			if (STAR[o + 2] > 0.93f) {
				// the brightest ones get a halo
				Ui.batchGlow(r, px, py, size * 9, Ui.alpha(0xFF9FB4FF, 0.12f * twinkle * alpha));
			}
		}
		Ui.endBatch();
		// a shooting star every 11 seconds, across for 0.9 of a second
		double cycle = t % 11;
		if (cycle < 0.9 && t > 0) {
			int n = (int) (t / 11);
			float p = (float) (cycle / 0.9);
			float sx = x + w * (0.15f + 0.5f * frac(n * 0.618f));
			float sy = y + h * (0.08f + 0.25f * frac(n * 0.381f));
			float len = w * 0.16f;
			float hx = sx + p * w * 0.3f;
			float hy = sy + p * w * 0.12f;
			float fade = (float) Math.sin(p * Math.PI) * alpha;
			float tx = hx - len * 0.93f;
			float ty = hy - len * 0.37f;
			float half = 1.1f * u;
			int tail = Ui.alpha(0xFFFFFFFF, 0);
			int head = Ui.alpha(0xFFFFFFFF, 0.85f * fade);
			r = Ui.beginShapes();
			Ui.batchQuad(r, tx, ty, tail, hx, hy - half, head, hx, hy + half, head, tx, ty, tail);
			Ui.endBatch();
			r = Ui.beginGlows();
			Ui.batchGlow(r, hx, hy, 6 * u, Ui.alpha(0xFFFFFFFF, 0.8f * fade));
			Ui.endBatch();
		}
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	/** Where {@link #cover} puts its box (drawing happens on the render thread only). */
	private static final float[] BOX = new float[4];

	/** The box (x, y, w, h) a picture of {@code aspect} fills to cover the given box, in {@link #BOX}. */
	private static float[] cover(float aspect, float x, float y, float w, float h, float dx, float dy, float zoom) {
		float bw = Math.max(w, h * aspect) * zoom;
		float bh = bw / aspect;
		BOX[0] = x + (w - bw) / 2 + dx;
		BOX[1] = y + (h - bh) / 2 + dy;
		BOX[2] = bw;
		BOX[3] = bh;
		return BOX;
	}

	// ---- the player's own image ------------------------------------------------------------------

	/** Looks for the image again every 3 seconds: added, changed or removed while the game runs. */
	private static void checkOwn() {
		long now = System.nanoTime();
		if (ownCheckedNs != 0 && now - ownCheckedNs < 3_000_000_000L) {
			return;
		}
		ownCheckedNs = now;
		File f = ownFile();
		if (f == null) {
			dropOwn();
			ownFile = null;
			return;
		}
		if (f.equals(ownFile) && f.lastModified() == ownModified) {
			return;
		}
		ownFile = f;
		ownModified = f.lastModified();
		if (ownLoading) {
			return;
		}
		ownLoading = true;
		Thread loader = new Thread(() -> {
			try {
				ownLoaded = read(f);
			} catch (Exception | LinkageError e) {
				LOG.warn("Could not read the background image {}", f, e);
			} finally {
				ownLoading = false;
			}
		}, "Rago background image");
		loader.setDaemon(true);
		loader.start();
	}

	/** Reads the image on a thread of its own, scaled down to {@link #OWN_MAX_WIDTH} at most. */
	private static BufferedImage read(File f) throws java.io.IOException {
		BufferedImage src = ImageIO.read(f);
		if (src == null) {
			throw new java.io.IOException("not an image");
		}
		int w = src.getWidth();
		int h = src.getHeight();
		if (w > OWN_MAX_WIDTH) {
			h = Math.max(1, Math.round(h * OWN_MAX_WIDTH / (float) w));
			w = OWN_MAX_WIDTH;
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/** The texture of the player's image, uploaded once read, or -1. */
	private static int ownTexture() {
		checkOwn();
		BufferedImage ready = ownLoaded;
		if (ready != null) {
			ownLoaded = null;
			dropOwn();
			own = new StaticTexture(ready);
			ownAspect = ready.getWidth() / (float) ready.getHeight();
			GlStateManager.bindTexture(own.getGlTextureId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		}
		return own == null ? -1 : own.getGlTextureId();
	}

	private static void dropOwn() {
		if (own != null) {
			own.deleteGlTexture();
			own = null;
		}
	}
}
