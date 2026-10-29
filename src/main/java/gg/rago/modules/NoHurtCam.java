package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;

/** Reduces or removes the camera tilt when you take damage. The tilt when you die is kept. */
public final class NoHurtCam extends Module {
	private final IntOption strength = addOption(new IntOption("shake_strength", 0, 0, 100));

	public NoHurtCam() {
		super("no_hurt_cam", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	/** How much of vanilla's tilt to keep, 0 to 1. */
	public float strength() {
		return strength.get() / 100f;
	}
}
