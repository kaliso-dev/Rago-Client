package gg.rago.render;

import gg.rago.hooks.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Minecraft-font text in one draw call. Vanilla's FontRenderer sends every character as its own
 * small immediate-mode strip; for HUD text that changes every frame this is laid out the same way
 * ({@code FontRenderer.renderDefaultChar}: same glyphs, widths, colours and shadow) into a single
 * batch. Text it cannot reproduce exactly (Unicode font, characters outside the ASCII page, bold,
 * italic, underline, strikethrough or obfuscated) is left to vanilla.
 */
public final class VanillaText {
	private static final ResourceLocation ASCII = new ResourceLocation("textures/font/ascii.png");

	private VanillaText() {
	}

	/** Whether {@code s} can be drawn here exactly as vanilla would draw it. */
	public static boolean batchable(String s) {
		s = Hooks.nick(s);
		if (Minecraft.getMinecraft().fontRendererObj.getUnicodeFlag() || s.isEmpty() || textureId() < 0) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\u00a7' && i + 1 < s.length()) {
				char code = Character.toLowerCase(s.charAt(++i));
				if ("0123456789abcdefr".indexOf(code) < 0) {
					return false;
				}
			} else if (c < 32 || c > 126) {
				return false;
			}
		}
		return true;
	}

	/** GL texture of the font's ASCII page, or -1 if it is not loaded. */
	public static int textureId() {
		ITextureObject tex = Minecraft.getMinecraft().getTextureManager().getTexture(ASCII);
		return tex == null ? -1 : tex.getGlTextureId();
	}

	/** Draws like {@code FontRenderer.drawString}; false if vanilla must draw it instead. */
	public static boolean draw(String s, float x, float y, int argb, boolean shadow) {
		if (!batchable(s)) {
			return false;
		}
		GlStateManager.enableTexture2D();
		GlStateManager.bindTexture(textureId());
		Tessellator t = Tessellator.getInstance();
		WorldRenderer b = t.getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		append(b, s, x, y, argb, shadow);
		t.draw();
		return true;
	}

	/** Adds the glyphs of a {@link #batchable} string to a batch begun with the ASCII page bound. */
	public static void append(WorldRenderer b, String s, float x, float y, int argb, boolean shadow) {
		s = Hooks.nick(s);
		if ((argb & 0xFC000000) == 0) {
			argb |= 0xFF000000; // as vanilla: a colour without alpha is opaque
		}
		FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
		if (shadow) {
			glyphs(b, fr, s, x + 1, y + 1, argb, true);
		}
		glyphs(b, fr, s, x, y, argb, false);
	}

	private static void glyphs(WorldRenderer b, FontRenderer fr, String s, float x, float y, int argb, boolean shadow) {
		int alpha = argb & 0xFF000000;
		int color = argb;
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				char code = Character.toLowerCase(s.charAt(++i));
				color = code == 'r' ? argb : alpha | fr.getColorCode(code) & 0xFFFFFF;
				continue;
			}
			int width = fr.getCharWidth(c);
			if (c != ' ') {
				int shown = shadow ? alpha | (color & 0xFCFCFC) >> 2 : color;
				int a = shown >>> 24;
				int r = shown >> 16 & 0xFF;
				int g = shown >> 8 & 0xFF;
				int bl = shown & 0xFF;
				float u = (c % 16) * 8 / 128f;
				float v = (c / 16) * 8 / 128f;
				float gw = width - 1.01f;
				float u1 = u + gw / 128f;
				float v1 = v + 7.99f / 128f;
				b.pos(cx, y, 0).tex(u, v).color(r, g, bl, a).endVertex();
				b.pos(cx, y + 7.99f, 0).tex(u, v1).color(r, g, bl, a).endVertex();
				b.pos(cx + gw, y + 7.99f, 0).tex(u1, v1).color(r, g, bl, a).endVertex();
				b.pos(cx + gw, y, 0).tex(u1, v).color(r, g, bl, a).endVertex();
			}
			cx += width;
		}
	}
}
