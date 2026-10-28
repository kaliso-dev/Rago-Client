package gg.rago.mixin;

import gg.rago.core.RagoClient;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class GameGuiMixin {
	@Inject(method = "render", at = @At("RETURN"))
	private void rago$renderOverlay(float tickDelta, CallbackInfo ci) {
		RagoClient.renderOverlay();
	}
}
