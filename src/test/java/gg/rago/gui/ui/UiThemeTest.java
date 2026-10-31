package gg.rago.gui.ui;

import gg.rago.gui.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiThemeTest {
	@Test
	void darkIsTheDesignAsDrawn() {
		for (int c : new int[] {Theme.BG, Theme.CARD, Theme.DIM, Theme.BORDER, Theme.ACCENT}) {
			assertEquals(c, UiTheme.apply(c, UiTheme.DARK));
		}
	}

	@Test
	void oledTurnsTheBackgroundBlackAndKeepsTheSteps() {
		int bg = UiTheme.apply(Theme.BG, UiTheme.OLED);
		assertTrue(max(bg) <= 2, Integer.toHexString(bg));
		int[] steps = {Theme.BG, Theme.SIDEBAR, Theme.PANEL_SOLID, Theme.CARD, Theme.CARD_HOVER};
		for (int i = 1; i < steps.length; i++) {
			assertTrue(max(UiTheme.apply(steps[i], UiTheme.OLED)) > max(UiTheme.apply(steps[i - 1], UiTheme.OLED)), "step " + i);
		}
		assertEquals(Theme.PANEL >>> 24, UiTheme.apply(Theme.PANEL, UiTheme.OLED) >>> 24, "alpha kept");
	}

	@Test
	void coloursTextAndWhiteStayInOled() {
		for (int c : new int[] {Theme.ACCENT, Theme.GREEN, Theme.TEXT, Theme.MUTED, Theme.DIM, Theme.BORDER, 0xFFFFFFFF}) {
			assertEquals(c, UiTheme.apply(c, UiTheme.OLED), Integer.toHexString(c));
		}
	}

	@Test
	void highContrastMakesDimTextReadable() {
		double before = contrast(Theme.DIM, Theme.CARD);
		double after = contrast(UiTheme.text(Theme.DIM, UiTheme.CONTRAST), UiTheme.apply(Theme.CARD, UiTheme.CONTRAST));
		assertTrue(before < 4.5, "the dimmest text is below WCAG AA on cards: " + before);
		assertTrue(after >= 7, "and above AAA in high contrast: " + after);
		assertEquals(Theme.ACCENT, UiTheme.text(Theme.ACCENT, UiTheme.CONTRAST));
		assertEquals(Theme.TEXT, UiTheme.text(Theme.TEXT, UiTheme.CONTRAST));
		assertEquals(Theme.DIM, UiTheme.text(Theme.DIM, UiTheme.OLED), "OLED leaves text as it is");
	}

	@Test
	void greyFillsAreNotTakenForText() {
		// a mid-grey pill behind white text stays dark, or the text would stop being readable
		assertEquals(0xFF4A4D55, UiTheme.apply(0xFF4A4D55, UiTheme.CONTRAST));
		assertEquals(0xFF9A98A0, UiTheme.apply(Theme.MUTED, UiTheme.CONTRAST));
	}

	@Test
	void highContrastStrengthensFaintBorders() {
		assertEquals(0x32, UiTheme.apply(Theme.BORDER, UiTheme.CONTRAST, true) >>> 24);
		assertEquals(UiTheme.BORDER_MAX, UiTheme.apply(0x60FFFFFF, UiTheme.CONTRAST, true) >>> 24);
		assertEquals(0x60000000, UiTheme.apply(0x60000000, UiTheme.CONTRAST, true), "black shadows stay");
		assertEquals(0x26FFFFFF, UiTheme.apply(0x26FFFFFF, UiTheme.CONTRAST, false), "a white fill behind text stays faint");
	}

	private static int max(int argb) {
		return Math.max(argb >> 16 & 0xFF, Math.max(argb >> 8 & 0xFF, argb & 0xFF));
	}

	/** The WCAG contrast ratio of two opaque colours. */
	private static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(int rgb) {
		return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
	}

	private static double linear(int c) {
		double v = c / 255.0;
		return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}
}
