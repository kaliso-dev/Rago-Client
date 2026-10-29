package gg.rago.gui.ui;

import gg.rago.gui.Theme;

/** Controls shared by Rago's screens, drawn with {@link Ui} in design units. */
public final class Widgets {
	public static final float SWITCH_W = 40;
	public static final float SWITCH_H = 22;

	private Widgets() {
	}

	/** Red pill with a white knob when on, grey when off. */
	public static void toggle(float x, float y, boolean on) {
		Ui.round(x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2, on ? Theme.ACCENT : Theme.SWITCH_OFF);
		float k = SWITCH_H - 6;
		float kx = on ? x + SWITCH_W - 3 - k : x + 3;
		if (on) {
			Ui.circle(kx + k / 2, y + 3 + k / 2, k / 2 + 1, 0xFF1A0605);
		}
		Ui.circle(kx + k / 2, y + 3 + k / 2, on ? k / 2 - 2 : k / 2, on ? 0xFF1A0605 : Theme.KNOB_OFF);
		if (on) {
			Ui.circle(kx + k / 2, y + 3 + k / 2, k / 2 - 1, 0xFF1A0605);
		}
	}

	/** A pill button with an optional icon; returns nothing, the caller registers the click. */
	public static void button(float x, float y, float w, float h, String icon, String label, boolean primary, float textSize) {
		boolean hover = Ui.hover(x, y, w, h);
		if (primary) {
			Ui.round(x, y, w, h, h / 2, hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
		} else {
			Ui.roundBorder(x, y, w, h, h / 2, hover ? Theme.CARD_HOVER : 0xE6141316, Theme.BORDER_STRONG);
		}
		float iconSize = textSize + 3;
		float tw = Ui.textWidth(Ui.SEMIBOLD, label, textSize);
		float total = tw + (icon != null ? iconSize + 10 : 0);
		float cx = x + (w - total) / 2;
		float ty = y + (h - textSize * 1.21f) / 2;
		int color = primary ? 0xFF1A0605 : Theme.TEXT;
		if (icon != null) {
			Ui.icon(icon, cx, y + (h - iconSize) / 2, iconSize, color);
			cx += iconSize + 10;
		}
		Ui.text(Ui.SEMIBOLD, label, cx, ty, textSize, color);
	}

	/** Round icon-only button. */
	public static void iconButton(float x, float y, float size, String icon) {
		boolean hover = Ui.hover(x, y, size, size);
		Ui.roundBorder(x, y, size, size, size / 2, hover ? Theme.CARD_HOVER : 0xE6141316, Theme.BORDER_STRONG);
		float i = size * 0.42f;
		Ui.icon(icon, x + (size - i) / 2, y + (size - i) / 2, i, hover ? Theme.TEXT : 0xFFD4D4D8);
	}

	/** Small pill with a coloured dot and monospace text, as in the top bar. */
	public static float statusPill(float x, float y, float h, int dot, String text) {
		float size = 12;
		float w = 12 + 8 + 6 + Ui.textWidth(Ui.MONO, text, size) + 14;
		Ui.roundBorder(x, y, w, h, h / 2, 0xCC141316, Theme.BORDER);
		Ui.circle(x + 16, y + h / 2, 3, dot);
		Ui.text(Ui.MONO, text, x + 26, y + (h - size * 1.3f) / 2, size, 0xFFD4D4D8);
		return w;
	}

	public static final float PILL_W = 44;
	public static final float PILL_H = 20;

	/**
	 * Settings-page switch: a pill that reads ON (accent) or OFF (grey), with a knob that slides.
	 * {@code key} identifies it for the animation.
	 */
	public static void pill(Object key, float x, float y, boolean on) {
		float t = Anim.to(key, on ? 1f : 0f, 160);
		boolean hover = Ui.hover(x, y, PILL_W, PILL_H);
		int track = Ui.mix(hover ? 0xFF46434A : Theme.SWITCH_OFF, hover ? Theme.ACCENT_HOVER : Theme.ACCENT, t);
		Ui.round(x, y, PILL_W, PILL_H, PILL_H / 2, track);
		float k = PILL_H - 6;
		float kx = Anim.lerp(x + 3, x + PILL_W - 3 - k, t);
		Ui.circle(kx + k / 2, y + PILL_H / 2, k / 2, on ? 0xFFFFFFFF : Theme.KNOB_OFF);
		float size = 8;
		String label = on ? "ON" : "OFF";
		float lw = Ui.textWidth(Ui.BOLD, label, size);
		float lx = on ? x + 7 : x + PILL_W - 7 - lw;
		Ui.text(Ui.BOLD, label, lx, y + (PILL_H - size * 1.21f) / 2, size, on ? 0xFFFFFFFF : 0xFFB9B7BE);
	}

	/** Hover amount 0..1 of an area, eased; for colour transitions. */
	public static float hoverAmount(Object key, float x, float y, float w, float h) {
		return Anim.to(key, Ui.hover(x, y, w, h) ? 1f : 0f, 120);
	}

	/** ‹ value › selector; returns nothing, the caller registers clicks on the arrows. */
	public static void chooser(float x, float y, float w, float h, String value) {
		boolean lh = Ui.hover(x, y, h, h);
		boolean rh = Ui.hover(x + w - h, y, h, h);
		Ui.roundBorder(x, y, w, h, h / 2, 0xFF151417, Theme.BORDER);
		float i = h * 0.5f;
		Ui.icon("chevron-left", x + (h - i) / 2, y + (h - i) / 2, i, lh ? Theme.TEXT : Theme.MUTED);
		Ui.icon("chevron-right", x + w - h + (h - i) / 2, y + (h - i) / 2, i, rh ? Theme.TEXT : Theme.MUTED);
		float size = 10;
		Ui.textCentered(Ui.MEDIUM, value, x + w / 2, y + (h - size * 1.21f) / 2, size, Theme.TEXT);
	}

	/** Thin horizontal slider: track, filled part and knob. {@code f} is 0..1. */
	public static void slider(float x, float y, float w, float f) {
		float cy = y + 9;
		Ui.round(x, cy - 2, w, 4, 2, Theme.SWITCH_OFF);
		Ui.round(x, cy - 2, Math.max(4, w * f), 4, 2, Theme.ACCENT);
		Ui.circle(x + w * f, cy, 8, Theme.TEXT);
	}
}
