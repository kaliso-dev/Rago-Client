package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Hides the crosshair in third person (F5), where it does not point at what you would hit. */
public final class F5Crosshair extends Module {
	public F5Crosshair() {
		super("hide_f5_crosshair", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (isEnabled() && event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS
			&& Minecraft.getMinecraft().gameSettings.thirdPersonView != 0) {
			event.setCanceled(true);
		}
	}
}
