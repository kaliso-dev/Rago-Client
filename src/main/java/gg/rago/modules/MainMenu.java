package gg.rago.modules;

import gg.rago.core.FeatureClass;
import gg.rago.core.Module;

/** Replaces the vanilla title screen with Rago's main menu. */
public final class MainMenu extends Module {
	public MainMenu() {
		super("main_menu", FeatureClass.VISUAL, true);
	}
}
