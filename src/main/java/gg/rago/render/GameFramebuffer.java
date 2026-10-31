package gg.rago.render;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The framebuffer the game draws the world and the HUD into, known without asking the driver each
 * time (a glGet can make a threaded driver wait for all the work queued before it): the game's own
 * framebuffer, or the screen when it has none (OptiFine's Fast Render). Checked against the driver
 * every few hundred calls; if it ever differs (a shader pack drawing elsewhere, say), it is asked
 * every time from then on.
 */
public final class GameFramebuffer {
	private static final int CHECK_EVERY = 600;
	private static int checkIn;
	private static boolean askAlways;

	private GameFramebuffer() {
	}

	/** The framebuffer bound while the game draws, to bind back after drawing into one of our own. */
	public static int bound() {
		Minecraft mc = Minecraft.getMinecraft();
		Framebuffer main = mc.getFramebuffer();
		int expected = OpenGlHelper.isFramebufferEnabled() && main != null ? main.framebufferObject : 0;
		if (!askAlways && --checkIn > 0) {
			return expected;
		}
		checkIn = CHECK_EVERY;
		int actual = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
		if (actual != expected && !askAlways) {
			askAlways = true;
			RagoClient.LOG.info("The game draws into framebuffer {} ({} expected): asking the driver every time from now on", actual, expected);
		}
		return actual;
	}
}
