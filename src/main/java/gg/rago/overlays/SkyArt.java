package gg.rago.overlays;

import java.util.Random;

/**
 * Rago's own skies, painted in code (no image from anywhere): a gradient by height, the sun or the
 * moon, clouds, stars, a nebula or an aurora depending on the sky. Painted as a sky box in the layout
 * OptiFine and MCPatcher use for packs' skies (three squares by two), and drawn by the same code
 * ({@link SkyBox}), so a pixel here lands where {@link #direction} says.
 */
public final class SkyArt {
	public enum Kind {
		/** A clear blue day with a high sun and a few thin clouds. */
		DAY,
		/** The sun on the western horizon, orange to violet. */
		SUNSET,
		/** A starry night with the Milky Way and the moon. */
		NIGHT,
		/** A nebula in violet, magenta and blue, full of stars. */
		GALAXY,
		/** Green and violet aurora curtains to the north, over a starry night. */
		AURORA,
		/** Soft pink and blue, with pastel clouds. */
		PASTEL
	}

	/** The side drawn into each cell of the 3x2 image, as GL turns it (see {@link SkyBox}). */
	private static final double[][][] SIDES = new double[6][][];

	static {
		double[][] m0 = mul(rotation(-90, 0, 1, 0), mul(rotation(90, 1, 0, 0), rotation(-90, 0, 0, 1)));
		SIDES[4] = m0;
		SIDES[1] = mul(m0, rotation(90, 1, 0, 0));
		SIDES[0] = mul(m0, rotation(-90, 1, 0, 0));
		double[][] m1 = mul(m0, rotation(90, 0, 0, 1));
		SIDES[5] = m1;
		double[][] m2 = mul(m1, rotation(90, 0, 0, 1));
		SIDES[2] = m2;
		SIDES[3] = mul(m2, rotation(90, 0, 0, 1));
	}

	private SkyArt() {
	}

	/**
	 * The sky box, 3 faces wide and 2 high, each {@code face} pixels: ARGB, rows from the top. Only
	 * what is smooth (gradients, glows, clouds, nebulas): stars, the sun and the moon would blur when
	 * a face is stretched over the screen, so {@link #bodies} gives them to draw as sprites.
	 */
	public static int[] box(Kind kind, int face) {
		int w = face * 3;
		int[] out = new int[w * face * 2];
		Painter p = new Painter(kind);
		p.discs = false;
		for (int side = 0; side < 6; side++) {
			int ox = side % 3 * face;
			int oy = side / 3 * face;
			for (int y = 0; y < face; y++) {
				for (int x = 0; x < face; x++) {
					double[] d = direction(side, (x + 0.5) / face, (y + 0.5) / face);
					out[(oy + y) * w + ox + x] = 0xFF000000 | p.color(d[0], d[1], d[2]);
				}
			}
		}
		return out;
	}

	/**
	 * The stars, sun and moon of a sky as sprites: per sprite {x, y, z (unit direction), half size in
	 * degrees, RGB, brightness 0 to 1, kind (0 star, 1 sun, 2 moon)}.
	 */
	public static double[][] bodies(Kind kind) {
		Painter p = new Painter(kind);
		java.util.List<double[]> out = new java.util.ArrayList<>();
		Random r = new Random(77);
		for (int i = 0; i < p.starCount(); i++) {
			double[] d = Painter.randomDirection(r);
			double bright = p.star(r, d);
			int tint = Painter.starTint(r);
			double size = 0.05 + r.nextDouble() * 0.04 + bright * 0.09;
			if (bright > 0.04) {
				out.add(new double[] {d[0], d[1], d[2], size, tint, bright, 0});
			}
		}
		double[] sun = p.sunAt();
		if (sun != null) {
			double[] d = toDirection(sun[0], sun[1]);
			out.add(new double[] {d[0], d[1], d[2], sun[2], sun[3], 1, 1});
		}
		double[] moon = p.moonAt();
		if (moon != null) {
			double[] d = toDirection(moon[0], moon[1]);
			out.add(new double[] {d[0], d[1], d[2], moon[2], 0xEEF2FF, 1, 2});
		}
		return out.toArray(new double[0][]);
	}

	/**
	 * The picture of a sun ({@code moon} false: a bright disc with a soft edge) or of the moon (grey
	 * seas on it) filling {@code size} pixels, ARGB with alpha for the edge.
	 */
	public static int[] disc(boolean moon, int rgb, int size) {
		int[] out = new int[size * size];
		Noise n = new Noise(0x3007L);
		double r = size / 2.0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				double dx = (x + 0.5 - r) / r;
				double dy = (y + 0.5 - r) / r;
				double d = Math.sqrt(dx * dx + dy * dy);
				// the disc fills 70 % of the sprite; the rest is a short soft edge
				double a = 1 - smooth(0.66, 0.74, d);
				if (a <= 0) {
					continue;
				}
				int c = rgb;
				if (moon) {
					double sea = n.fbm(dx * 2.4 + 4, dy * 2.4, 1.5, 4);
					double shade = 0.8 + 0.2 * smooth(0.42, 0.62, sea);
					double rim = 1 - 0.12 * smooth(0.3, 0.7, d);
					c = scale(rgb, shade * rim);
				}
				out[y * size + x] = (int) Math.round(a * 255) << 24 | c;
			}
		}
		return out;
	}

	/** A round, soft point of light {@code size} pixels wide, white with alpha, for the stars. */
	public static int[] starSprite(int size) {
		int[] out = new int[size * size];
		double r = size / 2.0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				double dx = (x + 0.5 - r) / r;
				double dy = (y + 0.5 - r) / r;
				double a = Math.exp(-(dx * dx + dy * dy) * 4.5) - 0.011;
				out[y * size + x] = (int) Math.round(Math.max(0, Math.min(1, a)) * 255) << 24 | 0xFFFFFF;
			}
		}
		return out;
	}

	private static int scale(int rgb, double k) {
		int r = (int) Math.min(255, (rgb >> 16 & 0xFF) * k);
		int g = (int) Math.min(255, (rgb >> 8 & 0xFF) * k);
		int b = (int) Math.min(255, (rgb & 0xFF) * k);
		return r << 16 | g << 8 | b;
	}

	/** The unit direction at azimuth {@code az} (degrees clockwise from north) and elevation {@code el}. */
	static double[] toDirection(double az, double el) {
		double a = Math.toRadians(az);
		double e = Math.toRadians(el);
		return new double[] {Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e)};
	}

	/**
	 * What a player sees looking at the horizon, {@code w} by {@code h} pixels: the preview in menus.
	 * Looks towards the sky's most interesting part (the sun, the aurora...).
	 */
	public static int[] preview(Kind kind, int w, int h) {
		Painter p = new Painter(kind);
		int[] out = new int[w * h];
		double yaw = Math.toRadians(p.lookAzimuth());
		double pitch = Math.toRadians(14);
		double fov = Math.toRadians(100);
		double f = 1 / Math.tan(fov / 2);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				double sx = (2 * (x + 0.5) / w - 1);
				double sy = -(2 * (y + 0.5) / h - 1) * h / w;
				// camera looking north (-z), pitched up, then turned to yaw (clockwise from north)
				double cx = sx;
				double cy = sy * Math.cos(pitch) + f * Math.sin(pitch);
				double cz = -(f * Math.cos(pitch) - sy * Math.sin(pitch));
				double dx = cx * Math.cos(yaw) - cz * Math.sin(yaw);
				double dz = cx * Math.sin(yaw) + cz * Math.cos(yaw);
				double n = Math.sqrt(dx * dx + cy * cy + dz * dz);
				out[y * w + x] = 0xFF000000 | p.color(dx / n, cy / n, dz / n);
			}
		}
		p.previewStars(out, w, h, yaw, pitch, f);
		return out;
	}

	/** The fog colour that matches the sky's horizon, RGB. */
	public static int horizon(Kind kind) {
		Painter p = new Painter(kind);
		double[] sum = new double[3];
		int n = 0;
		for (int a = 0; a < 360; a += 10) {
			for (int e = 0; e <= 6; e += 3) {
				double az = Math.toRadians(a);
				double el = Math.toRadians(e);
				int c = p.color(Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el));
				double[] lab = BlockArt.Oklab.fromRgb(c);
				for (int i = 0; i < 3; i++) {
					sum[i] += lab[i];
				}
				n++;
			}
		}
		return BlockArt.Oklab.toRgb(new double[] {sum[0] / n, sum[1] / n, sum[2] / n});
	}

	/**
	 * The world direction (x east, y up, z south, unit length) of the point ({@code u}, {@code v}) of
	 * a cell, 0 to 1 from its top left corner, for the six cells: 0 bottom, 1 top, 2 south, 3 west,
	 * 4 north, 5 east (sky not turned with the time of day).
	 */
	static double[] direction(int side, double u, double v) {
		double[] p = {-1 + 2 * u, -1, -1 + 2 * v};
		double[][] m = SIDES[side];
		double x = m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2];
		double y = m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2];
		double z = m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2];
		double n = Math.sqrt(x * x + y * y + z * z);
		return new double[] {x / n, y / n, z / n};
	}

	/** The cell and its (u, v) a direction falls in: {side, u, v}. */
	static double[] cell(double x, double y, double z) {
		for (int side = 0; side < 6; side++) {
			double[][] m = SIDES[side];
			// the inverse of a rotation is its transpose; the cell is the plane local y = -1
			double lx = m[0][0] * x + m[1][0] * y + m[2][0] * z;
			double ly = m[0][1] * x + m[1][1] * y + m[2][1] * z;
			double lz = m[0][2] * x + m[1][2] * y + m[2][2] * z;
			if (ly < 0 && Math.abs(lx) <= -ly && Math.abs(lz) <= -ly) {
				return new double[] {side, (lx / -ly + 1) / 2, (lz / -ly + 1) / 2};
			}
		}
		return new double[] {0, 0.5, 0.5};
	}

	// ---- painting -------------------------------------------------------------------------------

	/** One sky's colours; the noise is fixed, so a sky is the same every time. */
	private static final class Painter {
		final Kind kind;
		final Noise noise = new Noise(0x5A6E1L);
		/** Whether the sun and moon discs are painted (the preview) or left to sprites (the sky box). */
		boolean discs = true;

		Painter(Kind kind) {
			this.kind = kind;
		}

		/** The sun as {azimuth, elevation, radius in degrees, RGB}, or null. */
		double[] sunAt() {
			switch (kind) {
				case DAY:
					return new double[] {120, 38, 1.6, 0xFFFBEA};
				case SUNSET:
					return new double[] {270, 2.5, 2.4, 0xFFF1C2};
				default:
					return null;
			}
		}

		/** The moon as {azimuth, elevation, radius in degrees}, or null. */
		double[] moonAt() {
			return kind == Kind.NIGHT ? new double[] {150, 45, 1.7} : null;
		}

		/** Where the preview looks, degrees clockwise from north. */
		double lookAzimuth() {
			switch (kind) {
				case DAY:
					return 105;
				case SUNSET:
					return 270;
				case NIGHT:
					return 140;
				case AURORA:
					return 0;
				case PASTEL:
					return 200;
				default:
					return 300;
			}
		}

		int color(double x, double y, double z) {
			double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, y))));
			double az = Math.toDegrees(Math.atan2(x, -z));
			switch (kind) {
				case DAY:
					return day(x, y, z, el, az);
				case SUNSET:
					return sunset(x, y, z, el, az);
				case NIGHT:
					return night(x, y, z, el, az);
				case GALAXY:
					return galaxy(x, y, z, el);
				case AURORA:
					return aurora(x, y, z, el, az);
				default:
					return pastel(x, y, z, el, az);
			}
		}

		private int day(double x, double y, double z, double el, double az) {
			double[] c = gradient(el, new double[] {90, 45, 20, 6, 0, -4, -20, -90},
				new int[] {0x1D5BC9, 0x3478DB, 0x5C9BEA, 0x94C6F5, 0xC3E2FB, 0xA9C9E6, 0x6C8AA8, 0x3C5068});
			if (el > 1) {
				// thin high clouds, stretched along the wind
				double n = noise.fbm(x * 2.2, y * 7.0, z * 2.2, 5);
				double wisp = smooth(0.56, 0.78, n) * smooth(1, 10, el) * (1 - smooth(55, 80, el));
				c = mix(c, lab(0xF4F8FF), wisp * 0.75);
			}
			c = sun(c, x, y, z, 120, 38, 1.6, 0xFFFBEA, 0xFFF4D6, 0.55, 0.22);
			return rgb(c);
		}

		private int sunset(double x, double y, double z, double el, double az) {
			double[] stops = {90, 40, 18, 8, 3, 0, -5, -90};
			double[] warm = gradient(el, stops, new int[] {0x1E1747, 0x3E2A6E, 0x8A3C7A, 0xE0605E, 0xFF9450, 0xFFC56E, 0x6E3550, 0x1A0F24});
			double[] cool = gradient(el, stops, new int[] {0x1E1747, 0x2E2A66, 0x4A3B7C, 0x7C4F86, 0xB0688A, 0xD58A8E, 0x4A2A48, 0x1A0F24});
			double toSun = (1 - Math.cos(Math.toRadians(az - 270))) / 2; // 0 facing the sun
			double[] c = mix(warm, cool, Math.pow(toSun, 0.8));
			if (el > 0.5 && el < 22) {
				double n = noise.fbm(x * 3.0, y * 16.0, z * 3.0, 5);
				double streak = smooth(0.58, 0.8, n) * smooth(0.5, 4, el) * (1 - smooth(12, 22, el));
				c = mix(c, mix(lab(0xC4476A), lab(0x5E3A6E), toSun), streak * 0.8);
			}
			c = sun(c, x, y, z, 270, 2.5, 2.4, 0xFFF1C2, 0xFF9A4A, 0.85, 0.35);
			return rgb(c);
		}

		private int night(double x, double y, double z, double el, double az) {
			double[] c = gradient(el, new double[] {90, 40, 12, 2, 0, -6, -90},
				new int[] {0x02050E, 0x050B1C, 0x0B1830, 0x16274A, 0x1C2F55, 0x0B1426, 0x04070F});
			double band = milkyWay(x, y, z);
			c = add(c, lab(0x24335E), band * 0.55);
			c = moon(c, x, y, z, 150, 45, 1.7);
			return rgb(c);
		}

		private int galaxy(double x, double y, double z, double el) {
			double[] c = gradient(el, new double[] {90, 10, 0, -10, -90}, new int[] {0x04030C, 0x08061A, 0x1A1235, 0x0C0920, 0x04030C});
			double n1 = noise.fbm(x * 2.2 + 3.1, y * 2.2, z * 2.2, 6);
			double n2 = noise.fbm(x * 1.6, y * 1.6 + 7.7, z * 1.6, 4);
			double dust = smooth(0.52, 0.72, noise.fbm(x * 6 + 1.3, y * 6, z * 6, 4));
			double glow = Math.pow(smooth(0.42, 0.86, n1), 1.3) * (1 - 0.65 * dust);
			double[] hue = palette(n2, new int[] {0x3B1C8C, 0x8E2B9E, 0xC2347C, 0x2D4FD6, 0x1FA6C9});
			c = add(c, hue, glow * 1.05);
			c = add(c, lab(0x6E7BD8), Math.pow(glow, 3) * 0.6);
			return rgb(c);
		}

		private int aurora(double x, double y, double z, double el, double az) {
			double[] c = gradient(el, new double[] {90, 40, 12, 2, 0, -6, -90},
				new int[] {0x02050E, 0x041020, 0x07203A, 0x0C2E48, 0x0F3550, 0x07182A, 0x03070F});
			double a = Math.toRadians(az);
			double bottom = 14 + 5 * Math.sin(a * 2.3 + 0.7) + 3 * Math.sin(a * 5.1 + 2.0);
			double above = el - bottom;
			if (above > -3 && Math.abs(az) < 125) {
				double side = 1 - smooth(70, 125, Math.abs(az));
				double rise = smooth(-3, 1.5, above);
				// gone well before the zenith, where the curtains' rays would meet in a star
				double fade = Math.exp(-Math.max(0, above) / 13) * (1 - smooth(38, 60, el));
				double rays = 0.55 + 0.45 * noise.fbm(az * 0.35, 0.5, 2.0, 3);
				double v = side * rise * fade * rays;
				double t = Math.max(0, Math.min(1, above / 28));
				double[] col = palette(t, new int[] {0x3DFFA6, 0x2EE6B8, 0x35C2D8, 0x7C6BFF, 0xB066F0});
				c = add(c, col, v * 1.1);
			}
			c = add(c, lab(0x1E2C50), milkyWay(x, y, z) * 0.3);
			return rgb(c);
		}

		private int pastel(double x, double y, double z, double el, double az) {
			double[] c = gradient(el, new double[] {90, 50, 20, 6, 0, -10, -90},
				new int[] {0x7EA6FF, 0x9CB8FF, 0xC9C3FF, 0xF2C4EC, 0xFFD0E2, 0xE9B9D4, 0x8E86B8});
			if (el > 0) {
				double n = noise.fbm(x * 2.6 + 5, y * 4.0, z * 2.6, 5);
				double puff = smooth(0.5, 0.7, n) * smooth(0, 6, el) * (1 - smooth(45, 70, el));
				double shade = smooth(0.5, 0.75, noise.fbm(x * 2.6 + 5, y * 4.0 - 0.12, z * 2.6, 5));
				c = mix(c, mix(lab(0xFFF6FB), lab(0xF2C9E4), 1 - shade), puff * 0.85);
			}
			c = sun(c, x, y, z, 200, 22, 0, 0xFFF6E0, 0xFFF0E8, 0.45, 0.3);
			return rgb(c);
		}

		/** The Milky Way's glow (0 to 1): a band around the sky, uneven. */
		private double milkyWay(double x, double y, double z) {
			double d = x * 0.31 + y * 0.62 + z * 0.72;
			double band = Math.exp(-d * d / 0.05);
			return band * (0.35 + 0.65 * smooth(0.35, 0.75, noise.fbm(x * 4, y * 4, z * 4, 5)));
		}

		/** A sun of {@code radius} degrees (0: glow only) at an azimuth and elevation, with its glow. */
		private double[] sun(double[] c, double x, double y, double z, double az, double el, double radius,
			int disc, int glow, double near, double far) {
			double d = angle(x, y, z, az, el);
			double g = near * Math.exp(-d / 4) + far * Math.exp(-d / 20);
			c = add(c, lab(glow), g);
			if (radius > 0 && discs) {
				c = mix(c, lab(disc), 1 - smooth(radius - 0.35, radius + 0.35, d));
			}
			return c;
		}

		private double[] moon(double[] c, double x, double y, double z, double az, double el, double radius) {
			double d = angle(x, y, z, az, el);
			c = add(c, lab(0x6F86C0), 0.32 * Math.exp(-d / 5) + 0.12 * Math.exp(-d / 18));
			double inside = discs ? 1 - smooth(radius - 0.25, radius + 0.25, d) : 0;
			if (inside > 0) {
				double maria = noise.fbm(x * 90, y * 90, z * 90, 3);
				c = mix(c, lab(maria > 0.55 ? 0xC9D0E4 : 0xEEF2FF), inside);
			}
			return c;
		}

		void previewStars(int[] img, int w, int h, double yaw, double pitch, double f) {
			int count = starCount();
			if (count == 0) {
				return;
			}
			Random r = new Random(77);
			for (int i = 0; i < count; i++) {
				double[] d = randomDirection(r);
				double bright = star(r, d);
				int tint = starTint(r);
				r.nextDouble();
				if (bright <= 0) {
					continue;
				}
				// undo the preview camera: turn back by yaw, then by pitch, then project
				double cx = d[0] * Math.cos(yaw) + d[2] * Math.sin(yaw);
				double cz = -d[0] * Math.sin(yaw) + d[2] * Math.cos(yaw);
				double fy = d[1] * Math.cos(pitch) - (-cz) * Math.sin(pitch);
				double fz = d[1] * Math.sin(pitch) + (-cz) * Math.cos(pitch);
				if (fz <= 0.05) {
					continue;
				}
				double sx = cx / fz * f;
				double sy = fy / fz * f;
				double px = (sx + 1) / 2 * w;
				double py = (1 - sy * w / h) / 2 * h;
				splat(img, w, h, px, py, 0.6 + bright * 0.4, tint, bright, 0, 0, Math.max(w, h));
			}
		}

		private int starCount() {
			switch (kind) {
				case NIGHT:
					return 5200;
				case GALAXY:
					return 7000;
				case AURORA:
					return 3600;
				default:
					return 0;
			}
		}

		/** A star's brightness in direction {@code d} (0: none), more of them in the Milky Way. */
		private double star(Random r, double[] d) {
			double roll = r.nextDouble();
			double b = Math.pow(r.nextDouble(), 3.2);
			if (d[1] < -0.05) {
				return 0;
			}
			double way = kind == Kind.GALAXY ? 0.6 : milkyWay(d[0], d[1], d[2]);
			if (roll > 0.35 + 0.65 * way && roll > 0.55) {
				return 0;
			}
			double horizonFade = smooth(-0.05, 0.25, d[1]);
			return (0.25 + 0.75 * b) * horizonFade;
		}

		private static int starTint(Random r) {
			double t = r.nextDouble();
			return t < 0.15 ? 0xFFD9B0 : t < 0.35 ? 0xC9D8FF : 0xFFFFFF;
		}

		private static double[] randomDirection(Random r) {
			double z = r.nextDouble() * 2 - 1;
			double a = r.nextDouble() * Math.PI * 2;
			double s = Math.sqrt(1 - z * z);
			return new double[] {s * Math.cos(a), z, s * Math.sin(a)};
		}

		/** Adds a soft point of light; stays inside its own face so it never bleeds into the next cell. */
		private static void splat(int[] img, int w, int h, double px, double py, double radius, int tint, double bright,
			int fx, int fy, int face) {
			int r0 = (int) Math.ceil(radius * 2);
			int cx = (int) px;
			int cy = (int) py;
			for (int y = cy - r0; y <= cy + r0; y++) {
				for (int x = cx - r0; x <= cx + r0; x++) {
					if (x < fx || y < fy || x >= fx + face || y >= fy + face || x >= w || y >= h) {
						continue;
					}
					double dx = x + 0.5 - px;
					double dy = y + 0.5 - py;
					double a = bright * Math.exp(-(dx * dx + dy * dy) / (radius * radius));
					if (a < 0.01) {
						continue;
					}
					int c = img[y * w + x];
					int rr = Math.min(255, (int) ((c >> 16 & 0xFF) + (tint >> 16 & 0xFF) * a));
					int gg = Math.min(255, (int) ((c >> 8 & 0xFF) + (tint >> 8 & 0xFF) * a));
					int bb = Math.min(255, (int) ((c & 0xFF) + (tint & 0xFF) * a));
					img[y * w + x] = 0xFF000000 | rr << 16 | gg << 8 | bb;
				}
			}
		}
	}

	// ---- colour and maths -----------------------------------------------------------------------

	/** Angle in degrees between a direction and the one at azimuth {@code az}, elevation {@code el}. */
	private static double angle(double x, double y, double z, double az, double el) {
		double a = Math.toRadians(az);
		double e = Math.toRadians(el);
		double dot = x * Math.sin(a) * Math.cos(e) + y * Math.sin(e) + z * -Math.cos(a) * Math.cos(e);
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
	}

	/** The colour at elevation {@code el} between stops (elevations from high to low), in OKLab. */
	private static double[] gradient(double el, double[] at, int[] colors) {
		if (el >= at[0]) {
			return lab(colors[0]);
		}
		for (int i = 1; i < at.length; i++) {
			if (el >= at[i]) {
				double t = (at[i - 1] - el) / (at[i - 1] - at[i]);
				return mix(lab(colors[i - 1]), lab(colors[i]), t * t * (3 - 2 * t));
			}
		}
		return lab(colors[colors.length - 1]);
	}

	/** A colour along evenly spaced stops, for {@code t} from 0 to 1. */
	private static double[] palette(double t, int[] colors) {
		double s = Math.max(0, Math.min(1, t)) * (colors.length - 1);
		int i = Math.min(colors.length - 2, (int) s);
		return mix(lab(colors[i]), lab(colors[i + 1]), s - i);
	}

	private static double[] lab(int rgb) {
		return BlockArt.Oklab.fromRgb(rgb);
	}

	private static int rgb(double[] lab) {
		return BlockArt.Oklab.toRgb(lab);
	}

	private static double[] mix(double[] a, double[] b, double t) {
		return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
	}

	/** Light added: the sum in linear RGB, back in OKLab. */
	private static double[] add(double[] base, double[] light, double amount) {
		if (amount <= 0) {
			return base;
		}
		int a = rgb(base);
		int b = rgb(light);
		double[] out = new double[3];
		for (int i = 0; i < 3; i++) {
			int shift = 16 - 8 * i;
			double la = Math.pow((a >> shift & 0xFF) / 255.0, 2.2);
			double lb = Math.pow((b >> shift & 0xFF) / 255.0, 2.2);
			out[i] = Math.pow(Math.min(1, la + lb * amount), 1 / 2.2);
		}
		return lab((int) Math.round(out[0] * 255) << 16 | (int) Math.round(out[1] * 255) << 8 | (int) Math.round(out[2] * 255));
	}

	private static double smooth(double e0, double e1, double v) {
		double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}

	/** Rotation by {@code deg} degrees around an axis, as glRotate. */
	static double[][] rotation(double deg, double x, double y, double z) {
		double n = Math.sqrt(x * x + y * y + z * z);
		x /= n;
		y /= n;
		z /= n;
		double a = Math.toRadians(deg);
		double c = Math.cos(a);
		double s = Math.sin(a);
		double t = 1 - c;
		return new double[][] {
			{t * x * x + c, t * x * y - s * z, t * x * z + s * y},
			{t * x * y + s * z, t * y * y + c, t * y * z - s * x},
			{t * x * z - s * y, t * y * z + s * x, t * z * z + c}};
	}

	static double[][] mul(double[][] a, double[][] b) {
		double[][] r = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
			}
		}
		return r;
	}

	/** Smooth 3D value noise and its sum over octaves (0 to about 1). */
	static final class Noise {
		private final int[] perm = new int[512];

		Noise(long seed) {
			Random r = new Random(seed);
			int[] p = new int[256];
			for (int i = 0; i < 256; i++) {
				p[i] = i;
			}
			for (int i = 255; i > 0; i--) {
				int j = r.nextInt(i + 1);
				int t = p[i];
				p[i] = p[j];
				p[j] = t;
			}
			for (int i = 0; i < 512; i++) {
				perm[i] = p[i & 255];
			}
		}

		double value(double x, double y, double z) {
			int xi = (int) Math.floor(x);
			int yi = (int) Math.floor(y);
			int zi = (int) Math.floor(z);
			double xf = x - xi;
			double yf = y - yi;
			double zf = z - zi;
			double u = xf * xf * (3 - 2 * xf);
			double v = yf * yf * (3 - 2 * yf);
			double w = zf * zf * (3 - 2 * zf);
			int x0 = xi & 255;
			int y0 = yi & 255;
			int z0 = zi & 255;
			double a = lerp(u, h(x0, y0, z0), h(x0 + 1, y0, z0));
			double b = lerp(u, h(x0, y0 + 1, z0), h(x0 + 1, y0 + 1, z0));
			double c = lerp(u, h(x0, y0, z0 + 1), h(x0 + 1, y0, z0 + 1));
			double d = lerp(u, h(x0, y0 + 1, z0 + 1), h(x0 + 1, y0 + 1, z0 + 1));
			return lerp(w, lerp(v, a, b), lerp(v, c, d));
		}

		double fbm(double x, double y, double z, int octaves) {
			double sum = 0;
			double amp = 0.5;
			double total = 0;
			for (int i = 0; i < octaves; i++) {
				sum += value(x, y, z) * amp;
				total += amp;
				x = x * 2.03 + 17.1;
				y = y * 2.03 + 3.7;
				z = z * 2.03 + 9.2;
				amp *= 0.5;
			}
			return sum / total;
		}

		private double h(int x, int y, int z) {
			return perm[perm[perm[x & 255] + (y & 255)] + (z & 255)] / 255.0;
		}

		private static double lerp(double t, double a, double b) {
			return a + (b - a) * t;
		}
	}
}
