package gg.rago.optimize;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;

/**
 * Frame-rate caps for menus and unfocused windows. {@link #limit} is called by the patched
 * {@code Minecraft.getLimitFramerate()} (see {@code gg.rago.asm.RagoTransformer}); without the patch
 * nothing calls it and vanilla's caps apply.
 */
public final class FrameLimit {
	/** Vanilla treats this value as "Unlimited". */
	public static final int UNLIMITED = 260;

	private static volatile int menuFps = 120;
	private static volatile boolean unfocusedCap = true;
	private static volatile int unfocusedFps = 30;
	private static volatile boolean hooked;

	private FrameLimit() {
	}

	public static int limit(int vanilla) {
		hooked = true;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null) {
			return vanilla;
		}
		int fps = vanilla;
		if (mc.theWorld == null && mc.currentScreen != null) {
			fps = menuFps;
		}
		if (unfocusedCap && !Display.isActive()) {
			fps = Math.min(fps, unfocusedFps);
		}
		return fps;
	}

	/** Whether the game called the hook at least once, i.e. the class patch is active. */
	public static boolean hooked() {
		return hooked;
	}

	public static void setMenuFps(int fps) {
		menuFps = fps;
	}

	public static void setUnfocused(boolean enabled, int fps) {
		unfocusedCap = enabled;
		unfocusedFps = fps;
	}
}
