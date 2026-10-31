package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ThirdPersonBlockTest {
	/** A 4x4 matrix that each step multiplies on the right, as OpenGL's matrix stack does. */
	private static final class Matrix implements OldAnimations.Transform {
		double[][] m = {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};

		private void times(double[][] b) {
			double[][] r = new double[4][4];
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					for (int k = 0; k < 4; k++) {
						r[i][j] += m[i][k] * b[k][j];
					}
				}
			}
			m = r;
		}

		@Override
		public void translate(float x, float y, float z) {
			times(new double[][] {{1, 0, 0, x}, {0, 1, 0, y}, {0, 0, 1, z}, {0, 0, 0, 1}});
		}

		@Override
		public void rotate(float degrees, float x, float y, float z) {
			double a = Math.toRadians(degrees);
			double c = Math.cos(a);
			double s = Math.sin(a);
			times(new double[][] {
				{x * x * (1 - c) + c, x * y * (1 - c) - z * s, x * z * (1 - c) + y * s, 0},
				{y * x * (1 - c) + z * s, y * y * (1 - c) + c, y * z * (1 - c) - x * s, 0},
				{x * z * (1 - c) - y * s, y * z * (1 - c) + x * s, z * z * (1 - c) + c, 0},
				{0, 0, 0, 1}});
		}

		@Override
		public void scale(float x, float y, float z) {
			times(new double[][] {{x, 0, 0, 0}, {0, y, 0, 0}, {0, 0, z, 0}, {0, 0, 0, 1}});
		}

		float[] apply(double x, double y, double z) {
			float[] p = new float[3];
			for (int i = 0; i < 3; i++) {
				p[i] = (float) (m[i][0] * x + m[i][1] * y + m[i][2] * z + m[i][3]);
			}
			return p;
		}
	}

	/** Where 1.7 put the texel (u, v) of a blocking sword, from the hand (1.7.10's RenderPlayer and ItemRenderer). */
	private static float[] seventeen(double u, double v) {
		Matrix t = new Matrix();
		t.translate(0.05f, 0, -0.1f);
		t.rotate(-50, 0, 1, 0);
		t.rotate(-10, 1, 0, 0);
		t.rotate(-60, 0, 0, 1);
		t.translate(0, 0.1875f, 0);
		t.scale(0.625f, -0.625f, 0.625f);
		t.rotate(-100, 1, 0, 0);
		t.rotate(45, 0, 1, 0);
		t.translate(0, -0.3f, 0);
		t.scale(1.5f, 1.5f, 1.5f);
		t.rotate(50, 0, 1, 0);
		t.rotate(335, 0, 0, 1);
		t.translate(-0.9375f, -0.0625f, 0);
		// 1.7's flat item: x = 0 at the picture's right edge, y = 0 at its bottom, the middle of its depth 1/32 behind
		return t.apply(1 - u, 1 - v, -1 / 32.0);
	}

	/** Where 1.8 puts the same texel after OldAnimations' steps and the game's own (RenderItem, the sword's display). */
	private static float[] eighteen(double u, double v) {
		Matrix t = new Matrix();
		OldAnimations.block17(t);
		t.scale(2, 2, 2);
		t.translate(0, 1.25f / 16, -3.5f / 16);
		t.rotate(90, 0, 1, 0);
		t.rotate(0, 1, 0, 0);
		t.rotate(-35, 0, 0, 1);
		t.scale(0.85f, 0.85f, 0.85f);
		t.scale(0.5f, 0.5f, 0.5f);
		t.translate(-0.5f, -0.5f, -0.5f);
		// 1.8's generated model: x = u, y = 1 - v, the middle of its depth at 0.5
		return t.apply(u, 1 - v, 0.5);
	}

	@Test
	void theSwordIsWhere17PutIt() {
		double[][] texels = {{0.1, 0.9}, {0.9, 0.1}, {0.5, 0.5}, {0, 0}, {1, 1}, {0.3, 0.7}};
		for (double[] t : texels) {
			assertArrayEquals(seventeen(t[0], t[1]), eighteen(t[0], t[1]), 1e-5f, "texel " + t[0] + ", " + t[1]);
		}
	}
}
