package gg.rago.gui;

import net.minecraft.client.gui.GuiElement;

/** Colours (ARGB) and small drawing helpers shared by Rago's screens. */
final class Theme {
	static final int SHADE = 0x88000000;
	static final int PANEL = 0xF0121019;
	static final int ROW = 0xFF1C1926;
	static final int ROW_HOVER = 0xFF282435;
	static final int ACCENT = 0xFF8B6CF0;
	static final int TEXT = 0xFFF0EEF6;
	static final int MUTED = 0xFF9C98AC;
	static final int ON = 0xFF3FBF7F;
	static final int OFF = 0xFF4A4658;
	static final int DANGER = 0xFFE05A55;
	static final int OUTLINE = 0x80FFFFFF;
	static final int OUTLINE_ACTIVE = 0xFF8B6CF0;
	static final int GUIDE = 0x408B6CF0;

	private Theme() {
	}

	static void outline(int x, int y, int w, int h, int color) {
		GuiElement.fill(x, y, x + w, y + 1, color);
		GuiElement.fill(x, y + h - 1, x + w, y + h, color);
		GuiElement.fill(x, y + 1, x + 1, y + h - 1, color);
		GuiElement.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
