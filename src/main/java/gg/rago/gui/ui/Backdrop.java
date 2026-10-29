package gg.rago.gui.ui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The out-of-focus block landscape behind the main menu: drawn once into a small image as
 * blocks, blurred, then stretched over the screen. Nothing is loaded from disk.
 */
final class Backdrop {
	private static final int W = 384;
	private static final int H = 216;
	private static final int B = 12;
	private static DynamicTexture texture;

	private Backdrop() {
	}

	static int textureId() {
		if (texture == null) {
			texture = new DynamicTexture(blur(paint(), 5, 2));
			GlStateManager.bindTexture(texture.getGlTextureId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		}
		return texture.getGlTextureId();
	}

	static float aspect() {
		return W / (float) H;
	}

	private static BufferedImage paint() {
		Random rnd = new Random(0x5A60L);
		int cols = W / B;
		int rows = H / B;
		int[][] c = new int[rows][cols];
		for (int y = 0; y < rows; y++) {
			for (int x = 0; x < cols; x++) {
				// night sky, warmer towards the top-left
				float t = y / (float) rows;
				float warm = Math.max(0, 1 - (x / (float) cols) * 1.4f - t * 0.6f);
				int r = (int) (14 + 34 * warm + rnd.nextInt(4));
				int g = (int) (12 + 6 * warm + rnd.nextInt(3));
				int b = (int) (14 + 6 * warm + rnd.nextInt(3));
				c[y][x] = rgb(r, g, b);
			}
		}
		// the moon
		fill(c, (int) (cols * 0.69f), (int) (rows * 0.24f), 3, 3, rgb(205, 200, 190));
		// rolling ground
		int[] ground = new int[cols];
		double phase = rnd.nextDouble() * 6;
		for (int x = 0; x < cols; x++) {
			ground[x] = (int) Math.round(rows * 0.62 + Math.sin(x * 0.22 + phase) * 1.4 + Math.sin(x * 0.07) * 1.8);
		}
		// trees behind the ground line
		for (int x = 1; x < cols - 2; x += 3 + rnd.nextInt(4)) {
			int top = ground[x] - 5 - rnd.nextInt(5);
			int leaf = rgb(34 + rnd.nextInt(14), 74 + rnd.nextInt(24), 38 + rnd.nextInt(10));
			fill(c, x - 2, top, 5 + rnd.nextInt(2), 4, leaf);
			fill(c, x - 1, top - 2, 3, 2, leaf);
			fill(c, x, top + 4, 1, ground[x] - top - 4, rgb(70, 50, 32));
		}
		for (int x = 0; x < cols; x++) {
			for (int y = ground[x]; y < rows; y++) {
				int depth = y - ground[x];
				int col;
				if (depth == 0) {
					col = rgb(58 + rnd.nextInt(16), 104 + rnd.nextInt(22), 46 + rnd.nextInt(10));
				} else if (depth < 4) {
					col = rgb(96 + rnd.nextInt(18), 66 + rnd.nextInt(12), 44 + rnd.nextInt(8));
				} else {
					int v = 60 + rnd.nextInt(26);
					col = rgb(v, v - 4, v - 6);
				}
				c[y][x] = col;
			}
		}
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				img.setRGB(x, y, 0xFF000000 | c[Math.min(rows - 1, y / B)][Math.min(cols - 1, x / B)]);
			}
		}
		return img;
	}

	private static void fill(int[][] c, int x0, int y0, int w, int h, int col) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				if (y >= 0 && x >= 0 && y < c.length && x < c[0].length) {
					c[y][x] = col;
				}
			}
		}
	}

	private static int rgb(int r, int g, int b) {
		return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/** Repeated box blur, which approaches a gaussian blur. */
	private static BufferedImage blur(BufferedImage src, int radius, int passes) {
		int w = src.getWidth();
		int h = src.getHeight();
		int[] a = src.getRGB(0, 0, w, h, null, 0, w);
		int[] b = new int[a.length];
		for (int p = 0; p < passes; p++) {
			boxPass(a, b, w, h, radius, true);
			boxPass(b, a, w, h, radius, false);
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0, 0, w, h, a, 0, w);
		return out;
	}

	private static void boxPass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
		int lines = horizontal ? h : w;
		int len = horizontal ? w : h;
		for (int l = 0; l < lines; l++) {
			int sr = 0;
			int sg = 0;
			int sb = 0;
			int n = 2 * r + 1;
			for (int i = -r; i <= r; i++) {
				int px = in[index(l, Math.max(0, Math.min(len - 1, i)), w, horizontal)];
				sr += px >> 16 & 0xFF;
				sg += px >> 8 & 0xFF;
				sb += px & 0xFF;
			}
			for (int i = 0; i < len; i++) {
				out[index(l, i, w, horizontal)] = 0xFF000000 | (sr / n) << 16 | (sg / n) << 8 | (sb / n);
				int add = in[index(l, Math.min(len - 1, i + r + 1), w, horizontal)];
				int sub = in[index(l, Math.max(0, i - r), w, horizontal)];
				sr += (add >> 16 & 0xFF) - (sub >> 16 & 0xFF);
				sg += (add >> 8 & 0xFF) - (sub >> 8 & 0xFF);
				sb += (add & 0xFF) - (sub & 0xFF);
			}
		}
	}

	private static int index(int line, int i, int w, boolean horizontal) {
		return horizontal ? line * w + i : i * w + line;
	}
}
