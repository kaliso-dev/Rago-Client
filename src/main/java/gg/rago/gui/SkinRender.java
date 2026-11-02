package gg.rago.gui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A Minecraft player drawn in 3D from a 64 x 64 skin, without OpenGL: every face of the head,
 * body, arms and legs (and the second layer: hat, jacket, sleeves, trousers) is projected with a
 * turn and a tilt and drawn texel by texel through a depth buffer, so the parts hide each other as
 * in the game. The texels stay sharp; the picture is drawn at twice its size and halved, which
 * smooths the edges only. Faces get a light like the game's: tops brightest, sides darker.
 */
final class SkinRender {
	/** One box of the model, in skin pixels (y up, the front towards +z), with its place in the skin. */
	private static final class Part {
		final float x0, y0, z0, x1, y1, z1;
		final int u, v, w, h, d;
		final boolean overlay;
		/** Turned around the x axis (degrees) about the pivot (px, py, pz): arms and legs swing. */
		final float swing;
		final float px, py, pz;

		Part(float x, float y, float z, int w, int h, int d, int u, int v, float grow, boolean overlay, float swing, float px, float py, float pz) {
			this.x0 = x - grow;
			this.y0 = y - grow;
			this.z0 = z - grow;
			this.x1 = x + w + grow;
			this.y1 = y + h + grow;
			this.z1 = z + d + grow;
			this.u = u;
			this.v = v;
			this.w = w;
			this.h = h;
			this.d = d;
			this.overlay = overlay;
			this.swing = swing;
			this.px = px;
			this.py = py;
			this.pz = pz;
		}
	}

	private SkinRender() {
	}

	/**
	 * The player in a {@code width} x {@code height} picture with a transparent background, turned
	 * {@code yaw} degrees (negative shows its left side and has it look to the left) and seen {@code pitch} degrees from above.
	 */
	static BufferedImage render(BufferedImage skin, boolean slim, int width, int height, double yaw, double pitch) {
		BufferedImage tex = skin;
		if (skin.getHeight() == 32) {
			tex = legacy(skin);
		}
		List<Part> parts = parts(slim);
		int ss = 2;
		int W = width * ss;
		int H = height * ss;
		double cy = Math.cos(Math.toRadians(yaw));
		double sy = Math.sin(Math.toRadians(yaw));
		double cp = Math.cos(Math.toRadians(pitch));
		double sp = Math.sin(Math.toRadians(pitch));

		// fit: the model's bounds once turned
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (Part p : parts) {
			for (int i = 0; i < 8; i++) {
				double[] q = view(pose(p, (i & 1) == 0 ? p.x0 : p.x1, (i & 2) == 0 ? p.y0 : p.y1, (i & 4) == 0 ? p.z0 : p.z1), cy, sy, cp, sp);
				minX = Math.min(minX, q[0]);
				maxX = Math.max(maxX, q[0]);
				minY = Math.min(minY, q[1]);
				maxY = Math.max(maxY, q[1]);
			}
		}
		double scale = Math.min(W / (maxX - minX), H / (maxY - minY));
		double ox = W / 2.0 - (minX + maxX) / 2 * scale;
		double oy = H / 2.0 + (minY + maxY) / 2 * scale;

		int[] color = new int[W * H];
		float[] depth = new float[W * H];
		java.util.Arrays.fill(depth, -Float.MAX_VALUE);
		for (Part p : parts) {
			for (int f = 0; f < 6; f++) {
				face(p, f, tex, color, depth, W, H, scale, ox, oy, cy, sy, cp, sp);
			}
		}
		BufferedImage big = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		big.setRGB(0, 0, W, H, color, 0, W);
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(big, 0, 0, width, height, null);
		g.dispose();
		return out;
	}

	/** The model's boxes: base layer first, then the second layer a little larger. */
	private static List<Part> parts(boolean slim) {
		List<Part> out = new ArrayList<>();
		int aw = slim ? 3 : 4;
		float armSwing = 9;
		float legSwing = 7;
		for (int layer = 0; layer < 2; layer++) {
			boolean over = layer == 1;
			// head 8x8x8 and hat
			out.add(new Part(-4, 24, -4, 8, 8, 8, over ? 32 : 0, 0, over ? 0.5f : 0, over, 0, 0, 0, 0));
			// body 8x12x4 and jacket
			out.add(new Part(-4, 12, -2, 8, 12, 4, 16, over ? 32 : 16, over ? 0.25f : 0, over, 0, 0, 0, 0));
			// right arm (the player's right, on the viewer's left) and its sleeve
			out.add(new Part(-4 - aw, 12, -2, aw, 12, 4, 40, over ? 32 : 16, over ? 0.25f : 0, over, -armSwing, -4 - aw / 2f, 22, 0));
			// left arm and sleeve
			out.add(new Part(4, 12, -2, aw, 12, 4, over ? 48 : 32, 48, over ? 0.25f : 0, over, armSwing, 4 + aw / 2f, 22, 0));
			// right leg and trousers
			out.add(new Part(-4, 0, -2, 4, 12, 4, 0, over ? 32 : 16, over ? 0.25f : 0, over, legSwing, -2, 12, 0));
			// left leg and trousers
			out.add(new Part(0, 0, -2, 4, 12, 4, over ? 0 : 16, 48, over ? 0.25f : 0, over, -legSwing, 2, 12, 0));
		}
		return out;
	}

	/** A point of a part with its swing applied. */
	private static double[] pose(Part p, double x, double y, double z) {
		if (p.swing == 0) {
			return new double[] {x, y, z};
		}
		double a = Math.toRadians(p.swing);
		double dy = y - p.py;
		double dz = z - p.pz;
		return new double[] {x, p.py + dy * Math.cos(a) - dz * Math.sin(a), p.pz + dy * Math.sin(a) + dz * Math.cos(a)};
	}

	/** Turned by yaw around y, then tilted by pitch around x: {x, y, depth (larger is nearer)}. */
	private static double[] view(double[] q, double cy, double sy, double cp, double sp) {
		double x = q[0] * cy + q[2] * sy;
		double z = -q[0] * sy + q[2] * cy;
		double y = q[1] * cp - z * sp;
		double z2 = q[1] * sp + z * cp;
		return new double[] {x, y, z2};
	}

	/**
	 * Face {@code f} of a part (0 top, 1 bottom, 2 right (-x), 3 front (+z), 4 left (+x), 5 back),
	 * mapped as the game maps skins: corner (0,0) of the face's area in the skin, its u and v axes.
	 */
	private static void face(Part p, int f, BufferedImage tex, int[] color, float[] depth, int W, int H,
		double scale, double ox, double oy, double cy, double sy, double cp, double sp) {
		int u0, v0, fu, fv;
		double[] o, du, dv;
		double shade;
		switch (f) {
			case 0: // top: u along +x, v towards the front
				u0 = p.u + p.d;
				v0 = p.v;
				fu = p.w;
				fv = p.d;
				o = new double[] {p.x0, p.y1, p.z0};
				du = new double[] {p.x1 - p.x0, 0, 0};
				dv = new double[] {0, 0, p.z1 - p.z0};
				shade = 1.0;
				break;
			case 1: // bottom
				u0 = p.u + p.d + p.w;
				v0 = p.v;
				fu = p.w;
				fv = p.d;
				o = new double[] {p.x0, p.y0, p.z1};
				du = new double[] {p.x1 - p.x0, 0, 0};
				dv = new double[] {0, 0, p.z0 - p.z1};
				shade = 0.55;
				break;
			case 2: // the part's right side (-x): u from the back to the front
				u0 = p.u;
				v0 = p.v + p.d;
				fu = p.d;
				fv = p.h;
				o = new double[] {p.x0, p.y1, p.z0};
				du = new double[] {0, 0, p.z1 - p.z0};
				dv = new double[] {0, p.y0 - p.y1, 0};
				shade = 0.74;
				break;
			case 3: // front (+z): u along +x
				u0 = p.u + p.d;
				v0 = p.v + p.d;
				fu = p.w;
				fv = p.h;
				o = new double[] {p.x0, p.y1, p.z1};
				du = new double[] {p.x1 - p.x0, 0, 0};
				dv = new double[] {0, p.y0 - p.y1, 0};
				shade = 0.94;
				break;
			case 4: // the part's left side (+x): u from the front to the back
				u0 = p.u + p.d + p.w;
				v0 = p.v + p.d;
				fu = p.d;
				fv = p.h;
				o = new double[] {p.x1, p.y1, p.z1};
				du = new double[] {0, 0, p.z0 - p.z1};
				dv = new double[] {0, p.y0 - p.y1, 0};
				shade = 0.74;
				break;
			default: // back (-z): u along -x
				u0 = p.u + p.d + p.w + p.d;
				v0 = p.v + p.d;
				fu = p.w;
				fv = p.h;
				o = new double[] {p.x1, p.y1, p.z0};
				du = new double[] {p.x0 - p.x1, 0, 0};
				dv = new double[] {0, p.y0 - p.y1, 0};
				shade = 0.94;
				break;
		}
		if (u0 + fu > tex.getWidth() || v0 + fv > tex.getHeight()) {
			return;
		}
		double[] a = view(pose(p, o[0], o[1], o[2]), cy, sy, cp, sp);
		double[] b = view(pose(p, o[0] + du[0], o[1] + du[1], o[2] + du[2]), cy, sy, cp, sp);
		double[] c = view(pose(p, o[0] + dv[0], o[1] + dv[1], o[2] + dv[2]), cy, sy, cp, sp);
		// screen space: x right, y down
		double ax = ox + a[0] * scale, ay = oy - a[1] * scale;
		double ex = (b[0] - a[0]) * scale, ey = -(b[1] - a[1]) * scale;
		double fx = (c[0] - a[0]) * scale, fy = -(c[1] - a[1]) * scale;
		double det = ex * fy - ey * fx;
		if (Math.abs(det) < 1e-6) {
			return; // seen edge-on
		}
		// facing away: the face's normal (from its two edges in view space) points away from the viewer
		double nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
		if (faceAway(f, nz)) {
			return;
		}
		int minX = (int) Math.max(0, Math.floor(Math.min(Math.min(ax, ax + ex), Math.min(ax + fx, ax + ex + fx))));
		int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(Math.max(ax, ax + ex), Math.max(ax + fx, ax + ex + fx))));
		int minY = (int) Math.max(0, Math.floor(Math.min(Math.min(ay, ay + ey), Math.min(ay + fy, ay + ey + fy))));
		int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(Math.max(ay, ay + ey), Math.max(ay + fy, ay + ey + fy))));
		double da = b[2] - a[2];
		double db = c[2] - a[2];
		for (int y = minY; y <= maxY; y++) {
			double py = y + 0.5 - ay;
			for (int x = minX; x <= maxX; x++) {
				double px = x + 0.5 - ax;
				double s = (px * fy - py * fx) / det;
				double t = (py * ex - px * ey) / det;
				if (s < 0 || s >= 1 || t < 0 || t >= 1) {
					continue;
				}
				float z = (float) (a[2] + s * da + t * db) + (p.overlay ? 0.01f : 0);
				int i = y * W + x;
				if (z <= depth[i]) {
					continue;
				}
				int argb = tex.getRGB(u0 + Math.min(fu - 1, (int) (s * fu)), v0 + Math.min(fv - 1, (int) (t * fv)));
				int alpha = argb >>> 24;
				if (p.overlay && alpha < 128) {
					continue;
				}
				depth[i] = z;
				int r = (int) ((argb >> 16 & 255) * shade);
				int g = (int) ((argb >> 8 & 255) * shade);
				int bl = (int) ((argb & 255) * shade);
				color[i] = 0xFF000000 | r << 16 | g << 8 | bl;
			}
		}
	}

	/**
	 * Whether a face points away from the viewer. Every face's u and v edges, as set up in
	 * {@link #face}, cross to its inward normal, so a face that is seen has a negative cross
	 * product along the view axis (the viewer is towards +z).
	 */
	private static boolean faceAway(int f, double nz) {
		return nz >= 0;
	}

	/** A 64 x 32 skin made 64 x 64: the left arm and leg mirror the right ones, as in the game. */
	static BufferedImage legacy(BufferedImage skin) {
		BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(skin, 0, 0, null);
		g.dispose();
		copyMirrored(out, 0, 16, 16, 48);
		copyMirrored(out, 40, 16, 32, 48);
		return out;
	}

	/** Copies a limb's 16 x 16 area mirrored, faces swapped as the game does for old skins. */
	private static void copyMirrored(BufferedImage img, int sx, int sy, int dx, int dy) {
		// top and bottom (4x4 each), then the four sides (4x12 each), each flipped left to right
		int[][] boxes = {{4, 0, 4, 4, 4, 0}, {8, 0, 4, 4, 8, 0}, {0, 4, 4, 12, 8, 4}, {4, 4, 4, 12, 4, 4}, {8, 4, 4, 12, 0, 4}, {12, 4, 4, 12, 12, 4}};
		for (int[] b : boxes) {
			for (int y = 0; y < b[3]; y++) {
				for (int x = 0; x < b[2]; x++) {
					img.setRGB(dx + b[4] + b[2] - 1 - x, dy + b[5] + y, img.getRGB(sx + b[0] + x, sy + b[1] + y));
				}
			}
		}
	}
}
