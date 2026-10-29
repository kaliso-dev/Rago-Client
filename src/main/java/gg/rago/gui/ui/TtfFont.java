package gg.rago.gui.ui;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
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

	/** Width in real pixels of {@code s} drawn at {@code px} pixels. */
	public float width(String s, float px) {
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

	/**
	 * Draws {@code s} with its line box starting at (x, y) in real pixels. The line box is the
	 * font's ascent plus descent, so text of different sizes lines up by its top.
	 */
	public void draw(String s, float x, float y, float px, int argb) {
		Atlas a = atlas(Math.max(1, Math.round(px)));
		x = Math.round(x);
		y = Math.round(y);
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.bindTexture(a.texture.getGlTextureId());
		GlStateManager.color(1, 1, 1, 1);
		float r = (argb >> 16 & 0xFF) / 255f;
		float g = (argb >> 8 & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float al = (argb >>> 24) / 255f;
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			Glyph gl = a.glyph(s.charAt(i));
			if (gl == null) {
				continue;
			}
			float gx = cx - PAD;
			float gy = y - PAD;
			float u0 = gl.x / (float) a.size;
			float v0 = gl.y / (float) a.size;
			float u1 = (gl.x + gl.w) / (float) a.size;
			float v1 = (gl.y + gl.h) / (float) a.size;
			w.pos(gx, gy + gl.h, 0).tex(u0, v1).color(r, g, b, al).endVertex();
			w.pos(gx + gl.w, gy + gl.h, 0).tex(u1, v1).color(r, g, b, al).endVertex();
			w.pos(gx + gl.w, gy, 0).tex(u1, v0).color(r, g, b, al).endVertex();
			w.pos(gx, gy, 0).tex(u0, v0).color(r, g, b, al).endVertex();
			cx += gl.advance;
		}
		t.draw();
	}

	/** Minecraft's sixteen formatting colours (§0 to §f), as RGB. */
	private static final int[] CODE_COLORS = new int[16];

	static {
		for (int i = 0; i < 16; i++) {
			int j = (i >> 3 & 1) * 85;
			int r = (i >> 2 & 1) * 170 + j;
			int g = (i >> 1 & 1) * 170 + j;
			int b = (i & 1) * 170 + j;
			if (i == 6) {
				r += 85;
			}
			CODE_COLORS[i] = r << 16 | g << 8 | b;
		}
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
	 * down and right first, like the vanilla font's shadow.
	 */
	public void drawFormatted(String s, float x, float y, float px, int argb, boolean shadow, float shadowOffset) {
		if (shadow) {
			drawRuns(s, x + shadowOffset, y + shadowOffset, px, argb, true);
		}
		drawRuns(s, x, y, px, argb, false);
	}

	private void drawRuns(String s, float x, float y, float px, int argb, boolean shadow) {
		int alpha = argb & 0xFF000000;
		int color = argb;
		StringBuilder run = new StringBuilder();
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\u00a7' && i + 1 < s.length()) {
				cx = flush(run, cx, y, px, color, shadow);
				int code = "0123456789abcdef".indexOf(Character.toLowerCase(s.charAt(++i)));
				if (code >= 0) {
					color = alpha | CODE_COLORS[code];
				} else if (Character.toLowerCase(s.charAt(i)) == 'r') {
					color = argb;
				}
			} else {
				run.append(c);
			}
		}
		flush(run, cx, y, px, color, shadow);
	}

	private float flush(StringBuilder run, float x, float y, float px, int color, boolean shadow) {
		if (run.length() == 0) {
			return x;
		}
		String text = run.toString();
		run.setLength(0);
		draw(text, x, y, px, shadow ? (color & 0xFF000000) | (color & 0xFCFCFC) >> 2 : color);
		return x + width(text, px);
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
		final DynamicTexture texture;
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
			texture = new DynamicTexture(img);
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
