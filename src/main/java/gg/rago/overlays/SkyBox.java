package gg.rago.overlays;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Draws a sky box image in the layout of packs' custom skies (OptiFine, MCPatcher): three squares by
 * two, each side drawn as the bottom face of a cube and turned into place, so an image made for those
 * mods shows the same way here. Call with the image bound.
 */
public final class SkyBox {
	/** Half the cube's size: inside the far plane at the smallest render distance that draws a sky (4 chunks). */
	public static final double SIZE = 50;

	private SkyBox() {
	}

	/**
	 * The six sides; {@code insetU}, {@code insetV} pull the corners of each cell in (in texture
	 * coordinates), so smooth filtering never reads the next cell's pixels.
	 */
	public static void draw(double insetU, double insetV) {
		Tessellator t = Tessellator.getInstance();
		GlStateManager.pushMatrix();
		GlStateManager.rotate(90, 1, 0, 0);
		GlStateManager.rotate(-90, 0, 0, 1);
		side(t, 4, insetU, insetV);
		GlStateManager.pushMatrix();
		GlStateManager.rotate(90, 1, 0, 0);
		side(t, 1, insetU, insetV);
		GlStateManager.popMatrix();
		GlStateManager.pushMatrix();
		GlStateManager.rotate(-90, 1, 0, 0);
		side(t, 0, insetU, insetV);
		GlStateManager.popMatrix();
		GlStateManager.rotate(90, 0, 0, 1);
		side(t, 5, insetU, insetV);
		GlStateManager.rotate(90, 0, 0, 1);
		side(t, 2, insetU, insetV);
		GlStateManager.rotate(90, 0, 0, 1);
		side(t, 3, insetU, insetV);
		GlStateManager.popMatrix();
	}

	private static void side(Tessellator t, int side, double iu, double iv) {
		WorldRenderer wr = t.getWorldRenderer();
		double u0 = side % 3 / 3.0 + iu;
		double v0 = side / 3 / 2.0 + iv;
		double u1 = side % 3 / 3.0 + 1 / 3.0 - iu;
		double v1 = side / 3 / 2.0 + 0.5 - iv;
		double r = SIZE;
		wr.begin(7, DefaultVertexFormats.POSITION_TEX);
		wr.pos(-r, -r, -r).tex(u0, v0).endVertex();
		wr.pos(-r, -r, r).tex(u0, v1).endVertex();
		wr.pos(r, -r, r).tex(u1, v1).endVertex();
		wr.pos(r, -r, -r).tex(u1, v0).endVertex();
		t.draw();
	}
}
