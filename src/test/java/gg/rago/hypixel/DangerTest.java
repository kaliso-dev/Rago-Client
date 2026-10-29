package gg.rago.hypixel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DangerTest {
	@Test
	void fkdrColoursFollowTheSteps() {
		assertEquals("§7", Danger.fkdrColor(0.99));
		assertEquals("§a", Danger.fkdrColor(1));
		assertEquals("§a", Danger.fkdrColor(4.9));
		assertEquals("§2", Danger.fkdrColor(5));
		assertEquals("§e", Danger.fkdrColor(8));
		assertEquals("§6", Danger.fkdrColor(15));
		assertEquals("§c", Danger.fkdrColor(20));
		assertEquals("§c", Danger.fkdrColor(150));
	}
}
