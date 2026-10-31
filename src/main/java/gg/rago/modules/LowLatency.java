package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ARBSync;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;

import java.util.ArrayDeque;

/**
 * Low latency: keeps the graphics driver's queue of frames short, the idea behind NVIDIA Reflex.
 * Left alone, the game can prepare frames two or three ahead of what the graphics card is drawing,
 * so the frame you see was built from mouse input that is that many frames old. Here, at the start
 * of every frame (before the mouse and keyboard are read), the game waits until the card has
 * finished the earlier frames: none of them in the strict mode, at most one in the balanced mode.
 * The waiting uses a GL fence (OpenGL 3.2 or ARB_sync); without one there is no waiting.
 *
 * <p>Fresh input: the game reads the mouse and keyboard when it hands a frame to the driver, then
 * the frame-rate cap sleeps and this module waits, so the next frame used input that had been
 * waiting all that time; movement that came during the sleep only showed a frame later. With this
 * option the mouse and keyboard are read once more just before the frame starts. Nothing about
 * what is sent to the server changes: the same input, read sooner.
 */
public final class LowLatency extends Module {
	/** Never waits longer than this for the card, whatever happens. */
	private static final long TIMEOUT_NS = 100_000_000L;

	private final ChoiceOption mode = addOption(new ChoiceOption("latency_mode", "balanced", "balanced", "strict"));
	private final BooleanOption freshInput = addOption(new BooleanOption("latency_fresh_input", true));

	/** Fences put after each frame, oldest first. */
	private final ArrayDeque<GLSync> fences = new ArrayDeque<>();
	private Boolean supported;
	private boolean arb;

	public LowLatency() {
		super("low_latency", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PERFORMANCE;
	}

	@Override
	protected void onDisable() {
		release();
	}

	/** Start of Minecraft.runGameLoop: the frame before has been handed to the driver. */
	public void frameStart() {
		if (!isEnabled()) {
			release();
			return;
		}
		if (supported()) {
			waitForCard();
		}
		// in a world with the mouse captured: in menus the cursor's position is read, not its movement
		if (freshInput.get() && Mouse.isCreated() && Mouse.isGrabbed()) {
			Display.processMessages();
		}
	}

	private void waitForCard() {
		fences.addLast(arb ? ARBSync.glFenceSync(ARBSync.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
			: GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
		int ahead = mode.is("strict") ? 0 : 1;
		while (fences.size() > ahead) {
			GLSync s = fences.pollFirst();
			if (arb) {
				ARBSync.glClientWaitSync(s, ARBSync.GL_SYNC_FLUSH_COMMANDS_BIT, TIMEOUT_NS);
				ARBSync.glDeleteSync(s);
			} else {
				GL32.glClientWaitSync(s, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, TIMEOUT_NS);
				GL32.glDeleteSync(s);
			}
		}
	}

	private boolean supported() {
		if (supported == null) {
			ContextCapabilities caps = GLContext.getCapabilities();
			arb = !caps.OpenGL32 && caps.GL_ARB_sync;
			supported = caps.OpenGL32 || caps.GL_ARB_sync;
			if (supported) {
				RagoClient.LOG.info("Low Latency: on, with {} fences", arb ? "ARB_sync" : "OpenGL 3.2");
			} else {
				RagoClient.LOG.warn("Low Latency: the graphics driver has no GL fences (OpenGL 3.2 or ARB_sync), nothing is changed");
			}
		}
		return supported;
	}

	private void release() {
		while (!fences.isEmpty()) {
			GLSync s = fences.pollFirst();
			if (arb) {
				ARBSync.glDeleteSync(s);
			} else {
				GL32.glDeleteSync(s);
			}
		}
	}
}
