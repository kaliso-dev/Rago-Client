package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;

/** Hides the achievement pop-up in the top right corner (such as "Taking Inventory"). */
public final class HideAchievements extends Module {
	public HideAchievements() {
		super("hide_achievements", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.guiAchievement != null) {
			mc.guiAchievement.clearAchievements();
		}
	}
}
