package gg.rago.hud.elements;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.bedwars.Quests;
import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.RagoClient;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import gg.rago.hypixel.HypixelApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.UUID;

/**
 * Your Hypixel Bed Wars daily and weekly quests and how much is left of each ("Final kills 7/15"),
 * updated live during the game. The starting point comes from the Hypixel API with your own key.
 */
public final class BedwarsQuestsHud extends TextHudElement {
	/** How long after a game ends Hypixel is given to save it before Rago asks again. */
	private static final int REFRESH_AFTER_GAME_TICKS = 20 * 20;
	/** Out of a game, your quests are asked for again this often, to see the ones just accepted. */
	private static final int LOBBY_REFRESH_TICKS = 60 * 20;
	/** After a menu closes in a lobby (the quest master's, say), the API is asked again this soon. */
	private static final int AFTER_MENU_TICKS = 3 * 20;

	private final BooleanOption daily = addOption(new BooleanOption("quests_daily", true));
	private final BooleanOption weekly = addOption(new BooleanOption("quests_weekly", true));
	private final BooleanOption showDone = addOption(new BooleanOption("quests_show_done", true));
	private final BooleanOption showNotStarted = addOption(new BooleanOption("quests_show_not_started", false));
	private final BooleanOption onlyBedwars = addOption(new BooleanOption("quests_only_bedwars", true));

	private final Quests quests = new Quests();
	private int ticks;
	private boolean inGame;
	private int refreshAt = -1;
	private String lastTitle = "";
	private String text = "";
	private boolean asked;
	private boolean shown;
	private boolean menuOpen;
	private int lastAsked;
	private World world;

	public BedwarsQuestsHud() {
		super("bedwars_quests", FeatureClass.INFORMATIONAL, false, Anchor.TOP_RIGHT, -4, 150);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	public boolean hasContent() {
		return shown && !text.isEmpty();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.thePlayer == null) {
			return;
		}
		HypixelApi api = RagoClient.hypixel();
		UUID me = mc.thePlayer.getUniqueID();
		if (!asked) {
			// the stats cached for the tab list may be minutes old: start from fresh ones
			asked = true;
			api.refresh(me);
		}
		quests.update(api.get(me), System.currentTimeMillis());
		if (mc.theWorld != world) {
			// a game left before its end counts nothing more
			world = mc.theWorld;
			inGame = false;
		}
		if (!inGame && BedwarsGame.playing()) {
			inGame = true;
		}
		readTitle();
		boolean menu = mc.currentScreen instanceof GuiContainer;
		if (menuOpen && !menu && !inGame && BedwarsGame.inBedwars()) {
			// quests are accepted at the quest master, in a chest menu
			refreshAt = ticks + AFTER_MENU_TICKS;
		}
		menuOpen = menu;
		if (!inGame && refreshAt < 0 && ticks - lastAsked >= LOBBY_REFRESH_TICKS && BedwarsGame.inBedwars()) {
			refreshAt = ticks;
		}
		if (refreshAt >= 0 && ticks >= refreshAt) {
			refreshAt = -1;
			lastAsked = ticks;
			api.refresh(me);
		}
		if (ticks % 10 == 0) {
			text = build(api.status());
			shown = !onlyBedwars.get() || BedwarsGame.inBedwars();
		}
	}

	@Override
	protected void onEnable() {
		asked = false;
	}

	/** "VICTORY!" or "GAME OVER!" ends the game; titles fire no event. */
	private void readTitle() {
		String t = BedwarsGame.title();
		if (t.equals(lastTitle)) {
			return;
		}
		lastTitle = t;
		boolean won = BedwarsGame.isVictory(t);
		if (inGame && (won || BedwarsGame.isGameOver(t))) {
			inGame = false;
			if (won) {
				quests.count(Quests.Kind.WIN);
			}
			quests.count(Quests.Kind.GAME);
			refreshAt = ticks + REFRESH_AFTER_GAME_TICKS;
		}
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || event.type == 2 || mc.thePlayer == null) {
			return;
		}
		String line = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText()).trim();
		if (line.contains(BedwarsGame.START)) {
			inGame = true;
			return;
		}
		if (!inGame && !BedwarsGame.playing()) {
			return;
		}
		String name = mc.thePlayer.getName();
		if (BedwarsGame.isOwnFinalKill(line, name)) {
			quests.count(Quests.Kind.FINAL_KILL);
		} else if (BedwarsGame.isOwnBed(line, name)) {
			quests.count(Quests.Kind.BED);
		}
	}

	private String build(HypixelApi.Status status) {
		if (status == HypixelApi.Status.NO_KEY || status == HypixelApi.Status.BAD_KEY) {
			return "§c" + I18n.format(status == HypixelApi.Status.NO_KEY ? "rago.quests.no_key" : "rago.quests.bad_key");
		}
		if (!quests.known()) {
			return "§7" + I18n.format("rago.quests.loading");
		}
		long now = System.currentTimeMillis();
		StringBuilder b = new StringBuilder();
		for (boolean week : new boolean[] {false, true}) {
			if (week ? !weekly.get() : !daily.get()) {
				continue;
			}
			StringBuilder lines = new StringBuilder();
			for (Quests.Quest q : Quests.ALL) {
				if (q.weekly == week) {
					String l = line(q, quests.state(q, now), now);
					if (l != null) {
						lines.append('\n').append(l);
					}
				}
			}
			if (lines.length() > 0) {
				b.append(b.length() > 0 ? "\n" : "").append("§6§l").append(I18n.format(week ? "rago.quests.weekly" : "rago.quests.daily")).append(lines);
			}
		}
		return b.toString();
	}

	private String line(Quests.Quest q, Quests.State state, long now) {
		String name = I18n.format("rago.quest." + q.id);
		switch (state) {
			case DONE:
				return showDone.get() ? "§a" + name + " §2" + I18n.format("rago.quests.done") : null;
			case RUNNING:
				int n = quests.progress(q, now);
				return "§f" + name + " §e" + n + "§7/" + q.target;
			default:
				return showNotStarted.get() ? "§8" + name + " §8" + I18n.format("rago.quests.not_started") : null;
		}
	}

	@Override
	protected String value(boolean placeholder) {
		if (placeholder) {
			return "§6§l" + I18n.format("rago.quests.daily")
				+ "\n§f" + I18n.format("rago.quest.bedwars_daily_final_killer") + " §e7§7/15"
				+ "\n§a" + I18n.format("rago.quest.bedwars_daily_win") + " §2" + I18n.format("rago.quests.done")
				+ "\n§6§l" + I18n.format("rago.quests.weekly")
				+ "\n§f" + I18n.format("rago.quest.bedwars_weekly_final_killer") + " §e47§7/150";
		}
		return text;
	}
}
