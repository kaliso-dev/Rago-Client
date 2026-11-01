package gg.rago.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPlaytimeTest {
	@Test
	void everyHypixelAddressIsOneServer() {
		assertEquals("mc.hypixel.net", ServerPlaytime.key("mc.hypixel.net"));
		assertEquals("mc.hypixel.net", ServerPlaytime.key("Hypixel.net:25565"));
		assertEquals("mc.hypixel.net", ServerPlaytime.key(" stuck.hypixel.net. "));
	}

	@Test
	void otherAddressesKeepTheirRegionAndPort() {
		assertEquals("eu.minemen.club", ServerPlaytime.key("EU.Minemen.Club"));
		assertEquals("play.example.net:25566", ServerPlaytime.key("play.example.net:25566"));
	}

	@Test
	void knownServersHaveTheirNames() {
		assertEquals("Hypixel", ServerPlaytime.name("mc.hypixel.net"));
		assertEquals("Minemen Club", ServerPlaytime.name("eu.minemen.club"));
		assertEquals("play.example.net", ServerPlaytime.name("play.example.net"));
	}

	@Test
	void hypixelUntilAnotherServerIsPlayed() {
		assertEquals(ServerPlaytime.DEFAULT_ADDRESS, ServerPlaytime.favourite());
	}
}
