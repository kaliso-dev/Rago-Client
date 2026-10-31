package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.bedwars.Debrief;
import gg.rago.bedwars.DebriefTracker;
import gg.rago.bedwars.GameEvents;
import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.hypixel.HypixelApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The debrief after each Hypixel Bed Wars game: your kills, final kills, beds and deaths, when they
 * came (a timeline), your team's first upgrade, when your bed fell and, with your own API key, your
 * final kill ratio before and after. A line in chat (seen only by you) opens it; the debrief is also
 * a picture to copy into Discord. Everything is read from the chat, the sidebar and the titles of
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
	/** What the GG button puts in the chat bar: all chat, since a party chat mode would keep it in the party. */
	static final String GG = "/ac gg";
	/** The line's chat ID, outside the range Rago's chat stacking uses. */
	private static final int LINE_ID = 0x52A70001;

	private final BooleanOption chatLine = addOption(new BooleanOption("debrief_chat", true));
	private final BooleanOption signature = addOption(new BooleanOption("debrief_signature", true));

	private final DebriefTracker tracker = new DebriefTracker();
	private final List<Debrief> games = new ArrayList<>();
	private World world;
	/** The world a game ended in: its sidebar still shows the teams for a moment, which is no new game. */
	private World endedIn;
	private String lastTitle = "";
	private int ticks;
	private Debrief awaitingStats;
	private boolean statsAsked;
	/** The game whose line waits to be said, the line once said, and when the last chat line came. */
	private Debrief toSay;
	private Debrief said;
	private long lastLineAt;
	private boolean bump;

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
			if (tracker.inGame()) {
				// gone before the end: after your final death, or still in the game
				finish(tracker.current().count(Debrief.Kind.FINAL_DEATH) > 0 ? Debrief.Result.ELIMINATED : Debrief.Result.LEFT, now);
			}
		}
		if (ticks % 20 == 0) {
			tracker.sidebar(BedwarsGame.sidebarLines());
		}
		if (!tracker.inGame()) {
			if (world != endedIn && BedwarsGame.playing()) {
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
			if (readyToSay(now - toSay.endedAt(), now - lastLineAt)) {
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
		if (now - said.endedAt() > KEEP_LAST_MS) {
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
		HypixelApi.Stats s = RagoClient.hypixel().get(mc.thePlayer.getUniqueID());
		if (s != null && s.found) {
			tracker.statsBefore(s.fkdr(), s.star);
		}
	}

	/** "VICTORY!" or "GAME OVER!" ends the game; titles fire no event. */
	private void readTitle(long now) {
		String t = BedwarsGame.title();
		if (t.equals(lastTitle)) {
			return;
		}
		lastTitle = t;
		if (BedwarsGame.isVictory(t)) {
			finish(Debrief.Result.WIN, now);
		} else if (BedwarsGame.isGameOver(t)) {
			finish(Debrief.Result.LOSS, now);
		}
	}

	private void finish(Debrief.Result result, long now) {
		Debrief d = tracker.end(result, now);
		endedIn = world;
		if (d == null || d.seconds() < MIN_SECONDS && d.moments().isEmpty()) {
			return;
		}
		games.add(d);
		if (games.size() > KEPT) {
			games.remove(0);
		}
		if (chatLine.get()) {
			toSay = d;
			said = null;
			lastLineAt = now;
		}
		awaitingStats = !Double.isNaN(d.fkdrBefore()) && RagoClient.hypixel().hasKey() ? d : null;
		statsAsked = false;
	}

	/** Your stats once Hypixel has saved the game, for the ratio after it. */
	private void statsAfterGame(Minecraft mc, long now) {
		Debrief d = awaitingStats;
		if (d == null || now - d.endedAt() < STATS_AFTER_MS) {
			return;
		}
		if (now - d.endedAt() > STATS_GIVE_UP_MS) {
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
		if (s != null && s.found && s.time() > d.endedAt() + STATS_AFTER_MS / 2) {
			DebriefTracker.statsAfter(d, s.fkdr());
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
			}
			return;
		}
		Set<String> players = new HashSet<>();
		for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
			players.add(info.getGameProfile().getName());
		}
		tracker.chat(line, mc.thePlayer.getName(), players, now);
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
