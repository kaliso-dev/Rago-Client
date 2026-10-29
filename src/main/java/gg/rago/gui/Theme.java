package gg.rago.gui;

import net.minecraft.client.gui.Gui;

/** Rago's colours (ARGB), taken from the design mock-ups, and two GUI-pixel helpers for the HUD editor. */
public final class Theme {
	public static final int BG = 0xFF0B0A0D;
	public static final int PANEL = 0xF5121114;
	public static final int PANEL_SOLID = 0xFF121114;
	public static final int SIDEBAR = 0xFF0F0E11;
	public static final int CARD = 0xFF18171B;
	public static final int CARD_HOVER = 0xFF1E1D22;
	public static final int FIELD = 0xFF1A191D;
	public static final int CHIP = 0xFF1C1B1F;
	public static final int CHIP_ACTIVE = 0xFFF4F4F5;
	public static final int BORDER = 0x14FFFFFF;
	public static final int BORDER_STRONG = 0x26FFFFFF;
	public static final int ACCENT = 0xFFF2392C;
	public static final int ACCENT_HOVER = 0xFFFF4B3E;
	public static final int ACCENT_SOFT = 0x2EF2392C;
	public static final int TEXT = 0xFFF4F4F5;
	public static final int MUTED = 0xFF9A98A0;
	public static final int DIM = 0xFF6B6970;
	public static final int GREEN = 0xFF34D399;
	public static final int BLUE = 0xFF60A5FA;
	public static final int YELLOW = 0xFFFACC15;
	public static final int SWITCH_OFF = 0xFF3A383E;
	public static final int KNOB_OFF = 0xFFC9C8CD;

	// HUD editor (GUI pixels)
	public static final int OUTLINE = 0x80FFFFFF;
	public static final int OUTLINE_ACTIVE = ACCENT;
	public static final int GUIDE = 0x40F2392C;

	private Theme() {
	}

	static void outline(int x, int y, int w, int h, int color) {
		Gui.drawRect(x, y, x + w, y + 1, color);
		Gui.drawRect(x, y + h - 1, x + w, y + h, color);
		Gui.drawRect(x, y + 1, x + 1, y + h - 1, color);
		Gui.drawRect(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
