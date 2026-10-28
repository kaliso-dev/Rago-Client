package gg.rago.mixin;

import gg.rago.core.RagoClient;
import gg.rago.gui.RagoTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code runGame} is called once per frame (Feather name; MCP calls it runGameLoop).
 * Only HEAD/RETURN injects are used so this never competes with mods that wrap calls
 * inside the method (Argentum wraps Thread.yield and GlStateManager.clear there).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "runGame", at = @At("HEAD"))
	private void rago$frameStart(CallbackInfo ci) {
		RagoClient.onFrameStart(System.nanoTime());
	}

	@Inject(method = "runGame", at = @At("RETURN"))
	private void rago$frameEnd(CallbackInfo ci) {
		RagoClient.onFrameEnd(System.nanoTime());
	}

	/**
	 * Vanilla opens its title screen from several places (startup, disconnect, quitting a world),
	 * sometimes by substituting it inside this method, so the swap happens once it has returned.
	 */
	@Inject(method = "openScreen", at = @At("RETURN"))
	private void rago$mainMenu(Screen screen, CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.screen instanceof TitleScreen && RagoClient.useMainMenu()) {
			mc.openScreen(new RagoTitleScreen());
		}
	}
}
