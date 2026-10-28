package gg.rago.mixin;

import gg.rago.core.RagoClient;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes key and mouse presses for the CPS counter. Read-only: the call continues unchanged. */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
	@Inject(method = "click", at = @At("HEAD"))
	private static void rago$onClick(int keyCode, CallbackInfo ci) {
		RagoClient.onKeyPress(keyCode);
	}
}
