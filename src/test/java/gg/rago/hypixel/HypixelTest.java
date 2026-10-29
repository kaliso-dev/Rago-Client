package gg.rago.hypixel;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypixelTest {
	@Test
	void networkLevelFormula() {
		assertEquals(1.0, HypixelApi.networkLevel(0), 1e-9);
		// level 2 needs 10,000 exp, level 3 another 12,500
		assertEquals(2.0, HypixelApi.networkLevel(10_000), 1e-9);
		assertEquals(3.0, HypixelApi.networkLevel(22_500), 1e-9);
	}

	@Test
	void parsesLevelAndStar() {
		HypixelApi.Stats s = HypixelApi.parse(new JsonParser().parse(
			"{\"success\":true,\"player\":{\"networkExp\":22500,\"achievements\":{\"bedwars_level\":312}}}"), 0);
		assertEquals(3.0, s.level, 1e-9);
		assertEquals(312, s.star);
		HypixelApi.Stats never = HypixelApi.parse(new JsonParser().parse("{\"success\":true,\"player\":null}"), 0);
		assertEquals(-1, never.star);
		assertTrue(never.level < 1);
	}

	@Test
	void keyFormatAndStorage(@TempDir Path dir) throws Exception {
		assertFalse(HypixelApi.isKey("hello"));
		String key = UUID.randomUUID().toString();
		assertTrue(HypixelApi.isKey(key));
		HypixelApi api = new HypixelApi(dir);
		assertEquals(HypixelApi.Status.NO_KEY, api.status());
		api.setKey(key);
		assertEquals(key, new String(Files.readAllBytes(dir.resolve("rago-hypixel-key.txt")), "UTF-8"));
		assertEquals(HypixelApi.Status.OK, new HypixelApi(dir).status());
	}

	@Test
	void onlyVersion4UuidsAreRealPlayers() {
		assertTrue(Hypixel.realPlayer(UUID.randomUUID()));
		assertFalse(Hypixel.realPlayer(UUID.fromString("2a6f1d5e-0d0b-2c3a-9e1d-1a2b3c4d5e6f")));
		assertFalse(Hypixel.realPlayer(null));
	}

	@Test
	void bedwarsStarColours() {
		assertEquals("§7[5✫]", Hypixel.bedwarsStar(5));
		assertEquals("§6[250✫]", Hypixel.bedwarsStar(250));
		assertEquals("§5[999✫]", Hypixel.bedwarsStar(999));
		assertTrue(Hypixel.bedwarsStar(1200).startsWith("§c["));
	}

	@Test
	void parsesBedwarsStats() {
		HypixelApi.Stats s = HypixelApi.parse(new JsonParser().parse("{\"player\":{\"achievements\":{\"bedwars_level\":79},"
			+ "\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":120,\"final_deaths_bedwars\":200,\"wins_bedwars\":30,"
			+ "\"losses_bedwars\":60,\"beds_broken_bedwars\":44}}}}"), 0);
		assertTrue(s.found);
		assertEquals(0.6, s.fkdr(), 1e-9);
		assertEquals(0.5, s.wlr(), 1e-9);
		assertEquals(44, s.bedsBroken);
		assertEquals(-1, s.winstreak);
		assertFalse(HypixelApi.parse(new JsonParser().parse("{\"success\":true,\"player\":null}"), 0).found);
		HypixelApi.Stats none = HypixelApi.parse(new JsonParser().parse("{\"player\":{}}"), 0);
		assertTrue(none.found);
		assertEquals(0, none.fkdr(), 1e-9);
	}

	@Test
	void dangerLevels() {
		assertEquals(1, Danger.level(0.6, 79));
		assertEquals(2, Danger.level(1.5, 150));
		assertEquals(3, Danger.level(3, 400));
		assertEquals(4, Danger.level(6, 300));
		assertEquals(5, Danger.level(12, 1200));
		// a high ratio weighs more than a high star
		assertTrue(Danger.level(10, 50) > Danger.level(0.5, 1500));
		assertEquals("§c", Danger.color(5));
		assertEquals("0.6", Danger.ratio(0.6));
		assertEquals("150", Danger.ratio(150.4));
	}
}
