package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.hypixel.Danger;
import gg.rago.hypixel.Hypixel;
import gg.rago.hypixel.HypixelApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hypixel Bed Wars stats in the tab list: each player's star before their name and their final
 * kill/death ratio after it, coloured by how dangerous they are (Rago's own 1 to 5 level, see
 * {@link Danger}), nicked players marked, and the list grouped by team, the strongest team first and
 * each team strongest first. When a game starts, a chat message names the enemies to target (300
 * stars or 5 FKDR and more by default), strongest first, with their star and their name in their
 * team's colour. The stats come from the Hypixel API with your own key
 * ({@code /rago hypixelkey <key>}); nothing is sent to the Minecraft server.
 */
public final class BedwarsStats extends Module {
	/** How long the game-start alert waits for stats still on their way. */
	private static final int ALERT_WAIT_TICKS = 10 * 20;
	/** The alert waits this long after the start, while Hypixel puts every player in a team... */
	private static final int ALERT_DELAY_TICKS = 20;
	/** ...and until the number of enemies has held still this long. */
	private static final int ALERT_SETTLE_TICKS = 10;
	/** Gear seen in this long after the start counts for the sniper check. */
	private static final int GEAR_WINDOW_TICKS = 60 * 20;

	private final BooleanOption tabStar = addOption(new BooleanOption("stats_tab_star", true));
	private final BooleanOption tabFkdr = addOption(new BooleanOption("stats_tab_fkdr", true));
	private final BooleanOption tabDanger = addOption(new BooleanOption("stats_tab_danger", true));
	private final BooleanOption nicks = addOption(new BooleanOption("stats_nicks", true));
	private final BooleanOption mark = addOption(new BooleanOption("stats_tab_mark", true));
	private final BooleanOption tabSort = addOption(new BooleanOption("stats_tab_sort", true));
	/** A player to target: this final kill/death ratio or more (a new key, so the old default of 10 does not stay)... */
	private final IntOption strongFkdr = addOption(new IntOption("stats_target_fkdr", 5, 1, 50));
	/** ...or this Bed Wars star or more. */
	private final IntOption strongStar = addOption(new IntOption("stats_strong_star", 300, 50, 2000));
	private final BooleanOption alert = addOption(new BooleanOption("stats_alert", true));
	private final BooleanOption alertSound = addOption(new BooleanOption("stats_alert_sound", true));
	private final BooleanOption sniper;
	private final BooleanOption sniperName;
	private final BooleanOption sniperStats;
	private final IntOption sniperFinals;
	private final IntOption sniperFkdr;
	private final BooleanOption sniperGear;
	/** Players already flagged as possible snipers this game, with the reason. */
	private final Map<UUID, String> snipers = new HashMap<>();

	private final HypixelApi api;
	/** Before and after the name in the tab, by player; worked out when stats come in, not each frame. */
	private final Map<UUID, String[]> tab = new HashMap<>();
	/** How strong each player looks, to sort the tab list by (see {@link #strength}). */
	private final Map<UUID, Double> rank = new HashMap<>();
	/** The API's answers and the tab list's size when the tab was last worked out. */
	private int seenChanges = -1;
	private int seenPlayers = -1;
	/** The number of enemies the alert saw last, and the tick it changed at. */
	private int enemyCount = -1;
	private int enemiesSince;
	private World world;
	private boolean bedwars;
	private int ticks;
	/** Tick the game started at, or -1; the alert goes once per game. */
	private int startedAt = -1;
	private boolean alerted;
	private boolean warned;
	private boolean wasPlaying;

	/** The red danger sign before strong and nicked players. */
	static final String MARK = "§4§l\u26a0§r";

	/** More players than a four-team game, outside a game and its waiting room: a lobby. */
	private static final int BIG_LOBBY = 16;
	private boolean bigLobby;
	/** Said once per wait that Hypixel asks for, so the stats missing from the tab list are explained. */
	private boolean pauseWarned;

	public BedwarsStats(HypixelApi api) {
		super("bedwars_stats", FeatureClass.INFORMATIONAL, false);
		section("sniper");
		sniper = addOption(new BooleanOption("sniper_warning", true));
		sniperName = addOption(new BooleanOption("sniper_name", true));
		sniperStats = addOption(new BooleanOption("sniper_stats", true));
		sniperFinals = addOption(new IntOption("sniper_max_finals", 150, 10, 1000));
		sniperFkdr = addOption(new IntOption("sniper_min_fkdr", 4, 1, 20));
		sniperGear = addOption(new BooleanOption("sniper_gear", false));
		sniperName.showIf(sniper::get);
		sniperStats.showIf(sniper::get);
		sniperFinals.showIf(() -> sniper.get() && sniperStats.get());
		sniperFkdr.showIf(() -> sniper.get() && sniperStats.get());
		sniperGear.showIf(sniper::get);
		section(null);
		this.api = api;
		alertSound.showIf(alert::get);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	protected void onEnable() {
		warned = false;
	}

	@Override
	protected void onDisable() {
		tab.clear();
		rank.clear();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			wasPlaying = false;
			tab.clear();
			rank.clear();
			snipers.clear();
			// Hypixel may move you to a new world as the game starts: keep a start just seen
			if (startedAt >= 0 && ticks - startedAt > 5 * 20) {
				startedAt = -1;
				alerted = false;
			}
		}
		if (ticks % 5 == 0 && mc.thePlayer != null) {
			// the sidebar lists the teams once a game has started, in every language
			boolean playing = BedwarsGame.playing();
			if (playing && !wasPlaying && startedAt < 0) {
				startedAt = ticks;
				alerted = false;
				enemyCount = -1;
			}
			wasPlaying = playing;
		}
		// the tab is worked out again each second, and as soon as new stats or new players come in,
		// or Bed Wars starts or ends
		NetHandlerPlayClient handler = mc.getNetHandler();
		boolean news = ticks % 4 == 0 && (api.changes() != seenChanges
			|| handler != null && handler.getPlayerInfoMap().size() != seenPlayers || BedwarsGame.inBedwars() != bedwars);
		if (mc.thePlayer == null || ticks % 20 != 0 && !news) {
			if (startedAt >= 0 && !alerted && mc.thePlayer != null && ticks % 5 == 0) {
				maybeAlert(mc);
			}
			return;
		}
		seenChanges = api.changes();
		seenPlayers = handler == null ? -1 : handler.getPlayerInfoMap().size();
		bedwars = BedwarsGame.inBedwars();
		if (gg.rago.debug.InputTrace.on() && ticks % 100 == 0) {
			net.minecraft.scoreboard.ScoreObjective side = mc.theWorld == null ? null : mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
			RagoClient.LOG.info("[debug] bedwars stats: hypixel={} server={} sidebar=\"{}\" bedwars={} playing={} tab players={} with stats={} api {}",
				Hypixel.connected(), mc.getCurrentServerData() == null ? "-" : mc.getCurrentServerData().serverIP,
				side == null ? "-" : EnumChatFormatting.getTextWithoutFormattingCodes(side.getDisplayName()), bedwars,
				BedwarsGame.playing(), mc.getNetHandler() == null ? 0 : mc.getNetHandler().getPlayerInfoMap().size(), tab.size(), api.debugState());
		}
		if (!bedwars) {
			tab.clear();
			rank.clear();
			return;
		}
		HypixelApi.Status s = api.status();
		if (!warned && (s == HypixelApi.Status.NO_KEY || s == HypixelApi.Status.BAD_KEY)) {
			warned = true;
			ChatComponentTranslation msg = new ChatComponentTranslation(s == HypixelApi.Status.NO_KEY ? "rago.stats.no_key" : "rago.levelhead.bad_key");
			msg.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
			mc.thePlayer.addChatMessage(msg);
		}
		int wait = api.pausedSeconds();
		if (wait > 0 && !pauseWarned) {
			pauseWarned = true;
			ChatComponentTranslation msg = api.dailyQuota()
				? new ChatComponentTranslation("rago.stats.paused_daily", api.limit() > 0 ? String.valueOf(api.limit()) : "?", HypixelApi.duration(wait))
				: new ChatComponentTranslation("rago.stats.paused", HypixelApi.duration(wait));
			msg.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
			mc.thePlayer.addChatMessage(msg);
		} else if (wait == 0) {
			pauseWarned = false;
		}
		NetHandlerPlayClient net = mc.getNetHandler();
		if (net == null) {
			return;
		}
		tab.clear();
		rank.clear();
		boolean playing = BedwarsGame.playing();
		// a big lobby (not a game, not its waiting room): those players only get the key's spare requests
		bigLobby = !playing && !BedwarsGame.waitingRoom() && net.getPlayerInfoMap().size() > BIG_LOBBY;
		for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
			UUID id = info.getGameProfile().getId();
			if (id == null) {
				continue;
			}
			// asking for every player in the lobby has their stats ready by the time the game starts
			String[] parts = parts(info, playing);
			if (parts != null) {
				tab.put(id, parts);
			}
		}
		if (startedAt >= 0 && !alerted) {
			maybeAlert(mc);
		}
		if (startedAt >= 0 && alerted && sniper.get() && sniperGear.get() && ticks - startedAt < GEAR_WINDOW_TICKS) {
			checkGear(mc);
		}
	}

	/** Enemies in sight with chainmail armour and an iron sword early in the game: a sniper's buy. */
	private void checkGear(Minecraft mc) {
		char own = BedwarsGame.ownTeam();
		for (net.minecraft.entity.player.EntityPlayer p : mc.theWorld.playerEntities) {
			if (p == mc.thePlayer || snipers.containsKey(p.getUniqueID())) {
				continue;
			}
			ScorePlayerTeam team = mc.theWorld.getScoreboard().getPlayersTeam(p.getName());
			if (team == null || BedwarsGame.teamLetter(team) == 0 || BedwarsGame.teamLetter(team) == own) {
				continue;
			}
			net.minecraft.item.ItemStack legs = p.getCurrentArmor(1);
			net.minecraft.item.ItemStack held = p.getHeldItem();
			if (legs != null && legs.getItem() == net.minecraft.init.Items.chainmail_leggings
				&& held != null && held.getItem() == net.minecraft.init.Items.iron_sword) {
				snipers.put(p.getUniqueID(), "gear");
				send(mc, "§d§lRago §8» " + teamName(team) + " §f" + p.getName() + " §d"
					+ I18n.format("rago.stats.sniper_warn", I18n.format("rago.stats.sniper_gear")));
				ping(mc);
			}
		}
	}

	private void ping(Minecraft mc) {
		if (alertSound.get()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.2f));
		}
	}

	/** Why a player looks like a sniper ("name", "stats", "gear"), or null. */
	private String sniperReason(UUID id, String name, HypixelApi.Stats s) {
		if (!sniper.get()) {
			return null;
		}
		String seen = snipers.get(id);
		if (seen != null) {
			return seen;
		}
		return gg.rago.bedwars.SniperCheck.reason(name, s, sniperName.get(), sniperStats.get(), sniperFinals.get(), sniperFkdr.get());
	}

	/** The text before and after a player's name, or null to leave it as it is; notes how strong the player looks. */
	private String[] parts(NetworkPlayerInfo info, boolean playing) {
		UUID id = info.getGameProfile().getId();
		// NPCs and Hypixel's bots are no players; in a game, every player is in a team
		if (Hypixel.npc(id) || playing && !inTeam(info)) {
			return null;
		}
		String warn = mark.get() ? MARK + " " : "";
		HypixelApi.Stats s = Hypixel.realPlayer(id) ? api.get(id, bigLobby) : null;
		boolean nick = !Hypixel.realPlayer(id) || s != null && !s.found;
		boolean sniped = sniperReason(id, info.getGameProfile().getName(), s) != null;
		rank.put(id, strength(s, nick && nicks.get(), sniped, s != null && strong(s)));
		String sniperTag = sniped ? "§d[S] " : "";
		if (nick) {
			return nicks.get() ? new String[] {warn + "§c[NICK] ", ""} : null;
		}
		if (s == null) {
			return null;
		}
		String before = (strong(s) || !sniperTag.isEmpty() ? warn : "") + sniperTag
			+ (tabStar.get() && s.star >= 0 ? Hypixel.bedwarsStar(s.star) + " " : "");
		// "[300✫] Name 2.5/B": the ratio and the tier only, so a full game stays readable; the menu explains them
		StringBuilder after = new StringBuilder();
		if (tabFkdr.get()) {
			after.append(' ').append(Danger.fkdrColor(s.fkdr())).append(Danger.ratio(s.fkdr()));
		}
		if (tabDanger.get()) {
			int d = Danger.level(s);
			after.append(tabFkdr.get() ? "§8/" : " ").append(Danger.color(d)).append(Danger.tier(d));
		}
		return new String[] {before, after.toString()};
	}

	/**
	 * How strong a player looks, higher first: possible snipers, then nicks (hidden on purpose, often
	 * strong), then players to target ({@code target}: the star or FKDR settings), then the others;
	 * within each, by danger level, final kill/death ratio and star. -1 while the stats are unknown.
	 */
	static double strength(HypixelApi.Stats s, boolean nick, boolean sniper, boolean target) {
		double known = s == null || !s.found ? 0 : Danger.level(s) * 1e5 + Math.min(s.fkdr(), 999) * 100 + Math.min(Math.max(s.star, 0), 9999) / 100.0;
		if (sniper) {
			return 2e6 + known;
		}
		if (nick) {
			return 1e6;
		}
		if (s == null || !s.found) {
			return -1;
		}
		// below 6e5 whatever the level: a player to target comes before any other
		return target ? 6e5 + known : known;
	}

	/**
	 * The tab list's players in Bed Wars when the option is on: grouped by team, the team with the
	 * strongest player first, then the team with the next strongest, and so on, each with all its
	 * players, strongest first, weak ones included. Your own team comes after the enemies, and
	 * spectators (players out of the game) last; players alike keep the game's order.
	 */
	public List<NetworkPlayerInfo> tabOrder(List<NetworkPlayerInfo> vanilla) {
		if (!bedwars || !tabSort.get() || rank.isEmpty()) {
			return vanilla;
		}
		Map<String, Double> teams = new HashMap<>();
		for (NetworkPlayerInfo info : vanilla) {
			teams.merge(teamKey(info), rankOf(info), Math::max);
		}
		char own = BedwarsGame.ownTeam();
		String ownKey = own == 0 ? null : String.valueOf(own);
		List<NetworkPlayerInfo> out = new ArrayList<>(vanilla);
		out.sort((a, b) -> {
			boolean outA = a.getGameType() == WorldSettings.GameType.SPECTATOR;
			boolean outB = b.getGameType() == WorldSettings.GameType.SPECTATOR;
			if (outA != outB) {
				return outA ? 1 : -1;
			}
			String ta = teamKey(a);
			String tb = teamKey(b);
			if (!ta.equals(tb)) {
				boolean ownA = ta.equals(ownKey);
				boolean ownB = tb.equals(ownKey);
				if (ownA != ownB) {
					return ownA ? 1 : -1;
				}
				int c = Double.compare(teams.get(tb), teams.get(ta));
				return c != 0 ? c : ta.compareTo(tb);
			}
			return Double.compare(rankOf(b), rankOf(a));
		});
		return out;
	}

	/**
	 * The team a player is listed with: the letter of their team's prefix ("R" for Red). Hypixel
	 * gives each player a scoreboard team of their own, so the team's name cannot group them.
	 * Players with no team letter (a lobby) are listed together.
	 */
	private static String teamKey(NetworkPlayerInfo info) {
		ScorePlayerTeam team = info.getPlayerTeam();
		char letter = team == null ? 0 : BedwarsGame.teamLetter(team);
		return letter == 0 ? "" : String.valueOf(letter);
	}

	private double rankOf(NetworkPlayerInfo info) {
		Double r = rank.get(info.getGameProfile().getId());
		return r == null ? -1 : r;
	}

	/** A player of the game: in a team whose prefix is its letter ("R " for Red). */
	private static boolean inTeam(NetworkPlayerInfo info) {
		ScorePlayerTeam team = info.getPlayerTeam();
		return team != null && BedwarsGame.teamLetter(team) != 0;
	}

	/** A player to target, as the options say: a high final kill/death ratio or a high star (5 FKDR or 300 stars by default). */
	private boolean strong(HypixelApi.Stats s) {
		return s.found && (s.fkdr() >= strongFkdr.get() || s.star >= strongStar.get());
	}

	/** The final kill/death ratio from which a player is strong, for the legend in the menu. */
	public int strongFkdr() {
		return strongFkdr.get();
	}

	/** The star from which a player is strong, for the legend in the menu. */
	public int strongStar() {
		return strongStar.get();
	}

	/** The name the tab list shows for a player, given the game's. */
	public String tabName(String vanilla, NetworkPlayerInfo info) {
		if (!bedwars || info == null || info.getGameProfile().getId() == null) {
			return vanilla;
		}
		String[] p = tab.get(info.getGameProfile().getId());
		if (p == null) {
			return vanilla;
		}
		// the tab list asks every frame while it is open: reuse the line while nothing changed
		String[] last = tabLines.get(info);
		if (last != null && last[0] == (Object) p[0] && last[1] == (Object) p[1] && vanilla.equals(last[2])) {
			return last[3];
		}
		String line = p[0] + "§r" + vanilla + p[1];
		tabLines.put(info, new String[] {p[0], p[1], vanilla, line});
		return line;
	}

	private final java.util.Map<NetworkPlayerInfo, String[]> tabLines = new java.util.WeakHashMap<>();

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		if (!isEnabled() || event.type == 2 || startedAt >= 0) {
			return;
		}
		String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
		if (text.contains(BedwarsGame.START)) {
			startedAt = ticks;
			alerted = false;
			enemyCount = -1;
		}
	}

	private void maybeAlert(Minecraft mc) {
		if (!alert.get() || !bedwars) {
			if (ticks - startedAt > ALERT_WAIT_TICKS) {
				alerted = true;
			}
			return;
		}
		NetHandlerPlayClient net = mc.getNetHandler();
		if (net == null) {
			return;
		}
		char own = BedwarsGame.ownTeam();
		List<NetworkPlayerInfo> enemies = new ArrayList<>();
		boolean waiting = false;
		for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
			UUID id = info.getGameProfile().getId();
			if (id == null || id.equals(mc.thePlayer.getUniqueID()) || Hypixel.npc(id) || !inTeam(info)
				|| info.getGameType() == WorldSettings.GameType.SPECTATOR) {
				continue;
			}
			ScorePlayerTeam team = info.getPlayerTeam();
			if (own != 0 && team != null && BedwarsGame.teamLetter(team) == own) {
				continue;
			}
			enemies.add(info);
			// stats asked for and on their way (a failed answer, or Hypixel out of reach, is not waited for)
			HypixelApi.Status status = api.status();
			if (Hypixel.realPlayer(id) && api.get(id) == null && api.waiting(id)
				&& (status == HypixelApi.Status.OK || status == HypixelApi.Status.CHECKING)) {
				waiting = true;
			}
		}
		if (enemies.size() != enemyCount) {
			enemyCount = enemies.size();
			enemiesSince = ticks;
		}
		// Hypixel gives out the teams within a moment of the start: go once every enemy is in one
		// (their number holds still) and their stats are in, or after a while with what is known;
		// the stats are asked for from the start, while the teams settle
		boolean settled = !enemies.isEmpty() && own != 0 && ticks - enemiesSince >= ALERT_SETTLE_TICKS
			&& ticks - startedAt >= ALERT_DELAY_TICKS;
		if ((!settled || waiting) && ticks - startedAt < ALERT_WAIT_TICKS) {
			return;
		}
		alerted = true;
		if (enemies.isEmpty()) {
			return;
		}
		RagoClient.LOG.info("Bed Wars Stats: game-start message {} ms after the start, {} enemies{}",
			(ticks - startedAt) * 50, enemies.size(), waiting ? ", some stats still on their way" : "");
		// only the enemies to target (the star or FKDR settings, 300 or 5 by default), strongest first
		List<Object[]> targets = new ArrayList<>();
		for (NetworkPlayerInfo info : enemies) {
			UUID id = info.getGameProfile().getId();
			String name = info.getGameProfile().getName();
			HypixelApi.Stats s = Hypixel.realPlayer(id) ? api.get(id) : null;
			String why = sniperReason(id, name, s);
			if (why != null) {
				snipers.put(id, why);
			}
			if (s != null && strong(s)) {
				ScorePlayerTeam team = info.getPlayerTeam();
				char color = team == null ? 0 : BedwarsGame.teamColor(team);
				targets.add(new Object[] {strength(s, false, why != null, true), targetEntry(name, color == 0 ? "§f" : "§" + color, s, why)});
			}
		}
		targets.sort((a, b) -> Double.compare((Double) b[0], (Double) a[0]));
		StringBuilder line = new StringBuilder("§c§lRago §8» §f").append(I18n.format("rago.stats.alert_targets")).append(' ');
		if (targets.isEmpty()) {
			line.append("§a").append(I18n.format("rago.stats.alert_no_target", strongStar.get(), strongFkdr.get()));
		}
		for (int i = 0; i < targets.size(); i++) {
			line.append(i == 0 ? "" : "§8, ").append(targets.get(i)[1]);
		}
		send(mc, line.toString());
		if (!targets.isEmpty()) {
			ping(mc);
		}
	}

	/**
	 * One enemy to target in the game-start message: the danger sign, the star, the name in the
	 * team's colour, then the final kill/death ratio and the tier (and why they may be a sniper).
	 */
	private static String targetEntry(String name, String teamColor, HypixelApi.Stats s, String sniper) {
		StringBuilder b = new StringBuilder(MARK).append(' ');
		if (sniper != null) {
			b.append("§d[S] ");
		}
		if (s.star >= 0) {
			b.append(Hypixel.bedwarsStar(s.star)).append(' ');
		}
		b.append(teamColor).append(name).append(' ').append(Danger.fkdrColor(s.fkdr())).append(Danger.ratio(s.fkdr()));
		int d = Danger.level(s);
		b.append("§8/").append(Danger.color(d)).append(Danger.tier(d));
		if (sniper != null) {
			b.append(" §d(").append(I18n.format("rago.stats.sniper_warn", I18n.format("rago.stats.sniper_" + sniper))).append("§d)");
		}
		return b.toString();
	}

	/** "§cRed": the team's name in its colour, from the colour of its prefix. */
	private static String teamName(ScorePlayerTeam team) {
		if (team == null) {
			return "§7" + I18n.format("rago.team.unknown");
		}
		char color = BedwarsGame.teamColor(team);
		char letter = BedwarsGame.teamLetter(team);
		if (color == 0) {
			return "§7" + (letter != 0 ? String.valueOf(letter) : I18n.format("rago.team.unknown"));
		}
		return "§" + color + "§l" + I18n.format("rago.team." + color);
	}

	private static void send(Minecraft mc, String text) {
		mc.thePlayer.addChatMessage(new ChatComponentText(text));
	}
}
