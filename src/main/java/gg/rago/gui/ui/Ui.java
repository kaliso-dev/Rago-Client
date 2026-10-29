package gg.rago.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode drawing for Rago's screens, in "design units": a layout made for a 1280x720
 * window that scales with the real window, drawn in real pixels so edges and text stay sharp.
 * Screens call {@link #begin} at the start of drawing and {@link #end} after, then lay out with
 * {@link #width()} / {@link #height()} and register click targets with {@link #click}.
 */
public final class Ui {
	public static final TtfFont REGULAR = new TtfFont(new ResourceLocation("rago", "fonts/inter-400.ttf"));
	public static final TtfFont MEDIUM = new TtfFont(new ResourceLocation("rago", "fonts/inter-500.ttf"));
	public static final TtfFont SEMIBOLD = new TtfFont(new ResourceLocation("rago", "fonts/inter-600.ttf"));
	public static final TtfFont BOLD = new TtfFont(new ResourceLocation("rago", "fonts/inter-700.ttf"));
	public static final TtfFont MONO = new TtfFont(new ResourceLocation("rago", "fonts/jetbrains-mono-500.ttf"));

	private static final ResourceLocation LOGO = new ResourceLocation("rago", "textures/logo.png");
	private static final int CORNER_TEX = 64;
	private static DynamicTexture corner;
	private static DynamicTexture glow;
	private static DynamicTexture ring;

	/** Real pixels per design unit. */
	private static float s = 1f;
	private static int guiScale = 1;
	private static float mouseX;
	private static float mouseY;
	private static final List<Target> targets = new ArrayList<>();

	private Ui() {
	}

	/** A clickable area registered while drawing, checked on the next click. */
	private static final class Target {
		final float x;
		final float y;
		final float w;
		final float h;
		final Runnable action;

		Target(float x, float y, float w, float h, Runnable action) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.action = action;
		}
	}

	// ---- frame ----------------------------------------------------------------------------------

	public static void begin() {
		Minecraft mc = Minecraft.getMinecraft();
		guiScale = new ScaledResolution(mc).getScaleFactor();
		s = Math.max(0.75f, Math.min(2.5f, Math.min(mc.displayWidth / 1280f, mc.displayHeight / 720f)));
		mouseX = Mouse.getX() / s;
		mouseY = (mc.displayHeight - Mouse.getY() - 1) / s;
		targets.clear();
		GlStateManager.pushMatrix();
		GlStateManager.scale(1f / guiScale, 1f / guiScale, 1f);
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.disableCull();
		GlStateManager.disableAlpha();
	}

	public static void end() {
		GlStateManager.enableAlpha();
		GlStateManager.enableDepth();
		GlStateManager.popMatrix();
		GlStateManager.color(1, 1, 1, 1);
	}

	/** Visible width in design units. */
	public static float width() {
		return Minecraft.getMinecraft().displayWidth / s;
	}

	public static float height() {
		return Minecraft.getMinecraft().displayHeight / s;
	}

	public static float scale() {
		return s;
	}

	public static float mouseX() {
		return mouseX;
	}

	public static float mouseY() {
		return mouseY;
	}

	public static boolean hover(float x, float y, float w, float h) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/** Registers a click target; later registrations sit on top of earlier ones. */
	public static void click(float x, float y, float w, float h, Runnable action) {
		targets.add(new Target(x, y, w, h, action));
	}

	/** Runs the topmost target under the mouse; returns whether one was hit. */
	public static boolean dispatchClick() {
		for (int i = targets.size() - 1; i >= 0; i--) {
			Target t = targets.get(i);
			if (mouseX >= t.x && mouseY >= t.y && mouseX < t.x + t.w && mouseY < t.y + t.h) {
				t.action.run();
				return true;
			}
		}
		return false;
	}

	/** Updates the mouse position outside a frame (for clicks and drags). */
	public static void updateMouse() {
		Minecraft mc = Minecraft.getMinecraft();
		mouseX = Mouse.getX() / s;
		mouseY = (mc.displayHeight - Mouse.getY() - 1) / s;
	}

	// ---- shapes ---------------------------------------------------------------------------------

	private static float px(float v) {
		return v * s;
	}

	/** Rounds to whole real pixels so edges do not blur. */
	private static float snap(float v) {
		return Math.round(v * s);
	}

	public static void rect(float x, float y, float w, float h, int argb) {
		float x0 = snap(x);
		float y0 = snap(y);
		float x1 = snap(x + w);
		float y1 = snap(y + h);
		GlStateManager.disableTexture2D();
		blend();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer r = t.getWorldRenderer();
		r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		quad(r, x0, y0, x1, y1, argb, argb);
		t.draw();
		GlStateManager.enableTexture2D();
	}

	/** Vertical gradient from {@code top} to {@code bottom}. */
	public static void gradient(float x, float y, float w, float h, int top, int bottom) {
		GlStateManager.disableTexture2D();
		blend();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer r = t.getWorldRenderer();
		r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		quad(r, snap(x), snap(y), snap(x + w), snap(y + h), top, bottom);
		t.draw();
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableTexture2D();
	}

	private static void quad(WorldRenderer r, float x0, float y0, float x1, float y1, int top, int bottom) {
		color(r.pos(x0, y1, 0), bottom).endVertex();
		color(r.pos(x1, y1, 0), bottom).endVertex();
		color(r.pos(x1, y0, 0), top).endVertex();
		color(r.pos(x0, y0, 0), top).endVertex();
	}

	private static WorldRenderer color(WorldRenderer r, int argb) {
		return r.color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
	}

	private static void blend() {
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	/** Filled rectangle with anti-aliased round corners of radius {@code radius}. */
	public static void round(float x, float y, float w, float h, float radius, int argb) {
		float x0 = snap(x);
		float y0 = snap(y);
		float x1 = snap(x + w);
		float y1 = snap(y + h);
		// Whole pixels, so the corner quads meet the straight parts without a seam
		float r = (float) Math.floor(Math.min(Math.round(px(radius)), Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
		if (r < 1) {
			rect(x, y, w, h, argb);
			return;
		}
		GlStateManager.disableTexture2D();
		blend();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer b = t.getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		quad(b, x0 + r, y0, x1 - r, y1, argb, argb);
		quad(b, x0, y0 + r, x0 + r, y1 - r, argb, argb);
		quad(b, x1 - r, y0 + r, x1, y1 - r, argb, argb);
		t.draw();
		GlStateManager.enableTexture2D();
		GlStateManager.bindTexture(cornerTexture().getGlTextureId());
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		corner(b, x0, y0, r, 0, 0, argb);       // top-left: texture's quarter circle faces up-left
		corner(b, x1 - r, y0, r, 1, 0, argb);
		corner(b, x0, y1 - r, r, 0, 1, argb);
		corner(b, x1 - r, y1 - r, r, 1, 1, argb);
		t.draw();
	}

	/** One corner quad; flipX/flipY mirror the top-left quarter circle into the other corners. */
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

	/** Rounded rectangle with a one-pixel border. */
	public static void roundBorder(float x, float y, float w, float h, float radius, int fill, int border) {
		round(x, y, w, h, radius, border);
		float one = 1f / s;
		round(x + one, y + one, w - 2 * one, h - 2 * one, Math.max(0, radius - one), fill);
	}

	public static void circle(float cx, float cy, float r, int argb) {
		round(cx - r, cy - r, r * 2, r * 2, r, argb);
	}

	/**
	 * Area chart of {@code values} scaled between their min and max: a line of {@code color}
	 * with a fill that fades out towards the bottom.
	 */
	public static void sparkline(float x, float y, float w, float h, float[] values, int count, int color) {
		if (count < 2) {
			return;
		}
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			min = Math.min(min, values[i]);
			max = Math.max(max, values[i]);
		}
		float range = Math.max(1e-3f, max - min);
		// keep the line in the upper part, leaving room for the fade
		float top = y + h * 0.1f;
		float span = h * 0.55f;
		float[] px = new float[count];
		float[] py = new float[count];
		for (int i = 0; i < count; i++) {
			px[i] = px(x + w * i / (count - 1));
			py[i] = px(top + span * (1 - (values[i] - min) / range));
		}
		GlStateManager.disableTexture2D();
		blend();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer b = t.getWorldRenderer();
		b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
		float bottom = px(y + h);
		for (int i = 0; i < count; i++) {
			color(b.pos(px[i], py[i], 0), alpha(color, 0.28f)).endVertex();
			color(b.pos(px[i], bottom, 0), alpha(color, 0f)).endVertex();
		}
		t.draw();
		GL11.glEnable(GL11.GL_LINE_SMOOTH);
		GL11.glLineWidth(Math.max(1f, 2f * s));
		b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
		for (int i = 0; i < count; i++) {
			color(b.pos(px[i], py[i], 0), color).endVertex();
		}
		t.draw();
		GL11.glLineWidth(1f);
		GL11.glDisable(GL11.GL_LINE_SMOOTH);
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableTexture2D();
		circle((px[count - 1]) / s, py[count - 1] / s, 3, color);
	}

	/** Soft radial glow, strongest in the centre. */
	public static void glow(float cx, float cy, float radius, int argb) {
		texturedQuad(glowTexture().getGlTextureId(), cx - radius, cy - radius, radius * 2, radius * 2, argb);
	}

	/** A thin anti-aliased circle outline. */
	public static void ring(float cx, float cy, float radius, int argb) {
		texturedQuad(ringTexture().getGlTextureId(), cx - radius, cy - radius, radius * 2, radius * 2, argb);
	}

	public static void logo(float x, float y, float size, int argb) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
		linear();
		texturedQuad(-1, x, y, size, size, argb);
	}

	/** A Lucide icon from {@code assets/rago/textures/icons}, tinted. */
	public static void icon(String name, float x, float y, float size, int argb) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation("rago", "textures/icons/" + name + ".png"));
		linear();
		texturedQuad(-1, x, y, size, size, argb);
	}

	/** Draws part of the currently bound or a given texture; uv in 0..1. */
	public static void texture(ResourceLocation tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
		GlStateManager.enableTexture2D();
		blend();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer b = t.getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		float x0 = snap(x);
		float y0 = snap(y);
		float x1 = snap(x + w);
		float y1 = snap(y + h);
		color(b.pos(x0, y1, 0).tex(u0, v1), argb).endVertex();
		color(b.pos(x1, y1, 0).tex(u1, v1), argb).endVertex();
		color(b.pos(x1, y0, 0).tex(u1, v0), argb).endVertex();
		color(b.pos(x0, y0, 0).tex(u0, v0), argb).endVertex();
		t.draw();
	}

	private static void linear() {
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
	}

	/** Draws a whole texture; {@code glId} -1 keeps the texture already bound. */
	private static void texturedQuad(int glId, float x, float y, float w, float h, int argb) {
		GlStateManager.enableTexture2D();
		if (glId >= 0) {
			GlStateManager.bindTexture(glId);
		}
		blend();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer b = t.getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		float x0 = px(x);
		float y0 = px(y);
		float x1 = px(x + w);
		float y1 = px(y + h);
		color(b.pos(x0, y1, 0).tex(0, 1), argb).endVertex();
		color(b.pos(x1, y1, 0).tex(1, 1), argb).endVertex();
		color(b.pos(x1, y0, 0).tex(1, 0), argb).endVertex();
		color(b.pos(x0, y0, 0).tex(0, 0), argb).endVertex();
		t.draw();
	}

	// ---- text -----------------------------------------------------------------------------------

	/** Draws text with its line box top at y; size in design units. Returns the width drawn. */
	public static float text(TtfFont font, String s, float x, float y, float size, int argb) {
		font.draw(s, px(x), px(y), px(size), argb);
		return font.width(s, px(size)) / Ui.s;
	}

	public static float textWidth(TtfFont font, String s, float size) {
		return font.width(s, px(size)) / Ui.s;
	}

	public static void textCentered(TtfFont font, String s, float cx, float y, float size, int argb) {
		text(font, s, cx - textWidth(font, s, size) / 2, y, size, argb);
	}

	public static void textRight(TtfFont font, String s, float right, float y, float size, int argb) {
		text(font, s, right - textWidth(font, s, size), y, size, argb);
	}

	/** Text with extra space between letters (for small uppercase labels). */
	public static float textSpaced(TtfFont font, String s, float x, float y, float size, float spacing, int argb) {
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			String c = String.valueOf(s.charAt(i));
			cx += text(font, c, cx, y, size, argb) + spacing;
		}
		return cx - x;
	}

	public static float textSpacedWidth(TtfFont font, String s, float size, float spacing) {
		return textWidth(font, s, size) + spacing * s.length();
	}

	/** Splits text into lines no wider than {@code maxWidth}, at spaces. */
	public static List<String> wrap(TtfFont font, String text, float size, float maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && textWidth(font, candidate, size) > maxWidth) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		return lines;
	}

	// ---- clipping -------------------------------------------------------------------------------

	public static void scissor(float x, float y, float w, float h) {
		Minecraft mc = Minecraft.getMinecraft();
		int x0 = Math.round(px(x));
		int y0 = Math.round(px(y));
		int w0 = Math.max(0, Math.round(px(w)));
		int h0 = Math.max(0, Math.round(px(h)));
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(x0, mc.displayHeight - y0 - h0, w0, h0);
	}

	public static void endScissor() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}

	// ---- generated textures ---------------------------------------------------------------------

	private static DynamicTexture cornerTexture() {
		if (corner == null) {
			// Quarter of a disc with an anti-aliased edge: the disc's centre is the texture's bottom-right
			corner = generated(CORNER_TEX, (x, y) -> {
				double dx = CORNER_TEX - x - 0.5;
				double dy = CORNER_TEX - y - 0.5;
				double d = Math.sqrt(dx * dx + dy * dy);
				return clamp(CORNER_TEX - d + 0.5);
			});
		}
		return corner;
	}

	private static DynamicTexture glowTexture() {
		if (glow == null) {
			int n = 128;
			glow = generated(n, (x, y) -> {
				double dx = (x + 0.5) / n * 2 - 1;
				double dy = (y + 0.5) / n * 2 - 1;
				double d = Math.min(1, Math.sqrt(dx * dx + dy * dy));
				double f = 1 - d;
				return f * f;
			});
		}
		return glow;
	}

	private static DynamicTexture ringTexture() {
		if (ring == null) {
			int n = 512;
			double width = 1.6;
			ring = generated(n, (x, y) -> {
				double dx = x + 0.5 - n / 2.0;
				double dy = y + 0.5 - n / 2.0;
				double d = Math.abs(Math.sqrt(dx * dx + dy * dy) - (n / 2.0 - 2));
				return clamp(width - d);
			});
		}
		return ring;
	}

	private interface AlphaFn {
		double alpha(int x, int y);
	}

	private static double clamp(double v) {
		return Math.max(0, Math.min(1, v));
	}

	private static DynamicTexture generated(int n, AlphaFn fn) {
		BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int a = (int) Math.round(fn.alpha(x, y) * 255);
				img.setRGB(x, y, a << 24 | 0xFFFFFF);
			}
		}
		DynamicTexture t = new DynamicTexture(img);
		GlStateManager.bindTexture(t.getGlTextureId());
		linear();
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		return t;
	}

	// ---- colours --------------------------------------------------------------------------------

	/** Replaces the alpha of an ARGB colour (alpha 0..1). */
	public static int alpha(int argb, float alpha) {
		return Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | (argb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		int aa = a >>> 24;
		int ar = a >> 16 & 0xFF;
		int ag = a >> 8 & 0xFF;
		int ab = a & 0xFF;
		int ba = b >>> 24;
		int br = b >> 16 & 0xFF;
		int bg = b >> 8 & 0xFF;
		int bb = b & 0xFF;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
			| Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}
}
