package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.ui.TextColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;

/** Shows each player's ping in milliseconds in the tab list instead of the signal bars. */
public final class TabPing extends Module {
	private final BooleanOption colored = addOption(new BooleanOption("colored_ping", true));
	private final BooleanOption shadow = addOption(new BooleanOption("shadow", true));

	public TabPing() {
		super("tab_ping", FeatureClass.INFORMATIONAL, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	/**
	 * Draws the ping right-aligned in the 11 pixels vanilla keeps for the bars, at half size so
	 * four digits fit. Returns false (vanilla draws its bars) for players without a ping yet.
	 */
	public boolean draw(int width, int x, int y, NetworkPlayerInfo info) {
		int ms = info.getResponseTime();
		if (ms <= 0) {
			return false;
		}
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		String s = Integer.toString(Math.min(ms, 9999));
		int right = x + width - 1;
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, 0, 100);
		GlStateManager.scale(0.5f, 0.5f, 1f);
		int color = colored.get() ? color(ms) : 0xFFFFFFFF;
		float tx = right * 2f - font.getStringWidth(s);
		float ty = y * 2f + 4;
		font.drawString(s, tx, ty, color, shadow.get());
		GlStateManager.popMatrix();
		GlStateManager.color(1, 1, 1, 1);
		return true;
	}

	/** Green below 75 ms, yellow below 150, orange below 300, red above. */
	static int color(int ms) {
		return ms < 75 ? TextColors.argb('a') : ms < 150 ? TextColors.argb('e') : ms < 300 ? TextColors.argb('6') : TextColors.argb('c');
	}
}
