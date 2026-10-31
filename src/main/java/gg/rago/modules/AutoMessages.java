package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.config.StringOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.debug.InputTrace;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Messages sent on their own, on Hypixel only: GG (and a message of yours) when a game ends, a
 * message when you kill someone, and /tip all now and then. Off by default, as the player asked
 * for these knowingly: each message is an ordinary chat line, and kill messages wait a minimum
 * time between them so the chat is not flooded. Plain chat in a Bed Wars game only reaches your
 * team, so messages go to everyone with /shout in team modes and /ac in solo. Each message can be
 * written three ways: one is picked at random, never the one sent last time when another is
 * written, as Hypixel refuses the same message twice in a row.
 */
public final class AutoMessages extends Module {
	/** A death line: a name, then words, ending with a full stop or a final kill in capitals; no colon. */
	private static final Pattern DEATH = Pattern.compile("^[A-Za-z0-9_]{1,16} [\\p{Ll}\\p{L} '.,!]+");
	/** Messages wait this long after the last one, and at least this long after the event. */
	private static final int SPACING_TICKS = 25;

	private final BooleanOption gg = addOption(new BooleanOption("auto_gg", true));
	private final StringOption ggText = addOption(new StringOption("auto_gg_text", "gg", 100));
	private final StringOption ggText2 = addOption(new StringOption("auto_gg_text_2", "gg wp", 100));
	private final StringOption ggText3 = addOption(new StringOption("auto_gg_text_3", "good game", 100));
	private final StringOption endText = addOption(new StringOption("auto_end_text", "", 100));
	private final StringOption endText2 = addOption(new StringOption("auto_end_text_2", "", 100));
	private final StringOption endText3 = addOption(new StringOption("auto_end_text_3", "", 100));
	private final BooleanOption kill = addOption(new BooleanOption("auto_kill", false));
	private final StringOption killText = addOption(new StringOption("auto_kill_text", "", 100));
	private final StringOption killText2 = addOption(new StringOption("auto_kill_text_2", "", 100));
	private final StringOption killText3 = addOption(new StringOption("auto_kill_text_3", "", 100));
	private final BooleanOption finalOnly = addOption(new BooleanOption("auto_kill_final_only", true));
	private final IntOption killGap = addOption(new IntOption("auto_kill_gap", 15, 5, 120));
	private final BooleanOption tip = addOption(new BooleanOption("auto_tip", false));
	private final IntOption tipEvery = addOption(new IntOption("auto_tip_every", 15, 5, 60));

	private final Deque<String> queue = new ArrayDeque<>();
	private final Random random = new Random();
	/** The GG, end and kill messages picked last time, so the next one differs. */
	private String lastGg = "";
	private String lastEnd = "";
	private String lastKill = "";
	private World world;
	private boolean inGame;
	private boolean ended;
	private String lastTitle = "";
	private int ticks;
	private int nextSend;
	private int lastKillTick = -100000;
	private int nextTip = 20 * 30;
	/** Most players seen in your team this game: 1 in solo. */
	private int teamSize;
	/** Most teams seen alive this game, to know when only one is left. */
	private int teamsSeen;

	public AutoMessages() {
		super("auto_messages", FeatureClass.AUTO_CHAT, false);
		ggText.showIf(gg::get);
		ggText2.showIf(gg::get);
		ggText3.showIf(gg::get);
		endText.showIf(gg::get);
		endText2.showIf(gg::get);
		endText3.showIf(gg::get);
		killText.showIf(kill::get);
		killText2.showIf(kill::get);
		killText3.showIf(kill::get);
		finalOnly.showIf(kill::get);
		killGap.showIf(kill::get);
		tipEvery.showIf(tip::get);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			inGame = false;
			ended = false;
			teamSize = 0;
			teamsSeen = 0;
			queue.clear();
		}
		if (InputTrace.on() && ticks % 40 == 0) {
			RagoClient.LOG.info("[debug] auto messages: hypixel={} sidebar={} inGame={} ended={} teams={} own={} title=\"{}\" queue={}",
				Hypixel.connected(), BedwarsGame.inBedwars(), inGame, ended, BedwarsGame.teamsAlive(), BedwarsGame.ownTeam(), BedwarsGame.title(), queue);
		}
		if (mc.thePlayer == null || !Hypixel.connected()) {
			return;
		}
		if (!inGame && BedwarsGame.playing()) {
			inGame = true;
			ended = false;
			RagoClient.LOG.info("Auto Messages: Bed Wars game started");
		}
		if (inGame && !ended && ticks % 10 == 0) {
			teamSize = Math.max(teamSize, BedwarsGame.ownTeamSize());
			String end = endFromSidebar();
			if (end != null) {
				end(end);
			}
		}
		String title = BedwarsGame.title();
		if (!title.equals(lastTitle)) {
			lastTitle = title;
			if (inGame && !ended && (BedwarsGame.isVictory(title) || BedwarsGame.isGameOver(title))) {
				end("title \"" + title + "\"");
			}
		}
		if (tip.get() && ticks >= nextTip) {
			nextTip = ticks + tipEvery.get() * 60 * 20;
			enqueue("/tip all");
		}
		if (!queue.isEmpty() && ticks >= nextSend && mc.currentScreen == null) {
			nextSend = ticks + SPACING_TICKS;
			String message = queue.poll();
			RagoClient.LOG.info("Auto Messages: sending \"{}\"", message);
			mc.thePlayer.sendChatMessage(message);
		}
	}

	/**
	 * The game is over for you when the sidebar shows one team left (out of several), or your own
	 * team out of the game (✗); titles say the same, but in words that change with the language.
	 */
	private String endFromSidebar() {
		Map<Character, Boolean> teams = BedwarsGame.teamsAlive();
		if (teams.isEmpty()) {
			return null;
		}
		int left = 0;
		for (boolean a : teams.values()) {
			if (a) {
				left++;
			}
		}
		teamsSeen = Math.max(teamsSeen, left);
		if (teamsSeen >= 2 && left <= 1) {
			return "one team left";
		}
		Boolean own = teams.get(BedwarsGame.ownTeam());
		return own != null && !own ? "your team is out" : null;
	}

	private void end(String why) {
		ended = true;
		RagoClient.LOG.info("Auto Messages: game over ({}), GG {}", why, gg.get() ? "on" : "off");
		if (!gg.get()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer != null) {
			// only in your own chat: says the end was seen, so a GG that never shows can be told apart
			mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§7" + net.minecraft.client.resources.I18n.format("rago.messages.end_seen")));
		}
		lastGg = pick(random, lastGg, ggText.get(), ggText2.get(), ggText3.get());
		lastEnd = pick(random, lastEnd, endText.get(), endText2.get(), endText3.get());
		if (solo()) {
			enqueue(toAll(lastGg));
			enqueue(toAll(lastEnd));
		} else {
			// /shout waits a while between two uses: both in one message
			enqueue(toAll((lastGg + " " + lastEnd).trim()));
		}
	}

	private boolean solo() {
		return teamSize <= 1;
	}

	/** A message for everyone in the game: /ac in solo, /shout in team modes; commands as they are. */
	private String toAll(String text) {
		String t = text == null ? "" : text.trim();
		if (t.isEmpty() || t.startsWith("/") || !inGame) {
			return t;
		}
		return (solo() ? "/ac " : "/shout ") + t;
	}

	private void enqueue(String text) {
		String t = text == null ? "" : text.trim();
		if (!t.isEmpty() && queue.size() < 4) {
			if (queue.isEmpty()) {
				nextSend = Math.max(nextSend, ticks + SPACING_TICKS);
			}
			queue.add(t);
		}
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || !kill.get() || event.type == 2 || mc.thePlayer == null || !inGame || ended) {
			return;
		}
		String line = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText()).trim();
		String name = mc.thePlayer.getName();
		boolean finalKill = BedwarsGame.isOwnFinalKill(line, name);
		boolean anyKill = finalKill || isOwnKill(line, name);
		if ((finalOnly.get() ? finalKill : anyKill) && ticks - lastKillTick >= killGap.get() * 20) {
			lastKillTick = ticks;
			lastKill = pick(random, lastKill, killText.get(), killText2.get(), killText3.get());
			enqueue(toAll(lastKill));
		}
	}

	/**
	 * One of the messages written (empty ones skipped), at random; not {@code last} when another is
	 * written. Empty when none is.
	 */
	static String pick(Random random, String last, String... texts) {
		List<String> written = new ArrayList<>();
		for (String t : texts) {
			String s = t == null ? "" : t.trim();
			if (!s.isEmpty() && !written.contains(s)) {
				written.add(s);
			}
		}
		if (written.size() > 1) {
			written.remove(last);
		}
		return written.isEmpty() ? "" : written.get(random.nextInt(written.size()));
	}

	/** "Steve was killed by You.": someone else's death that names you, not a player's chat line. */
	static boolean isOwnKill(String line, String name) {
		if (line.contains(":") || line.startsWith(name + " ") || !DEATH.matcher(line).find() || !line.endsWith(".")) {
			return false;
		}
		int space = line.indexOf(' ');
		return BedwarsGame.mentionsName(line.substring(space + 1), name);
	}
}
