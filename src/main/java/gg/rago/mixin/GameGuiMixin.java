package gg.rago.mixin;

import gg.rago.core.RagoClient;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameGui.class)
public abstract class GameGuiMixin {
	@Inject(method = "render", at = @At("RETURN"))
	private void rago$renderOverlay(float tickDelta, CallbackInfo ci) {
		RagoClient.renderOverlay();
	}

	/** Keeps vanilla's rules for when a crosshair shows, and draws Rago's instead when enabled. */
	@Inject(method = "hasCrosshair", at = @At("RETURN"), cancellable = true)
	private void rago$customCrosshair(CallbackInfoReturnable<Boolean> cir) {
		boolean vanilla = cir.getReturnValueZ();
		boolean result = RagoClient.crosshair().replaceVanilla(vanilla);
		if (result != vanilla) {
			cir.setReturnValue(result);
		}
	}
}
