package gg.rago.hud;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * HUD cache: at high frame rates Rago's HUD is drawn a fixed number of times per second into an
 * image, and that image is laid over every frame in between, one quad instead of every element.
 *
 * <p>The HUD is drawn twice per update, over black and over white: the black image is each pixel's
 * colour already multiplied by its opacity, and the difference between the two is how much of the
 * world shows through. Each frame the world is dimmed by the second and the first is added, so
 * translucent backgrounds look exactly as when drawn directly, whatever blending the elements use. When the game runs slower than the update rate the HUD is drawn
 * directly, as before.
 */
public final class HudCache {
	/** Our own targets: the game's Framebuffer class does nothing when OptiFine's Fast Render is on. */
	private Target black;
	private Target white;
	private boolean failed;
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
			// asked only when the image is redrawn: asking the driver can make it wait
			int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
			draw(black, 0, drawHud);
			draw(white, 1, drawHud);
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
		target.bind();
		GlStateManager.clearColor(clear, clear, clear, 1);
		GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		drawHud.run();
	}

	private boolean ready(Minecraft mc) {
		int w = mc.displayWidth;
		int h = mc.displayHeight;
		if (black != null && black.width == w && black.height == h) {
			return true;
		}
		int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
		if (black != null) {
			black.delete();
			white.delete();
		}
		black = new Target(w, h);
		white = new Target(w, h);
		valid = false;
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
		GlStateManager.viewport(0, 0, w, h);
		if (!black.complete || !white.complete) {
			RagoClient.LOG.warn("HUD cache unavailable: framebuffer incomplete");
			black.delete();
			white.delete();
			black = null;
			white = null;
			failed = true;
			return false;
		}
		return true;
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
		quad(white, GL11.GL_ZERO, GL11.GL_SRC_COLOR);
		quad(black, GL11.GL_ONE, GL11.GL_ONE);
	}

	/** One of the images over the whole target, blended with the given factors (alpha kept). */
	private static void quad(Target image, int src, int dst) {
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
		// the framebuffer image is upside down compared with the screen
		wr.pos(0, 1, 0).tex(0, 0).endVertex();
		wr.pos(1, 1, 0).tex(1, 0).endVertex();
		wr.pos(1, 0, 0).tex(1, 1).endVertex();
		wr.pos(0, 0, 0).tex(0, 1).endVertex();
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
}
