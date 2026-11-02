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
 * The menus' background, chosen on the home screen: one of Rago's looping videos (Minecraft
 * landscapes, each a few seconds cross-faded into themselves, decoded only while a menu shows it),
 * or the player's own video (JPEG frames in config/rago-background-video) or image
 * (config/rago-background.png or .jpg). Changing it fades the new one in over the old one; while a
 * video loads, its first frame shows as a still picture.
 */
public final class Background {
	public static final int NIGHT_DESERT = 0;
	public static final int OWN_IMAGE = 4;
	/**
	 * The names of the backgrounds, in the order of their ids. Ids 1 to 3 were drawn backgrounds
	 * until 1.10.11 and are videos now; 4 stays the player's own, so a saved choice keeps meaning it.
	 */
	public static final String[] KEYS = {"rago.value.bg_night_desert", "rago.value.bg_lake_sunset", "rago.value.bg_oak",
		"rago.value.bg_golden_peak", "rago.value.bg_own", "rago.value.bg_coast", "rago.value.bg_valley", "rago.value.bg_seabed",
		"rago.value.bg_meadow", "rago.value.bg_mountain_flower", "rago.value.bg_dusk", "rago.value.bg_campfire",
		"rago.value.bg_night_peaks"};
	/** Each background's clip in assets/rago/video, by id; null for the player's own. */
	static final String[] CLIPS = {"night_desert", "lake_sunset", "oak", "golden_peak", null, "coast", "valley", "seabed",
		"meadow", "mountain_flower", "dusk", "campfire", "night_peaks"};
	/** The order of the home screen's menu: Rago's videos, then the player's own. */
	public static final int[] ORDER = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 4};
	/** The names the player's own image may have, in the config folder. */
	public static final String[] OWN_NAMES = {"rago-background.png", "rago-background.jpg", "rago-background.jpeg"};
	/** The folder, in the config folder, of the player's own video as JPEG frames. */
	public static final String OWN_VIDEO = "rago-background-video";
	/** Own images are scaled down to this width at most: the background is blurred and darkened anyway. */
	private static final int OWN_MAX_WIDTH = 1920;
	private static final float FADE_MS = 450;

	private static final Logger LOG = LogManager.getLogger("Rago");

	private static int current = NIGHT_DESERT;
	private static int previous = -1;
	private static long changedNs;
	private static Path configDir;

	/** Each video, made the first time it is shown; and each one's first frame, as a still picture. */
	private static final VideoBackdrop[] VIDEOS = new VideoBackdrop[CLIPS.length];
	private static final StaticTexture[] POSTERS = new StaticTexture[CLIPS.length];
	private static final boolean[] NO_POSTER = new boolean[CLIPS.length];

	/** The player's image: its texture once loaded, the file and its date it was read from. */
	private static StaticTexture own;
	private static float ownAspect = 16 / 9f;
	private static File ownFile;
	private static long ownModified;
	private static long ownCheckedNs;
	private static volatile BufferedImage ownLoaded;
	private static volatile boolean ownLoading;
	/** The player's video, and the date of its folder when it was made. */
	private static VideoBackdrop ownVideo;
	private static long ownVideoModified;

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

	/** Whether a background is a video (the player's own counts when it is a video). */
	public static boolean isVideo(int id) {
		return CLIPS[clamp(id)] != null || id == OWN_IMAGE && ownVideo != null;
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

	/** Whether the player's own video or image is there to show (looked for again every few seconds). */
	public static boolean hasOwnImage() {
		checkOwn();
		return ownVideo != null || own != null || ownLoading || ownLoaded != null;
	}

	/**
	 * Covers the screen, moved by ({@code dx}, {@code dy}) and zoomed by {@code zoom} for the
	 * parallax, darkened by {@code dim} (0..1); while a change fades, the new background is drawn
	 * over the old one.
	 */
	static void drawScreen(float w, float h, float dx, float dy, float zoom, float dim) {
		VideoBackdrop.releaseIdle();
		float fade = previous >= 0 ? Math.min(1, (System.nanoTime() - changedNs) / (FADE_MS * 1_000_000f)) : 1;
		if (fade < 1) {
			draw(previous, 0, 0, w, h, dx, dy, zoom, 1, dim, true);
			draw(current, 0, 0, w, h, dx, dy, zoom, Anim.easeOut(fade), dim, true);
		} else {
			previous = -1;
			draw(current, 0, 0, w, h, dx, dy, zoom, 1, dim, true);
		}
	}

	/**
	 * Draws background {@code id} covering the box (x, y, w, h) at {@code alpha}; what lies outside
	 * the box is drawn too, so a box smaller than the screen needs a {@link Ui#scissor}.
	 */
	public static void draw(int id, float x, float y, float w, float h, float dx, float dy, float zoom, float alpha) {
		draw(id, x, y, w, h, dx, dy, zoom, alpha, 0, true);
	}

	/**
	 * A small preview of background {@code id} (the home screen's menu): the video playing when
	 * {@code live}, else its first frame, so a menu of them decodes one video at most.
	 */
	public static void preview(int id, float x, float y, float w, float h, float alpha, boolean live) {
		draw(id, x, y, w, h, 0, 0, 1, alpha, 0, live);
	}

	/**
	 * {@link #draw} darkened by {@code dim}: the video and the image are drawn darker (their colour
	 * multiplied, no extra layer over the whole screen).
	 */
	private static void draw(int id, float x, float y, float w, float h, float dx, float dy, float zoom, float alpha, float dim,
		boolean live) {
		id = clamp(id);
		int shade = Math.round(255 * (1 - Math.max(0, Math.min(1, dim))));
		int argb = Ui.alpha(0xFF000000 | shade << 16 | shade << 8 | shade, alpha);
		if (id == OWN_IMAGE) {
			checkOwn();
			if (ownVideo != null && (!live || video(ownVideo, x, y, w, h, dx, dy, zoom, argb, alpha))) {
				if (!live) {
					dark(x, y, w, h, alpha, 0);
					Ui.icon("film", x + w / 2 - 12, y + h / 2 - 12, 24, Ui.alpha(0xFFD4D2D9, alpha));
				}
				return;
			}
			if (ownTexture() >= 0) {
				// the picture drifts slowly, while the menus are allowed to move
				double t = VideoBackdrop.isPlaying() && Anim.enabled() ? System.nanoTime() / 1e9 : 0;
				image(own.getGlTextureId(), ownAspect, x, y, w, h, dx, dy, zoom, argb, t);
				return;
			}
			dark(x, y, w, h, alpha, 0);
			return;
		}
		if (live) {
			if (VIDEOS[id] == null) {
				VIDEOS[id] = VideoBackdrop.builtIn(CLIPS[id]);
			}
			if (video(VIDEOS[id], x, y, w, h, dx, dy, zoom, argb, alpha)) {
				return;
			}
		}
		// until its first frame is decoded, or not playing here: the first frame as a still picture
		int poster = poster(id);
		if (poster >= 0) {
			float[] box = cover(16 / 9f, x, y, w, h, dx, dy, zoom);
			Ui.texturedQuad(poster, box[0], box[1], box[2], box[3], argb);
		} else {
			dark(x, y, w, h, alpha, 0);
		}
	}

	/** Draws a video's frame due now; false while it has none (loading, or it cannot play). */
	private static boolean video(VideoBackdrop v, float x, float y, float w, float h, float dx, float dy, float zoom, int argb,
		float alpha) {
		int frame = v.textureId();
		if (frame < 0) {
			return false;
		}
		float[] box = cover(v.aspect(), x, y, w, h, dx, dy, zoom);
		int before = v.previousTextureId();
		float mix = before >= 0 ? v.mix() : 1;
		if (mix < 1) {
			// the frame before, with the current one fading in over it: smooth at any frame rate
			Ui.texturedQuad(before, box[0], box[1], box[2], box[3], argb);
			Ui.texturedQuad(frame, box[0], box[1], box[2], box[3], Ui.alpha(argb, mix * alpha));
		} else {
			Ui.texturedQuad(frame, box[0], box[1], box[2], box[3], argb);
		}
		return true;
	}

	/** The texture of a video's first frame (320x180, read once from the jar), or -1. */
	private static int poster(int id) {
		if (POSTERS[id] == null && !NO_POSTER[id]) {
			String path = "/assets/rago/video/" + CLIPS[id] + ".jpg";
			try (java.io.InputStream in = Background.class.getResourceAsStream(path)) {
				BufferedImage img = in == null ? null : ImageIO.read(in);
				if (img == null) {
					throw new java.io.IOException("missing " + path);
				}
				POSTERS[id] = new StaticTexture(img);
				GlStateManager.bindTexture(POSTERS[id].getGlTextureId());
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			} catch (Exception | LinkageError e) {
				LOG.warn("No still picture for background {}", CLIPS[id], e);
				NO_POSTER[id] = true;
			}
		}
		return POSTERS[id] == null ? -1 : POSTERS[id].getGlTextureId();
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

	/** Plain dark with soft lights, Rago's red and a cold blue (while a background loads). */
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
		checkOwnVideo();
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

	/** The player's video, a folder of JPEG frames: made again when the folder changes, dropped when it goes. */
	private static void checkOwnVideo() {
		File dir = configDir == null ? null : configDir.resolve(OWN_VIDEO).toFile();
		boolean there = dir != null && dir.isDirectory() && hasFrames(dir);
		long modified = there ? dir.lastModified() : 0;
		if (ownVideo != null && (!there || modified != ownVideoModified)) {
			ownVideo.dispose();
			ownVideo = null;
		}
		if (there && ownVideo == null) {
			ownVideo = VideoBackdrop.folder(dir);
			ownVideoModified = modified;
		}
	}

	private static boolean hasFrames(File dir) {
		String[] names = dir.list((d, n) -> {
			String lower = n.toLowerCase(java.util.Locale.ROOT);
			return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
		});
		return names != null && names.length >= 2;
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
