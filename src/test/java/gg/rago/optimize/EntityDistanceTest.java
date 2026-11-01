package gg.rago.optimize;

import net.minecraft.util.AxisAlignedBB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How far each kind of entity is drawn once its distance is chosen. */
class EntityDistanceTest {
	/** A villager-sized box: 0.6 by 1.95, an average edge of 1.05 blocks. */
	private static final AxisAlignedBB VILLAGER = new AxisAlignedBB(0, 0, 0, 0.6, 1.95, 0.6);

	@Test
	void aDistanceCanBeFartherThanTheGames() {
		// the game draws a villager up to about 67 blocks (weight 1); 200 blocks is three times that
		double w = Optimizations.weight(200, VILLAGER, 1);
		assertEquals(200, w * VILLAGER.getAverageEdgeLength() * 64, 1e-9);
	}

	@Test
	void orNearer() {
		double w = Optimizations.weight(32, VILLAGER, 1);
		assertEquals(32, w * VILLAGER.getAverageEdgeLength() * 64, 1e-9);
	}

	@Test
	void unlimitedIsNeverNearerThanTheGame() {
		// other players: the game's weight is 10, 640 blocks for a one-block box, more than 512
		AxisAlignedBB player = new AxisAlignedBB(0, 0, 0, 0.6, 1.8, 0.6);
		assertEquals(10, Optimizations.weight(Optimizations.NO_LIMIT, player, 10), 1e-9);
		// a villager: 512 blocks, farther than the game's 67
		assertEquals(512, Optimizations.weight(Optimizations.NO_LIMIT, VILLAGER, 1) * VILLAGER.getAverageEdgeLength() * 64, 1e-9);
	}
}
