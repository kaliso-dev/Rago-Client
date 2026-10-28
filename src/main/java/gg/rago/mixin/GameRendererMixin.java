package gg.rago.mixin;

import gg.rago.core.RagoClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** Zoom: narrows the world FOV only (changingFov = true), so the held item keeps its size. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void rago$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		if (changingFov) {
			float fov = cir.getReturnValueF();
			float zoomed = RagoClient.zoom().apply(fov);
			if (zoomed != fov) {
				cir.setReturnValue(zoomed);
			}
		}
	}
}
