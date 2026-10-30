package gg.rago.modules;

import com.google.gson.JsonParser;
import gg.rago.hypixel.HypixelApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedwarsStatsTest {
	private static HypixelApi.Stats player(int star, int finalKills, int finalDeaths) {
		return HypixelApi.parse(new JsonParser().parse("{\"player\":{\"achievements\":{\"bedwars_level\":" + star
			+ "},\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":" + finalKills + ",\"final_deaths_bedwars\":" + finalDeaths + "}}}}"), 0);
	}

	@Test
	void strongerPlayersComeFirst() {
		double elite = BedwarsStats.strength(player(1200, 9000, 400), false, false);
		double good = BedwarsStats.strength(player(400, 3000, 500), false, false);
		double sameTierMoreFinals = BedwarsStats.strength(player(400, 3500, 500), false, false);
		double newcomer = BedwarsStats.strength(player(20, 30, 90), false, false);
		assertTrue(elite > good);
		assertTrue(sameTierMoreFinals > good, "the FKDR decides within a tier");
		assertTrue(good > newcomer);
		assertTrue(newcomer > 0);
	}

	@Test
	void snipersThenNicksLeadAndUnknownPlayersTrail() {
		double elite = BedwarsStats.strength(player(3000, 50000, 1000), false, false);
		double nick = BedwarsStats.strength(null, true, false);
		double sniper = BedwarsStats.strength(player(50, 120, 10), false, true);
		assertTrue(sniper > nick);
		assertTrue(nick > elite);
		assertEquals(-1, BedwarsStats.strength(null, false, false), "stats not in yet");
	}
}
