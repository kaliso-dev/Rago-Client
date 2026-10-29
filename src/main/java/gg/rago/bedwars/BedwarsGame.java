package gg.rago.bedwars;

import gg.rago.core.RagoClient;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where you are in Hypixel Bed Wars: a lobby or a game, from the sidebar, and what the chat and
 * titles say happened. Hypixel can be played in many languages, so a game is recognised from the
 * sidebar's team lines ("R Red: ✓") rather than from a sentence, and chat lines by their shape.
 */
public final class BedwarsGame {
	/** The line Hypixel prints when a game starts (in English). */
	public static final String START = "Protect your bed and destroy the enemy beds";
	/** A team's sidebar line during a game: its letter, its name, then its bed (✓ / ✗) or players left. */
	private static final Pattern TEAM_LINE = Pattern.compile("^([A-Z]) [\\p{L} ]+?: ?([\u2713\u2714]|[\u2717\u2718]|\\d+)");
	/** "Victim was killed by You. FINAL KILL!": a death line ends with words in capitals for a final kill. */
	private static final Pattern CAPITAL_ENDING = Pattern.compile("\\p{Lu}[\\p{Lu} ]{3,}\\s*!\\s*$");
	/** "BED DESTRUCTION > Red Bed was destroyed by You!": words in capitals, then an arrow. */
	private static final Pattern CAPITAL_ANNOUNCE = Pattern.compile("^\\s*\\p{Lu}[\\p{Lu} ]{4,}\\s*[>\u00bb]");
	private static Field titleField;
	private static boolean titleFieldMissing;
	private static long readAt;
	private static Map<Character, Boolean> beds = Collections.emptyMap();
	private static Map<Character, Boolean> alive = Collections.emptyMap();

	private BedwarsGame() {
	}

	public static boolean inBedwars() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || !Hypixel.connected()) {
			return false;
		}
		ScoreObjective sidebar = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
		return sidebar != null && EnumChatFormatting.getTextWithoutFormattingCodes(sidebar.getDisplayName()).contains("BED WARS");
	}

	/** Whether a Bed Wars game is being played (not the lobby, not the wait before it starts). */
	public static boolean playing() {
		return !teamBeds().isEmpty();
	}

	/** Each team's bed in the sidebar now, by the team's letter: true while it stands. */
	public static Map<Character, Boolean> teamBeds() {
		long now = System.nanoTime();
		if (now - readAt < 40_000_000L) {
			return beds;
		}
		readAt = now;
		Map<Character, Boolean> out = new LinkedHashMap<>();
		Map<Character, Boolean> teamsAlive = new LinkedHashMap<>();
		Minecraft mc = Minecraft.getMinecraft();
		if (inBedwars()) {
			Scoreboard board = mc.theWorld.getScoreboard();
			ScoreObjective o = board.getObjectiveInDisplaySlot(1);
			for (Score score : board.getSortedScores(o)) {
				ScorePlayerTeam team = board.getPlayersTeam(score.getPlayerName());
				String line = EnumChatFormatting.getTextWithoutFormattingCodes(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()));
				Boolean bed = bed(line);
				if (bed != null) {
					out.put(line.charAt(0), bed);
					teamsAlive.put(line.charAt(0), alive(line));
				}
			}
		}
		// a single line could be something else: a game has at least two teams
		beds = out.size() >= 2 ? out : Collections.<Character, Boolean>emptyMap();
		alive = out.size() >= 2 ? teamsAlive : Collections.<Character, Boolean>emptyMap();
		return beds;
	}

	/** Each team in the game now, by letter: true while it has a bed or players left (not ✗). */
	public static Map<Character, Boolean> teamsAlive() {
		teamBeds();
		return alive;
	}

	/** A team line's team still in the game: a bed (✓) or players left (a number above 0). */
	static boolean alive(String line) {
		Matcher m = TEAM_LINE.matcher(line);
		if (!m.find()) {
			return false;
		}
		String state = m.group(2);
		char c = state.charAt(0);
		return c == '\u2713' || c == '\u2714' || Character.isDigit(c) && Integer.parseInt(state) > 0;
	}

	/** A sidebar line's team bed: true standing, false gone, null for a line that is not a team's. */
	static Boolean bed(String line) {
		Matcher m = line == null ? null : TEAM_LINE.matcher(line);
		if (m == null || !m.find()) {
			return null;
		}
		char c = m.group(2).charAt(0);
		return c == '\u2713' || c == '\u2714';
	}

	/** Your team's letter, from the colour prefix Hypixel gives your name ("R" for Red), or 0. */
	public static char ownTeam() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) {
			return 0;
		}
		ScorePlayerTeam team = mc.theWorld.getScoreboard().getPlayersTeam(mc.thePlayer.getName());
		return team == null ? 0 : teamLetter(team);
	}

	/** How many players in the tab list are in your team (by its letter), or 0 when unknown. */
	public static int ownTeamSize() {
		Minecraft mc = Minecraft.getMinecraft();
		char own = ownTeam();
		if (own == 0 || mc.getNetHandler() == null) {
			return 0;
		}
		Scoreboard board = mc.theWorld.getScoreboard();
		int n = 0;
		for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
			ScorePlayerTeam team = board.getPlayersTeam(info.getGameProfile().getName());
			if (team != null && teamLetter(team) == own) {
				n++;
			}
		}
		return n;
	}

	/** A team's letter from its prefix ("§c§lR §c" is R), or 0 when it has none. */
	public static char teamLetter(ScorePlayerTeam team) {
		String prefix = EnumChatFormatting.getTextWithoutFormattingCodes(team.getColorPrefix()).trim();
		return !prefix.isEmpty() && Character.isUpperCase(prefix.charAt(0)) ? prefix.charAt(0) : 0;
	}

	/** A team's colour, the last colour code of its prefix ('c' for red), or 0. */
	public static char teamColor(ScorePlayerTeam team) {
		String prefix = team.getColorPrefix();
		char color = 0;
		for (int i = 0; i + 1 < prefix.length(); i++) {
			if (prefix.charAt(i) == '\u00a7' && "0123456789abcdef".indexOf(Character.toLowerCase(prefix.charAt(i + 1))) >= 0) {
				color = Character.toLowerCase(prefix.charAt(i + 1));
			}
		}
		return color;
	}

	/** A final kill of yours, in any language: your name after the first word, and a capital ending. */
	public static boolean isOwnFinalKill(String line, String name) {
		int space = line.indexOf(' ');
		// the name taken out first, so a name in capitals is not taken for the ending
		return space > 0 && !line.startsWith(name + " ") && !CAPITAL_ANNOUNCE.matcher(line).find()
			&& CAPITAL_ENDING.matcher(line.replace(name, "")).find() && mentions(line.substring(space + 1), name);
	}

	/** A bed you broke: an announcement in capitals ("BED DESTRUCTION >") that names you. */
	public static boolean isOwnBed(String line, String name) {
		return CAPITAL_ANNOUNCE.matcher(line).find() && mentions(line, name);
	}

	/** The titles that end a game: true for a win. */
	public static boolean isVictory(String title) {
		String t = title.toUpperCase(Locale.ROOT);
		return t.contains("VICTORY") || t.contains("VICTOIRE") || t.contains("SIEG") || t.contains("VICTORIA") || t.contains("VITTORIA");
	}

	public static boolean isGameOver(String title) {
		String t = title.toUpperCase(Locale.ROOT);
		return t.contains("GAME OVER") || t.contains("PARTIE TERMIN") || t.contains("FIN DE PARTIE") || t.contains("FIN DE LA PARTIE")
			|| t.contains("SPIEL VORBEI") || t.contains("FIN DEL JUEGO");
	}

	/** "TRAP TRIGGERED!", in English or French. */
	public static boolean isTrapTitle(String title) {
		String t = title.toUpperCase(Locale.ROOT);
		return t.contains("TRAP") || t.contains("PIÈGE") || t.contains("PIEGE");
	}

	/** Whether {@code text} has the player name {@code name} as a whole word. */
	public static boolean mentionsName(String text, String name) {
		return mentions(text, name);
	}

	static boolean mentions(String text, String name) {
		return Pattern.compile("(^|[^A-Za-z0-9_])" + Pattern.quote(name) + "($|[^A-Za-z0-9_])").matcher(text).find();
	}

	/**
	 * The big title on screen now, without colours, or "" (Hypixel shows "TRAP TRIGGERED!",
	 * "VICTORY!" and "GAME OVER!" this way; titles fire no event).
	 */
	public static String title() {
		GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
		if (titleFieldMissing || gui == null) {
			return "";
		}
		try {
			if (titleField == null) {
				titleField = ReflectionHelper.findField(GuiIngame.class, "displayedTitle", "field_175201_x");
			}
			String t = (String) titleField.get(gui);
			return t == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(t);
		} catch (ReflectiveOperationException | RuntimeException e) {
			titleFieldMissing = true;
			RagoClient.LOG.warn("Cannot read titles: {}", e.toString());
			return "";
		}
	}
}
