package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MouseHelper;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Raw input: the camera turns by what the mouse itself reports, read straight from the device
 * (JInput, already shipped with 1.8.9), instead of the Windows cursor, which "Enhance pointer
 * precision" accelerates and which drops movement at high polling rates. Turning stays exactly
 * as sensitive as the game's own setting makes it. When no mouse can be read this way, the game's
 * own mouse is used and a message says so.
 */
public final class RawInput extends Module {
	/** Every mouse plugged in moves the camera, not only the first one found. */
	private final BooleanOption allMice = addOption(new BooleanOption("raw_input_all_mice", true));

	private final AtomicInteger dx = new AtomicInteger();
	private final AtomicInteger dy = new AtomicInteger();
	private volatile List<Mouse> mice = new ArrayList<>();
	private volatile boolean running;
	private Thread poller;
	private MouseHelper vanilla;
	private boolean scanned;

	public RawInput() {
		super("raw_input", FeatureClass.INPUT, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@Override
	protected void onEnable() {
		scanned = false; // a fresh scan finds a mouse plugged in since
	}

	@Override
	protected void onDisable() {
		running = false;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.mouseHelper instanceof RawMouse && vanilla != null) {
			mc.mouseHelper = vanilla;
		}
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		if (!scanned) {
			scanned = true;
			start(mc);
		}
	}

	private void start(Minecraft mc) {
		List<Mouse> found = scan();
		if (found.isEmpty()) {
			RagoClient.LOG.warn("Raw input: no mouse could be read, the game's own mouse stays");
			if (mc.thePlayer != null) {
				mc.thePlayer.addChatMessage(new ChatComponentText("§c§lRago §8» §f" + I18n.format("rago.raw_input.no_mouse")));
			}
			return;
		}
		mice = found;
		RagoClient.LOG.info("Raw input: reading {} mouse device(s)", found.size());
		if (!(mc.mouseHelper instanceof RawMouse)) {
			vanilla = mc.mouseHelper;
			mc.mouseHelper = new RawMouse();
		}
		dx.set(0);
		dy.set(0);
		if (!running) {
			running = true;
			poller = new Thread(this::poll, "Rago raw input");
			poller.setDaemon(true);
			poller.start();
		}
	}

	/** The mice JInput can read, from a fresh scan so mice plugged in since are found. */
	private List<Mouse> scan() {
		List<Mouse> out = mice(fresh());
		if (out.isEmpty()) {
			out = mice(ControllerEnvironment.getDefaultEnvironment());
		}
		return out;
	}

	private List<Mouse> mice(ControllerEnvironment env) {
		List<Mouse> out = new ArrayList<>();
		try {
			for (Controller c : env.getControllers()) {
				if (c instanceof Mouse && c.getType() == Controller.Type.MOUSE && c.poll()) {
					out.add((Mouse) c);
					if (!allMice.get()) {
						break;
					}
				}
			}
		} catch (Throwable t) {
			// missing natives or an unsupported system: the game's own mouse stays
			RagoClient.LOG.warn("Raw input unavailable: {}", t.toString());
		}
		return out;
	}

	/** JInput keeps the first list of devices forever; a new environment lists them again. */
	private static ControllerEnvironment fresh() {
		ControllerEnvironment def = ControllerEnvironment.getDefaultEnvironment();
		try {
			Constructor<? extends ControllerEnvironment> c = def.getClass().getDeclaredConstructor();
			c.setAccessible(true);
			return c.newInstance();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return def;
		}
	}

	private void poll() {
		while (running) {
			for (Mouse m : mice) {
				if (!m.poll()) {
					continue;
				}
				Component x = m.getX();
				Component y = m.getY();
				if (x != null) {
					dx.addAndGet(Math.round(x.getPollData()));
				}
				if (y != null) {
					dy.addAndGet(Math.round(y.getPollData()));
				}
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	/** The game's mouse, turning the camera by the raw movement since the last frame. */
	private final class RawMouse extends MouseHelper {
		@Override
		public void mouseXYChange() {
			// the cursor's own movement still has to be read so it does not pile up
			org.lwjgl.input.Mouse.getDX();
			org.lwjgl.input.Mouse.getDY();
			deltaX = dx.getAndSet(0);
			deltaY = -dy.getAndSet(0); // devices count downwards, the game upwards
		}

		@Override
		public void grabMouseCursor() {
			super.grabMouseCursor();
			dx.set(0);
			dy.set(0);
		}
	}
}
