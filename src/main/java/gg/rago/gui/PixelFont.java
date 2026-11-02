package gg.rago.gui;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Minecraft's own font drawn with Java 2D, for pictures made outside the game's renderer: the
 * glyphs of the game's font texture ({@code font/ascii.png}, the resource pack's when it has one)
 * at a whole number of pixels per font pixel, with the game's widths, shadow and colour codes
 * ({@code §a}, {@code §l} for bold, {@code §r}). Characters outside that texture come from the
 * game's unicode pages, as in the game.
 */
final class PixelFont {
	/** The characters of {@code ascii.png}, in its order (FontRenderer's table). */
	static final String TABLE = "ÀÁÂÈÊËÍÓÔÕÚßãõğİ"
		+ "ıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
		+ " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000"
		+ "ÇüéâäàåçêëèïîìÄÅ"
		+ "ÉæÆôöòûùÿÖÜø£Ø×ƒ"
		+ "áíóúñÑªº¿®¬½¼¡«»"
		+ "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐"
		+ "└┴┬├─┼╞╟╚╔╩╦╠═╬╧"
		+ "╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
		+ "αβΓπΣσμτΦΘΩδ∞∅∈∩"
		+ "≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";

	/** The 16 chat colours, by code 0 to f. */
	static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private final BufferedImage ascii;
	/** Texels per glyph cell of {@code ascii} (8 in a 128 x 128 font). */
	private final int cell;
	/** Widths of the {@link #TABLE} characters, in font pixels (8 per cell), spacing included. */
	private final int[] widths = new int[256];
	private final byte[] glyphSizes;
	private final Function<Integer, BufferedImage> pages;
	private final Map<Long, BufferedImage> tinted = new HashMap<>();

	/**
	 * {@code ascii}: the font texture; {@code glyphSizes}: {@code font/glyph_sizes.bin} (null: no
	 * unicode); {@code pages}: unicode page {@code n} ({@code font/unicode_page_nn.png}) or null.
	 */
	PixelFont(BufferedImage ascii, byte[] glyphSizes, Function<Integer, BufferedImage> pages) {
		this.ascii = ascii;
		this.cell = Math.max(1, ascii.getWidth() / 16);
		this.glyphSizes = glyphSizes;
		this.pages = pages;
		readWidths();
	}

	/** FontRenderer.readFontTexture: the last column with a visible texel, scaled to 8 per cell, plus one. */
	private void readWidths() {
		int w = ascii.getWidth();
		int rows = ascii.getHeight() / 16;
		float scale = 8f / cell;
		for (int c = 0; c < 256; c++) {
			int cx = c % 16 * cell;
			int cy = c / 16 * rows;
			int last = cell - 1;
			for (; last >= 0; last--) {
				boolean empty = true;
				for (int y = 0; y < rows && empty; y++) {
					if (cx + last < w && cy + y < ascii.getHeight() && (ascii.getRGB(cx + last, cy + y) >>> 24) != 0) {
						empty = false;
					}
				}
				if (!empty) {
					break;
				}
			}
			widths[c] = (int) (0.5 + (last + 1) * scale) + 1;
		}
	}

	/** The width of one character in font pixels, as the game counts it (-1 for the colour sign). */
	int charWidth(char ch) {
		if (ch == '§') {
			return -1;
		}
		if (ch == ' ') {
			return 4;
		}
		int i = TABLE.indexOf(ch);
		if (ch > 0 && i >= 0) {
			return widths[i];
		}
		if (glyphSizes != null && ch < glyphSizes.length && glyphSizes[ch] != 0) {
			int start = (glyphSizes[ch] & 0xFF) >>> 4;
			int end = (glyphSizes[ch] & 15) + 1;
			return (end - start) / 2 + 1;
		}
		return 0;
	}

	/** The width of a text with colour codes, in font pixels (bold adds one per character, as in the game). */
	int width(String s) {
		int w = 0;
		boolean bold = false;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '§' && i + 1 < s.length()) {
				char code = Character.toLowerCase(s.charAt(++i));
				if (code == 'l') {
					bold = true;
				} else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) {
					bold = false;
				}
				continue;
			}
			int cw = charWidth(ch);
			if (cw > 0) {
				w += cw + (bold ? 1 : 0);
			}
		}
		return w;
	}

	/**
	 * Draws {@code s} with its top-left corner at (x, y), {@code scale} pixels per font pixel, in
	 * {@code rgb} until a colour code changes it, with the game's shadow when {@code shadow}.
	 * Returns the width drawn, in pixels.
	 */
	float draw(Graphics2D g, String s, float x, float y, int scale, int rgb, boolean shadow) {
		Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		try {
			if (shadow) {
				run(g, s, x + scale, y + scale, scale, rgb, true);
			}
			return run(g, s, x, y, scale, rgb, false);
		} finally {
			if (interpolation != null) {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			}
		}
	}

	private float run(Graphics2D g, String s, float x0, float y, int scale, int rgb, boolean shadow) {
		int color = rgb & 0xFFFFFF;
		boolean bold = false;
		float x = x0;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '§' && i + 1 < s.length()) {
				char code = Character.toLowerCase(s.charAt(++i));
				int c = "0123456789abcdef".indexOf(code);
				if (c >= 0) {
					color = COLORS[c];
					bold = false;
				} else if (code == 'l') {
					bold = true;
				} else if (code == 'r') {
					color = rgb & 0xFFFFFF;
					bold = false;
				}
				continue;
			}
			int paint = shadow ? (color & 0xFCFCFC) >> 2 : color;
			float advance = glyph(g, ch, x, y, scale, paint);
			if (bold) {
				glyph(g, ch, x + scale, y, scale, paint);
				advance += scale;
			}
			x += advance;
		}
		return x - x0;
	}

	/** One character; returns its advance in pixels. */
	private float glyph(Graphics2D g, char ch, float x, float y, int scale, int rgb) {
		if (ch == ' ') {
			return 4f * scale;
		}
		int i = TABLE.indexOf(ch);
		if (ch > 0 && i >= 0) {
			int rows = ascii.getHeight() / 16;
			int sx = i % 16 * cell;
			int sy = i / 16 * rows;
			int px = Math.round(x);
			int py = Math.round(y);
			g.drawImage(tint(-1, ascii, rgb), px, py, px + 8 * scale, py + 8 * scale, sx, sy, sx + cell, sy + rows, null);
			return widths[i] * (float) scale;
		}
		if (glyphSizes == null || ch >= glyphSizes.length || glyphSizes[ch] == 0) {
			return 0;
		}
		BufferedImage page = pages.apply(ch >> 8);
		if (page == null) {
			return 0;
		}
		int start = (glyphSizes[ch] & 0xFF) >>> 4;
		int end = (glyphSizes[ch] & 15) + 1;
		int texel = page.getWidth() / 256;
		int sx = ((ch & 0xFF) % 16 * 16 + start) * texel;
		int sy = (ch & 0xFF) / 16 * 16 * texel;
		int px = Math.round(x);
		int py = Math.round(y);
		// unicode glyphs are 16 texels high in an 8-pixel line: half a font pixel each
		int dw = Math.round((end - start) * scale / 2f);
		g.drawImage(tint(ch >> 8, page, rgb), px, py, px + dw, py + 8 * scale, sx, sy, sx + (end - start) * texel, sy + 16 * texel, null);
		return ((end - start) / 2f + 1) * scale;
	}

	/** The texture with every texel in {@code rgb}, keeping its alpha; kept per page and colour. */
	private synchronized BufferedImage tint(int page, BufferedImage src, int rgb) {
		long key = (long) page << 32 | (rgb & 0xFFFFFFFFL);
		BufferedImage out = tinted.get(key);
		if (out == null) {
			out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.drawImage(src, 0, 0, null);
			g.setComposite(AlphaComposite.SrcIn);
			g.setColor(new java.awt.Color(rgb & 0xFFFFFF));
			g.fillRect(0, 0, out.getWidth(), out.getHeight());
			g.dispose();
			tinted.put(key, out);
		}
		return out;
	}
}
