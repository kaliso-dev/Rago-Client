package gg.rago.render;

import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LabelAxesTest {
	/** Rotation about x then y (degrees), as glRotate, column-major like OpenGL keeps a matrix. */
	private static FloatBuffer camera(double pitch, double yaw) {
		double p = Math.toRadians(pitch);
		double y = Math.toRadians(yaw);
		double[][] rx = {{1, 0, 0}, {0, Math.cos(p), -Math.sin(p)}, {0, Math.sin(p), Math.cos(p)}};
		double[][] ry = {{Math.cos(y), 0, Math.sin(y)}, {0, 1, 0}, {-Math.sin(y), 0, Math.cos(y)}};
		FloatBuffer m = FloatBuffer.allocate(16);
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				double v = 0;
				for (int k = 0; k < 3; k++) {
					v += rx[r][k] * ry[k][c];
				}
				m.put(c * 4 + r, (float) v);
			}
		}
		m.put(15, 1);
		return m;
	}

	private static float[] view(float viewYaw, float viewPitch) {
		float[] out = new float[6];
		LabelBatch.viewAxes(viewYaw, viewPitch, 1, out);
		return out;
	}

	private static float[] screen(FloatBuffer camera) {
		float[] out = new float[6];
		LabelBatch.screenAxes(camera, 1, out);
		return out;
	}

	@Test
	void behindThePlayerALabelLiesFlatOnTheScreen() {
		// the game's camera behind the player: pitch, then yaw + 180
		for (float pitch : new float[] {-90, -60, -20, 0, 35, 70, 90}) {
			for (float yaw : new float[] {0, 45, 130, -100}) {
				float[] turned = view(yaw, WorldDraw.labelPitch(pitch, 1));
				assertArrayEquals(screen(camera(pitch, yaw + 180)), turned, 1e-5f, "pitch " + pitch + ", yaw " + yaw);
			}
		}
	}

	@Test
	void inFrontOfThePlayerTooWhereVanillaTipsItTheWrongWay() {
		// F5 twice: the camera looks back at the player, its pitch the other way (RenderManager adds 180 to the yaw)
		for (float pitch : new float[] {-90, -60, -20, 0, 35, 70, 90}) {
			for (float yaw : new float[] {0, 45, 130, -100}) {
				float[] turned = view(yaw + 180, WorldDraw.labelPitch(pitch, 2));
				assertArrayEquals(screen(camera(-pitch, yaw)), turned, 1e-5f, "pitch " + pitch + ", yaw " + yaw);
			}
		}
	}

	@Test
	void thePitchIsTurnedRoundOnlyInTheFrontView() {
		assertEquals(-40, WorldDraw.labelPitch(40, 2), 0);
		assertEquals(40, WorldDraw.labelPitch(40, 1), 0);
		assertEquals(40, WorldDraw.labelPitch(40, 0), 0);
	}
}
