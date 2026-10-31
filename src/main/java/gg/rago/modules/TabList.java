package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The look of the tab list: how see-through its background and rows are, whether the server's
 * header and footer show, text shadow and size, and Hypixel's bots left out. Only changes how the
 * list is drawn.
 */
public final class TabList extends Module {
	/** Vanilla's background (black at 50%) and row colour (white at 12.5%). */
	public static final int VANILLA_BACKGROUND = 0x80000000;
	public static final int VANILLA_ROW = 0x20FFFFFF;

	private final IntOption background = addOption(new IntOption("tab_background", 25, 0, 100));
	private final IntOption rows = addOption(new IntOption("tab_rows", 6, 0, 100));
	private final BooleanOption header = addOption(new BooleanOption("tab_header", true));
	private final BooleanOption footer = addOption(new BooleanOption("tab_footer", true));
	private final BooleanOption shadow = addOption(new BooleanOption("shadow", true));
	private final IntOption scale = addOption(new IntOption("tab_scale", 100, 50, 150));
	private final BooleanOption hideBots = addOption(new BooleanOption("tab_hide_bots", true));
	private boolean scaled;

	public TabList() {
		super("tab_list", FeatureClass.VISUAL, true);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** The colour to fill a rectangle of the list with, from the colour vanilla uses. */
	public int rectColor(int vanilla) {
		if (vanilla == VANILLA_BACKGROUND) {
			return alpha(background.get()) << 24;
		}
		if (vanilla == VANILLA_ROW) {
			return alpha(rows.get()) << 24 | 0xFFFFFF;
		}
		return vanilla;
	}

	private static int alpha(int percent) {
		return Math.round(percent * 2.55f);
	}

	/** The header ({@code first}) or footer, or null to hide it. */
	public IChatComponent headerFooter(IChatComponent text, boolean first) {
		return (first ? header : footer).get() ? text : null;
	}

	public int drawText(FontRenderer font, String text, float x, float y, int color) {
		return font.drawString(text, x, y, color, shadow.get());
	}

	/** Whether the tab list's text has a shadow. */
	public boolean shadow() {
		return shadow.get();
	}

	/**
	 * The list without Hypixel's bots: the NPCs it lists with made-up names (shopkeepers with a
	 * skin, for one), when the option is on. Nothing is asked of Hypixel for them.
	 */
	public List<NetworkPlayerInfo> withoutBots(List<NetworkPlayerInfo> list) {
		if (!hideBots.get() || !Hypixel.connected()) {
			return list;
		}
		boolean playing = BedwarsGame.playing();
		List<NetworkPlayerInfo> out = null;
		for (int i = 0; i < list.size(); i++) {
			NetworkPlayerInfo info = list.get(i);
			ScorePlayerTeam team = info.getPlayerTeam();
			boolean bot = bot(info.getGameProfile().getId(), info.getGameProfile().getName(),
				playing && info.getGameType() != WorldSettings.GameType.SPECTATOR && (team == null || BedwarsGame.teamLetter(team) == 0));
			if (bot && out == null) {
				out = new ArrayList<>(list.subList(0, i));
			} else if (!bot && out != null) {
				out.add(info);
			}
		}
		return out == null ? list : out;
	}

	/**
	 * Hypixel's NPCs have version 2 UUIDs. In a Bed Wars game, where every player has a team
	 * letter, an entry with none ({@code teamless}) and a name of ten lower-case letters and
	 * digits ("38qx60s71a") is one too.
	 */
	static boolean bot(UUID id, String name, boolean teamless) {
		if (Hypixel.npc(id)) {
			return true;
		}
		if (!teamless || name == null || name.length() != 10) {
			return false;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9')) {
				return false;
			}
		}
		return true;
	}

	/** Start of the list: scales it around the top centre of the screen. */
	public void begin(int width) {
		scaled = scale.get() != 100;
		if (scaled) {
			float s = scale.get() / 100f;
			GlStateManager.pushMatrix();
			GlStateManager.translate(width / 2f, 0, 0);
			GlStateManager.scale(s, s, 1);
			GlStateManager.translate(-width / 2f, 0, 0);
		}
	}

	public void end() {
		if (scaled) {
			GlStateManager.popMatrix();
			scaled = false;
		}
	}
}
