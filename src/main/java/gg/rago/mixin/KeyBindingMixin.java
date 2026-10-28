package gg.rago.mixin;

import gg.rago.core.RagoClient;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
	/** Observes key and mouse presses for the CPS counter. Read-only: the call continues unchanged. */
	@Inject(method = "click", at = @At("HEAD"))
	private static void rago$onClick(int keyCode, CallbackInfo ci) {
		RagoClient.onKeyPress(keyCode);
	}

	/** Toggle sprint/sneak: the binding reads as held, as if the key were kept down. */
	@Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
	private void rago$toggleHeld(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && RagoClient.forceHeld((KeyBinding) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
