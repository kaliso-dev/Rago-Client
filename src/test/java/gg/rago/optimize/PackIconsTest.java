package gg.rago.optimize;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PackIconsTest {
	@AfterEach
	void noSwitch() {
		PackIcons.setSwitch(null);
	}

	private static BufferedImage icon(int w, int h, int type) {
		BufferedImage img = new BufferedImage(w, h, type);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setRGB(x, y, 0xFF000000 | (x * 255 / w) << 16 | (y * 255 / h) << 8 | ((x + y) & 1) * 255);
			}
		}
		return img;
	}

	@Test
	void iconsBiggerThan64AreStretchedTo64() {
		PackIcons.setSwitch(() -> true);
		for (int[] size : new int[][] {{128, 128}, {65, 65}, {100, 50}, {64, 128}, {512, 512}, {300, 200}}) {
			BufferedImage small = PackIcons.packIcon(icon(size[0], size[1], BufferedImage.TYPE_4BYTE_ABGR));
			assertEquals(64, small.getWidth());
			assertEquals(64, small.getHeight());
			assertEquals(BufferedImage.TYPE_INT_ARGB, small.getType());
		}
	}

	@Test
	void shrinkingKeepsThePixelUnderEachCentre() {
		BufferedImage big = icon(128, 128, BufferedImage.TYPE_INT_ARGB);
		BufferedImage small = PackIcons.shrink(big);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				assertEquals(big.getRGB(x * 2 + 1, y * 2 + 1), small.getRGB(x, y), "pixel " + x + "," + y);
			}
		}
	}

	@Test
	void smallIconsTheSettingOffAndNoIconAreLeftAsTheyAre() {
		PackIcons.setSwitch(() -> true);
		BufferedImage icon = icon(64, 64, BufferedImage.TYPE_4BYTE_ABGR);
		assertSame(icon, PackIcons.packIcon(icon));
		BufferedImage small = icon(32, 32, BufferedImage.TYPE_4BYTE_ABGR);
		assertSame(small, PackIcons.packIcon(small));
		assertNull(PackIcons.packIcon(null));
		PackIcons.setSwitch(() -> false);
		BufferedImage big = icon(128, 128, BufferedImage.TYPE_4BYTE_ABGR);
		assertSame(big, PackIcons.packIcon(big));
	}
}
