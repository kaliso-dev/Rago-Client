package gg.rago.optimize;

import net.minecraft.block.material.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FogTest {
	@Test
	void onlyTheDistanceHazeGoes() {
		assertFalse(Optimizations.keepsFog(Material.air, false));
		assertTrue(Optimizations.keepsFog(Material.air, true));
		assertTrue(Optimizations.keepsFog(Material.water, false));
		assertTrue(Optimizations.keepsFog(Material.lava, false));
	}
}
