package gg.rago.overlays;

import gg.rago.gui.ui.TextColors;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rago's wool, clay and skies: colours that stay apart, any pack size, the sky box's cells. */
class OverlaysArtTest {
	/** The dyes of the eight Bed Wars teams: red, blue, lime (green), yellow, cyan (aqua), white, pink, gray. */
	private static final int[] TEAMS = {14, 11, 5, 4, 9, 0, 6, 7};
	private static final double[][] DEUTAN = {
		{0.367322, 0.860646, -0.227968}, {0.280085, 0.672501, 0.047413}, {-0.011820, 0.042940, 0.968881}};
	private static final double[][] PROTAN = {
		{0.152286, 1.052583, -0.204868}, {0.114503, 0.786281, 0.099216}, {-0.003882, -0.048116, 1.051998}};
	private static final double[][] TRITAN = {
		{1.255528, -0.076749, -0.178779}, {-0.078411, 0.930809, 0.147602}, {0.004733, 0.691367, 0.303900}};

	@Test
	void teamWoolStaysApartWithNormalSight() {
		assertTrue(closest(false, TextColors.NORMAL, null) > 0.08, "closest " + closest(false, TextColors.NORMAL, null));
		assertTrue(closest(true, TextColors.NORMAL, null) > 0.07, "clay closest " + closest(true, TextColors.NORMAL, null));
	}

	/**
	 * The colour-blind palettes on the blocks: much further apart for protanopia (Rago's aqua and pink
	 * nearly match there), further for tritanopia, and as far for deuteranopia (Rago's own colours are
	 * already apart there; the palette's are kept so blocks match the names).
	 */
	@Test
	void palettesHelpOnBlocks() {
		for (boolean clay : new boolean[] {false, true}) {
			double protan = closest(clay, TextColors.RED_GREEN, PROTAN);
			double tritan = closest(clay, TextColors.BLUE_YELLOW, TRITAN);
			double deutan = closest(clay, TextColors.RED_GREEN, DEUTAN);
			assertTrue(protan > 2 * closest(clay, TextColors.NORMAL, PROTAN), "protan " + protan);
			assertTrue(tritan > closest(clay, TextColors.NORMAL, TRITAN), "tritan " + tritan);
			assertTrue(deutan > 0.95 * closest(clay, TextColors.NORMAL, DEUTAN), "deutan " + deutan);
		}
	}

	@Test
	void clayIsDarkerThanWool() {
		for (int dye = 0; dye < 16; dye++) {
			double wool = BlockArt.Oklab.fromRgb(BlockArt.color(dye, false, -1))[0];
			double clay = BlockArt.Oklab.fromRgb(BlockArt.color(dye, true, -1))[0];
			assertTrue(clay < wool, BlockArt.DYES[dye]);
		}
	}

	@Test
	void blocksFitEveryPackSize() {
		for (BlockArt.Style style : BlockArt.Style.values()) {
			for (int size : new int[] {8, 16, 32, 64, 128, 512}) {
				int[] px = BlockArt.pixels(style, 0xC22F2A, size, false);
				assertEquals(size * size, px.length);
				for (int p : px) {
					assertEquals(0xFF, p >>> 24, style + " " + size);
				}
			}
		}
	}

	@Test
	void dyesByName() {
		assertEquals(14, BlockArt.dye("red"));
		assertEquals(3, BlockArt.dye("light_blue"));
		assertEquals(-1, BlockArt.dye("chartreuse"));
		assertEquals(0xc, BlockArt.teamCode(14));
		assertEquals(-1, BlockArt.teamCode(1));
	}

	/** A cell's point and back: every direction lands in one cell and maps to itself. */
	@Test
	void skyCellsRoundTrip() {
		Random r = new Random(7);
		for (int i = 0; i < 2000; i++) {
			double[] d = {r.nextGaussian(), r.nextGaussian(), r.nextGaussian()};
			double n = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
			double[] c = SkyArt.cell(d[0] / n, d[1] / n, d[2] / n);
			double[] back = SkyArt.direction((int) c[0], c[1], c[2]);
			double dot = (back[0] * d[0] + back[1] * d[1] + back[2] * d[2]) / n;
			assertTrue(dot > 0.99999, "dot " + dot);
		}
	}

	/** The cells as OptiFine lays them out: bottom, top, south, then west, north, east. */
	@Test
	void skyCellsFaceTheirSides() {
		double[][] expected = {{0, -1, 0}, {0, 1, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}};
		for (int side = 0; side < 6; side++) {
			double[] d = SkyArt.direction(side, 0.5, 0.5);
			for (int k = 0; k < 3; k++) {
				assertEquals(expected[side][k], d[k], 1e-9, "side " + side);
			}
		}
	}

	@Test
	void skiesHaveTheirHorizon() {
		for (SkyArt.Kind kind : SkyArt.Kind.values()) {
			int h = SkyArt.horizon(kind);
			assertTrue(h >= 0 && h <= 0xFFFFFF, kind.name());
		}
		int[] view = SkyArt.preview(SkyArt.Kind.DAY, 32, 18);
		assertEquals(32 * 18, view.length);
	}

	/** The OKLab distance between the two closest team blocks, as seen through {@code cvd} (null: normal sight). */
	private static double closest(boolean clay, int palette, double[][] cvd) {
		int[] c = new int[TEAMS.length];
		for (int i = 0; i < TEAMS.length; i++) {
			int code = BlockArt.teamCode(TEAMS[i]);
			int team = palette != TextColors.NORMAL && code >= 0 ? TextColors.rgb(palette, code) : -1;
			c[i] = BlockArt.color(TEAMS[i], clay, team);
		}
		double min = Double.MAX_VALUE;
		for (int i = 0; i < c.length; i++) {
			for (int j = i + 1; j < c.length; j++) {
				double[] x = seen(c[i], cvd);
				double[] y = seen(c[j], cvd);
				min = Math.min(min, Math.sqrt(sq(x[0] - y[0]) + sq(x[1] - y[1]) + sq(x[2] - y[2])));
			}
		}
		return min;
	}

	private static double[] seen(int rgb, double[][] cvd) {
		double[] l = {linear(rgb >> 16 & 0xFF), linear(rgb >> 8 & 0xFF), linear(rgb & 0xFF)};
		if (cvd != null) {
			double[] o = new double[3];
			for (int i = 0; i < 3; i++) {
				o[i] = Math.max(0, Math.min(1, cvd[i][0] * l[0] + cvd[i][1] * l[1] + cvd[i][2] * l[2]));
			}
			l = o;
		}
		double a = Math.cbrt(0.4122214708 * l[0] + 0.5363325363 * l[1] + 0.0514459929 * l[2]);
		double m = Math.cbrt(0.2119034982 * l[0] + 0.6806995451 * l[1] + 0.1073969566 * l[2]);
		double s = Math.cbrt(0.0883024619 * l[0] + 0.2817188376 * l[1] + 0.6299787005 * l[2]);
		return new double[] {
			0.2104542553 * a + 0.7936177850 * m - 0.0040720468 * s,
			1.9779984951 * a - 2.4285922050 * m + 0.4505937099 * s,
			0.0259040371 * a + 0.7827717662 * m - 0.8086757660 * s};
	}

	private static double linear(int c) {
		double v = c / 255.0;
		return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	private static double sq(double v) {
		return v * v;
	}
}
