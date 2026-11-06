package gg.rago.modules;

import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;

/**
 * Where the view bobs, when Minecraft's view bobbing is on. "f5": the camera no longer sways in third
 * person (F5, behind or in front), first person keeps Minecraft's bobbing. "minimal": the world never
 * sways, only the hand and the item it holds bob (minimal view bobbing). One check per frame, nothing
 * else: the camera's bobbing is skipped, the hand's is Minecraft's own.
 */
public final class NoF5Bobbing extends Module {
	private final ChoiceOption mode = addOption(new ChoiceOption("bobbing_mode", "f5", "f5", "minimal"));

	public NoF5Bobbing() {
		super("no_f5_bobbing", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	/** Whether the camera bobs (view bobbing on) in this view (0 = first person). */
	public boolean cameraBobs(int thirdPersonView) {
		return !isEnabled() || !mode.is("minimal") && thirdPersonView == 0;
	}
}
