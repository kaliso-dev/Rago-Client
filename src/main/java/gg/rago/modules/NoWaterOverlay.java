package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Removes the wavy water texture drawn over the screen underwater. Underwater fog is unchanged. */
public final class NoWaterOverlay extends Module {
	public NoWaterOverlay() {
		super("no_water_overlay", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	@SubscribeEvent
	public void onOverlay(RenderBlockOverlayEvent event) {
		if (isEnabled() && event.overlayType == RenderBlockOverlayEvent.OverlayType.WATER) {
			event.setCanceled(true);
		}
	}
}
