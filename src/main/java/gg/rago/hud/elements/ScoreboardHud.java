package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.GuiIngameForge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The sidebar scoreboard as a movable HUD element: position, scale, background opacity, and the
 * red score numbers can be hidden. Shows the same objective vanilla would, including the one for
 * your team's colour.
 */
public final class ScoreboardHud extends HudElement {
	private static final int MAX_LINES = 15;

	private final BooleanOption numbers = addOption(new BooleanOption("show_numbers", false));
	private final IntOption opacity = addOption(new IntOption("background_opacity", 30, 0, 100));
	private final BooleanOption hide = addOption(new BooleanOption("hide_scoreboard", false));

	public ScoreboardHud() {
		super("scoreboard", FeatureClass.VISUAL, false, Anchor.MIDDLE_RIGHT, -1, 0, 0xFFFFFFFF, true);
	}

	/** Vanilla's own sidebar is off while this element replaces it. */
	@Override
	public void tick() {
		GuiIngameForge.renderObjective = false;
	}

	@Override
	protected void onDisable() {
		GuiIngameForge.renderObjective = true;
	}

	/** The sidebar objective vanilla would show: your team colour's slot first, then the sidebar. */
	private static ScoreObjective objective() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) {
			return null;
		}
		Scoreboard board = mc.theWorld.getScoreboard();
		ScorePlayerTeam team = board.getPlayersTeam(mc.thePlayer.getName());
		if (team != null) {
			int color = team.getChatFormat().getColorIndex();
			if (color >= 0) {
				ScoreObjective o = board.getObjectiveInDisplaySlot(3 + color);
				if (o != null) {
					return o;
				}
			}
		}
		return board.getObjectiveInDisplaySlot(1);
	}

	/** Title first, then the lines from top to bottom, each as {name, score}. */
	private static List<String[]> lines(boolean placeholder) {
		List<String[]> out = new ArrayList<>();
		ScoreObjective o = placeholder ? null : objective();
		if (o == null) {
			if (placeholder) {
				out.add(new String[] {EnumChatFormatting.YELLOW + "" + EnumChatFormatting.BOLD + "BED WARS", ""});
				for (String s : Arrays.asList("", "Diamond II in 4:12", "", EnumChatFormatting.RED + "R " + EnumChatFormatting.WHITE + "Red: " + EnumChatFormatting.GREEN + "✔",
					EnumChatFormatting.BLUE + "B " + EnumChatFormatting.WHITE + "Blue: " + EnumChatFormatting.GREEN + "✔", "", "Kills: 3", "Final Kills: 1", "",
					EnumChatFormatting.YELLOW + "www.hypixel.net")) {
					out.add(new String[] {s, Integer.toString(10 - out.size())});
				}
			}
			return out;
		}
		Scoreboard board = o.getScoreboard();
		List<Score> scores = new ArrayList<>();
		Collection<Score> all = board.getSortedScores(o);
		for (Score s : all) {
			if (s.getPlayerName() != null && !s.getPlayerName().startsWith("#")) {
				scores.add(s);
			}
		}
		if (scores.size() > MAX_LINES) {
			scores = scores.subList(scores.size() - MAX_LINES, scores.size());
		}
		out.add(new String[] {o.getDisplayName(), ""});
		// sorted lowest first; the highest score is drawn at the top
		for (int i = scores.size() - 1; i >= 0; i--) {
			Score s = scores.get(i);
			ScorePlayerTeam team = board.getPlayersTeam(s.getPlayerName());
			out.add(new String[] {ScorePlayerTeam.formatPlayerName(team, s.getPlayerName()), Integer.toString(s.getScorePoints())});
		}
		return out;
	}

	@Override
	public boolean replacesVanilla() {
		return true;
	}

	@Override
	public boolean hasContent() {
		return !hide.get() && objective() != null;
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		int w = 0;
		List<String[]> lines = lines(placeholder);
		for (int i = 0; i < lines.size(); i++) {
			String[] l = lines.get(i);
			int lw = text.getStringWidth(l[0]);
			if (i > 0 && numbers.get()) {
				lw += text.getStringWidth(": " + l[1]);
			}
			w = Math.max(w, lw);
		}
		return w + 4;
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		int n = lines(placeholder).size();
		return n == 0 ? 0 : n * text.FONT_HEIGHT + 1;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		List<String[]> lines = lines(placeholder);
		if (lines.isEmpty()) {
			return;
		}
		int w = width(text, placeholder);
		int h = text.FONT_HEIGHT;
		int alpha = Math.round(opacity.get() * 2.55f);
		boolean bg = background.get() && alpha > 0;
		int color = textColor.get();
		for (int i = 0; i < lines.size(); i++) {
			String[] l = lines.get(i);
			int y = i == 0 ? 0 : i * h + 1;
			if (bg) {
				// the title row is a little darker, like vanilla's
				Gui.drawRect(0, y, w, y + h + (i == 0 ? 1 : 0), Math.min(255, alpha + (i == 0 ? 16 : 0)) << 24);
			}
			if (i == 0) {
				drawText(text, l[0], (w - text.getStringWidth(l[0])) / 2, y + 1, color);
			} else {
				drawText(text, l[0], 2, y, color);
				if (numbers.get()) {
					String n = EnumChatFormatting.RED + l[1];
					drawText(text, n, w - 2 - text.getStringWidth(n), y, color);
				}
			}
		}
	}
}
