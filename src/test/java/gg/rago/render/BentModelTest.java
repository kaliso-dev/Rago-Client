package gg.rago.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BentModelTest {
	private static final float PI = (float) Math.PI;

	@Test
	void straightChangesNothing() {
		BentModel.Frame f = new BentModel.Frame(0, 0, 6, new BentModel.Bend(false).set(0, 0));
		assertArrayEquals(new float[] {1, 11, -2}, f.apply(1, 11, -2, new float[3]), 1e-4f);
	}

	@Test
	void theNearHalfStaysAndTheFarEndSwingsAroundTheKnee() {
		// a leg 12 pixels long, bent a quarter turn backward (+z) at its middle
		BentModel.Frame f = new BentModel.Frame(0, 0, 6, new BentModel.Bend(false).set(PI / 2, PI));
		assertArrayEquals(new float[] {1, 3, -2}, f.apply(1, 3, -2, new float[3]), 1e-4f);
		float r = BentModel.ZONE / (PI / 2);
		// the centre of the foot: down the curve, then 4 pixels straight back
		assertArrayEquals(new float[] {0, 4 + r, r + 4}, f.apply(0, 12, 0, new float[3]), 1e-3f);
		// the limb keeps its thickness: the front of the foot is 2 pixels lower than its centre
		assertArrayEquals(new float[] {0, 4 + r + 2, r + 4}, f.apply(0, 12, -2, new float[3]), 1e-3f);
	}

	@Test
	void aWaistBendMovesTheTopAndKeepsTheHips() {
		BentModel.Frame f = new BentModel.Frame(0, 0, 6, new BentModel.Bend(true).set(0.3f, PI / 2));
		assertArrayEquals(new float[] {3, 12, 1}, f.apply(3, 12, 1, new float[3]), 1e-4f);
		float[] neck = f.apply(0, 0, 0, new float[3]);
		// the chest leans toward +x, and the neck comes down a little
		org.junit.jupiter.api.Assertions.assertTrue(neck[0] > 1.5f && neck[1] > 0 && Math.abs(neck[2]) < 1e-4f,
			java.util.Arrays.toString(neck));
	}
}
