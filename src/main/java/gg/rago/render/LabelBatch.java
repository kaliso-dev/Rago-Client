package gg.rago.render;

import gg.rago.gui.ui.TtfFont;
import gg.rago.gui.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects every name tag of a frame and draws them together after the world, in a handful of
 * draw calls instead of several per tag. Each tag is a flat label facing the camera; its corners
 * are worked out here, so no matrix changes are needed per tag.
 *
 * <p>Layers follow vanilla's name tags: {@link #SEE_THROUGH} ignores depth (the faint copy seen
 * through walls and the background), {@link #DEPTH} is depth tested without writing depth
 * (sneaking players), {@link #SOLID} is the text itself, depth tested and written.
 */
public final class LabelBatch {
	public static final int SEE_THROUGH = 0;
	public static final int DEPTH = 1;
	public static final int SOLID = 2;

	private static final ResourceLocation ASCII = new ResourceLocation("textures/font/ascii.png");
	private static final int CORNER_SEGMENTS = 4;

	private final Layer[] layers = {new Layer(), new Layer(), new Layer()};
	// the current label: world position of its origin and of one label unit right and down
	private float ox;
	private float oy;
	private float oz;
	private float rx;
	private float ry;
	private float rz;
	private float dx;
	private float dy;
	private float dz;

	/** Starts a label at camera-relative (x, y, z) facing the camera, {@code scale} blocks per unit, moved {@code down} units. */
	public void begin(double x, double y, double z, float scale, float down) {
		Minecraft mc = Minecraft.getMinecraft();
		float yaw = (float) Math.toRadians(-mc.getRenderManager().playerViewY);
		float pitch = (float) Math.toRadians(mc.getRenderManager().playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1 : 1));
		float cy = (float) Math.cos(yaw);
		float sy = (float) Math.sin(yaw);
		float cp = (float) Math.cos(pitch);
		float sp = (float) Math.sin(pitch);
		// Ry(yaw) * Rx(pitch) * scale(-s, -s, s) applied to the unit x and y axes, as vanilla's GL calls do
		rx = -scale * cy;
		ry = 0;
		rz = scale * sy;
		dx = -scale * sy * sp;
		dy = -scale * cp;
		dz = -scale * cy * sp;
		ox = (float) x + dx * down;
		oy = (float) y + dy * down;
		oz = (float) z + dz * down;
	}

	private float wx(float lx, float ly) {
		return ox + lx * rx + ly * dx;
	}

	private float wy(float lx, float ly) {
		return oy + lx * ry + ly * dy;
	}

	private float wz(float lx, float ly) {
		return oz + lx * rz + ly * dz;
	}

	// ---- shapes ---------------------------------------------------------------------------------

	private void tri(Layer l, float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
		FloatList f = l.shapes;
		f.vertex(wx(x0, y0), wy(x0, y0), wz(x0, y0), 0, 0, argb);
		f.vertex(wx(x1, y1), wy(x1, y1), wz(x1, y1), 0, 0, argb);
		f.vertex(wx(x2, y2), wy(x2, y2), wz(x2, y2), 0, 0, argb);
	}

	private void rect(Layer l, float x, float y, float w, float h, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		tri(l, x, y, x, y + h, x + w, y + h, argb);
		tri(l, x, y, x + w, y + h, x + w, y, argb);
	}

	/** A filled rectangle with anti-aliased round corners, in label units. */
	public void round(int layer, float x, float y, float w, float h, float radius, int argb) {
		if (argb >>> 24 == 0) {
			return;
		}
		Layer l = layers[layer];
		float r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0.05f) {
			rect(l, x, y, w, h, argb);
			return;
		}
		rect(l, x + r, y, w - 2 * r, h, argb);
		rect(l, x, y + r, r, h - 2 * r, argb);
		rect(l, x + w - r, y + r, r, h - 2 * r, argb);
		FloatList c = l.textured(Ui.cornerTextureId());
		corner(c, x, y, r, 0, 0, argb);
		corner(c, x + w - r, y, r, 1, 0, argb);
		corner(c, x, y + h - r, r, 0, 1, argb);
		corner(c, x + w - r, y + h - r, r, 1, 1, argb);
	}

	private void corner(FloatList f, float x, float y, float r, int flipX, int flipY, int argb) {
		float u0 = flipX;
		float u1 = 1 - flipX;
		float v0 = flipY;
		float v1 = 1 - flipY;
		quad(f, x, y, x + r, y + r, u0, v0, u1, v1, argb);
	}

	private void quad(FloatList f, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
		f.vertex(wx(x0, y1), wy(x0, y1), wz(x0, y1), u0, v1, argb);
		f.vertex(wx(x1, y1), wy(x1, y1), wz(x1, y1), u1, v1, argb);
		f.vertex(wx(x1, y0), wy(x1, y0), wz(x1, y0), u1, v0, argb);
		f.vertex(wx(x0, y0), wy(x0, y0), wz(x0, y0), u0, v0, argb);
	}

	/** An outline {@code width} units thick inside the bounds, following round corners. */
	public void outline(int layer, float x, float y, float w, float h, float radius, float width, int argb) {
		if (argb >>> 24 == 0 || width <= 0) {
			return;
		}
		Layer l = layers[layer];
		float r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0.05f) {
			rect(l, x, y, w, width, argb);
			rect(l, x, y + h - width, w, width, argb);
			rect(l, x, y + width, width, h - 2 * width, argb);
			rect(l, x + w - width, y + width, width, h - 2 * width, argb);
			return;
		}
		float inner = Math.max(0, r - width);
		float[] cx = {x + w - r, x + w - r, x + r, x + r};
		float[] cy = {y + r, y + h - r, y + h - r, y + r};
		float px0 = 0;
		float py0 = 0;
		float px1 = 0;
		float py1 = 0;
		boolean first = true;
		for (int c = 0; c <= 4; c++) {
			int k = c % 4;
			for (int i = 0; i <= CORNER_SEGMENTS; i++) {
				if (c == 4 && i > 0) {
					break;
				}
				double a = Math.toRadians(-90 + k * 90 + 90.0 * i / CORNER_SEGMENTS);
				float cos = (float) Math.cos(a);
				float sin = (float) Math.sin(a);
				float ox0 = cx[k] + cos * r;
				float oy0 = cy[k] + sin * r;
				float ix0 = cx[k] + cos * inner;
				float iy0 = cy[k] + sin * inner;
				if (!first) {
					tri(l, px0, py0, px1, py1, ox0, oy0, argb);
					tri(l, ox0, oy0, px1, py1, ix0, iy0, argb);
				}
				first = false;
				px0 = ox0;
				py0 = oy0;
				px1 = ix0;
				py1 = iy0;
			}
		}
	}

	// ---- text -----------------------------------------------------------------------------------

	/**
	 * Text with its line box top at (x, y) in label units. TrueType fonts are rasterised at
	 * {@code pixelsPerUnit}; the Minecraft font is used when {@code font} is null.
	 *
	 * @return false if the text has characters this batch cannot draw (the caller draws it itself)
	 */
	public boolean text(int layer, TtfFont font, float em, float pixelsPerUnit, String s, float x, float y, int argb, boolean shadow) {
		Layer l = layers[layer];
		if (font == null) {
			return vanillaText(l, s, x, y, argb, shadow);
		}
		float px = Math.max(6, Math.round(em * pixelsPerUnit * 2) / 2f);
		float k = px / em;
		FloatList f = l.textured(font.textureId(px));
		float top = (y + 7) * k - font.ascent(px);
		font.emitFormatted(s, x * k, top, px, argb, shadow, Math.max(1, k * 0.75f),
			(x0, y0, x1, y1, u0, v0, u1, v1, c) -> quad(f, x0 / k, y0 / k, x1 / k, y1 / k, u0, v0, u1, v1, c));
		return true;
	}

	/** Width of {@code s} in label units, as {@link #text} draws it. */
	public static float width(TtfFont font, float em, float pixelsPerUnit, String s) {
		if (font == null) {
			return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
		}
		float px = Math.max(6, Math.round(em * pixelsPerUnit * 2) / 2f);
		return font.widthFormatted(s, px) / (px / em);
	}

	/** The Minecraft font's ASCII page, glyph by glyph as {@code FontRenderer.renderDefaultChar} lays it out. */
	private boolean vanillaText(Layer l, String s, float x, float y, int argb, boolean shadow) {
		FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
		if (fr.getUnicodeFlag()) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§') {
				i++;
			} else if (c < 32 || c > 126) {
				return false;
			}
		}
		ITextureObject tex = Minecraft.getMinecraft().getTextureManager().getTexture(ASCII);
		if (tex == null) {
			return false;
		}
		FloatList f = l.textured(tex.getGlTextureId());
		if (shadow) {
			vanillaRuns(f, fr, s, x + 1, y + 1, argb, true);
		}
		vanillaRuns(f, fr, s, x, y, argb, false);
		return true;
	}

	private void vanillaRuns(FloatList f, FontRenderer fr, String s, float x, float y, int argb, boolean shadow) {
		int alpha = argb & 0xFF000000;
		int color = argb;
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				char code = Character.toLowerCase(s.charAt(++i));
				int index = "0123456789abcdef".indexOf(code);
				if (index >= 0) {
					color = alpha | fr.getColorCode(code) & 0xFFFFFF;
				} else if (code == 'r') {
					color = argb;
				}
				continue;
			}
			int width = fr.getCharWidth(c);
			if (c != ' ') {
				float u = (c % 16) * 8;
				float v = (c / 16) * 8;
				float gw = width - 1.01f;
				int shown = shadow ? color & 0xFF000000 | (color & 0xFCFCFC) >> 2 : color;
				quad(f, cx, y, cx + gw, y + 7.99f, u / 128f, v / 128f, (u + gw) / 128f, (v + 7.99f) / 128f, shown);
			}
			cx += width;
		}
	}

	// ---- drawing --------------------------------------------------------------------------------

	public boolean isEmpty() {
		for (Layer l : layers) {
			if (!l.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public void clear() {
		for (Layer l : layers) {
			l.clear();
		}
	}

	/** Draws everything collected, then empties the batch. Call with the world's camera matrix. */
	public void flush() {
		if (isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		GlStateManager.disableLighting();
		mc.entityRenderer.disableLightmap();
		GlStateManager.disableCull();
		GlStateManager.disableAlpha();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.color(1, 1, 1, 1);
		for (int i = 0; i < layers.length; i++) {
			if (layers[i].isEmpty()) {
				continue;
			}
			if (i == SEE_THROUGH) {
				GlStateManager.disableDepth();
			} else {
				GlStateManager.enableDepth();
			}
			GlStateManager.depthMask(i == SOLID);
			layers[i].draw();
		}
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.enableTexture2D();
		GlStateManager.enableAlpha();
		GlStateManager.enableCull();
		GlStateManager.disableBlend();
		mc.entityRenderer.enableLightmap();
		GlStateManager.color(1, 1, 1, 1);
		clear();
	}

	private static final class Layer {
		final FloatList shapes = new FloatList();
		/** Textured quads by GL texture id, in the order the textures were first used. */
		final Map<Integer, FloatList> textured = new LinkedHashMap<>();

		FloatList textured(int texture) {
			FloatList f = textured.get(texture);
			if (f == null) {
				f = new FloatList();
				textured.put(texture, f);
			}
			return f;
		}

		boolean isEmpty() {
			if (shapes.size > 0) {
				return false;
			}
			for (FloatList f : textured.values()) {
				if (f.size > 0) {
					return false;
				}
			}
			return true;
		}

		void clear() {
			shapes.size = 0;
			for (FloatList f : textured.values()) {
				f.size = 0;
			}
		}

		void draw() {
			Tessellator t = Tessellator.getInstance();
			WorldRenderer w = t.getWorldRenderer();
			if (shapes.size > 0) {
				GlStateManager.disableTexture2D();
				w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
				shapes.upload(w, false);
				t.draw();
				GlStateManager.enableTexture2D();
			}
			for (Map.Entry<Integer, FloatList> e : textured.entrySet()) {
				if (e.getValue().size == 0) {
					continue;
				}
				GlStateManager.bindTexture(e.getKey());
				w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
				e.getValue().upload(w, true);
				t.draw();
			}
		}
	}

	/** Vertices as x, y, z, u, v, argb (bits) in one growing float array, reused every frame. */
	private static final class FloatList {
		private static final int STRIDE = 6;
		float[] data = new float[STRIDE * 256];
		int size;

		void vertex(float x, float y, float z, float u, float v, int argb) {
			if (size + STRIDE > data.length) {
				data = java.util.Arrays.copyOf(data, data.length * 2);
			}
			data[size] = x;
			data[size + 1] = y;
			data[size + 2] = z;
			data[size + 3] = u;
			data[size + 4] = v;
			data[size + 5] = Float.intBitsToFloat(argb);
			size += STRIDE;
		}

		void upload(WorldRenderer w, boolean textured) {
			for (int i = 0; i < size; i += STRIDE) {
				int c = Float.floatToRawIntBits(data[i + 5]);
				w.pos(data[i], data[i + 1], data[i + 2]);
				if (textured) {
					w.tex(data[i + 3], data[i + 4]);
				}
				w.color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).endVertex();
			}
		}
	}
}
