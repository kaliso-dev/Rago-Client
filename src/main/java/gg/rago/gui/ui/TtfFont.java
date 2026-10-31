package gg.rago.gui.ui;

import gg.rago.core.RagoClient;
import gg.rago.hooks.Hooks;
import gg.rago.render.StaticTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A TrueType font drawn from glyph atlases rendered with Java2D. One atlas is built per pixel
 * size the first time that size is used, so text is rasterised at the exact size it is shown
 * and stays sharp at any window size. Covers Latin-1 plus a few typographic marks.
 */
public final class TtfFont {
	private static final String EXTRA = "·•…×→—–’‘“”▶";
	private static final int PAD = 2;

	private final ResourceLocation source;
	private Font base;
	/** Least recently used first, so the oldest size is dropped when there are too many. */
	private final Map<Integer, Atlas> atlases = new LinkedHashMap<>(16, 0.75f, true);
	/** Sizes kept at once; each atlas is a texture of up to a few MB. */
	private static final int MAX_ATLASES = 12;

	public TtfFont(ResourceLocation source) {
		this.source = source;
	}

	private Font base() {
		if (base == null) {
			try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(source).getInputStream()) {
				base = Font.createFont(Font.TRUETYPE_FONT, in);
			} catch (Exception e) {
				RagoClient.LOG.error("Could not load font {}, using a system font", source, e);
				base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
			}
		}
		return base;
	}

	private Atlas atlas(int px) {
		Atlas a = atlases.get(px);
		if (a == null) {
			if (atlases.size() >= MAX_ATLASES) {
				Iterator<Atlas> oldest = atlases.values().iterator();
				oldest.next().texture.deleteGlTexture();
				oldest.remove();
			}
			a = new Atlas(base().deriveFont((float) px));
			atlases.put(px, a);
		}
		return a;
	}

	/** Advances at {@link #METRICS_PX}, for measuring text without building a texture. */
	private float[] metrics;
	private float metricsSpace;
	/** Size the size-independent measurements are taken at. */
	public static final int METRICS_PX = 48;

	/** Width of {@code s} without § codes at {@link #METRICS_PX} pixels; no texture is created. */
	public float measure(String s) {
		s = Hooks.nickPlain(s);
		if (metrics == null) {
			Font f = base().deriveFont((float) METRICS_PX);
			FontRenderContext frc = new FontRenderContext(null, true, true);
			float[] m = new float[256];
			for (char c = 32; c < 256; c++) {
				m[c] = (float) f.getStringBounds(String.valueOf(c), frc).getWidth();
			}
			metricsSpace = m[' '];
			metrics = m;
		}
		float w = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\u00a7' && i + 1 < s.length()) {
				i++;
			} else if (c < 256) {
				w += c < 32 ? metricsSpace : metrics[c];
			} else {
				w += extraAdvance(c);
			}
		}
		return w;
	}

	private final Map<Character, Float> extraMetrics = new HashMap<>();

	private float extraAdvance(char c) {
		Float a = extraMetrics.get(c);
		if (a == null) {
			a = EXTRA.indexOf(c) >= 0
				? (float) base().deriveFont((float) METRICS_PX).getStringBounds(String.valueOf(c), new FontRenderContext(null, true, true)).getWidth()
				: metricsSpace;
			extraMetrics.put(c, a);
		}
		return a;
	}

	/** Width in real pixels of {@code s} drawn at {@code px} pixels. */
	public float width(String s, float px) {
		s = Hooks.nickPlain(s);
		Atlas a = atlas(Math.max(1, Math.round(px)));
		float w = 0;
		for (int i = 0; i < s.length(); i++) {
			w += a.advance(s.charAt(i));
		}
		return w;
	}

	/** Distance from the top of a line to the baseline, in real pixels. */
	public float ascent(float px) {
		return atlas(Math.max(1, Math.round(px))).ascent;
	}

	/** Receives one glyph quad: corners in real pixels, atlas coordinates and colour. */
	public interface QuadSink {
		void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb);
	}

	/** GL texture of the atlas used at {@code px} pixels, for callers that batch glyphs themselves. */
	public int textureId(float px) {
		return atlas(Math.max(1, Math.round(px))).texture.getGlTextureId();
	}

	/**
	 * Draws {@code s} with its line box starting at (x, y) in real pixels. The line box is the
	 * font's ascent plus descent, so text of different sizes lines up by its top.
	 */
	public void draw(String s, float x, float y, float px, int argb) {
		begin(px);
		emit(Hooks.nickPlain(s), x, y, px, argb, TESSELLATOR_SINK);
		Tessellator.getInstance().draw();
	}

	private void begin(float px) {
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.bindTexture(textureId(px));
		GlStateManager.color(1, 1, 1, 1);
		Tessellator.getInstance().getWorldRenderer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
	}

	/** Glyph quads straight into the tessellator, which must have been begun with POSITION_TEX_COLOR quads. */
	public static final QuadSink TESSELLATOR_SINK = (x0, y0, x1, y1, u0, v0, u1, v1, argb) -> {
		WorldRenderer w = Tessellator.getInstance().getWorldRenderer();
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		int a = argb >>> 24;
		w.pos(x0, y1, 0).tex(u0, v1).color(r, g, b, a).endVertex();
		w.pos(x1, y1, 0).tex(u1, v1).color(r, g, b, a).endVertex();
		w.pos(x1, y0, 0).tex(u1, v0).color(r, g, b, a).endVertex();
		w.pos(x0, y0, 0).tex(u0, v0).color(r, g, b, a).endVertex();
	};

	/** Sends the glyph quads of {@code s} (no § codes) to {@code sink}; returns the advance in pixels. */
	private float emit(CharSequence s, float x, float y, float px, int argb, QuadSink sink) {
		Atlas a = atlas(Math.max(1, Math.round(px)));
		float cx = Math.round(x);
		float gy = Math.round(y) - PAD;
		float inv = 1f / a.size;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			Glyph gl = a.glyph(c);
			if (gl == null) {
				cx += a.spaceAdvance;
				continue;
			}
			float gx = cx - PAD;
			sink.quad(gx, gy, gx + gl.w, gy + gl.h, gl.x * inv, gl.y * inv, (gl.x + gl.w) * inv, (gl.y + gl.h) * inv, argb);
			cx += gl.advance;
		}
		return cx - Math.round(x);
	}

	/** Width of {@code s} without its § formatting codes. */
	public float widthFormatted(String s, float px) {
		return width(strip(s), px);
	}

	/** Removes § formatting codes. */
	public static String strip(String s) {
		if (s.indexOf('\u00a7') < 0) {
			return s;
		}
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\u00a7' && i + 1 < s.length()) {
				i++;
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	/**
	 * Draws {@code s} honouring Minecraft's § colour codes (§r goes back to {@code argb}; styles such
	 * as bold are ignored). With {@code shadow} a darker copy is drawn {@code shadowOffset} pixels
	 * down and right first, like the vanilla font's shadow. One draw call for the whole string.
	 */
	public void drawFormatted(String s, float x, float y, float px, int argb, boolean shadow, float shadowOffset) {
		begin(px);
		emitFormatted(s, x, y, px, argb, shadow, shadowOffset, TESSELLATOR_SINK);
		Tessellator.getInstance().draw();
	}

	/** {@link #drawFormatted} into {@code sink} instead of the screen, for batched drawing. */
	public void emitFormatted(String s, float x, float y, float px, int argb, boolean shadow, float shadowOffset, QuadSink sink) {
		s = Hooks.nick(s);
		if (shadow) {
			emitRuns(s, x + shadowOffset, y + shadowOffset, px, argb, true, sink);
		}
		emitRuns(s, x, y, px, argb, false, sink);
	}

	/** Only the shadow {@link #emitFormatted} would draw under the text. */
	public void emitShadow(String s, float x, float y, float px, int argb, float shadowOffset, QuadSink sink) {
		emitRuns(Hooks.nick(s), x + shadowOffset, y + shadowOffset, px, argb, true, sink);
	}

	private void emitRuns(String s, float x, float y, float px, int argb, boolean shadow, QuadSink sink) {
		if (s.indexOf('\u00a7') < 0) {
			emit(s, x, y, px, shadow ? shade(argb) : argb, sink);
			return;
		}
		int alpha = argb & 0xFF000000;
		int color = argb;
		int start = 0;
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\u00a7' && i + 1 < s.length()) {
				if (i > start) {
					cx += emit(s.subSequence(start, i), cx, y, px, shadow ? shade(color) : color, sink);
				}
				char code = Character.toLowerCase(s.charAt(++i));
				int index = "0123456789abcdef".indexOf(code);
				if (index >= 0) {
					color = alpha | TextColors.rgb(index);
				} else if (code == 'r') {
					color = argb;
				}
				start = i + 1;
			}
		}
		if (start < s.length()) {
			emit(s.subSequence(start, s.length()), cx, y, px, shadow ? shade(color) : color, sink);
		}
	}

	private static int shade(int color) {
		return color & 0xFF000000 | (color & 0xFCFCFC) >> 2;
	}

	private static final class Glyph {
		final int x;
		final int y;
		final int w;
		final int h;
		final float advance;

		Glyph(int x, int y, int w, int h, float advance) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.advance = advance;
		}
	}

	private static final class Atlas {
		final Glyph[] latin = new Glyph[256];
		final Map<Character, Glyph> extra = new HashMap<>();
		final StaticTexture texture;
		final int size;
		final float ascent;
		final float spaceAdvance;

		Atlas(Font font) {
			FontRenderContext frc = new FontRenderContext(null, true, true);
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			Graphics2D pg = probe.createGraphics();
			pg.setFont(font);
			FontMetrics fm = pg.getFontMetrics();
			pg.dispose();
			int lineH = fm.getAscent() + fm.getDescent() + PAD * 2;
			ascent = fm.getAscent();
			StringBuilder chars = new StringBuilder();
			for (char c = 32; c < 256; c++) {
				if (c < 127 || c >= 160) {
					chars.append(c);
				}
			}
			chars.append(EXTRA);

			int side = 128;
			while (!fits(font, frc, chars, side, lineH)) {
				side *= 2;
			}
			size = side;
			BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setFont(font);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(java.awt.Color.WHITE);
			int x = 0;
			int y = 0;
			for (int i = 0; i < chars.length(); i++) {
				char c = chars.charAt(i);
				float adv = (float) font.getStringBounds(String.valueOf(c), frc).getWidth();
				int w = (int) Math.ceil(adv) + PAD * 2 + 2;
				if (x + w > side) {
					x = 0;
					y += lineH;
				}
				g.drawString(String.valueOf(c), x + PAD, y + PAD + fm.getAscent());
				Glyph gl = new Glyph(x, y, w, lineH, adv);
				if (c < 256) {
					latin[c] = gl;
				} else {
					extra.put(c, gl);
				}
				x += w;
			}
			g.dispose();
			spaceAdvance = latin[' '].advance;
			texture = new StaticTexture(img);
			GlStateManager.bindTexture(texture.getGlTextureId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		}

		private static boolean fits(Font font, FontRenderContext frc, CharSequence chars, int side, int lineH) {
			int x = 0;
			int y = 0;
			for (int i = 0; i < chars.length(); i++) {
				int w = (int) Math.ceil(font.getStringBounds(String.valueOf(chars.charAt(i)), frc).getWidth()) + PAD * 2 + 2;
				if (x + w > side) {
					x = 0;
					y += lineH;
				}
				x += w;
			}
			return y + lineH <= side;
		}

		Glyph glyph(char c) {
			return c < 256 ? latin[c] : extra.get(c);
		}

		float advance(char c) {
			Glyph g = glyph(c);
			return g == null ? spaceAdvance : g.advance;
		}
	}
}
