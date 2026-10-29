package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hypixel Bed Wars stats in the tab list: each player's star before their name and their final
 * kill/death ratio after it, coloured by how dangerous they are (Rago's own 1 to 5 level, see
 * {@link Danger}), and nicked players marked. When a game starts, a chat message lists the most
 * dangerous players against you. The stats come from the Hypixel API with your own key
 * ({@code /rago hypixelkey <key>}); nothing is sent to the Minecraft server.
 */
public final class BedwarsStats extends Module {
	/** How long the game-start alert waits for stats still on their way. */
	private static final int ALERT_WAIT_TICKS = 12 * 20;
	private static final int ALERT_MAX = 5;

	private final BooleanOption tabStar = addOption(new BooleanOption("stats_tab_star", true));
	private final BooleanOption tabFkdr = addOption(new BooleanOption("stats_tab_fkdr", true));
	private final BooleanOption tabDanger = addOption(new BooleanOption("stats_tab_danger", false));
	private final BooleanOption nicks = addOption(new BooleanOption("stats_nicks", true));
	private final BooleanOption mark = addOption(new BooleanOption("stats_tab_mark", true));
	/** A strong player: this final kill/death ratio or more... */
	private final IntOption strongFkdr = addOption(new IntOption("stats_strong_fkdr", 10, 1, 50));
	/** ...or this Bed Wars star or more. */
	private final IntOption strongStar = addOption(new IntOption("stats_strong_star", 300, 50, 2000));
	private final BooleanOption alert = addOption(new BooleanOption("stats_alert", true));
	private final BooleanOption alertSound = addOption(new BooleanOption("stats_alert_sound", true));

	private final HypixelApi api;
	/** Before and after the name in the tab, by player; worked out each second, not each frame. */
	private final Map<UUID, String[]> tab = new HashMap<>();
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

	public BedwarsStats(HypixelApi api) {
		super("bedwars_stats", FeatureClass.INFORMATIONAL, false);
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
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			wasPlaying = false;
			tab.clear();
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
			}
			wasPlaying = playing;
		}
		if (mc.thePlayer == null || ticks % 20 != 0) {
			if (startedAt >= 0 && !alerted && mc.thePlayer != null && ticks % 5 == 0) {
				maybeAlert(mc);
			}
			return;
		}
		bedwars = BedwarsGame.inBedwars();
		if (!bedwars) {
			tab.clear();
			return;
		}
		HypixelApi.Status s = api.status();
		if (!warned && (s == HypixelApi.Status.NO_KEY || s == HypixelApi.Status.BAD_KEY)) {
			warned = true;
			ChatComponentTranslation msg = new ChatComponentTranslation(s == HypixelApi.Status.NO_KEY ? "rago.stats.no_key" : "rago.levelhead.bad_key");
			msg.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
			mc.thePlayer.addChatMessage(msg);
		}
		NetHandlerPlayClient net = mc.getNetHandler();
		if (net == null) {
			return;
		}
		tab.clear();
		boolean playing = BedwarsGame.playing();
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
	}

	/** The text before and after a player's name, or null to leave it as it is. */
	private String[] parts(NetworkPlayerInfo info, boolean playing) {
		UUID id = info.getGameProfile().getId();
		// NPCs and Hypixel's bots are no players; in a game, every player is in a team
		if (Hypixel.npc(id) || playing && !inTeam(info)) {
			return null;
		}
		String warn = mark.get() ? MARK + " " : "";
		if (!Hypixel.realPlayer(id)) {
			return nicks.get() ? new String[] {warn + "§c[NICK] ", ""} : null;
		}
		HypixelApi.Stats s = api.get(id);
		if (s == null) {
			return null;
		}
		if (!s.found) {
			return nicks.get() ? new String[] {warn + "§c[NICK] ", ""} : null;
		}
		String before = (strong(s) ? warn : "") + (tabStar.get() && s.star >= 0 ? Hypixel.bedwarsStar(s.star) + " " : "");
		StringBuilder after = new StringBuilder();
		// each number with its name, so the list reads on its own
		if (tabFkdr.get()) {
			after.append(" §8| §7FKDR ").append(Danger.fkdrColor(s.fkdr())).append(Danger.ratio(s.fkdr()));
		}
		if (tabDanger.get()) {
			int d = Danger.level(s);
			after.append(" §8| §7").append(I18n.format("rago.stats.danger_short")).append(' ').append(Danger.color(d)).append(d).append("§7/5");
		}
		return new String[] {before, after.toString()};
	}

	/** A player of the game: in a team whose prefix is its letter ("R " for Red). */
	private static boolean inTeam(NetworkPlayerInfo info) {
		ScorePlayerTeam team = info.getPlayerTeam();
		return team != null && BedwarsGame.teamLetter(team) != 0;
	}

	/** A player the options call strong: a high final kill/death ratio or a high star. */
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
		return p == null ? vanilla : p[0] + "§r" + vanilla + p[1];
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		if (!isEnabled() || event.type == 2 || startedAt >= 0) {
			return;
		}
		String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
		if (text.contains(BedwarsGame.START)) {
			startedAt = ticks;
			alerted = false;
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
		ScorePlayerTeam own = mc.theWorld.getScoreboard().getPlayersTeam(mc.thePlayer.getName());
		List<NetworkPlayerInfo> enemies = new ArrayList<>();
		boolean waiting = false;
		for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
			UUID id = info.getGameProfile().getId();
			if (id == null || id.equals(mc.thePlayer.getUniqueID()) || Hypixel.npc(id) || !inTeam(info)
				|| info.getGameType() == WorldSettings.GameType.SPECTATOR) {
				continue;
			}
			ScorePlayerTeam team = info.getPlayerTeam();
			if (own != null && team != null && own.getRegisteredName().equals(team.getRegisteredName())) {
				continue;
			}
			enemies.add(info);
			if (Hypixel.realPlayer(id) && api.get(id) == null && (api.status() == HypixelApi.Status.OK || api.status() == HypixelApi.Status.CHECKING)) {
				waiting = true;
			}
		}
		if (waiting && ticks - startedAt < ALERT_WAIT_TICKS) {
			return;
		}
		alerted = true;
		// enemies to watch, by team: strong players first, then nicks
		Map<String, List<String>> byTeam = new LinkedHashMap<>();
		Map<String, Double> teamThreat = new HashMap<>();
		Map<String, String> teamLabel = new HashMap<>();
		for (NetworkPlayerInfo info : enemies) {
			UUID id = info.getGameProfile().getId();
			String name = info.getGameProfile().getName();
			HypixelApi.Stats s = Hypixel.realPlayer(id) ? api.get(id) : null;
			String entry;
			double threat;
			if (!Hypixel.realPlayer(id) || s != null && !s.found) {
				if (!nicks.get()) {
					continue;
				}
				entry = MARK + " §c" + name + " §7(" + I18n.format("rago.stats.nick") + ")";
				threat = 5;
			} else if (s != null && strong(s)) {
				entry = MARK + " " + (s.star >= 0 ? Hypixel.bedwarsStar(s.star) + " " : "") + "§f" + name
					+ " §7(FKDR " + Danger.fkdrColor(s.fkdr()) + Danger.ratio(s.fkdr()) + "§7)";
				threat = s.fkdr();
			} else {
				continue;
			}
			ScorePlayerTeam team = info.getPlayerTeam();
			String key = team == null ? "?" : team.getRegisteredName();
			if (!teamLabel.containsKey(key)) {
				teamLabel.put(key, teamName(team));
			}
			byTeam.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
			teamThreat.merge(key, threat, Math::max);
		}
		List<String> order = new ArrayList<>(byTeam.keySet());
		order.sort((a, b) -> Double.compare(teamThreat.get(b), teamThreat.get(a)));
		send(mc, "§c§lRago §8» " + (order.isEmpty() ? "§a" + I18n.format("rago.stats.alert_none") : "§f" + I18n.format("rago.stats.alert_title")));
		for (String key : order) {
			send(mc, "  " + teamLabel.get(key) + " §8» " + String.join("§8, ", byTeam.get(key)));
		}
		if (alertSound.get() && !order.isEmpty()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.2f));
		}
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
