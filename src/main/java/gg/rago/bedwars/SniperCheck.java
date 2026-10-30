package gg.rago.bedwars;

import gg.rago.hypixel.HypixelApi;

import java.util.Locale;

/**
 * Signs that an enemy may be a sniper: an account made to join your games and go after you, or to
 * cheat without risking a main account. Each sign only makes it likely, never certain:
 * <ul>
 *   <li>a name with words that alt and cheat accounts often carry ("alt_", "hack", "vape"...);</li>
 *   <li>few final kills with a high final kill/death ratio: a new account that wins every fight;</li>
 *   <li>(seen in the game) chainmail armour with an iron sword in the first minute.</li>
 * </ul>
 */
public final class SniperCheck {
	/** Parts of names alt and cheat accounts often have. */
	private static final String[] NAME_WORDS = {"mcalt", "alt_", "_alt", "hack", "vape", "cheat", "client", "watchdog", "anticheat"};

	private SniperCheck() {
	}

	/** "name" or "stats" when the name or the stats look like a sniper's, otherwise null. */
	public static String reason(String name, HypixelApi.Stats s, boolean checkName, boolean checkStats, int maxFinals, double minFkdr) {
		if (checkName && suspiciousName(name)) {
			return "name";
		}
		if (checkStats && s != null && s.found && s.finalKills < maxFinals && s.fkdr() >= minFkdr) {
			return "stats";
		}
		return null;
	}

	public static boolean suspiciousName(String name) {
		if (name == null) {
			return false;
		}
		String n = name.toLowerCase(Locale.ROOT);
		for (String w : NAME_WORDS) {
			if (n.contains(w)) {
				return true;
			}
		}
		return false;
	}
}
