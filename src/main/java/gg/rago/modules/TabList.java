package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The look of the tab list: how see-through its background and rows are, the server's header and
 * footer (whole, without their ads, or hidden), text shadow and size, and Hypixel's bots left out.
 * Only changes how the list is drawn.
 */
public final class TabList extends Module {
	/** Vanilla's background (black at 50%) and row colour (white at 12.5%). */
	public static final int VANILLA_BACKGROUND = 0x80000000;
	public static final int VANILLA_ROW = 0x20FFFFFF;

	private final IntOption background = addOption(new IntOption("tab_background", 25, 0, 100));
	private final IntOption rows = addOption(new IntOption("tab_rows", 6, 0, 100));
	/** The server's header and footer: "all", "no_ads" (the lines with a web address left out) or "hidden". */
	private final ChoiceOption header = addOption(new ChoiceOption("tab_header_mode", "no_ads", "all", "no_ads", "hidden"));
	private final ChoiceOption footer = addOption(new ChoiceOption("tab_footer_mode", "no_ads", "all", "no_ads", "hidden"));
	/** A line with a web address (STORE.HYPIXEL.NET, a Discord invite): an ad. */
	private static final Pattern AD = Pattern.compile(
		"(?i)\\b[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(net|com|org|gg|io|fr|eu|co|me|us|club|xyz|pw|tv|uk|de|es|it|nl|pl|ru|be|ch|ca)\\b");
	/** The header and footer last given, and what is shown of them, made again only when the server sends new ones. */
	private final IChatComponent[] shownFrom = new IChatComponent[2];
	private final IChatComponent[] shown = new IChatComponent[2];
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

	/** The header ({@code first}) or footer as shown, or null to hide it. */
	public IChatComponent headerFooter(IChatComponent text, boolean first) {
		ChoiceOption mode = first ? header : footer;
		if (text == null || mode.is("all")) {
			return text;
		}
		if (mode.is("hidden")) {
			return null;
		}
		int i = first ? 0 : 1;
		if (shownFrom[i] != text) {
			shownFrom[i] = text;
			String all = text.getFormattedText();
			String kept = withoutAds(all);
			shown[i] = kept == null ? null : kept.equals(all) ? text : new ChatComponentText(kept);
		}
		return shown[i];
	}

	/**
	 * {@code text} (lines with § codes) without its ads, the lines with a web address in them, and
	 * without the empty lines left around them; each line kept starts with the codes in force where it
	 * was. Null when nothing is left.
	 */
	static String withoutAds(String text) {
		String[] lines = text.split("\n", -1);
		List<String> kept = new ArrayList<>();
		StringBuilder before = new StringBuilder();
		boolean removed = false;
		for (String line : lines) {
			String codes = RagoBadge.codesAt(before.toString());
			before.append(line).append('\n');
			if (AD.matcher(plain(line)).find()) {
				removed = true;
				continue;
			}
			boolean blank = plain(line).trim().isEmpty();
			if (blank && (kept.isEmpty() || kept.get(kept.size() - 1) == null)) {
				continue; // no empty line at the top, nor two in a row
			}
			kept.add(blank ? null : codes + line);
		}
		if (!removed) {
			return text;
		}
		while (!kept.isEmpty() && kept.get(kept.size() - 1) == null) {
			kept.remove(kept.size() - 1);
		}
		if (kept.isEmpty()) {
			return null;
		}
		StringBuilder out = new StringBuilder();
		for (String line : kept) {
			if (out.length() > 0) {
				out.append('\n');
			}
			out.append(line == null ? "" : line);
		}
		return out.toString();
	}

	private static String plain(String s) {
		return s.replaceAll("\u00a7.", "");
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
