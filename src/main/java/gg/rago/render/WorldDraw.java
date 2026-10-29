package gg.rago.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** Boxes and name labels drawn in the world, positioned relative to the camera. */
public final class WorldDraw {
	/** Vanilla's name tag scale: 1.6 / 60 blocks per text pixel. */
	public static final float LABEL_SCALE = 0.02666667f;

	private WorldDraw() {
	}

	/** Lines along the twelve edges of {@code box} (camera-relative), {@code width} pixels wide. */
	public static void outline(AxisAlignedBB box, int argb, float width) {
		WorldRenderer wr = beginLines(width);
		box(wr, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, argb);
		endLines();
	}

	/**
	 * Starts a batch of lines {@code width} pixels wide, all drawn at once by {@link #endLines}: one
	 * draw call for many boxes instead of one each.
	 */
	public static WorldRenderer beginLines(float width) {
		begin(width);
		WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
		wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		return wr;
	}

	public static void endLines() {
		Tessellator.getInstance().draw();
		end();
	}

	/** The twelve edges of a box (camera-relative), into a batch from {@link #beginLines}. */
	public static void box(WorldRenderer wr, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
		line(wr, x0, y0, z0, x1, y0, z0, argb);
		line(wr, x0, y0, z1, x1, y0, z1, argb);
		line(wr, x0, y1, z0, x1, y1, z0, argb);
		line(wr, x0, y1, z1, x1, y1, z1, argb);
		line(wr, x0, y0, z0, x0, y1, z0, argb);
		line(wr, x1, y0, z0, x1, y1, z0, argb);
		line(wr, x0, y0, z1, x0, y1, z1, argb);
		line(wr, x1, y0, z1, x1, y1, z1, argb);
		line(wr, x0, y0, z0, x0, y0, z1, argb);
		line(wr, x1, y0, z0, x1, y0, z1, argb);
		line(wr, x0, y1, z0, x0, y1, z1, argb);
		line(wr, x1, y1, z0, x1, y1, z1, argb);
	}

	/** One line from (x0, y0, z0) to (x1, y1, z1), camera-relative. */
	public static void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb, float width) {
		begin(width);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		line(wr, x0, y0, z0, x1, y1, z1, argb);
		t.draw();
		end();
	}

	/** The six faces of {@code box}, filled with a (usually translucent) colour. */
	public static void filled(AxisAlignedBB box, int argb) {
		begin(1);
		GlStateManager.disableCull();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
		quad(wr, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(wr, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(wr, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(wr, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(wr, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(wr, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
		t.draw();
		GlStateManager.enableCull();
		end();
	}

	private static void begin(float width) {
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.depthMask(false);
		GL11.glLineWidth(width);
	}

	private static void end() {
		GL11.glLineWidth(1);
		GlStateManager.depthMask(true);
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.color(1, 1, 1, 1);
	}

	/** One line (camera-relative) into a batch from {@link #beginLines}. */
	public static void line(WorldRenderer wr, double x0, double y0, double z0, double x1, double y1, double z1, int c) {
		wr.pos(x0, y0, z0).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
		wr.pos(x1, y1, z1).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
	}

	private static void quad(WorldRenderer wr, int c, double... v) {
		for (int i = 0; i < 12; i += 3) {
			wr.pos(v[i], v[i + 1], v[i + 2]).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
		}
	}

	/**
	 * A name-tag style label facing the camera with its top edge at (x, y, z), drawn the
	 * way vanilla draws names: faint through walls, solid where visible.
	 */
	public static void label(String text, double x, double y, double z, boolean background) {
		Minecraft mc = Minecraft.getMinecraft();
		RenderManager rm = mc.getRenderManager();
		FontRenderer font = mc.fontRendererObj;
		GlStateManager.pushMatrix();
		GlStateManager.translate((float) x, (float) y, (float) z);
		GL11.glNormal3f(0, 1, 0);
		GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
		GlStateManager.rotate(rm.playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1 : 1), 1, 0, 0);
		GlStateManager.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
		GlStateManager.disableLighting();
		GlStateManager.depthMask(false);
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		int half = font.getStringWidth(text) / 2;
		if (background) {
			GlStateManager.disableTexture2D();
			Tessellator t = Tessellator.getInstance();
			WorldRenderer wr = t.getWorldRenderer();
			wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
			wr.pos(-half - 1, -1, 0).color(0, 0, 0, 0.25f).endVertex();
			wr.pos(-half - 1, 8, 0).color(0, 0, 0, 0.25f).endVertex();
			wr.pos(half + 1, 8, 0).color(0, 0, 0, 0.25f).endVertex();
			wr.pos(half + 1, -1, 0).color(0, 0, 0, 0.25f).endVertex();
			t.draw();
			GlStateManager.enableTexture2D();
		}
		font.drawString(text, -half, 0, 0x20FFFFFF);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		font.drawString(text, -half, 0, 0xFFFFFFFF);
		GlStateManager.enableLighting();
		GlStateManager.disableBlend();
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.popMatrix();
	}
}
