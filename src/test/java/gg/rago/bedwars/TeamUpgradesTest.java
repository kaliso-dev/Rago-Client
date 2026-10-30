package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamUpgradesTest {
	@Test
	void countsPurchasedUpgrades() {
		TeamUpgrades u = new TeamUpgrades();
		assertTrue(u.chat("Steve purchased Sharpened Swords"));
		assertTrue(u.chat("Steve purchased Reinforced Armor II"));
		assertTrue(u.chat("Alex purchased Maniac Miner I"));
		assertTrue(u.chat("Alex purchased Golden Forge"));
		assertTrue(u.chat("You purchased Heal Pool"));
		assertTrue(u.chat("Alex purchased Cushioned Boots I"));
		assertEquals(1, u.sharpness);
		assertEquals(2, u.protection);
		assertEquals(1, u.haste);
		assertEquals(2, u.forge);
		assertTrue(u.healPool);
		assertEquals(1, u.featherFalling);
		assertTrue(u.chat("Alex purchased Molten Forge"));
		assertEquals(4, u.forge);
	}

	@Test
	void ignoresShopItemsAndPlayerChat() {
		TeamUpgrades u = new TeamUpgrades();
		assertFalse(u.chat("You purchased Iron Sword"));
		assertFalse(u.chat("You purchased Wool"));
		assertFalse(u.chat("[VIP] Steve: I purchased Heal Pool"));
		assertFalse(u.chat("Steve was killed by Alex."));
		assertFalse(u.healPool);
		assertNull(TeamUpgrades.purchase("Steve: purchased Alarm Trap"));
	}

	@Test
	void queuesTrapsAndSetsOffTheFirst() {
		TeamUpgrades u = new TeamUpgrades();
		u.chat("Steve purchased It's a Trap!");
		u.chat("Steve purchased Alarm Trap");
		u.chat("Alex a acheté Piège contre-attaque");
		u.chat("Alex purchased Miner Fatigue Trap");
		assertEquals(Arrays.asList(TeamUpgrades.Trap.BLINDNESS, TeamUpgrades.Trap.REVEAL, TeamUpgrades.Trap.COUNTER_OFFENSIVE), u.traps());
		u.triggered(null);
		assertEquals(Arrays.asList(TeamUpgrades.Trap.REVEAL, TeamUpgrades.Trap.COUNTER_OFFENSIVE), u.traps());
		u.triggered("Your Counter-Offensive Trap has been set off!");
		assertEquals(Arrays.asList(TeamUpgrades.Trap.REVEAL), u.traps());
		u.triggered("TRAP TRIGGERED!");
		assertTrue(u.traps().isEmpty());
		u.triggered(null);
		assertTrue(u.traps().isEmpty());
	}

	@Test
	void gearLevelsNeverGoDown() {
		TeamUpgrades u = new TeamUpgrades();
		u.seen(1, 3, 0, 2);
		u.seen(0, 0, 0, 0);
		assertEquals(1, u.sharpness);
		assertEquals(3, u.protection);
		assertEquals(2, u.haste);
		u.reset();
		assertEquals(0, u.protection);
	}
}
