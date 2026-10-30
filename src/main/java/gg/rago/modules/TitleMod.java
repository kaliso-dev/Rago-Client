package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/**
 * The size of the big titles servers show ("VICTORY!", "TRAP TRIGGERED!") and of the subtitle
 * under them, or no titles at all. A long title can shrink to fit the screen. Only changes how
 * your client draws them.
 */
public final class TitleMod extends Module {
	/** Vanilla draws the title at 4 times the font size and the subtitle at 2. */
	private static final float VANILLA_TITLE = 4f;
	/** Space kept on each side of a title shrunk to fit, in GUI pixels. */
	private static final int MARGIN = 4;

	private final IntOption titleSize = addOption(new IntOption("title_scale", 100, 20, 200));
	private final IntOption subtitleSize = addOption(new IntOption("subtitle_scale", 100, 20, 200));
	private final BooleanOption fit = addOption(new BooleanOption("title_fit", true));
	private final BooleanOption hide = addOption(new BooleanOption("title_hide", false));

	private Field titleField;
	private Field subtitleField;
	private boolean fieldsMissing;

	public TitleMod() {
		super("title_mod", FeatureClass.VISUAL, false);
		titleSize.showIf(() -> !hide.get());
		subtitleSize.showIf(() -> !hide.get());
		fit.showIf(() -> !hide.get());
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	public boolean hides() {
		return hide.get();
	}

	/**
	 * The scale for the title ({@code vanilla} 4) or the subtitle ({@code vanilla} 2) on a screen
	 * {@code width} GUI pixels wide.
	 */
	public float scale(float vanilla, int width) {
		boolean title = vanilla == VANILLA_TITLE;
		float s = vanilla * (title ? titleSize.get() : subtitleSize.get()) / 100f;
		if (fit.get()) {
			String text = text(title);
			int w = text == null ? 0 : Minecraft.getMinecraft().fontRendererObj.getStringWidth(text);
			if (w > 0 && w * s > width - MARGIN * 2) {
				s = Math.max(0.5f, (width - MARGIN * 2) / (float) w);
			}
		}
		return s;
	}

	private String text(boolean title) {
		GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
		if (fieldsMissing || gui == null) {
			return null;
		}
		try {
			if (titleField == null) {
				titleField = ReflectionHelper.findField(GuiIngame.class, "displayedTitle", "field_175201_x");
				subtitleField = ReflectionHelper.findField(GuiIngame.class, "displayedSubTitle", "field_175200_y");
			}
			return (String) (title ? titleField : subtitleField).get(gui);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fieldsMissing = true;
			RagoClient.LOG.warn("Title Mod cannot read titles, long ones will not shrink: {}", e.toString());
			return null;
		}
	}
}
