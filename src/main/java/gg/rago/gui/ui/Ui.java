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
	private static final ResourceLocation LOGO_GLOW = new ResourceLocation("rago", "textures/logo_glow.png");
	/** How much of a panel's own fill stays when glass is on; the rest is the blurred background. */
	private static final float GLASS_FILL = 0.5f;
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
	/** Multiplies the alpha of everything drawn, for fades. */
	private static float opacity = 1f;
	private static float tScale = 1f;
	private static float tCx;
	private static float tCy;
	private static float tDx;
	private static float tDy;
	private static boolean transformed;
	private static boolean clipping;
	private static float clipX;
	private static float clipY;
	private static float clipW;
	private static float clipH;

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
		clipping = false;
		Glass.frameStart();
		GlStateManager.pushMatrix();
		GlStateManager.scale(1f / guiScale, 1f / guiScale, 1f);
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.disableCull();
		GlStateManager.disableAlpha();
	}

	/** Sets the alpha multiplier for everything drawn until it is set again (0..1). */
	public static void opacity(float value) {
		opacity = Math.max(0, Math.min(1, value));
	}

	public static float opacity() {
		return opacity;
	}

	/** Applies a colour's alpha multiplied by the current opacity. */
	public static int fade(int argb) {
		return opacity >= 1f ? argb : Math.round((argb >>> 24) * opacity) << 24 | (argb & 0xFFFFFF);
	}

	/**
	 * Scales everything drawn around (cx, cy) and moves it by (dx, dy), for open and close
	 * animations. Clipping follows; click targets do not, so use it only while animating.
	 */
	public static void pushTransform(float cx, float cy, float scale, float dx, float dy) {
		popTransform();
		tScale = scale;
		tCx = cx;
		tCy = cy;
		tDx = dx;
		tDy = dy;
		transformed = true;
		GlStateManager.pushMatrix();
		GlStateManager.translate(px(cx + dx), px(cy + dy), 0);
		GlStateManager.scale(scale, scale, 1);
		GlStateManager.translate(-px(cx), -px(cy), 0);
	}

	public static void popTransform() {
		if (transformed) {
			GlStateManager.popMatrix();
			transformed = false;
			tScale = 1f;
			tDx = 0;
			tDy = 0;
		}
	}

	public static void end() {
		popTransform();
		opacity = 1f;
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
		if (clipping && !(mouseX >= clipX && mouseY >= clipY && mouseX < clipX + clipW && mouseY < clipY + clipH)) {
			return false;
		}
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/**
	 * Registers a click target; later registrations sit on top of earlier ones. While a
	 * {@link #scissor} is active the target is cut to the visible area.
	 */
	public static void click(float x, float y, float w, float h, Runnable action) {
		if (clipping) {
			float x0 = Math.max(x, clipX);
			float y0 = Math.max(y, clipY);
			float x1 = Math.min(x + w, clipX + clipW);
			float y1 = Math.min(y + h, clipY + clipH);
			if (x1 <= x0 || y1 <= y0) {
				return;
			}
			x = x0;
			y = y0;
			w = x1 - x0;
			h = y1 - y0;
		}
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

	/** Horizontal gradient from {@code left} to {@code right}. */
	public static void hgradient(float x, float y, float w, float h, int left, int right) {
		GlStateManager.disableTexture2D();
		blend();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer r = t.getWorldRenderer();
		r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		float x0 = snap(x);
		float y0 = snap(y);
		float x1 = snap(x + w);
		float y1 = snap(y + h);
		color(r.pos(x0, y1, 0), left).endVertex();
		color(r.pos(x1, y1, 0), right).endVertex();
		color(r.pos(x1, y0, 0), right).endVertex();
		color(r.pos(x0, y0, 0), left).endVertex();
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
		return r.color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, Math.round((argb >>> 24) * opacity));
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

	// ---- glass ----------------------------------------------------------------------------------

	/** Turns the frosted glass effect on or off for every screen. */
	public static void setGlass(boolean on) {
		Glass.setEnabled(on);
	}

	/**
	 * Takes the blurred copy of the screen that glass panels show through. Call once per frame,
	 * after the background and before the first panel.
	 */
	public static void captureGlass() {
		Glass.capture();
	}

	/**
	 * {@link #round} with frosted glass behind it: the blurred background shows through a
	 * lighter version of {@code fill}. Without glass (switched off, or no framebuffer support)
	 * it is exactly {@link #round}.
	 */
	public static void glass(float x, float y, float w, float h, float radius, int fill) {
		if (!Glass.ready()) {
			round(x, y, w, h, radius, fill);
			return;
		}
		float x0 = snap(x);
		float y0 = snap(y);
		float x1 = snap(x + w);
		float y1 = snap(y + h);
		float r = (float) Math.floor(Math.min(Math.round(px(radius)), Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
		Glass.draw(x0, y0, x1, y1, Math.max(0, r), screenPx(x, true), screenPx(y, false),
			screenPx(x + w, true), screenPx(y + h, false), cornerTexture().getGlTextureId(), opacity);
		round(x, y, w, h, radius, scaleAlpha(fill, GLASS_FILL));
	}

	/** {@link #glass} with a one-pixel border, the glass version of {@link #roundBorder}. */
	public static void glassBorder(float x, float y, float w, float h, float radius, int fill, int border) {
		if (!Glass.ready()) {
			roundBorder(x, y, w, h, radius, fill, border);
			return;
		}
		glass(x, y, w, h, radius, fill);
		outline(x, y, w, h, radius, border);
	}

	/** A one-pixel rounded outline with nothing inside. */
	public static void outline(float x, float y, float w, float h, float radius, int argb) {
		float one = 1f / s;
		float r = Math.max(0, radius);
		rect(x + r, y, w - 2 * r, one, argb);
		rect(x + r, y + h - one, w - 2 * r, one, argb);
		rect(x, y + r, one, h - 2 * r, argb);
		rect(x + w - one, y + r, one, h - 2 * r, argb);
		if (r > 0) {
			arc(x + r, y + r, r - one / 2, 180, 270, argb);
			arc(x + w - r, y + r, r - one / 2, 270, 360, argb);
			arc(x + w - r, y + h - r, r - one / 2, 0, 90, argb);
			arc(x + r, y + h - r, r - one / 2, 90, 180, argb);
		}
	}

	/** Where a design-unit coordinate ends up on screen in real pixels, after {@link #pushTransform}. */
	private static float screenPx(float v, boolean horizontal) {
		if (transformed) {
			v = horizontal ? tCx + (v - tCx) * tScale + tDx : tCy + (v - tCy) * tScale + tDy;
		}
		return snap(v);
	}

	private static int scaleAlpha(int argb, float f) {
		return Math.round((argb >>> 24) * f) << 24 | (argb & 0xFFFFFF);
	}

	/** A one-pixel anti-aliased arc of radius {@code r} from {@code a0} to {@code a1} degrees (0 = right, clockwise). */
	private static void arc(float cx, float cy, float r, float a0, float a1, int argb) {
		annulusArc(px(cx), px(cy), px(r), 1f, a0, a1, argb);
	}

	/**
	 * A ring of radius {@code radius} and line width {@code thickness} (design units) with soft
	 * anti-aliased edges. Unlike {@link #ring}, the line keeps its width at any size.
	 */
	public static void annulus(float cx, float cy, float radius, float thickness, int argb) {
		annulusArc(px(cx), px(cy), px(radius), Math.max(1f, px(thickness)), 0, 360, argb);
	}

	private static void annulusArc(float cx, float cy, float r, float t, float a0, float a1, int argb) {
		int segments = Math.max(12, Math.min(160, (int) (r * (a1 - a0) / 360f)));
		float feather = 0.75f;
		float[] radii = {r - t / 2 - feather, r - t / 2 + feather * 0.25f, r + t / 2 - feather * 0.25f, r + t / 2 + feather};
		float[] alphas = {0, 1, 1, 0};
		GlStateManager.disableTexture2D();
		blend();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer b = tess.getWorldRenderer();
		for (int band = 0; band < 3; band++) {
			b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
			for (int i = 0; i <= segments; i++) {
				double a = Math.toRadians(a0 + (a1 - a0) * i / segments);
				float cos = (float) Math.cos(a);
				float sin = (float) Math.sin(a);
				for (int k = band; k <= band + 1; k++) {
					float rr = Math.max(0, radii[k]);
					color(b.pos(cx + cos * rr, cy + sin * rr, 0), alpha(argb, (argb >>> 24) / 255f * alphas[k])).endVertex();
				}
			}
			tess.draw();
		}
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableTexture2D();
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

	/**
	 * The menu background covering the whole screen, darkened by {@code dim} (0..1): the looping
	 * menu video, or the generated block landscape when the video cannot play.
	 */
	public static void backdrop(float dim) {
		float w = width();
		float h = height();
		int video = VideoBackdrop.textureId();
		float aspect = video >= 0 ? VideoBackdrop.aspect() : Backdrop.aspect();
		float bw = Math.max(w, h * aspect);
		float bh = bw / aspect;
		texturedQuad(video >= 0 ? video : Backdrop.textureId(), (w - bw) / 2, (h - bh) / 2, bw, bh, 0xFFFFFFFF);
		rect(0, 0, w, h, alpha(0xFF050406, dim));
	}

	/** Whether the menu video moves; when off it shows a still frame. */
	public static void setMenuVideo(boolean playing) {
		VideoBackdrop.setPlaying(playing);
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

	/** The soft glow that goes behind {@link #logo}, drawn over twice the logo's size. */
	public static void logoGlow(float x, float y, float size, int argb) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO_GLOW);
		linear();
		texturedQuad(-1, x - size / 2, y - size / 2, size * 2, size * 2, argb);
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
		font.draw(s, px(x), px(y), px(size), fade(argb));
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
		clipping = true;
		clipX = x;
		clipY = y;
		clipW = w;
		clipH = h;
		if (transformed) {
			x = tCx + (x - tCx) * tScale + tDx;
			y = tCy + (y - tCy) * tScale + tDy;
			w *= tScale;
			h *= tScale;
		}
		Minecraft mc = Minecraft.getMinecraft();
		int x0 = Math.round(px(x));
		int y0 = Math.round(px(y));
		int w0 = Math.max(0, Math.round(px(w)));
		int h0 = Math.max(0, Math.round(px(h)));
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(x0, mc.displayHeight - y0 - h0, w0, h0);
	}

	public static void endScissor() {
		clipping = false;
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}

	// ---- generated textures ---------------------------------------------------------------------

	/** GL id of the anti-aliased quarter-circle texture used for round corners. */
	public static int cornerTextureId() {
		return cornerTexture().getGlTextureId();
	}

	static DynamicTexture cornerTexture() {
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
