package gg.rago.mixin;

import gg.rago.core.RagoClient;
import net.minecraft.entity.living.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
	/** While zooming, the mouse wheel adjusts the zoom instead of changing the hotbar slot. */
	@Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
	private void rago$zoomScroll(int direction, CallbackInfo ci) {
		if (RagoClient.zoom().onScroll(direction)) {
			ci.cancel();
		}
	}
}
