package gg.rago.modules;

import com.google.gson.JsonParser;
import gg.rago.hypixel.HypixelApi;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

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

	@Test
	void sharedTargetsFitOneChatMessage() {
		String all = BedwarsStats.shareLine("/pc Target: ", Arrays.asList("Shvmy (Red 450* 7.1)", "L10nn3 (Red 312* 5.0)"));
		assertEquals("/pc Target: Shvmy (Red 450* 7.1), L10nn3 (Red 312* 5.0)", all);
		String[] many = new String[8];
		for (int i = 0; i < many.length; i++) {
			many[i] = "Player_" + i + "xx (Yellow 1234* 12.3)";
		}
		String cut = BedwarsStats.shareLine("/pc Target: ", Arrays.asList(many));
		assertTrue(cut.length() <= BedwarsStats.CHAT_MAX, cut);
		assertTrue(cut.startsWith("/pc Target: Player_0xx (Yellow 1234* 12.3), "), cut);
		int shown = cut.split(", ").length;
		assertTrue(cut.endsWith(" +" + (many.length - shown)), cut);
		assertTrue(cut.indexOf('\u00a7') < 0, "no colours");
		String one = BedwarsStats.shareLine("/pc Target: ", Collections.singletonList(new String(new char[150]).replace('\0', 'a')));
		assertEquals(BedwarsStats.CHAT_MAX, one.length());
	}

	private static BedwarsStats.Enemy enemy(String name, char team, double strength) {
		return new BedwarsStats.Enemy(name, team, 'c', null, null, false, strength);
	}

	@Test
	void withNoTargetTheStrongestPlayersWholeTeamIsListed() {
		BedwarsStats.Enemy weak = enemy("Weak", 'R', 1e5);
		BedwarsStats.Enemy best = enemy("Best", 'B', 3e5);
		BedwarsStats.Enemy mate = enemy("Mate", 'B', 2e5);
		BedwarsStats.Enemy unknown = enemy("Unknown", 'G', -1);
		java.util.List<BedwarsStats.Enemy> team = BedwarsStats.strongestTeam(Arrays.asList(weak, mate, unknown, best));
		assertEquals(Arrays.asList(best, mate), team, "the strongest first, their team mate after");
		assertTrue(BedwarsStats.strongestTeam(Collections.singletonList(unknown)).isEmpty(), "no stats: no team to name");
	}

	private static BedwarsStats.Enemy target(String name, char team, double strength, boolean nick) {
		return new BedwarsStats.Enemy(name, team, 'c', null, null, true, strength, nick);
	}

	@Test
	void everyTargetIsListedWithTheirMatesGroupedByTeam() {
		// solo: a 5 FKDR player and a nick, both listed, the nick first (ranked above), weak players left out
		BedwarsStats.Enemy sweat = target("Sweat", 'R', 6e5 + 3e5, false);
		BedwarsStats.Enemy nick = target("Nicked", 'B', 1e6, true);
		BedwarsStats.Enemy weak = enemy("Weak", 'G', 1e5);
		java.util.List<java.util.List<BedwarsStats.Enemy>> solo = BedwarsStats.targetTeams(Arrays.asList(weak, sweat, nick));
		assertEquals(Arrays.asList(Collections.singletonList(nick), Collections.singletonList(sweat)), solo);

		// doubles: each target's whole team, the mate after them whatever their stats
		BedwarsStats.Enemy mate = enemy("Mate", 'R', 2e4);
		BedwarsStats.Enemy nickMate = enemy("Who", 'B', -1);
		BedwarsStats.Enemy weak2 = enemy("Weak2", 'G', 2e5);
		java.util.List<java.util.List<BedwarsStats.Enemy>> doubles =
			BedwarsStats.targetTeams(Arrays.asList(mate, weak, nickMate, sweat, weak2, nick));
		assertEquals(Arrays.asList(Arrays.asList(nick, nickMate), Arrays.asList(sweat, mate)), doubles);

		assertTrue(BedwarsStats.targetTeams(Arrays.asList(weak, weak2)).isEmpty(), "no target: the strongest team instead");
		// a target whose team is not known stands alone
		BedwarsStats.Enemy loose = target("Loose", (char) 0, 7e5, false);
		BedwarsStats.Enemy other = enemy("Other", (char) 0, 1e5);
		assertEquals(Collections.singletonList(Collections.singletonList(loose)), BedwarsStats.targetTeams(Arrays.asList(loose, other)));
	}

	@Test
	void theSharedLineNamesEachTeamOnce() {
		assertEquals("Nicked (NICK)", target("Nicked", 'B', 1e6, true).shared());
		assertEquals("Mate (?)", enemy("Mate", 'R', -1).shared());
		String line = BedwarsStats.shareGroups("/pc Target: ", Arrays.asList(
			Arrays.asList("Blue: Nicked (NICK)", "Who (?)"), Arrays.asList("Red: Sweat (312* 5.4)", "Mate (20* 0.4)")));
		assertEquals("/pc Target: Blue: Nicked (NICK), Who (?) | Red: Sweat (312* 5.4), Mate (20* 0.4)", line);
		assertTrue(line.length() <= BedwarsStats.CHAT_MAX);
	}

	@Test
	void theLineSentInTheChatIsPlainAscii() {
		assertEquals("/pc A cibler : Equipe Rouge", BedwarsStats.ascii("/pc À cibler : Équipe Rouge"));
		assertEquals("Steve (120* 1.2)", BedwarsStats.ascii("Steve (120* 1.2)\u272b"));
	}
}
