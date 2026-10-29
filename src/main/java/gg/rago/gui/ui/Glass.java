package gg.rago.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.IntBuffer;

/**
 * Frosted glass: {@link #capture} copies what is on screen, shrinks it to a quarter and blurs it
 * on the GPU (a few Kawase passes, no shaders); panels then draw that blurred copy under their
 * fill so whatever is behind them shows through softly. Costs one screen copy and four small
 * passes per frame, and only while a Rago screen with glass is open.
 */
final class Glass {
	/** {@code GL_FRAMEBUFFER_BINDING}, the same value in GL 3.0 and EXT_framebuffer_object. */
	private static final int FRAMEBUFFER_BINDING = 0x8CA6;
	private static final int CONSTANT_ALPHA = 0x8003;
	private static final int ONE_MINUS_CONSTANT_ALPHA = 0x8004;
	private static final int DOWNSCALE = 4;
	private static final float[] OFFSETS = {1.5f, 2.5f, 3.5f};

	private static boolean enabled = true;
	private static boolean ready;
	private static int screenTex = -1;
	private static int screenW;
	private static int screenH;
	private static Target ping;
	private static Target pong;
	private static Target result;
	private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

	private Glass() {
	}

	static void setEnabled(boolean on) {
		enabled = on;
	}

	static boolean enabled() {
		return enabled;
	}

	/** Whether a blurred copy is ready for this frame. */
	static boolean ready() {
		return ready;
	}

	static void frameStart() {
		ready = false;
	}

	/** Copies and blurs the screen as it is now. Call after the background, before any panel. */
	static void capture() {
		ready = false;
		// framebuffers are used directly: OptiFine's Fast Render reports them as off, yet they work
		if (!enabled || !OpenGlHelper.framebufferSupported) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		int dw = mc.displayWidth;
		int dh = mc.displayHeight;
		if (dw < DOWNSCALE || dh < DOWNSCALE) {
			return;
		}
		int previous = GL11.glGetInteger(FRAMEBUFFER_BINDING);
		VIEWPORT.clear();
		GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
		boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
		GL11.glDisable(GL11.GL_SCISSOR_TEST);

		copyScreen(dw, dh);
		int bw = dw / DOWNSCALE;
		int bh = dh / DOWNSCALE;
		if (ping == null || ping.w != bw || ping.h != bh) {
			ping = Target.resize(ping, bw, bh);
			pong = Target.resize(pong, bw, bh);
		}

		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.ortho(0, 1, 0, 1, -1, 1);
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.disableDepth();
		GlStateManager.disableAlpha();
		GlStateManager.disableCull();
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(CONSTANT_ALPHA, ONE_MINUS_CONSTANT_ALPHA);

		pass(screenTex, dw, dh, ping, 1f);
		Target src = ping;
		Target dst = pong;
		for (float off : OFFSETS) {
			pass(src.tex, bw, bh, dst, off);
			Target t = src;
			src = dst;
			dst = t;
		}
		result = src;

		GL14.glBlendColor(0, 0, 0, 0);
		GlStateManager.colorMask(true, true, true, true);
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.popMatrix();
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.popMatrix();
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
		GlStateManager.viewport(VIEWPORT.get(0), VIEWPORT.get(1), VIEWPORT.get(2), VIEWPORT.get(3));
		if (scissor) {
			GL11.glEnable(GL11.GL_SCISSOR_TEST);
		}
		GlStateManager.resetColor();
		ready = true;
	}

	private static void copyScreen(int dw, int dh) {
		if (screenTex < 0) {
			screenTex = GlStateManager.generateTexture();
		}
		GlStateManager.bindTexture(screenTex);
		if (dw != screenW || dh != screenH) {
			screenW = dw;
			screenH = dh;
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, dw, dh, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
		}
		GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dw, dh);
	}

	/** A texture with a framebuffer drawing into it. */
	private static final class Target {
		final int w;
		final int h;
		final int tex;
		final int fbo;

		private Target(int w, int h) {
			this.w = w;
			this.h = h;
			tex = GlStateManager.generateTexture();
			GlStateManager.bindTexture(tex);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
			fbo = OpenGlHelper.glGenFramebuffers();
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
			OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
		}

		static Target resize(Target old, int w, int h) {
			if (old != null) {
				OpenGlHelper.glDeleteFramebuffers(old.fbo);
				TextureUtil.deleteTexture(old.tex);
			}
			return new Target(w, h);
		}

		void bind() {
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
			GlStateManager.viewport(0, 0, w, h);
		}
	}

	/**
	 * One Kawase pass: averages four bilinear taps placed {@code offset} source texels away
	 * diagonally. Each tap is blended with a constant weight (1, 1/2, 1/3, 1/4), which gives the
	 * exact mean without depending on the alpha stored in the source.
	 */
	private static void pass(int tex, int tw, int th, Target dst, float offset) {
		dst.bind();
		GlStateManager.colorMask(true, true, true, true);
		GlStateManager.clearColor(0, 0, 0, 1);
		GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
		// keep alpha at 1 so panels can blend the result with their own alpha
		GlStateManager.colorMask(true, true, true, false);
		GlStateManager.bindTexture(tex);
		float dx = (offset + 0.5f) / tw;
		float dy = (offset + 0.5f) / th;
		float[][] taps = {{-dx, -dy}, {dx, dy}, {-dx, dy}, {dx, -dy}};
		GlStateManager.color(1, 1, 1, 1);
		for (int i = 0; i < taps.length; i++) {
			GL14.glBlendColor(0, 0, 0, 1f / (i + 1));
			float ox = taps[i][0];
			float oy = taps[i][1];
			GL11.glBegin(GL11.GL_QUADS);
			GL11.glTexCoord2f(ox, oy);
			GL11.glVertex2f(0, 0);
			GL11.glTexCoord2f(1 + ox, oy);
			GL11.glVertex2f(1, 0);
			GL11.glTexCoord2f(1 + ox, 1 + oy);
			GL11.glVertex2f(1, 1);
			GL11.glTexCoord2f(ox, 1 + oy);
			GL11.glVertex2f(0, 1);
			GL11.glEnd();
		}
	}

	/**
	 * Draws the blurred copy inside a rounded rectangle given in real pixels (as drawn, before
	 * {@link Ui#pushTransform}); {@code sx0..sy1} is where that rectangle ends up on screen, which
	 * picks the part of the copy to show. The corners are cut with {@code maskTex} (a quarter disc)
	 * on the second texture unit.
	 */
	static void draw(float x0, float y0, float x1, float y1, float r,
					 float sx0, float sy0, float sx1, float sy1, int maskTex, float alpha) {
		if (!ready || result == null) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		float dw = mc.displayWidth;
		float dh = mc.displayHeight;
		float u0 = sx0 / dw;
		float u1 = sx1 / dw;
		float v0 = 1 - sy0 / dh;
		float v1 = 1 - sy1 / dh;
		float ru = r * (u1 - u0) / Math.max(1e-3f, x1 - x0);
		float rv = r * (v1 - v0) / Math.max(1e-3f, y1 - y0);

		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.bindTexture(result.tex);
		GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.enableTexture2D();
		GlStateManager.bindTexture(maskTex);
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GL11.glColor4f(1, 1, 1, alpha);

		GL11.glBegin(GL11.GL_QUADS);
		if (r < 1) {
			quad(x0, y0, x1, y1, u0, v0, u1, v1);
		} else {
			// middle column, then the left and right strips between the corners
			quad(x0 + r, y0, x1 - r, y1, u0 + ru, v0, u1 - ru, v1);
			quad(x0, y0 + r, x0 + r, y1 - r, u0, v0 + rv, u0 + ru, v1 - rv);
			quad(x1 - r, y0 + r, x1, y1 - r, u1 - ru, v0 + rv, u1, v1 - rv);
			corner(x0, y0, r, u0, v0, ru, rv, 0, 0);
			corner(x1 - r, y0, r, u1 - ru, v0, ru, rv, 1, 0);
			corner(x0, y1 - r, r, u0, v1 - rv, ru, rv, 0, 1);
			corner(x1 - r, y1 - r, r, u1 - ru, v1 - rv, ru, rv, 1, 1);
		}
		GL11.glEnd();

		GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.disableTexture2D();
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.resetColor();
	}

	/** A rectangle fully inside the shape: the mask is sampled where the quarter disc is solid. */
	private static void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
		vertex(x0, y1, u0, v1, 0.98f, 0.98f);
		vertex(x1, y1, u1, v1, 0.98f, 0.98f);
		vertex(x1, y0, u1, v0, 0.98f, 0.98f);
		vertex(x0, y0, u0, v0, 0.98f, 0.98f);
	}

	private static void corner(float x, float y, float r, float u, float v, float ru, float rv, int flipX, int flipY) {
		float m0 = flipX;
		float m1 = 1 - flipX;
		float n0 = flipY;
		float n1 = 1 - flipY;
		vertex(x, y + r, u, v + rv, m0, n1);
		vertex(x + r, y + r, u + ru, v + rv, m1, n1);
		vertex(x + r, y, u + ru, v, m1, n0);
		vertex(x, y, u, v, m0, n0);
	}

	private static void vertex(float x, float y, float u, float v, float mu, float mv) {
		GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, u, v);
		GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, mu, mv);
		GL11.glVertex2f(x, y);
	}
}
