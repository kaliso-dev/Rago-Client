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
		float r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0.05f) {
			rect(x, y, w, h, argb);
			return;
		}
		if (argb >>> 24 == 0) {
			return;
		}
		float x1 = x + w;
		float y1 = y + h;
		GlStateManager.disableTexture2D();
		blend();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer b = t.getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		quad(b, x + r, y, x1 - r, y1, argb);
		quad(b, x, y + r, x + r, y1 - r, argb);
		quad(b, x1 - r, y + r, x1, y1 - r, argb);
		t.draw();
		GlStateManager.enableTexture2D();
		GlStateManager.bindTexture(Ui.cornerTextureId());
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		corner(b, x, y, r, 0, 0, argb);
		corner(b, x1 - r, y, r, 1, 0, argb);
		corner(b, x, y1 - r, r, 0, 1, argb);
		corner(b, x1 - r, y1 - r, r, 1, 1, argb);
		t.draw();
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

	/** An outline {@code width} units thick, inside the given bounds, following round corners. */
	public static void outline(float x, float y, float w, float h, float radius, float width, int argb) {
		if (argb >>> 24 == 0 || width <= 0) {
			return;
		}
		float r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0.05f) {
			rect(x, y, w, width, argb);
			rect(x, y + h - width, w, width, argb);
			rect(x, y + width, width, h - 2 * width, argb);
			rect(x + w - width, y + width, width, h - 2 * width, argb);
			return;
		}
		GlStateManager.disableTexture2D();
		blend();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer b = t.getWorldRenderer();
		b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
		float inner = Math.max(0, r - width);
		int seg = 8;
		float[][] centers = {{x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}, {x + r, y + r}};
		float start = -90;
		for (int c = 0; c < 4; c++) {
			for (int i = 0; i <= seg; i++) {
				double a = Math.toRadians(start + c * 90 + 90.0 * i / seg);
				float cos = (float) Math.cos(a);
				float sin = (float) Math.sin(a);
				color(b.pos(centers[c][0] + cos * r, centers[c][1] + sin * r, 0), argb).endVertex();
				color(b.pos(centers[c][0] + cos * inner, centers[c][1] + sin * inner, 0), argb).endVertex();
			}
		}
		color(b.pos(centers[0][0], centers[0][1] - r, 0), argb).endVertex();
		color(b.pos(centers[0][0], centers[0][1] - inner, 0), argb).endVertex();
		t.draw();
		GlStateManager.enableTexture2D();
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
