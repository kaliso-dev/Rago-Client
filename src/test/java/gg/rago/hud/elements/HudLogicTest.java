package gg.rago.hud.elements;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudLogicTest {
	@Test
	void facingMatchesVanillaYaw() {
		assertEquals("south", CoordsHud.facingKey(0));
		assertEquals("west", CoordsHud.facingKey(90));
		assertEquals("north", CoordsHud.facingKey(180));
		assertEquals("east", CoordsHud.facingKey(270));
		assertEquals("east", CoordsHud.facingKey(-90));
		assertEquals("south", CoordsHud.facingKey(44));
		assertEquals("west", CoordsHud.facingKey(46));
		assertEquals("north", CoordsHud.facingKey(-180 - 720));
	}

	@Test
	void durabilityColourThresholds() {
		int normal = 0xFFFFFFFF;
		assertEquals(0xFF55FF55, ArmorHud.durabilityColor(300, 500, normal));
		assertEquals(0xFFFFFF55, ArmorHud.durabilityColor(200, 500, normal));
		assertEquals(0xFFFF5555, ArmorHud.durabilityColor(100, 500, normal));
		assertEquals(normal, ArmorHud.durabilityColor(0, 0, normal));
	}

	@Test
	void mouseButtonNames() {
		assertEquals("LMB", KeystrokesHud.keyName(-100));
		assertEquals("RMB", KeystrokesHud.keyName(-99));
		assertEquals("M4", KeystrokesHud.keyName(-97));
	}
}
