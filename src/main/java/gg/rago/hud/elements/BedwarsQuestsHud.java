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
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Your Hypixel Bed Wars daily and weekly quests and how much is left of each ("Final kills 7/15"),
 * updated live during the game. The starting point comes from the Hypixel API with your own key.
 */
public final class BedwarsQuestsHud extends TextHudElement {
	/** How long after a game ends Hypixel is given to save it before Rago asks again. */
	private static final int REFRESH_AFTER_GAME_TICKS = 20 * 20;

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
		readTitle();
		if (refreshAt >= 0 && ticks >= refreshAt) {
			refreshAt = -1;
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
		String up = t.toUpperCase(Locale.ROOT);
		if (inGame && (up.contains("VICTORY") || up.contains("GAME OVER"))) {
			inGame = false;
			if (up.contains("VICTORY")) {
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
		if (!inGame) {
			return;
		}
		String name = mc.thePlayer.getName();
		if (Quests.isOwnFinalKill(line, name)) {
			quests.count(Quests.Kind.FINAL_KILL);
		} else if (Quests.isOwnBed(line, name)) {
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
