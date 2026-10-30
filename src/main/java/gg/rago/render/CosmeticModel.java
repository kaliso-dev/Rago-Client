package gg.rago.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A textured 3D model of a cosmetic, built by tools/models.py into assets/rago/models/cosmetics. Its
 * groups (a hat, or a left and a right wing) are triangles with a baked shade. Each group is compiled
 * once into a display list, so drawing a model of thousands of voxels is one call; the list is built
 * again only when the tint changes.
 */
public final class CosmeticModel {
	private static final Logger LOG = LogManager.getLogger("Rago");

	private final String name;
	private final ResourceLocation texture;
	private final Map<String, Group> groups = new LinkedHashMap<>();
	private boolean loaded;

	public CosmeticModel(String name, ResourceLocation texture) {
		this.name = name;
		this.texture = texture;
	}

	public ResourceLocation texture() {
		return texture;
	}

	/** The point a group turns around, in the model's space; zeros when the group is unknown. */
	public float[] pivot(String group) {
		load();
		Group g = groups.get(group);
		return g == null ? new float[3] : g.pivot;
	}

	/** Draws a group tinted with argb; the caller has set up the matrix and disabled lighting. */
	public void render(String group, int argb) {
		render(group, argb, texture);
	}

	/** {@link #render(String, int)} with another texture of the same layout, such as a recoloured one. */
	public void render(String group, int argb, ResourceLocation texture) {
		load();
		Group g = groups.get(group);
		if (g == null) {
			return;
		}
		GlStateManager.enableTexture2D();
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		GlStateManager.enableAlpha();
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
		if (g.list == 0 || g.tint != argb) {
			compile(g, argb);
		}
		GlStateManager.callList(g.list);
	}

	private void compile(Group g, int argb) {
		if (g.list == 0) {
			g.list = GLAllocation.generateDisplayLists(1);
		}
		g.tint = argb;
		int r = argb >> 16 & 0xFF;
		int gr = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		int a = argb >>> 24;
		GL11.glNewList(g.list, GL11.GL_COMPILE);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
		float[] v = g.vertices;
		for (int i = 0, k = 0; k < g.shades.length; i += 5, k++) {
			int s = g.shades[k] & 0xFF;
			w.pos(v[i], v[i + 1], v[i + 2]).tex(v[i + 3], v[i + 4]).color(r * s / 255, gr * s / 255, b * s / 255, a).endVertex();
		}
		t.draw();
		GL11.glEndList();
	}

	private void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		ResourceLocation file = new ResourceLocation("rago", "models/cosmetics/" + name + ".rmdl");
		try (InputStream raw = Minecraft.getMinecraft().getResourceManager().getResource(file).getInputStream();
			DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
			if (in.readInt() != 0x524D444C || in.readInt() != 1) {
				throw new IOException("not a Rago model");
			}
			int count = in.readInt();
			for (int i = 0; i < count; i++) {
				String group = in.readUTF();
				Group g = new Group();
				g.pivot = new float[] {in.readFloat(), in.readFloat(), in.readFloat()};
				int vertices = in.readInt() * 3;
				g.vertices = new float[vertices * 5];
				g.shades = new byte[vertices];
				for (int k = 0; k < vertices; k++) {
					for (int c = 0; c < 5; c++) {
						g.vertices[k * 5 + c] = in.readFloat();
					}
					g.shades[k] = in.readByte();
				}
				groups.put(group, g);
			}
		} catch (IOException e) {
			LOG.warn("Could not load the cosmetic model {}", file, e);
		}
	}

	private static final class Group {
		float[] pivot;
		float[] vertices;
		byte[] shades;
		int list;
		int tint;
	}
}
