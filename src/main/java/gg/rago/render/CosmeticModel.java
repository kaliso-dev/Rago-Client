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
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
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

	/**
	 * {@link #render(String, int, ResourceLocation)} for a glow drawn over the model with a texture
	 * that is clear but for its lit pixels (the mecha wings' lines, a neon colour): only the triangles
	 * that show any lit pixel are drawn, so the second pass costs a fraction of the model. The
	 * others would draw nothing anyway: the alpha test drops every clear pixel.
	 */
	public void renderGlow(String group, int argb, ResourceLocation texture) {
		load();
		Group g = groups.get(group);
		if (g == null) {
			return;
		}
		GlStateManager.enableTexture2D();
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		GlStateManager.enableAlpha();
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
		Glow glow = g.glows.get(texture);
		if (glow == null) {
			glow = new Glow();
			glow.keep = litTriangles(g);
			g.glows.put(texture, glow);
		}
		if (glow.list == 0 || glow.tint != argb) {
			if (glow.list == 0) {
				glow.list = GLAllocation.generateDisplayLists(1);
			}
			glow.tint = argb;
			compile(g, glow.list, argb, glow.keep);
		}
		GlStateManager.callList(glow.list);
	}

	/**
	 * Which triangles of {@code g} show a pixel of the bound texture that the alpha test keeps (a
	 * texel around them included, for the edges); null (all) if the texture cannot be read.
	 */
	private static boolean[] litTriangles(Group g) {
		int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
		int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
		if (w <= 0 || h <= 0 || w > 4096 || h > 4096) {
			return null;
		}
		ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		boolean[] lit = new boolean[w * h];
		for (int i = 0; i < lit.length; i++) {
			lit[i] = (pixels.get(i * 4 + 3) & 0xFF) > 25;
		}
		float[] v = g.vertices;
		boolean[] keep = new boolean[g.shades.length / 3];
		for (int t = 0; t < keep.length; t++) {
			float u0 = Float.MAX_VALUE;
			float u1 = -Float.MAX_VALUE;
			float v0 = Float.MAX_VALUE;
			float v1 = -Float.MAX_VALUE;
			for (int k = 0; k < 3; k++) {
				int i = (t * 3 + k) * 5;
				u0 = Math.min(u0, v[i + 3]);
				u1 = Math.max(u1, v[i + 3]);
				v0 = Math.min(v0, v[i + 4]);
				v1 = Math.max(v1, v[i + 4]);
			}
			int x0 = Math.max(0, (int) Math.floor(u0 * w) - 1);
			int x1 = Math.min(w - 1, (int) Math.ceil(u1 * w));
			int y0 = Math.max(0, (int) Math.floor(v0 * h) - 1);
			int y1 = Math.min(h - 1, (int) Math.ceil(v1 * h));
			search:
			for (int y = y0; y <= y1; y++) {
				for (int x = x0; x <= x1; x++) {
					if (lit[y * w + x]) {
						keep[t] = true;
						break search;
					}
				}
			}
		}
		return keep;
	}

	private void compile(Group g, int argb) {
		if (g.list == 0) {
			g.list = GLAllocation.generateDisplayLists(1);
		}
		g.tint = argb;
		compile(g, g.list, argb, null);
	}

	/** Compiles the triangles of {@code g} into {@code list}: those {@code keep} says, or all for null. */
	private static void compile(Group g, int list, int argb, boolean[] keep) {
		int r = argb >> 16 & 0xFF;
		int gr = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		int a = argb >>> 24;
		GL11.glNewList(list, GL11.GL_COMPILE);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
		float[] v = g.vertices;
		for (int i = 0, k = 0; k < g.shades.length; i += 5, k++) {
			if (keep != null && !keep[k / 3]) {
				continue;
			}
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
		/** The glow passes, by texture. */
		final Map<ResourceLocation, Glow> glows = new HashMap<>();
	}

	/** The triangles a glow texture lights, compiled on their own. */
	private static final class Glow {
		boolean[] keep;
		int list;
		int tint;
	}
}
