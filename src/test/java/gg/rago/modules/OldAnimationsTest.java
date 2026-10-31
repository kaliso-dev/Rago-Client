package gg.rago.modules;

import net.minecraft.util.MathHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OldAnimationsTest {
	/** The way of a movement as onUpdate works it out: atan2(dz, dx) in degrees, minus 90. */
	private static float moving(double dx, double dz) {
		return (float) MathHelper.atan2(dz, dx) * 180.0F / 3.1415927F - 90.0F;
	}

	@Test
	void walkingBackwardsTheBodyFacesWhereYouLook() {
		for (float look : new float[] {0, 90, 170, -135, 400}) {
			double rad = Math.toRadians(look);
			// where you look is (-sin, cos); backwards is the opposite
			float back = moving(Math.sin(rad), -Math.cos(rad));
			float body = OldAnimations.backwardsYaw(back, look);
			assertEquals(0, MathHelper.wrapAngleTo180_float(body - look), 0.01f, "look " + look);
		}
	}

	@Test
	void walkingForwardsOrSidewaysIsUnchanged() {
		float look = 30;
		double rad = Math.toRadians(look);
		float forward = moving(-Math.sin(rad), Math.cos(rad));
		float side = moving(Math.cos(rad), Math.sin(rad));
		assertEquals(forward, OldAnimations.backwardsYaw(forward, look));
		assertEquals(side, OldAnimations.backwardsYaw(side, look));
	}
}
