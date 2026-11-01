package gg.rago.optimize;

import gg.rago.core.RagoClient;
import gg.rago.debug.FrameProfile;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/**
 * Parts of Minecraft's own HUD drawn with far fewer draw calls, and the same pixels.
 *
 * <p>Status icons: GuiIngameForge draws every heart, armour point, food icon and air bubble with a
 * draw call of its own (up to 50 a frame in survival, more with absorption). Here they are queued
 * as the game asks for them and drawn together just before the method turns blending off: the same
 * quads in the same order, with the same texture and the same state, so the same image.
 *
 * <p>Sidebar: GuiIngame.renderScoreboard draws a dark rectangle under each line and the line's text
 * on it, rectangle, text, rectangle, text (17 rectangles for a full Hypixel sidebar). Here the
 * rectangles all go in one draw call first, then the text in the order the game drew it, with the
 * state each would have had. No rectangle covers another line's text, so nothing changes on screen.
 *
 * <p>Both are reached from the game's code through {@code Hooks}; with the setting off, or anything
 * missing, each call goes straight to the game's own method.
 */
public final class GuiBatch {
	private static BooleanSupplier on = () -> false;

	private GuiBatch() {
	}

	/** Set once by the settings. */
	public static void setSwitch(BooleanSupplier setting) {
		on = setting;
	}

	private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

	// ---- status icons ----------------------------------------------------------------------------

	private static final WorldRenderer ICONS = new WorldRenderer(4096);
	private static boolean iconsOpen;
	private static Field zLevel;
	private static boolean zLevelMissing;

	/** Developer timing ({@code -Drago.bench.frame=true}): from the first icon or sidebar call to its flush. */
	private static long iconsStart;
	private static long sidebarStart;

	/** Gui.drawTexturedModalRect from one of GuiIngameForge's status bars. */
	public static void icon(Gui gui, int x, int y, int u, int v, int w, int h) {
		if (FrameProfile.ON && iconsStart == 0) {
			iconsStart = System.nanoTime();
		}
		float z = iconsOpen || on.getAsBoolean() ? zLevel(gui) : Float.NaN;
		if (Float.isNaN(z)) {
			gui.drawTexturedModalRect(x, y, u, v, w, h);
			return;
		}
		if (!iconsOpen) {
			ICONS.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
			iconsOpen = true;
		}
		// exactly the vertices Gui.drawTexturedModalRect makes, in its order
		float f = 0.00390625F;
		ICONS.pos(x, y + h, z).tex((float) u * f, (float) (v + h) * f).endVertex();
		ICONS.pos(x + w, y + h, z).tex((float) (u + w) * f, (float) (v + h) * f).endVertex();
		ICONS.pos(x + w, y, z).tex((float) (u + w) * f, (float) v * f).endVertex();
		ICONS.pos(x, y, z).tex((float) u * f, (float) v * f).endVertex();
	}

	/** Draws the queued icons with the state the game has now (before it turns blending off). */
	public static void flushIcons() {
		if (iconsOpen) {
			iconsOpen = false;
			ICONS.finishDrawing();
			UPLOADER.draw(ICONS);
		}
		if (FrameProfile.ON && iconsStart != 0) {
			FrameProfile.element("vanilla_icons", System.nanoTime() - iconsStart);
			iconsStart = 0;
		}
	}

	private static float zLevel(Gui gui) {
		if (zLevelMissing) {
			return Float.NaN;
		}
		try {
			if (zLevel == null) {
				zLevel = ReflectionHelper.findField(Gui.class, "field_73735_i", "zLevel");
			}
			return zLevel.getFloat(gui);
		} catch (RuntimeException | IllegalAccessException e) {
			zLevelMissing = true;
			RagoClient.LOG.warn("Minecraft's HUD icons are drawn one by one: {}", e.toString());
			return Float.NaN;
		}
	}

	// ---- sidebar ---------------------------------------------------------------------------------

	private static final WorldRenderer RECTS = new WorldRenderer(2048);
	private static boolean sidebarOpen;
	private static int rects;
	private static FontRenderer[] fonts = new FontRenderer[48];
	private static String[] texts = new String[48];
	private static int[] textX = new int[48];
	private static int[] textY = new int[48];
	private static int[] textColor = new int[48];
	private static int textCount;

	/** Gui.drawRect from renderScoreboard. */
	public static void sidebarRect(int left, int top, int right, int bottom, int color) {
		if (FrameProfile.ON && sidebarStart == 0) {
			sidebarStart = System.nanoTime();
		}
		if (!sidebarOpen && !(sidebarOpen = on.getAsBoolean())) {
			Gui.drawRect(left, top, right, bottom, color);
			return;
		}
		// Gui.drawRect's own ordering of the corners
		if (left < right) {
			int i = left;
			left = right;
			right = i;
		}
		if (top < bottom) {
			int j = top;
			top = bottom;
			bottom = j;
		}
		if (rects++ == 0) {
			RECTS.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		}
		int a = color >>> 24;
		int r = color >> 16 & 255;
		int g = color >> 8 & 255;
		int b = color & 255;
		RECTS.pos(left, bottom, 0.0D).color(r, g, b, a).endVertex();
		RECTS.pos(right, bottom, 0.0D).color(r, g, b, a).endVertex();
		RECTS.pos(right, top, 0.0D).color(r, g, b, a).endVertex();
		RECTS.pos(left, top, 0.0D).color(r, g, b, a).endVertex();
	}

	/** FontRenderer.drawString(String, int, int, int) from renderScoreboard; its result is not used there. */
	public static int sidebarText(FontRenderer font, String text, int x, int y, int color) {
		if (FrameProfile.ON && sidebarStart == 0) {
			sidebarStart = System.nanoTime();
		}
		if (!sidebarOpen && !(sidebarOpen = on.getAsBoolean())) {
			return font.drawString(text, x, y, color);
		}
		if (textCount == texts.length) {
			int n = textCount * 2;
			fonts = java.util.Arrays.copyOf(fonts, n);
			texts = java.util.Arrays.copyOf(texts, n);
			textX = java.util.Arrays.copyOf(textX, n);
			textY = java.util.Arrays.copyOf(textY, n);
			textColor = java.util.Arrays.copyOf(textColor, n);
		}
		fonts[textCount] = font;
		texts[textCount] = text;
		textX[textCount] = x;
		textY[textCount] = y;
		textColor[textCount] = color;
		textCount++;
		return x;
	}

	/** End of renderScoreboard: every rectangle in one draw call, as Gui.drawRect draws one, then the text. */
	public static void flushSidebar() {
		if (sidebarOpen) {
			sidebarOpen = false;
			drawSidebar();
		}
		if (FrameProfile.ON && sidebarStart != 0) {
			FrameProfile.element("vanilla_sidebar", System.nanoTime() - sidebarStart);
			sidebarStart = 0;
		}
	}

	private static void drawSidebar() {
		if (rects > 0) {
			rects = 0;
			GlStateManager.enableBlend();
			GlStateManager.disableTexture2D();
			GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
			RECTS.finishDrawing();
			UPLOADER.draw(RECTS);
			GlStateManager.enableTexture2D();
			GlStateManager.disableBlend();
		}
		int n = textCount;
		textCount = 0;
		for (int i = 0; i < n; i++) {
			fonts[i].drawString(texts[i], textX[i], textY[i], textColor[i]);
			fonts[i] = null;
			texts[i] = null;
		}
	}
}
