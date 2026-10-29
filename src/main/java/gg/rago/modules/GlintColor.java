package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.Paint;

/** The colour of the enchantment glint on items and armour, or a rainbow. */
public final class GlintColor extends Module {
	/** Vanilla's purple, the same on items (0xFF8040CC) and armour. */
	public static final int VANILLA = 0xFF8040CC;

	private final ColorOption color = addOption(new ColorOption("glint_color", 0xFFFF3B3B));
	private final BooleanOption chroma = addOption(new BooleanOption("glint_chroma", false));

	public GlintColor() {
		super("glint_color", FeatureClass.VISUAL, false);
		color.showIf(() -> !chroma.get());
	}

	/** The glint colour as ARGB. */
	public int argb() {
		return chroma.get() ? Paint.chroma(0, 0xFF) : color.get() | 0xFF000000;
	}
}
