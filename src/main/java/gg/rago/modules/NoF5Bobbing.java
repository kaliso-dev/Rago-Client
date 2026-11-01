package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;

/**
 * View bobbing only in first person: in third person (F5, behind or in front) the camera no longer
 * sways with each step, while the first-person view keeps Minecraft's bobbing if it is on. One
 * check per frame, nothing else.
 */
public final class NoF5Bobbing extends Module {
	public NoF5Bobbing() {
		super("no_f5_bobbing", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}
}
