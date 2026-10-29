package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;

/**
 * The look of the tab list: how see-through its background and rows are, whether the server's
 * header and footer show, text shadow and size. Only changes how the list is drawn.
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
