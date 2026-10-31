package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened to you in one Bed Wars game, for the debrief shown after it: your kills, final
 * kills, beds and deaths, when they came, and the game's result. Only your own game: nothing
 * about other players is kept beyond the names in your own kill and death lines.
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
	private final List<Moment> moments = new ArrayList<>();

	Debrief(String player, long startedAt) {
		this.player = player;
		this.startedAt = startedAt;
	}

	void add(int at, Kind kind, String detail) {
		moments.add(new Moment(Math.max(0, at), kind, detail));
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
