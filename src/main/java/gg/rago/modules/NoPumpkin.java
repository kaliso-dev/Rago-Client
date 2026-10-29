package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Removes the dark pumpkin mask drawn over the screen when you wear a pumpkin. */
public final class NoPumpkin extends Module {
	public NoPumpkin() {
		super("no_pumpkin", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	@SubscribeEvent
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (!isEnabled() || event.type != RenderGameOverlayEvent.ElementType.HELMET) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		ItemStack helmet = mc.thePlayer == null ? null : mc.thePlayer.inventory.armorItemInSlot(3);
		if (helmet != null && helmet.getItem() == Item.getItemFromBlock(Blocks.pumpkin)) {
			event.setCanceled(true);
		}
	}
}
