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
		double elite = BedwarsStats.strength(player(1200, 9000, 400), false, false, false);
		double good = BedwarsStats.strength(player(400, 3000, 500), false, false, false);
		double sameTierMoreFinals = BedwarsStats.strength(player(400, 3500, 500), false, false, false);
		double newcomer = BedwarsStats.strength(player(20, 30, 90), false, false, false);
		assertTrue(elite > good);
		assertTrue(sameTierMoreFinals > good, "the FKDR decides within a tier");
		assertTrue(good > newcomer);
		assertTrue(newcomer > 0);
	}

	@Test
	void playersToTargetComeBeforeAHigherTierThatIsNot() {
		// 340 stars: a player to target at a low tier; 3.8 FKDR and 247 stars: a higher tier, not to target
		double target = BedwarsStats.strength(player(340, 1100, 1000), false, false, true);
		double higherTier = BedwarsStats.strength(player(247, 3800, 1000), false, false, false);
		double eliteTarget = BedwarsStats.strength(player(1200, 9000, 400), false, false, true);
		assertTrue(target > higherTier);
		assertTrue(eliteTarget > target);
	}

	@Test
	void snipersThenNicksLeadAndUnknownPlayersTrail() {
		double elite = BedwarsStats.strength(player(3000, 50000, 1000), false, false, false);
		double nick = BedwarsStats.strength(null, true, false, false);
		double sniper = BedwarsStats.strength(player(50, 120, 10), false, true, false);
		assertTrue(sniper > nick);
		assertTrue(nick > elite);
		assertEquals(-1, BedwarsStats.strength(null, false, false, false), "stats not in yet");
	}
}
