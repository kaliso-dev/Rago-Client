package gg.rago.gui;

import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;

/** Colours (ARGB) and small drawing helpers shared by Rago's screens. */
final class Theme {
	static final int SHADE = 0x88000000;
	static final int PANEL = 0xF0121019;
	static final int SIDEBAR = 0xFF17141F;
	static final int ROW = 0xFF1C1926;
	static final int ROW_HOVER = 0xFF282435;
	static final int CARD = 0xFF1E1A29;
	static final int CARD_HOVER = 0xFF29243A;
	static final int ACCENT = 0xFF8B6CF0;
	static final int ACCENT_DARK = 0xFF5A3FA6;
	static final int TEXT = 0xFFF0EEF6;
	static final int MUTED = 0xFF9C98AC;
	static final int ON = 0xFF3FBF7F;
	static final int OFF = 0xFF4A4658;
	static final int DANGER = 0xFFE05A55;
	static final int OUTLINE = 0x80FFFFFF;
	static final int OUTLINE_ACTIVE = 0xFF8B6CF0;
	static final int GUIDE = 0x408B6CF0;
	static final int BG_TOP = 0xFF1B1235;
	static final int BG_BOTTOM = 0xFF07060C;

	private Theme() {
	}

	static void outline(int x, int y, int w, int h, int color) {
		GuiElement.fill(x, y, x + w, y + 1, color);
		GuiElement.fill(x, y + h - 1, x + w, y + h, color);
		GuiElement.fill(x, y + 1, x + 1, y + h - 1, color);
		GuiElement.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** A rectangle with its four corner pixels cut, which reads as slightly rounded at GUI scale. */
	static void rounded(int x, int y, int w, int h, int color) {
		GuiElement.fill(x + 1, y, x + w - 1, y + h, color);
		GuiElement.fill(x, y + 1, x + 1, y + h - 1, color);
		GuiElement.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	/** Draws text scaled up around its own top-left corner. */
	static void scaledText(TextRenderer text, String s, int x, int y, float scale, int color, boolean shadow) {
		GlStateManager.pushMatrix();
		GlStateManager.translatef(x, y, 0);
		GlStateManager.scalef(scale, scale, 1);
		if (shadow) {
			text.drawWithShadow(s, 0, 0, color);
		} else {
			text.draw(s, 0, 0, color);
		}
		GlStateManager.popMatrix();
	}

	static void centered(TextRenderer text, String s, int centerX, int y, int color) {
		text.draw(s, centerX - text.getWidth(s) / 2, y, color);
	}
}
