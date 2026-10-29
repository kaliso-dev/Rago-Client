package gg.rago.render;

import gg.rago.gui.ui.TtfFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * Text in GUI units with a choice of font: Minecraft's own, or a TrueType font drawn at the real
 * pixel size it ends up on screen so it stays sharp at any GUI or HUD scale. TrueType text is
 * sized and placed so its capitals and baseline line up with the Minecraft font's, so elements
 * keep the same layout whichever font is picked.
 */
public final class HudText {
	public static final String MINECRAFT = "minecraft";
	public static final String POPPINS = "poppins";
	public static final String INTER = "inter";
	public static final String[] FONTS = {MINECRAFT, POPPINS, INTER};

	public static final TtfFont POPPINS_FONT = new TtfFont(new ResourceLocation("rago", "fonts/poppins-500.ttf"));
	public static final TtfFont POPPINS_BOLD = new TtfFont(new ResourceLocation("rago", "fonts/poppins-600.ttf"));
	private static final TtfFont INTER_FONT = new TtfFont(new ResourceLocation("rago", "fonts/inter-500.ttf"));

	/** TrueType size in GUI units: capitals about 7 units tall, as in the Minecraft font. */
	private static final float POPPINS_EM = 9.6f;
	private static final float INTER_EM = 9.4f;
	/** Minecraft font baseline below the top of a line. */
	private static final float BASELINE = 7f;
	private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

	private HudText() {
	}

	/** The TrueType font for a font id, or null for the Minecraft font. */
	public static TtfFont ttf(String font) {
		return POPPINS.equals(font) ? POPPINS_FONT : INTER.equals(font) ? INTER_FONT : null;
	}

	/** TrueType size in GUI units for a font id. */
	public static float em(String font) {
		return POPPINS.equals(font) ? POPPINS_EM : INTER_EM;
	}

	private static FontRenderer vanilla() {
		return Minecraft.getMinecraft().fontRendererObj;
	}

	/** Width in GUI units, ignoring § codes. */
	public static int width(String font, String s) {
		TtfFont f = ttf(font);
		if (f == null) {
			return vanilla().getStringWidth(s);
		}
		// measured at a large size, so the result does not depend on the screen scale
		return (int) Math.ceil(f.measure(s) / TtfFont.METRICS_PX * em(font));
	}

	/** Real screen pixels per unit of the current matrix, for HUD and menu drawing. */
	public static float pixelScale() {
		MATRIX.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
		float sx = (float) Math.sqrt(MATRIX.get(0) * MATRIX.get(0) + MATRIX.get(1) * MATRIX.get(1));
		return Math.max(0.25f, sx * guiScale());
	}

	private static int scaleW = -1;
	private static int scaleH;
	private static int scaleSetting;
	private static boolean scaleUnicode;
	private static int scale = 1;

	/** The GUI scale factor, as ScaledResolution works it out, without making one for every string. */
	private static int guiScale() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.displayWidth != scaleW || mc.displayHeight != scaleH || mc.gameSettings.guiScale != scaleSetting || mc.isUnicode() != scaleUnicode) {
			scaleW = mc.displayWidth;
			scaleH = mc.displayHeight;
			scaleSetting = mc.gameSettings.guiScale;
			scaleUnicode = mc.isUnicode();
			scale = new ScaledResolution(mc).getScaleFactor();
		}
		return scale;
	}

	/** Draws with its line box top at (x, y), on the HUD or in a menu. */
	public static void draw(String font, String s, float x, float y, int argb, boolean shadow) {
		TtfFont f = ttf(font);
		if (f == null) {
			vanilla().drawString(s, x, y, argb, shadow);
			return;
		}
		drawTtf(f, em(font), s, x, y, argb, shadow, pixelScale());
	}

	/**
	 * Draws at a fixed sharpness of {@code pixelsPerUnit}; for world text (name tags), where the
	 * size on screen changes with distance.
	 */
	public static void draw(String font, String s, float x, float y, int argb, boolean shadow, float pixelsPerUnit) {
		TtfFont f = ttf(font);
		if (f == null) {
			vanilla().drawString(s, x, y, argb, shadow);
			return;
		}
		drawTtf(f, em(font), s, x, y, argb, shadow, pixelsPerUnit);
	}

	private static void drawTtf(TtfFont f, float em, String s, float x, float y, int argb, boolean shadow, float k) {
		// quantise so a slowly changing scale does not create a new glyph atlas every frame
		float px = Math.max(6, Math.round(em * k * 2) / 2f);
		float kk = px / em;
		GlStateManager.pushMatrix();
		GlStateManager.scale(1 / kk, 1 / kk, 1);
		float top = (y + BASELINE) * kk - f.ascent(px);
		f.drawFormatted(s, x * kk, top, px, argb, shadow, Math.max(1, kk * 0.75f));
		GlStateManager.popMatrix();
		GlStateManager.color(1, 1, 1, 1);
	}
}
