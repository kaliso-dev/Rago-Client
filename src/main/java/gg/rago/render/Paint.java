package gg.rago.render;

import gg.rago.gui.ui.Ui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Flat shapes drawn in whatever units the current matrix uses (GUI pixels on the HUD, label
 * pixels on a name tag), unlike {@link Ui}, which works in its own menu units.
 */
public final class Paint {
	private Paint() {
	}

	private static void blend() {
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	private static WorldRenderer color(WorldRenderer r, int argb) {
		return r.color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
	}

	public static void rect(float x, float y, float w, float h, int argb) {
		if (argb >>> 24 == 0 || w <= 0 || h <= 0) {
			return;
		}
		GlStateManager.disableTexture2D();
		blend();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer r = t.getWorldRenderer();
		r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		quad(r, x, y, x + w, y + h, argb);
		t.draw();
		GlStateManager.enableTexture2D();
	}

	private static void quad(WorldRenderer r, float x0, float y0, float x1, float y1, int argb) {
		color(r.pos(x0, y1, 0), argb).endVertex();
		color(r.pos(x1, y1, 0), argb).endVertex();
		color(r.pos(x1, y0, 0), argb).endVertex();
		color(r.pos(x0, y0, 0), argb).endVertex();
	}

	/** Filled rectangle with anti-aliased round corners. */
	public static void round(float x, float y, float w, float h, float radius, int argb) {
		if (argb >>> 24 == 0 || w <= 0 || h <= 0) {
			return;
		}
		roundInto(beginShapes(), x, y, w, h, radius, argb);
		endShapes();
	}

	/**
	 * Starts a batch of shapes drawn with one draw call: add them with {@link #roundInto} and
	 * {@link #outlineInto}, in back-to-front order, then call {@link #endShapes}.
	 */
	public static WorldRenderer beginShapes() {
		blend();
		GlStateManager.enableTexture2D();
		GlStateManager.bindTexture(Ui.cornerTextureId());
		WorldRenderer b = Tessellator.getInstance().getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		return b;
	}

	public static void endShapes() {
		Tessellator.getInstance().draw();
	}

	/** Adds a round rectangle to a shape batch. */
	public static void roundInto(WorldRenderer b, float x, float y, float w, float h, float radius, int argb) {
		if (argb >>> 24 == 0 || w <= 0 || h <= 0) {
			return;
		}
		float r = Math.min(radius, Math.min(w, h) / 2);
		float x1 = x + w;
		float y1 = y + h;
		if (r <= 0.05f) {
			solid(b, x, y, x1, y1, argb);
			return;
		}
		// the straight parts sample the corner texture's solid bottom-right texel
		solid(b, x + r, y, x1 - r, y1, argb);
		solid(b, x, y + r, x + r, y1 - r, argb);
		solid(b, x1 - r, y + r, x1, y1 - r, argb);
		corner(b, x, y, r, 0, 0, argb);
		corner(b, x1 - r, y, r, 1, 0, argb);
		corner(b, x, y1 - r, r, 0, 1, argb);
		corner(b, x1 - r, y1 - r, r, 1, 1, argb);
	}

	/** Centre of the corner texture's last texel, which is fully opaque (64 texels wide). */
	private static final float SOLID_UV = 1 - 0.5f / 64;

	private static void solid(WorldRenderer b, float x0, float y0, float x1, float y1, int argb) {
		color(b.pos(x0, y1, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
		color(b.pos(x1, y1, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
		color(b.pos(x1, y0, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
		color(b.pos(x0, y0, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
	}

	private static void corner(WorldRenderer b, float x, float y, float r, int flipX, int flipY, int argb) {
		float u0 = flipX;
		float u1 = 1 - flipX;
		float v0 = flipY;
		float v1 = 1 - flipY;
		color(b.pos(x, y + r, 0).tex(u0, v1), argb).endVertex();
		color(b.pos(x + r, y + r, 0).tex(u1, v1), argb).endVertex();
		color(b.pos(x + r, y, 0).tex(u1, v0), argb).endVertex();
		color(b.pos(x, y, 0).tex(u0, v0), argb).endVertex();
	}

	/** Segments per quarter circle of an outline. */
	private static final int SEG = 8;
	/** cos and sin around the outline, clockwise from the top: 4 corners of SEG + 1 points. */
	private static final float[] COS = new float[4 * (SEG + 1)];
	private static final float[] SIN = new float[4 * (SEG + 1)];

	static {
		for (int c = 0; c < 4; c++) {
			for (int i = 0; i <= SEG; i++) {
				double a = Math.toRadians(-90 + c * 90 + 90.0 * i / SEG);
				COS[c * (SEG + 1) + i] = (float) Math.cos(a);
				SIN[c * (SEG + 1) + i] = (float) Math.sin(a);
			}
		}
	}

	/** An outline {@code width} units thick, inside the given bounds, following round corners. */
	public static void outline(float x, float y, float w, float h, float radius, float width, int argb) {
		if (argb >>> 24 == 0 || width <= 0) {
			return;
		}
		outlineInto(beginShapes(), x, y, w, h, radius, width, argb);
		endShapes();
	}

	/** Adds an outline to a shape batch. */
	public static void outlineInto(WorldRenderer b, float x, float y, float w, float h, float radius, float width, int argb) {
		if (argb >>> 24 == 0 || width <= 0) {
			return;
		}
		float r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0.05f) {
			solid(b, x, y, x + w, y + width, argb);
			solid(b, x, y + h - width, x + w, y + h, argb);
			solid(b, x, y + width, x + width, y + h - width, argb);
			solid(b, x + w - width, y + width, x + w, y + h - width, argb);
			return;
		}
		float inner = Math.max(0, r - width);
		// corner centres, in the order of COS/SIN: top right, bottom right, bottom left, top left
		// (worked out per point rather than kept in arrays: this runs for every outline, every frame)
		float right = x + w - r;
		float left = x + r;
		float top = y + r;
		float bottom = y + h - r;
		int n = COS.length;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			int ci = i / (SEG + 1);
			int cj = j / (SEG + 1);
			float cxi = ci < 2 ? right : left;
			float cyi = ci == 1 || ci == 2 ? bottom : top;
			float cxj = cj < 2 ? right : left;
			float cyj = cj == 1 || cj == 2 ? bottom : top;
			// a ring quad from point i to point j (the step between corners is a straight edge)
			// same winding as vanilla's GUI quads, so face culling never drops it
			color(b.pos(cxi + COS[i] * inner, cyi + SIN[i] * inner, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
			color(b.pos(cxj + COS[j] * inner, cyj + SIN[j] * inner, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
			color(b.pos(cxj + COS[j] * r, cyj + SIN[j] * r, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
			color(b.pos(cxi + COS[i] * r, cyi + SIN[i] * r, 0).tex(SOLID_UV, SOLID_UV), argb).endVertex();
		}
	}

	/** Linear blend between two colours, alpha included. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24;
		int ar = a >> 16 & 0xFF;
		int ag = a >> 8 & 0xFF;
		int ab = a & 0xFF;
		return Math.round(aa + ((b >>> 24) - aa) * t) << 24
			| Math.round(ar + ((b >> 16 & 0xFF) - ar) * t) << 16
			| Math.round(ag + ((b >> 8 & 0xFF) - ag) * t) << 8
			| Math.round(ab + ((b & 0xFF) - ab) * t);
	}

	/** Rainbow colour cycling over time; {@code offset} shifts the hue, e.g. per character. */
	public static int chroma(float offset, int alpha) {
		float hue = (System.currentTimeMillis() % 4000L) / 4000f + offset;
		return alpha << 24 | java.awt.Color.HSBtoRGB(hue, 0.75f, 1f) & 0xFFFFFF;
	}
}
