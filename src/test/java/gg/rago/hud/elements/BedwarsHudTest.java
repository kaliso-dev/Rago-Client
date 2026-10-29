package gg.rago.hud.elements;

import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BedwarsHudTest {
	@Test
	void mapNameFromTheLobbyScoreboard() {
		assertEquals("Lighthouse", HeightLimitHud.mapFromLine("Map: Lighthouse"));
		assertEquals("Ice Temple", HeightLimitHud.mapFromLine(" map: Ice Temple "));
		assertNull(HeightLimitHud.mapFromLine("Mode: 4v4v4v4"));
		assertNull(HeightLimitHud.mapFromLine("Map:"));
	}

	@Test
	void serverKeyIgnoresSubdomainAndPort() {
		assertEquals("hypixel.net", HeightLimitHud.serverKey(new ServerData("H", "mc.hypixel.net", false)));
		assertEquals("hypixel.net", HeightLimitHud.serverKey(new ServerData("H", "hypixel.net:25565", false)));
		assertEquals("unknown", HeightLimitHud.serverKey(null));
	}

	@Test
	void compassPoints() {
		assertEquals("S", DirectionHud.point(0));
		assertEquals("W", DirectionHud.point(90));
		assertEquals("N", DirectionHud.point(180));
		assertEquals("NE", DirectionHud.point(-135));
		assertEquals(0, DirectionHud.bearing(180));
		assertEquals(90, DirectionHud.bearing(-90));
	}

	@Test
	void packNames() {
		assertEquals("Rago 16x", PackDisplayHud.clean("§aRago_16x.zip"));
		assertEquals("Default", PackDisplayHud.clean("Default"));
	}
}
