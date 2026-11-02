package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What happened to you in one Bed Wars game, for the debrief shown after it: your kills, final
 * kills, beds and deaths, when they came, and the game's result, the game's kill leaderboard
 * (who killed most, from the death lines everyone sees in the chat) and, with your own Hypixel
 * API key, your Bed Wars career before and after it. Only that game: nothing is kept once the
 * session ends.
 */
public final class Debrief {
	public enum Result {
		/** "VICTORY!" */
		WIN,
		/** "GAME OVER!" */
		LOSS,
		/** Final-killed, and gone before the end. */
		ELIMINATED,
		/** Gone before the end, still in the game. */
		LEFT
	}

	public enum Kind {
		KILL, FINAL_KILL, BED, DEATH, FINAL_DEATH, BED_LOST, UPGRADE
	}

	/** Something that happened, {@code at} seconds after the game started. */
	public static final class Moment {
		public final int at;
		public final Kind kind;
		/** The other player (victim or killer) or the upgrade bought; may be empty. */
		public final String detail;

		Moment(int at, Kind kind, String detail) {
			this.at = at;
			this.kind = kind;
			this.detail = detail == null ? "" : detail;
		}
	}

	/**
	 * Your Bed Wars career from the Hypixel API: stars, experience and the totals the ratios come
	 * from. A count Hypixel did not give (stats saved by an older Rago) is -1.
	 */
	public static final class Career {
		public final int star;
		public final long xp;
		public final int finalKills;
		public final int finalDeaths;
		public final int wins;
		public final int losses;
		public final int bedsBroken;
		public final int bedsLost;
		public final int kills;
		public final int deaths;
		public final int games;
		/** -1 when hidden. */
		public final int winstreak;
		/** The rank before the name, with its colour codes ("§b[MVP§c+§b]"), or "" for none. */
		public final String rank;

		public Career(int star, long xp, int finalKills, int finalDeaths, int wins, int losses, int bedsBroken, int bedsLost,
			int kills, int deaths, int games, int winstreak, String rank) {
			this.star = star;
			this.xp = xp;
			this.finalKills = finalKills;
			this.finalDeaths = finalDeaths;
			this.wins = wins;
			this.losses = losses;
			this.bedsBroken = bedsBroken;
			this.bedsLost = bedsLost;
			this.kills = kills;
			this.deaths = deaths;
			this.games = games;
			this.winstreak = winstreak;
			this.rank = rank == null ? "" : rank;
		}

		public double fkdr() {
			return finalKills / (double) Math.max(1, finalDeaths);
		}

		public double wlr() {
			return wins / (double) Math.max(1, losses);
		}

		/** Beds broken per bed lost; NaN when unknown. */
		public double bblr() {
			return bedsLost < 0 ? Double.NaN : bedsBroken / (double) Math.max(1, bedsLost);
		}

		/** {@code total} per game played, NaN when either is unknown or no game was played. */
		public double perGame(int total) {
			return games > 0 && total >= 0 ? total / (double) games : Double.NaN;
		}

		/** Deaths per game, final deaths included; NaN when unknown. */
		public double deathsPerGame() {
			return deaths < 0 ? Double.NaN : perGame(deaths + finalDeaths);
		}
	}

	/** A player's kills in this game, finals included, told from the death lines in the chat. */
	public static final class Killer {
		public final String name;
		int kills;
		int finals;
		/** The colour code of the player's team ('c' for red), 0 when unknown. */
		char color;

		Killer(String name) {
			this.name = name;
		}

		/** All kills, final kills included. */
		public int kills() {
			return kills;
		}

		public int finals() {
			return finals;
		}

		public char color() {
			return color;
		}
	}

	public final String player;
	/** When the game started (milliseconds since 1970), told from the sidebar's countdown when it was joined late. */
	public final long startedAt;
	long endedAt;
	Result result;
	int teams;
	int teamSize;
	String map = "";
	/** Whether your bed was seen standing in the sidebar (else nothing is said about it). */
	boolean bedSeen;
	int voidDeaths;
	/** Hypixel's own counts in the sidebar ("Kills: 3"), shown in some modes; -1 when not seen. */
	int sidebarKills = -1;
	int sidebarFinalKills = -1;
	int sidebarBeds = -1;
	/** Your Bed Wars final kill / final death ratio before and after the game, from the Hypixel API (NaN unknown). */
	double fkdrBefore = Double.NaN;
	double fkdrAfter = Double.NaN;
	int starBefore = -1;
	/** Your career from the Hypixel API when the game started and once Hypixel saved it; null unknown. */
	Career before;
	Career after;
	/** Your team's colour code, 0 when unknown. */
	char teamColor;
	/** The most players seen in the teams at once. */
	int players;
	/** The player who made the game's first kill, "" before it. */
	String firstKiller = "";
	/** Changed after the game ended (stats, the end of a game you were eliminated from): the picture is drawn again. */
	volatile int version;
	private final List<Moment> moments = new ArrayList<>();
	private final Map<String, Killer> board = new LinkedHashMap<>();

	Debrief(String player, long startedAt) {
		this.player = player;
		this.startedAt = startedAt;
	}

	void add(int at, Kind kind, String detail) {
		moments.add(new Moment(Math.max(0, at), kind, detail));
	}

	/**
	 * A kill by {@code name} on the leaderboard, in team {@code color} (0 unknown). The leaderboard is
	 * read by the thread that draws the debrief while kills can still come (after your final death).
	 */
	synchronized void kill(String name, boolean fin, char color) {
		Killer k = killer(name);
		k.kills++;
		if (fin) {
			k.finals++;
		}
		if (color != 0) {
			k.color = color;
		}
		if (firstKiller.isEmpty()) {
			firstKiller = name;
		}
	}

	private Killer killer(String name) {
		Killer k = board.get(name);
		if (k == null) {
			k = new Killer(name);
			board.put(name, k);
		}
		return k;
	}

	/** Hypixel's own count for one of its top killers, at the end of the game: it wins over a count from missed lines. */
	synchronized void topKiller(String name, int kills) {
		Killer k = killer(name);
		k.kills = Math.max(k.kills, kills);
	}

	void touch() {
		version++;
	}

	/** Goes up each time something changes after the game ended. */
	public int version() {
		return version;
	}

	/**
	 * The game's kill leaderboard, most kills first (then most finals): everyone seen killing, and
	 * you, with your own count when Hypixel's sidebar or your lines say more than the board.
	 */
	public synchronized List<Killer> board() {
		List<Killer> out = new ArrayList<>();
		boolean me = false;
		for (Killer k : board.values()) {
			if (k.name.equals(player)) {
				out.add(own(k));
				me = true;
			} else {
				out.add(k);
			}
		}
		if (!me) {
			out.add(own(null));
		}
		out.sort((a, b) -> a.kills != b.kills ? b.kills - a.kills : a.finals != b.finals ? b.finals - a.finals
			: a.name.equals(player) ? -1 : b.name.equals(player) ? 1 : 0);
		return out;
	}

	private Killer own(Killer seen) {
		Killer k = new Killer(player);
		k.kills = Math.max(seen == null ? 0 : seen.kills, kills() + finalKills());
		k.finals = Math.max(seen == null ? 0 : seen.finals, finalKills());
		k.color = seen != null && seen.color != 0 ? seen.color : teamColor;
		return k;
	}

	/** Your place on the kill leaderboard: 1 plus the players with more kills than you. */
	public synchronized int rank() {
		int mine = kills() + finalKills();
		Killer seen = board.get(player);
		if (seen != null) {
			mine = Math.max(mine, seen.kills);
		}
		int above = 0;
		for (Killer k : board.values()) {
			if (!k.name.equals(player) && k.kills > mine) {
				above++;
			}
		}
		return above + 1;
	}

	/** Whether the game's first kill was yours. */
	public synchronized boolean firstBlood() {
		return firstKiller.equals(player);
	}

	public int players() {
		return players;
	}

	public char teamColor() {
		return teamColor;
	}

	public Career careerBefore() {
		return before;
	}

	public Career careerAfter() {
		return after;
	}

	/** The Bed Wars experience this game gave, once Hypixel saved it; -1 unknown. */
	public long xpGained() {
		if (before == null || after == null || before.xp < 0 || after.xp < before.xp) {
			return -1;
		}
		return after.xp - before.xp;
	}

	/** Everything that happened, in the order it happened. */
	public List<Moment> moments() {
		return Collections.unmodifiableList(moments);
	}

	public int count(Kind kind) {
		int n = 0;
		for (Moment m : moments) {
			if (m.kind == kind) {
				n++;
			}
		}
		return n;
	}

	/** Hypixel's count when its sidebar shows one, the kills seen in the chat otherwise. */
	public int kills() {
		return sidebarKills >= 0 ? sidebarKills : count(Kind.KILL);
	}

	public int finalKills() {
		return sidebarFinalKills >= 0 ? sidebarFinalKills : count(Kind.FINAL_KILL);
	}

	public int beds() {
		return sidebarBeds >= 0 ? sidebarBeds : count(Kind.BED);
	}

	public int deaths() {
		return count(Kind.DEATH) + count(Kind.FINAL_DEATH);
	}

	public int voidDeaths() {
		return voidDeaths;
	}

	public boolean bedSeen() {
		return bedSeen;
	}

	/** How long the game lasted for you, in seconds. */
	public int seconds() {
		return (int) Math.max(0, (endedAt - startedAt) / 1000);
	}

	public long endedAt() {
		return endedAt;
	}

	public Result result() {
		return result;
	}

	public int teams() {
		return teams;
	}

	public int teamSize() {
		return teamSize;
	}

	public String map() {
		return map;
	}

	public double fkdrBefore() {
		return fkdrBefore;
	}

	public double fkdrAfter() {
		return fkdrAfter;
	}

	public int starBefore() {
		return starBefore;
	}

	/** The first moment of this kind, or null. */
	public Moment first(Kind kind) {
		for (Moment m : moments) {
			if (m.kind == kind) {
				return m;
			}
		}
		return null;
	}

	/** Who final-killed you, or "" (also when it was the void). */
	public String finalKiller() {
		Moment m = first(Kind.FINAL_DEATH);
		return m == null ? "" : m.detail;
	}

	/** Hypixel's mode from the teams in the sidebar and the players in yours: "solo", "doubles", "threes", "fours", "4v4" or "". */
	public String mode() {
		if (teams == 8 && teamSize == 1) {
			return "solo";
		}
		if (teams == 8 && teamSize == 2) {
			return "doubles";
		}
		if (teams == 4 && teamSize == 3) {
			return "threes";
		}
		if (teams == 4 && teamSize == 4) {
			return "fours";
		}
		if (teams == 2 && teamSize == 4) {
			return "4v4";
		}
		return "";
	}
}
