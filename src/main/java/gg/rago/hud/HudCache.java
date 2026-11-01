package gg.rago.hud;

import gg.rago.core.RagoClient;
import gg.rago.render.GameFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;

/**
 * HUD cache: at high frame rates Rago's HUD is drawn a fixed number of times per second into an
 * image, and that image is laid over every frame in between, one quad instead of every element.
 *
 * <p>Each update makes two images of the HUD, over black and over white: the black image is each
 * pixel's colour already multiplied by its opacity, and the difference between the two is how much
 * of the world shows through. Each frame the world is dimmed by the second and the first is added,
 * so translucent backgrounds look exactly as when drawn directly, whatever blending the elements
 * use. Both images come out of a single drawing of the HUD (two colour targets at once, which
 * every card with framebuffers can do); a driver that cannot gets the HUD drawn twice instead.
 * When the game runs slower than the update rate the HUD is drawn directly, as before.
 */
public final class HudCache {
	private static final int MAX_AREAS = 32;
	/** Room around each element, in GUI pixels, for shadows and outlines drawn just outside it. */
	private static final int MARGIN = 3;

	/** Our own targets: the game's Framebuffer class does nothing when OptiFine's Fast Render is on. */
	private Target black;
	private Target white;
	/**
	 * A framebuffer with the black image on its first colour target and the white one on its second,
	 * so one drawing of the HUD fills both; 0 when the driver cannot (then the HUD is drawn twice).
	 */
	private int both;
	/**
	 * Two colour targets at once did not work once: not tried again until the game restarts. Set
	 * from the start by {@code -Drago.hud.twoPass=true}, to compare with the old way.
	 */
	private boolean bothFailed = Boolean.getBoolean("rago.hud.twoPass");
	private boolean failed;
	/** Element bounds from the last redraw, in GUI pixels (x, y, w, h). */
	private final int[] bounds = new int[MAX_AREAS * 4];
	/**
	 * Where the cached image has something, in framebuffer pixels from the top left (x0, y0, x1, y1),
	 * none overlapping; {@code areaCount} -1 means the whole screen. Laying the images over these
	 * areas only, instead of the whole screen, is most of the cache's cost per frame saved.
	 */
	private final float[] areas = new float[MAX_AREAS * 4];
	private int areaCount = -1;
	private long drawnAt;
	private long lastFrame;
	private boolean valid;

	/** Draws the HUD through the cache when it is worth it, otherwise directly. */
	public void render(Runnable drawHud, int rate) {
		Minecraft mc = Minecraft.getMinecraft();
		long now = System.nanoTime();
		long frame = lastFrame == 0 ? Long.MAX_VALUE : now - lastFrame;
		lastFrame = now;
		long interval = 1_000_000_000L / Math.max(1, rate);
		// slower than the update rate (or no framebuffers): nothing to save
		if (failed || frame >= interval || !OpenGlHelper.framebufferSupported || !ready(mc)) {
			valid = false;
			drawHud.run();
			return;
		}
		if (!valid || now - drawnAt >= interval) {
			drawnAt = now;
			int previous = GameFramebuffer.bound();
			HudManager.collectBounds(bounds);
			if (both != 0) {
				// both images from one drawing: what the HUD costs, once
				clear(black, 0, true);
				clear(white, 1, false);
				OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, both);
				GlStateManager.viewport(0, 0, black.width, black.height);
				drawHud.run();
				areas(HudManager.collected(), mc);
			} else {
				draw(black, 0, drawHud);
				areas(HudManager.collected(), mc);
				draw(white, 1, drawHud);
			}
			// white minus black: how much of the world shows through each pixel, in the white image
			white.bind();
			GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
			quad(black, GL11.GL_ONE, GL11.GL_ONE);
			GL14.glBlendEquation(GL14.GL_FUNC_ADD);
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
			GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
			valid = true;
		}
		composite(mc);
	}

	/** Forgets the cached image, so the next frame draws the HUD again. */
	public void invalidate() {
		valid = false;
	}

	private void draw(Target target, float clear, Runnable drawHud) {
		clear(target, clear, true);
		drawHud.run();
	}

	/** Binds {@code target} and fills it with {@code clear} (0 black, 1 white), and its depth with {@code depth}. */
	private static void clear(Target target, float clear, boolean depth) {
		target.bind();
		GlStateManager.clearColor(clear, clear, clear, 1);
		GlStateManager.clear(depth ? GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT);
	}

	private boolean ready(Minecraft mc) {
		int w = mc.displayWidth;
		int h = mc.displayHeight;
		if (black != null && black.width == w && black.height == h) {
			return true;
		}
		int previous = GameFramebuffer.bound();
		if (black != null) {
			deleteBoth();
			black.delete();
			white.delete();
		}
		black = new Target(w, h);
		white = new Target(w, h);
		valid = false;
		if (black.complete && white.complete && !bothFailed) {
			both = both(black, white);
			if (both == 0) {
				bothFailed = true;
				RagoClient.LOG.info("HUD cache: two colour targets at once unavailable, the HUD is drawn twice per update");
			}
		}
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
		GlStateManager.viewport(0, 0, w, h);
		if (!black.complete || !white.complete) {
			RagoClient.LOG.warn("HUD cache unavailable: framebuffer incomplete");
			deleteBoth();
			black.delete();
			white.delete();
			black = null;
			white = null;
			failed = true;
			return false;
		}
		return true;
	}

	/**
	 * A framebuffer drawing into {@code black}'s colour on its first target and {@code white}'s on its
	 * second (sharing black's depth), or 0 if the driver cannot draw into two at once. Without a
	 * shader, everything drawn goes to both targets, each blended with its own content.
	 */
	private static int both(Target black, Target white) {
		try {
			org.lwjgl.opengl.ContextCapabilities caps = org.lwjgl.opengl.GLContext.getCapabilities();
			if (!caps.OpenGL20 || GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_MAX_DRAW_BUFFERS) < 2
				|| GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_MAX_COLOR_ATTACHMENTS) < 2) {
				clearErrors();
				return 0;
			}
			int fbo = OpenGlHelper.glGenFramebuffers();
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
			OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, black.texture, 0);
			OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, white.texture, 0);
			OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, black.depth);
			java.nio.IntBuffer targets = org.lwjgl.BufferUtils.createIntBuffer(2);
			targets.put(OpenGlHelper.GL_COLOR_ATTACHMENT0).put(COLOR_ATTACHMENT1).flip();
			// the draw buffers belong to this framebuffer: set once, kept
			org.lwjgl.opengl.GL20.glDrawBuffers(targets);
			boolean ok = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE
				&& GL11.glGetError() == GL11.GL_NO_ERROR;
			if (!ok) {
				OpenGlHelper.glDeleteFramebuffers(fbo);
				clearErrors();
				return 0;
			}
			return fbo;
		} catch (RuntimeException | LinkageError e) {
			RagoClient.LOG.debug("HUD cache: no two colour targets", e);
			clearErrors();
			return 0;
		}
	}

	/** The second colour target; the same number for core, ARB and EXT framebuffers. */
	private static final int COLOR_ATTACHMENT1 = 0x8CE1;

	private static void clearErrors() {
		for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
			// errors from the checks above are ours, not the game's
		}
	}

	private void deleteBoth() {
		if (both != 0) {
			OpenGlHelper.glDeleteFramebuffers(both);
			both = 0;
		}
	}

	/** A colour texture with a depth buffer to draw into. */
	private static final class Target {
		final int width;
		final int height;
		final int fbo;
		final int texture;
		final int depth;
		final boolean complete;

		Target(int w, int h) {
			width = w;
			height = h;
			texture = GL11.glGenTextures();
			GlStateManager.bindTexture(texture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
			fbo = OpenGlHelper.glGenFramebuffers();
			depth = OpenGlHelper.glGenRenderbuffers();
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
			OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
			OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, depth);
			OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, w, h);
			OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, depth);
			complete = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE;
		}

		void bind() {
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
			GlStateManager.viewport(0, 0, width, height);
		}

		void delete() {
			OpenGlHelper.glDeleteFramebuffers(fbo);
			OpenGlHelper.glDeleteRenderbuffers(depth);
			GlStateManager.deleteTexture(texture);
		}
	}

	/** The cached HUD over the frame: the world dimmed where the HUD covers it, then the HUD added. */
	private void composite(Minecraft mc) {
		if (areaCount == 0) {
			return;
		}
		quad(white, GL11.GL_ZERO, GL11.GL_SRC_COLOR);
		quad(black, GL11.GL_ONE, GL11.GL_ONE);
	}

	/**
	 * The areas the HUD was drawn in at the last redraw ({@code n} elements, their bounds in
	 * {@link #bounds}), widened by {@link #MARGIN}, in framebuffer pixels, overlapping ones merged
	 * (an area laid over twice would dim and add twice). Too many elements: the whole screen.
	 */
	private void areas(int n, Minecraft mc) {
		if (n > MAX_AREAS) {
			areaCount = -1;
			return;
		}
		int scale = gg.rago.render.HudText.guiScale();
		float w = mc.displayWidth;
		float h = mc.displayHeight;
		int count = 0;
		for (int i = 0; i < n; i++) {
			if (bounds[i * 4 + 2] <= 0 || bounds[i * 4 + 3] <= 0) {
				continue;
			}
			areas[count * 4] = Math.max(0, (bounds[i * 4] - MARGIN) * scale);
			areas[count * 4 + 1] = Math.max(0, (bounds[i * 4 + 1] - MARGIN) * scale);
			areas[count * 4 + 2] = Math.min(w, (bounds[i * 4] + bounds[i * 4 + 2] + MARGIN) * scale);
			areas[count * 4 + 3] = Math.min(h, (bounds[i * 4 + 1] + bounds[i * 4 + 3] + MARGIN) * scale);
			if (areas[count * 4 + 2] > areas[count * 4] && areas[count * 4 + 3] > areas[count * 4 + 1]) {
				count++;
			}
		}
		areaCount = merge(areas, count);
	}

	/** Merges overlapping rectangles (x0, y0, x1, y1) in place until none overlap; returns how many are left. */
	static int merge(float[] r, int count) {
		boolean merged = true;
		while (merged) {
			merged = false;
			for (int i = 0; i < count && !merged; i++) {
				for (int j = i + 1; j < count; j++) {
					if (r[i * 4] < r[j * 4 + 2] && r[j * 4] < r[i * 4 + 2] && r[i * 4 + 1] < r[j * 4 + 3] && r[j * 4 + 1] < r[i * 4 + 3]) {
						r[i * 4] = Math.min(r[i * 4], r[j * 4]);
						r[i * 4 + 1] = Math.min(r[i * 4 + 1], r[j * 4 + 1]);
						r[i * 4 + 2] = Math.max(r[i * 4 + 2], r[j * 4 + 2]);
						r[i * 4 + 3] = Math.max(r[i * 4 + 3], r[j * 4 + 3]);
						count--;
						System.arraycopy(r, count * 4, r, j * 4, 4);
						merged = true;
						break;
					}
				}
			}
		}
		return count;
	}

	/** One of the images over the HUD's areas (or the whole target), blended with the given factors (alpha kept). */
	private void quad(Target image, int src, int dst) {
		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.ortho(0, 1, 1, 0, -1, 1);
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(src, dst, GL11.GL_ZERO, GL11.GL_ONE);
		GlStateManager.disableAlpha();
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.enableTexture2D();
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.bindTexture(image.texture);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		if (areaCount < 0) {
			area(wr, 0, 0, 1, 1);
		} else {
			float w = image.width;
			float h = image.height;
			for (int i = 0; i < areaCount; i++) {
				area(wr, areas[i * 4] / w, areas[i * 4 + 1] / h, areas[i * 4 + 2] / w, areas[i * 4 + 3] / h);
			}
		}
		tess.draw();
		GlStateManager.depthMask(true);
		GlStateManager.enableDepth();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.enableAlpha();
		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.popMatrix();
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.popMatrix();
	}

	/** A rectangle of the screen (0 to 1, from the top left) with the same part of the image; the framebuffer image is upside down compared with the screen. */
	private static void area(WorldRenderer wr, float x0, float y0, float x1, float y1) {
		wr.pos(x0, y1, 0).tex(x0, 1 - y1).endVertex();
		wr.pos(x1, y1, 0).tex(x1, 1 - y1).endVertex();
		wr.pos(x1, y0, 0).tex(x1, 1 - y0).endVertex();
		wr.pos(x0, y0, 0).tex(x0, 1 - y0).endVertex();
	}
}
