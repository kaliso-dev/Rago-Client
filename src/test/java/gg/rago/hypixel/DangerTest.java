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
		assertEquals("§2", Danger.fkdrColor(9.9));
		assertEquals("§c", Danger.fkdrColor(10));
		assertEquals("§4", Danger.fkdrColor(20));
		assertEquals("§4", Danger.fkdrColor(150));
		assertEquals("§7", Danger.color(1));
		assertEquals("§a", Danger.color(2));
		assertEquals("§2", Danger.color(3));
		assertEquals("§c", Danger.color(4));
		assertEquals("§4", Danger.color(5));
	}

	@Test
	void levelsReadAsTiers() {
		assertEquals('D', Danger.tier(1));
		assertEquals('C', Danger.tier(2));
		assertEquals('B', Danger.tier(3));
		assertEquals('A', Danger.tier(4));
		assertEquals('S', Danger.tier(5));
	}
}
