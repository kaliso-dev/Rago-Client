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
	private final BooleanOption alert = addOption(new BooleanOption("stats_alert", true));
	private final IntOption alertMin = addOption(new IntOption("stats_alert_min", 4, 1, 5));
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

	public BedwarsStats(HypixelApi api) {
		super("bedwars_stats", FeatureClass.INFORMATIONAL, false);
		this.api = api;
		alertMin.showIf(alert::get);
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
			startedAt = -1;
			alerted = false;
			tab.clear();
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
		for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
			UUID id = info.getGameProfile().getId();
			if (id == null) {
				continue;
			}
			// asking for every player in the lobby has their stats ready by the time the game starts
			String[] parts = parts(id);
			if (parts != null) {
				tab.put(id, parts);
			}
		}
		if (startedAt >= 0 && !alerted) {
			maybeAlert(mc);
		}
	}

	/** The text before and after a player's name, or null to leave it as it is. */
	private String[] parts(UUID id) {
		if (!Hypixel.realPlayer(id)) {
			return nicks.get() ? new String[] {"§c[NICK] ", ""} : null;
		}
		HypixelApi.Stats s = api.get(id);
		if (s == null) {
			return null;
		}
		if (!s.found) {
			return nicks.get() ? new String[] {"§c[NICK] ", ""} : null;
		}
		String before = tabStar.get() && s.star >= 0 ? Hypixel.bedwarsStar(s.star) + " " : "";
		StringBuilder after = new StringBuilder();
		if (tabFkdr.get()) {
			after.append(" §7• ").append(Danger.color(Danger.fkdrScore(s.fkdr()))).append(Danger.ratio(s.fkdr()));
		}
		if (tabDanger.get()) {
			int d = Danger.level(s);
			after.append(" §8[").append(Danger.color(d)).append(d).append("§8]");
		}
		return new String[] {before, after.toString()};
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
			if (id == null || id.equals(mc.thePlayer.getUniqueID())) {
				continue;
			}
			ScorePlayerTeam team = info.getPlayerTeam();
			if (own != null && team != null && own.getRegisteredName().equals(team.getRegisteredName())) {
				continue;
			}
			enemies.add(info);
			if (Hypixel.realPlayer(id) && api.get(id) == null && api.status() == HypixelApi.Status.OK) {
				waiting = true;
			}
		}
		if (waiting && ticks - startedAt < ALERT_WAIT_TICKS) {
			return;
		}
		alerted = true;
		List<Object[]> dangerous = new ArrayList<>();
		List<String> nicked = new ArrayList<>();
		for (NetworkPlayerInfo info : enemies) {
			UUID id = info.getGameProfile().getId();
			String name = info.getGameProfile().getName();
			HypixelApi.Stats s = Hypixel.realPlayer(id) ? api.get(id) : null;
			if (!Hypixel.realPlayer(id) || s != null && !s.found) {
				nicked.add(name);
			} else if (s != null && Danger.level(s) >= alertMin.get()) {
				dangerous.add(new Object[] {name, s});
			}
		}
		dangerous.sort((a, b) -> Double.compare(((HypixelApi.Stats) b[1]).fkdr(), ((HypixelApi.Stats) a[1]).fkdr()));
		send(mc, "§c§lRago §8» " + (dangerous.isEmpty() ? "§a" + I18n.format("rago.stats.alert_none") : "§f" + I18n.format("rago.stats.alert_title")));
		for (int i = 0; i < Math.min(ALERT_MAX, dangerous.size()); i++) {
			String name = (String) dangerous.get(i)[0];
			HypixelApi.Stats s = (HypixelApi.Stats) dangerous.get(i)[1];
			int d = Danger.level(s);
			send(mc, "  " + (s.star >= 0 ? Hypixel.bedwarsStar(s.star) + " " : "") + Danger.color(d) + name
				+ " §8- §7FKDR " + Danger.color(Danger.fkdrScore(s.fkdr())) + Danger.ratio(s.fkdr())
				+ " §8- " + Danger.color(d) + I18n.format("rago.stats.danger", d));
		}
		if (nicks.get() && !nicked.isEmpty()) {
			send(mc, "  §c[NICK] §7" + String.join("§8, §7", nicked));
		}
		if (alertSound.get() && !dangerous.isEmpty()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.2f));
		}
	}

	private static void send(Minecraft mc, String text) {
		mc.thePlayer.addChatMessage(new ChatComponentText(text));
	}
}
