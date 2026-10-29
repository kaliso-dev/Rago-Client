package gg.rago.modules;

import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;

/**
 * The colour players and mobs flash when they take damage (vanilla red at 30%). The colour's own
 * opacity sets how strong the flash is.
 */
public final class HitColor extends Module {
	private final ColorOption color = addOption(new ColorOption("hit_color", 0x4DFF0000));

	public HitColor() {
		super("hit_color", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	/** Component {@code index} (0 red, 1 green, 2 blue, 3 strength) from 0 to 1. */
	public float component(int index) {
		int c = color.get();
		switch (index) {
			case 0:
				return (c >> 16 & 0xFF) / 255f;
			case 1:
				return (c >> 8 & 0xFF) / 255f;
			case 2:
				return (c & 0xFF) / 255f;
			default:
				return (c >>> 24) / 255f;
		}
	}
}
