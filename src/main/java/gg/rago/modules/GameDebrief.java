package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.bedwars.Debrief;
import gg.rago.bedwars.DebriefTracker;
import gg.rago.bedwars.GameEvents;
import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.gui.DebriefCard;
import gg.rago.hypixel.HypixelApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The debrief after each Hypixel Bed Wars game: your kills, final kills, beds and deaths, when they
 * came, your team's first upgrade, when your bed fell, the game's kill leaderboard (from the death
 * lines everyone sees, and Hypixel's own top three at the end) and, with your own API key, your
 * stars, experience and ratios before and after. A line in chat (seen only by you) opens it, as the game ends for
 * you: at "VICTORY!" or "GAME OVER!", or at your final death (and again if your team wins after it).
 * A game you leave before its end keeps its debrief in /rago debrief, without the line, so none shows
 * up when you join the next one. The debrief is also a picture to copy into Discord. Everything is read from the chat, the sidebar and the titles of
 * your own game; nothing is sent, and nothing is kept once the game closes.
 */
public final class GameDebrief extends Module {
	/** The debriefs kept for this session (the oldest go first). */
	private static final int KEPT = 30;
	/** Hypixel is given this long to save the game before your stats are asked for again. */
	private static final long STATS_AFTER_MS = 20_000;
	private static final long STATS_GIVE_UP_MS = 3 * 60_000;
	/** A game shorter than this where nothing happened (joined, then left) gets no debrief. */
	private static final int MIN_SECONDS = 45;
	/**
	 * The line after a game waits for Hypixel's own end-of-game lines (rewards, experience, the
	 * reward summary): a second at least, then a second without a new line, eight at most.
	 */
	static final long SAY_AFTER_MS = 1_000;
	static final long QUIET_MS = 1_000;
	static final long SAY_BY_MS = 8_000;
	/** Lines that still come this long after the game put it back at the bottom (one copy only). */
	static final long KEEP_LAST_MS = 12_000;
	/** Hypixel's top killers come in the chat within this after the end of the game. */
	private static final long TOP_KILLERS_MS = 15_000;
	/** Your stats are taken again for this long after the start: a fresh answer replaces cached ones. */
	private static final long STATS_BEFORE_MS = 120_000;
	/** What the GG button puts in the chat bar: all chat, since a party chat mode would keep it in the party. */
	static final String GG = "/ac gg";
	/** The line's chat ID, outside the range Rago's chat stacking uses. */
	private static final int LINE_ID = 0x52A70001;

	private final BooleanOption chatLine = addOption(new BooleanOption("debrief_chat", true));
	private final BooleanOption signature = addOption(new BooleanOption("debrief_signature", true));
	private final ChoiceOption style = addOption(new ChoiceOption("debrief_style", "classic", "classic", "story", "card", "session"));

	private final DebriefTracker tracker = new DebriefTracker();
	private final List<Debrief> games = new ArrayList<>();
	private World world;
	/** The world a game ended in: its sidebar still shows the teams for a moment, which is no new game. */
	private World endedIn;
	private String lastTitle = "";
	private int ticks;
	private Debrief awaitingStats;
	private boolean statsAsked;
	/** When the game was over for Hypixel, from which your stats are asked for again; MAX_VALUE while it goes on. */
	private long statsFrom = Long.MAX_VALUE;
	/** The game whose line waits to be said, the line once said, since when, and when the last chat line came. */
	private Debrief toSay;
	private Debrief said;
	private long sayFrom;
	private long lastLineAt;
	private boolean bump;
	/** A game you were final-killed in, while you watch its end: your team may still win it. */
	private Debrief eliminated;
	/** The game Hypixel's top killer lines are for, until when; and the stats its start was told from. */
	private Debrief topKillersFor;
	private long topKillersUntil;
	private HypixelApi.Stats statsUsedBefore;

	public GameDebrief() {
		super("game_debrief", FeatureClass.INFORMATIONAL, true);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	/** The debriefs of this session, oldest first. */
	public List<Debrief> games() {
		return Collections.unmodifiableList(games);
	}

	public boolean signature() {
		return signature.get();
	}

	/** How the debrief's picture is drawn: the landscape picture, a story, a player card or the session's recap. */
	public DebriefCard.Style style() {
		return DebriefCard.Style.of(style.get());
	}

	public void style(DebriefCard.Style s) {
		style.set(s.id);
	}

	@Override
	protected void onDisable() {
		tracker.end(Debrief.Result.LEFT, System.currentTimeMillis());
		awaitingStats = null;
		toSay = null;
		said = null;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.theWorld == null) {
			return;
		}
		ticks++;
		long now = System.currentTimeMillis();
		if (mc.theWorld != world) {
			world = mc.theWorld;
			lastTitle = "";
			// a line still waiting goes now, before the chat of the next place
			if (toSay != null) {
				print(mc, toSay);
				toSay = null;
			}
			said = null;
			topKillersFor = null;
			if (eliminated != null) {
				eliminated = null;
				statsFrom = Math.min(statsFrom, now);
			}
			if (tracker.inGame()) {
				// gone before the end: after your final death, or still in the game
				finish(tracker.current().count(Debrief.Kind.FINAL_DEATH) > 0 ? Debrief.Result.ELIMINATED : Debrief.Result.LEFT, now);
				statsFrom = Math.min(statsFrom, now);
				eliminated = null; // its end is not watched from here
				toSay = null;
			}
		}
		if (ticks % 20 == 0) {
			tracker.sidebar(BedwarsGame.sidebarLines());
		}
		if (!tracker.inGame()) {
			if (eliminated != null) {
				titleAfterElimination(now);
			} else if (world != endedIn && BedwarsGame.playing()) {
				start(mc, now);
			}
		} else {
			readTitle(now);
		}
		if (tracker.inGame()) {
			char own = BedwarsGame.ownTeam();
			if (own != 0) {
				tracker.ownBed(BedwarsGame.teamBeds().get(own), now);
			}
			if (ticks % 20 == 0 && now - tracker.current().startedAt < 120_000) {
				tracker.teamSize(BedwarsGame.ownTeamSize());
			}
			if (ticks % 20 == 0 && now - tracker.current().startedAt < STATS_BEFORE_MS) {
				statsBefore(RagoClient.hypixel().get(mc.thePlayer.getUniqueID()));
			}
		}
		statsAfterGame(mc, now);
		lineAfterGame(mc, now);
	}

	/**
	 * Says the line once Hypixel's end-of-game lines are in, then keeps it the last line of the chat
	 * for a few seconds: a line that comes after it moves it back down (the old copy goes, by its ID).
	 */
	private void lineAfterGame(Minecraft mc, long now) {
		if (toSay != null) {
			if (readyToSay(now - sayFrom, now - lastLineAt)) {
				said = toSay;
				toSay = null;
				bump = false;
				print(mc, said);
			}
			return;
		}
		if (said == null) {
			return;
		}
		if (now - sayFrom > KEEP_LAST_MS) {
			said = null;
		} else if (bump) {
			bump = false;
			print(mc, said);
		}
	}

	/**
	 * The line, at the bottom of the chat. The copy above goes first, so that a chat that stacks
	 * repeated lines (Patcher's adds "(2)") does not take it for a repeat; a new line every time,
	 * since such a chat may add to the one it was given.
	 */
	private static void print(Minecraft mc, Debrief d) {
		mc.ingameGUI.getChatGUI().deleteChatLine(LINE_ID);
		mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(line(d), LINE_ID);
	}

	/** Whether the line is due, {@code sinceEnd} ms after the game and {@code sinceLine} after the last chat line. */
	static boolean readyToSay(long sinceEnd, long sinceLine) {
		return sinceEnd >= SAY_BY_MS || sinceEnd >= SAY_AFTER_MS && sinceLine >= QUIET_MS;
	}

	private void start(Minecraft mc, long now) {
		GameEvents.Reading r = GameEvents.read(BedwarsGame.eventLine());
		int elapsed = r == null ? 0 : Math.max(0, r.elapsed());
		tracker.start(mc.thePlayer.getName(), now, elapsed, BedwarsGame.teamBeds().size());
		Map<String, Character> colors = new HashMap<>();
		tabList(mc, new HashSet<>(), colors);
		tracker.teams(colors.getOrDefault(mc.thePlayer.getName(), (char) 0), colors.size());
		HypixelApi api = RagoClient.hypixel();
		statsUsedBefore = null;
		statsBefore(api.get(mc.thePlayer.getUniqueID()));
		// cached stats can be two days old: fresh ones replace them in a second or two
		api.refresh(mc.thePlayer.getUniqueID());
	}

	/** Your stats for the start of the game, when they are new. */
	private void statsBefore(HypixelApi.Stats s) {
		if (s != null && s.found && s != statsUsedBefore) {
			statsUsedBefore = s;
			tracker.statsBefore(career(s));
		}
	}

	/** Your Bed Wars career as the Hypixel API gave it. */
	static Debrief.Career career(HypixelApi.Stats s) {
		return new Debrief.Career(s.star, s.xp, s.finalKills, s.finalDeaths, s.wins, s.losses, s.bedsBroken, s.bedsLost,
			s.kills, s.deaths, s.games, s.winstreak, s.rank());
	}

	/** The names in the tab list, and the colour of each one's team (players with no team, Hypixel's bots, have none). */
	private static void tabList(Minecraft mc, Set<String> players, Map<String, Character> colors) {
		Scoreboard board = mc.theWorld == null ? null : mc.theWorld.getScoreboard();
		for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
			String name = info.getGameProfile().getName();
			players.add(name);
			ScorePlayerTeam team = board == null ? null : board.getPlayersTeam(name);
			char c = team == null ? 0 : BedwarsGame.teamColor(team);
			if (c != 0) {
				colors.put(name, c);
			}
		}
	}

	/** "VICTORY!" or "GAME OVER!" ends the game; titles fire no event. */
	private void readTitle(long now) {
		String t = BedwarsGame.title();
		if (t.equals(lastTitle)) {
			return;
		}
		lastTitle = t;
		boolean won = BedwarsGame.isVictory(t);
		if (won || BedwarsGame.isGameOver(t)) {
			topKillersFor = finish(won ? Debrief.Result.WIN : Debrief.Result.LOSS, now);
			topKillersUntil = now + TOP_KILLERS_MS;
			statsFrom = now;
		}
	}

	/**
	 * After your final death, the end of the game: your team's "VICTORY!" makes it a win (the line
	 * says so again); "GAME OVER!" leaves it as it is. Either way your stats can be asked for again.
	 */
	private void titleAfterElimination(long now) {
		String t = BedwarsGame.title();
		if (t.equals(lastTitle)) {
			return;
		}
		lastTitle = t;
		boolean won = BedwarsGame.isVictory(t);
		if (!won && !BedwarsGame.isGameOver(t)) {
			return;
		}
		Debrief d = eliminated;
		eliminated = null;
		statsFrom = now;
		topKillersFor = d;
		topKillersUntil = now + TOP_KILLERS_MS;
		if (won) {
			DebriefTracker.result(d, Debrief.Result.WIN);
			if (chatLine.get()) {
				say(d, now);
			}
		}
	}

	/** Ends the game being followed; returns its debrief, or null when it gets none. */
	private Debrief finish(Debrief.Result result, long now) {
		Debrief d = tracker.end(result, now);
		endedIn = world;
		statsFrom = Long.MAX_VALUE;
		if (d == null || d.seconds() < MIN_SECONDS && d.moments().isEmpty()) {
			return null;
		}
		games.add(d);
		if (games.size() > KEPT) {
			games.remove(0);
		}
		if (result == Debrief.Result.ELIMINATED) {
			eliminated = d;
		}
		if (chatLine.get() && announced(result)) {
			say(d, now);
			if (result == Debrief.Result.ELIMINATED) {
				// no end-of-game lines to wait for: the line goes at once, and stays last for a moment
				said = d;
				toSay = null;
				print(Minecraft.getMinecraft(), d);
			}
		}
		awaitingStats = !Double.isNaN(d.fkdrBefore()) && RagoClient.hypixel().hasKey() ? d : null;
		statsAsked = false;
		return d;
	}

	/** Whether a game ending so gets its line: not one you left before its end (it would show in the next place). */
	static boolean announced(Debrief.Result result) {
		return result != Debrief.Result.LEFT;
	}

	private void say(Debrief d, long now) {
		toSay = d;
		said = null;
		sayFrom = now;
		lastLineAt = now;
	}

	/** Your stats once Hypixel has saved the game, for the ratio after it. */
	private void statsAfterGame(Minecraft mc, long now) {
		Debrief d = awaitingStats;
		if (d == null || statsFrom == Long.MAX_VALUE || now - statsFrom < STATS_AFTER_MS) {
			return;
		}
		if (now - statsFrom > STATS_GIVE_UP_MS) {
			awaitingStats = null;
			return;
		}
		HypixelApi api = RagoClient.hypixel();
		UUID me = mc.thePlayer.getUniqueID();
		if (!statsAsked) {
			statsAsked = true;
			api.refresh(me);
			return;
		}
		HypixelApi.Stats s = api.get(me);
		if (s != null && s.found && s.time() > statsFrom + STATS_AFTER_MS / 2) {
			DebriefTracker.statsAfter(d, career(s));
			awaitingStats = null;
		}
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || event.type == 2 || mc.thePlayer == null || mc.getNetHandler() == null) {
			return;
		}
		String line = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText()).trim();
		long now = System.currentTimeMillis();
		// the end-of-game line waits for this one, or goes back below it on the next tick
		lastLineAt = now;
		bump = said != null;
		if (!tracker.inGame()) {
			if (line.contains(BedwarsGame.START) && mc.theWorld != endedIn && BedwarsGame.inBedwars()) {
				start(mc, now);
				return;
			}
			afterGame(mc, line, now);
			return;
		}
		Set<String> players = new HashSet<>();
		Map<String, Character> colors = new HashMap<>();
		tabList(mc, players, colors);
		tracker.teams(colors.getOrDefault(mc.thePlayer.getName(), (char) 0), colors.size());
		// Hypixel's top killers may come a tick before the title that ends the game
		DebriefTracker.topKiller(tracker.current(), line);
		tracker.chat(line, mc.thePlayer.getName(), players, colors, now);
		if (tracker.current().count(Debrief.Kind.FINAL_DEATH) > 0) {
			// your final death: the game is over for you, its debrief comes now
			finish(Debrief.Result.ELIMINATED, now);
		}
	}

	/**
	 * A chat line once the game is over for you: after your final death, the kills of the players
	 * still in the game go on counting on its leaderboard; after its end, Hypixel's top killers.
	 */
	private void afterGame(Minecraft mc, String line, long now) {
		if (eliminated != null) {
			Set<String> players = new HashSet<>();
			Map<String, Character> colors = new HashMap<>();
			tabList(mc, players, colors);
			DebriefTracker.countKill(eliminated, line, players, colors);
		}
		if (topKillersFor != null && now < topKillersUntil) {
			DebriefTracker.topKiller(topKillersFor, line);
		}
	}

	/** The line in chat after a game: the result, the four counts and a button that opens the debrief. */
	private static IChatComponent line(Debrief d) {
		Debrief.Result r = d.result();
		EnumChatFormatting color = r == Debrief.Result.WIN ? EnumChatFormatting.GREEN : r == Debrief.Result.LEFT ? EnumChatFormatting.GRAY : EnumChatFormatting.RED;
		String text = "§c§lRago §8» " + color + I18n.format("rago.debrief.result." + r.name().toLowerCase(Locale.ROOT))
			+ " §8· §7" + I18n.format("rago.debrief.final_kills") + " §6" + d.finalKills()
			+ " §8· §7" + I18n.format("rago.debrief.kills") + " §f" + d.kills()
			+ " §8· §7" + I18n.format("rago.debrief.beds") + " §b" + d.beds()
			+ " §8· §7" + I18n.format("rago.debrief.deaths") + " §c" + d.deaths() + " ";
		IChatComponent line = new ChatComponentText(text);
		IChatComponent open = new ChatComponentText(I18n.format("rago.debrief.chat_open"));
		open.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN).setBold(true)
			.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rago debrief"))
			.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(I18n.format("rago.debrief.chat_open_tip")))));
		line.appendSibling(open);
		// a GG ready in the chat bar: the player sends it with Enter, or not at all
		IChatComponent gg = new ChatComponentText(" " + I18n.format("rago.debrief.chat_gg"));
		gg.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA).setBold(true)
			.setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, GG))
			.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(I18n.format("rago.debrief.chat_gg_tip", GG)))));
		line.appendSibling(gg);
		return line;
	}
}
