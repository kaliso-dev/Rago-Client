package gg.rago.bedwars;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows one Bed Wars game for its debrief, from the chat lines, the sidebar and your bed, and
 * hands the {@link Debrief} over when the game ends. Only lines about you count: your kills (a
 * death line that names you after the victim), your deaths (a line that starts with your name)
 * and the beds you break; your team's upgrades are kept for the timeline.
 */
public final class DebriefTracker {
	/** Hypixel's own counts at the bottom of the sidebar in some modes: "Kills: 3". */
	private static final Pattern COUNTER = Pattern.compile("^(Kills|Final Kills|Beds Broken)\\s*:\\s*(\\d+)$");
	/** The waiting room's map line: "Map: Lighthouse". */
	private static final Pattern MAP = Pattern.compile("^(?:Map|Carte|Karte|Mapa|Mappa)\\s*:\\s*(.+)$");
	/** A death in the void, in the languages Hypixel's death lines use. */
	private static final Pattern VOID = Pattern.compile("(?i)(^|[^\\p{L}])(void|vide|leere|vac[ií]o|vuoto)($|[^\\p{L}])");

	private final TeamUpgrades upgrades = new TeamUpgrades();
	private Debrief game;
	private String lastMap = "";
	private Boolean ownBed;

	public boolean inGame() {
		return game != null;
	}

	/** The game being followed, or null. */
	public Debrief current() {
		return game;
	}

	/**
	 * A game starts for {@code player}, {@code elapsed} seconds in (0 at its start, more when it
	 * was joined late), with {@code teams} teams in the sidebar.
	 */
	public void start(String player, long now, int elapsed, int teams) {
		game = new Debrief(player, now - Math.max(0, elapsed) * 1000L);
		game.teams = teams;
		game.map = lastMap;
		upgrades.reset();
		ownBed = null;
	}

	/** Your Bed Wars stats from the Hypixel API when the game started. */
	public void statsBefore(double fkdr, int star) {
		if (game != null) {
			game.fkdrBefore = fkdr;
			game.starBefore = star;
		}
	}

	/** Your final kill ratio once Hypixel has saved the game. */
	public static void statsAfter(Debrief d, double fkdr) {
		d.fkdrAfter = fkdr;
	}

	private int at(long now) {
		return (int) ((now - game.startedAt) / 1000);
	}

	/**
	 * A chat line without colours, during the game. {@code players} are the names in the tab list:
	 * a death line starts with its victim's name.
	 */
	public void chat(String line, String name, Set<String> players, long now) {
		if (game == null || line.isEmpty()) {
			return;
		}
		int at = at(now);
		Debrief.Kind kill = ownKill(line, name, players);
		if (kill != null) {
			game.add(at, kill, line.substring(0, line.indexOf(' ')));
			return;
		}
		Debrief.Kind death = ownDeath(line, name);
		if (death != null) {
			if (isVoid(line)) {
				game.voidDeaths++;
			}
			game.add(at, death, killer(line, name, players));
			return;
		}
		if (BedwarsGame.isOwnBed(line, name)) {
			game.add(at, Debrief.Kind.BED, "");
			return;
		}
		if (upgrades.chat(line)) {
			game.add(at, Debrief.Kind.UPGRADE, TeamUpgrades.purchase(line));
		}
	}

	/** Your team's bed in the sidebar now (null when unknown): its fall is a moment of the game. */
	public void ownBed(Boolean standing, long now) {
		if (game == null || standing == null) {
			return;
		}
		if (Boolean.TRUE.equals(ownBed) && !standing) {
			game.add(at(now), Debrief.Kind.BED_LOST, "");
		}
		if (standing) {
			game.bedSeen = true;
		}
		ownBed = standing;
	}

	/** How many players your team has (from the tab list): the largest count seen is kept. */
	public void teamSize(int n) {
		if (game != null) {
			game.teamSize = Math.max(game.teamSize, n);
		}
	}

	/** The sidebar's lines, cleaned: the map before the game, Hypixel's own counts during it. */
	public void sidebar(List<String> lines) {
		for (String line : lines) {
			Matcher m = MAP.matcher(line);
			if (m.find()) {
				lastMap = m.group(1).trim();
				if (game != null && game.map.isEmpty()) {
					game.map = lastMap;
				}
				continue;
			}
			m = COUNTER.matcher(line);
			if (game != null && m.find()) {
				int n = Integer.parseInt(m.group(2));
				switch (m.group(1)) {
					case "Kills":
						game.sidebarKills = n;
						break;
					case "Final Kills":
						game.sidebarFinalKills = n;
						break;
					default:
						game.sidebarBeds = n;
						break;
				}
			}
		}
	}

	/** The game ends for you: its debrief, or null when none was being followed. */
	public Debrief end(Debrief.Result result, long now) {
		Debrief d = game;
		game = null;
		if (d != null) {
			d.endedAt = now;
			d.result = result;
		}
		return d;
	}

	/**
	 * A kill of yours: a death line (it starts with a player's name, has no colon as chat does) that
	 * names you after the victim. FINAL_KILL when it ends with "FINAL KILL!", in any language.
	 */
	static Debrief.Kind ownKill(String line, String name, Set<String> players) {
		if (line.indexOf(':') >= 0 || line.startsWith(name + " ")) {
			return null;
		}
		int space = line.indexOf(' ');
		if (space <= 0 || !players.contains(line.substring(0, space))) {
			return null;
		}
		if (BedwarsGame.isOwnFinalKill(line, name)) {
			return Debrief.Kind.FINAL_KILL;
		}
		return endsSentence(line) && BedwarsGame.mentionsName(line.substring(space + 1), name) ? Debrief.Kind.KILL : null;
	}

	/** A death of yours: a line that starts with your name, not one of your purchases nor a chat message. */
	static Debrief.Kind ownDeath(String line, String name) {
		if (!line.startsWith(name + " ") || line.indexOf(':') >= 0 || !endsSentence(line) || TeamUpgrades.purchase(line) != null) {
			return null;
		}
		return BedwarsGame.finalEnding(line, name) ? Debrief.Kind.FINAL_DEATH : Debrief.Kind.DEATH;
	}

	static boolean isVoid(String line) {
		return VOID.matcher(line).find();
	}

	/** The player named last in a death line of yours (the killer), or "" when none is (the void, a fall). */
	static String killer(String line, String name, Set<String> players) {
		String best = "";
		int bestAt = -1;
		for (String p : players) {
			if (p.equals(name)) {
				continue;
			}
			int i = lastWord(line, p);
			if (i > bestAt) {
				bestAt = i;
				best = p;
			}
		}
		return best;
	}

	/** Where {@code word} last stands on its own in {@code text}, or -1. */
	private static int lastWord(String text, String word) {
		int i = text.lastIndexOf(word);
		while (i >= 0) {
			boolean before = i == 0 || !nameChar(text.charAt(i - 1));
			int end = i + word.length();
			boolean after = end >= text.length() || !nameChar(text.charAt(end));
			if (before && after) {
				return i;
			}
			i = i == 0 ? -1 : text.lastIndexOf(word, i - 1);
		}
		return -1;
	}

	private static boolean nameChar(char c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
	}

	private static boolean endsSentence(String line) {
		char c = line.charAt(line.length() - 1);
		return c == '.' || c == '!';
	}
}
