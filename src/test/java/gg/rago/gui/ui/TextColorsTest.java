package gg.rago.gui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colour-blind palettes, checked the way they were chosen: each Bed Wars team colour is put
 * through the Machado, Oliveira and Fernandes (2009) simulation of the colour blindness at full
 * severity, in linear RGB, and the closest two teams are measured in OKLab (distance 1 is black to
 * white; about 0.02 is barely visible).
 */
class TextColorsTest {
	/** Red, blue, green, yellow, aqua, white, pink and gray (§8): the eight teams. */
	private static final String TEAMS = "c9aebfd8";
	private static final double[][] PROTAN = {
		{0.152286, 1.052583, -0.204868}, {0.114503, 0.786281, 0.099216}, {-0.003882, -0.048116, 1.051998}};
	private static final double[][] DEUTAN = {
		{0.367322, 0.860646, -0.227968}, {0.280085, 0.672501, 0.047413}, {-0.011820, 0.042940, 0.968881}};
	private static final double[][] TRITAN = {
		{1.255528, -0.076749, -0.178779}, {-0.078411, 0.930809, 0.147602}, {0.004733, 0.691367, 0.303900}};

	@Test
	void theNormalPaletteIsMinecrafts() {
		int[] c = TextColors.colors(TextColors.NORMAL);
		assertEquals(0xFF5555, c[0xc]);
		assertEquals(0xFFAA00, c[6]);
		assertEquals(0x5555FF, c[9]);
		assertArrayEquals(TextColors.VANILLA, c);
	}

	@Test
	void onlyTheChangedColoursChange() {
		for (int palette : new int[] {TextColors.RED_GREEN, TextColors.BLUE_YELLOW}) {
			int[] c = TextColors.colors(palette);
			for (int i = 0; i < 16; i++) {
				boolean changed = TextColors.CHANGED.indexOf(Character.forDigit(i, 16)) >= 0;
				assertEquals(!changed, c[i] == TextColors.VANILLA[i], "code " + Integer.toHexString(i));
			}
		}
	}

	@Test
	void shadowsAreAQuarterLikeTheGames() {
		assertEquals(0x3F1515, TextColors.shadow(0xFF5555));
		assertEquals(0x151515, TextColors.shadow(0x555555));
	}

	@Test
	void redGreenTeamsMoveApartForDeutansAndProtans() {
		int[] vanilla = TextColors.VANILLA;
		int[] rg = TextColors.colors(TextColors.RED_GREEN);
		double before = Math.min(closest(vanilla, DEUTAN), closest(vanilla, PROTAN));
		double after = Math.min(closest(rg, DEUTAN), closest(rg, PROTAN));
		assertTrue(before < 0.035, "vanilla green and yellow almost meet for protans: " + before);
		assertTrue(after > 0.12, "red-green palette, closest teams: " + after);
		assertTrue(closest(rg, null) > 0.15, "still apart with normal sight");
	}

	@Test
	void blueYellowTeamsMoveApartForTritans() {
		double before = closest(TextColors.VANILLA, TRITAN);
		int[] by = TextColors.colors(TextColors.BLUE_YELLOW);
		assertTrue(before < 0.05, "vanilla green and aqua almost meet for tritans: " + before);
		assertTrue(closest(by, TRITAN) > 0.12, "blue-yellow palette, closest teams");
		assertTrue(closest(by, null) > 0.12, "still apart with normal sight");
	}

	@Test
	void goldStaysApartFromGreenForTheRedGreenPalette() {
		// gold is ping orange, ranks and coins; green moved towards mint so they do not meet
		int[] rg = TextColors.colors(TextColors.RED_GREEN);
		double before = distance(TextColors.VANILLA[0xa], TextColors.VANILLA[6], DEUTAN);
		double after = distance(rg[0xa], rg[6], DEUTAN);
		assertTrue(after > before, before + " -> " + after);
	}

	/** The OKLab distance between the two closest teams, as seen through {@code cvd} (null: normal sight). */
	private static double closest(int[] palette, double[][] cvd) {
		double min = Double.MAX_VALUE;
		for (int i = 0; i < TEAMS.length(); i++) {
			for (int j = i + 1; j < TEAMS.length(); j++) {
				min = Math.min(min, distance(palette[Character.digit(TEAMS.charAt(i), 16)], palette[Character.digit(TEAMS.charAt(j), 16)], cvd));
			}
		}
		return min;
	}

	private static double distance(int a, int b, double[][] cvd) {
		double[] x = oklab(seen(a, cvd));
		double[] y = oklab(seen(b, cvd));
		return Math.sqrt(sq(x[0] - y[0]) + sq(x[1] - y[1]) + sq(x[2] - y[2]));
	}

	private static double[] seen(int rgb, double[][] cvd) {
		double[] l = {linear(rgb >> 16 & 0xFF), linear(rgb >> 8 & 0xFF), linear(rgb & 0xFF)};
		if (cvd == null) {
			return l;
		}
		double[] o = new double[3];
		for (int i = 0; i < 3; i++) {
			o[i] = Math.max(0, Math.min(1, cvd[i][0] * l[0] + cvd[i][1] * l[1] + cvd[i][2] * l[2]));
		}
		return o;
	}

	private static double linear(int c) {
		double v = c / 255.0;
		return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	private static double[] oklab(double[] rgb) {
		double l = Math.cbrt(0.4122214708 * rgb[0] + 0.5363325363 * rgb[1] + 0.0514459929 * rgb[2]);
		double m = Math.cbrt(0.2119034982 * rgb[0] + 0.6806995451 * rgb[1] + 0.1073969566 * rgb[2]);
		double s = Math.cbrt(0.0883024619 * rgb[0] + 0.2817188376 * rgb[1] + 0.6299787005 * rgb[2]);
		return new double[] {
			0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
			1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
			0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
	}

	private static double sq(double v) {
		return v * v;
	}
}
