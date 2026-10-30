package gg.rago.debug;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

/**
 * Diagnostic trace, off unless turned on with {@code /rago debug} (or {@code -Drago.debug.input=true}):
 * logs to latest.log, each tick, the state that decides whether a click or a block placement goes
 * through, every mouse button and hotbar key the game reads, the state at the moment the game runs
 * a left or right click, every right click it handles, and frames slower than 50 ms, so a delay a
 * player feels can be found from their log. Nothing is sent anywhere.
 */
public final class InputTrace {
	private static volatile boolean on = Boolean.getBoolean("rago.debug.input");
	/** START and END of each client tick both count, so one game tick is two steps. */
	private static long tick;
	private static Field rightDelay;
	private static Field leftCounter;
	private static Field hitting;
	private static Field blockDelay;
	private long lastFrameNs;

	public static boolean on() {
		return on;
	}

	/** Turns the trace on or off; returns the new state. */
	public static boolean toggle() {
		on = !on;
		RagoClient.LOG.info("[debug] trace {}", on ? "on" : "off");
		return on;
	}

	/** Head of Minecraft.clickMouse ({@code right} false) or rightClickMouse: what decides if the click does anything. */
	public static void click(boolean right) {
		if (!on) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.playerController == null) {
			return;
		}
		try {
			fields();
			MovingObjectPosition over = mc.objectMouseOver;
			RagoClient.LOG.info("[input] t{} {} run: hitting={} rcDelay={} lcCounter={} using={} over={} item={}", tick,
				right ? "right click" : "left click", hitting.getBoolean(mc.playerController), rightDelay.getInt(mc),
				leftCounter.getInt(mc), mc.thePlayer.isUsingItem(), over == null ? "null" : over.typeOfHit,
				mc.thePlayer.getHeldItem() == null ? "-" : mc.thePlayer.getHeldItem().getItem().getRegistryName());
		} catch (ReflectiveOperationException | RuntimeException e) {
			RagoClient.LOG.warn("[input] trace failed: {}", e.toString());
		}
	}

	/** Each mouse button the game reads, in the tick it reads it. */
	@SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST, receiveCanceled = true)
	public void onMouse(MouseEvent event) {
		if (on && event.button >= 0) {
			RagoClient.LOG.info("[input] t{} mouse {} {}{}", tick, event.button, event.buttonstate ? "down" : "up",
				event.isCanceled() ? " canceled" : "");
		}
	}

	/** The hotbar keys, in the tick the game reads them. */
	@SubscribeEvent
	public void onKey(InputEvent.KeyInputEvent event) {
		if (!on || !Keyboard.getEventKeyState()) {
			return;
		}
		int key = Keyboard.getEventKey();
		KeyBinding[] hotbar = Minecraft.getMinecraft().gameSettings.keyBindsHotbar;
		for (int i = 0; i < hotbar.length; i++) {
			if (hotbar[i].getKeyCode() == key) {
				RagoClient.LOG.info("[input] t{} key hotbar {}", tick, i + 1);
			}
		}
	}

	@SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST, receiveCanceled = true)
	public void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
		if (on && event.entityPlayer == Minecraft.getMinecraft().thePlayer) {
			RagoClient.LOG.info("[input] t{} interact {} at {} face {} canceled={}", tick, event.action, event.pos, event.face, event.isCanceled());
		}
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (!on || event.phase != TickEvent.Phase.START) {
			return;
		}
		long now = System.nanoTime();
		if (lastFrameNs != 0 && now - lastFrameNs > 50_000_000L) {
			RagoClient.LOG.info("[input] t{} slow frame {} ms", tick, (now - lastFrameNs) / 1_000_000);
		}
		lastFrameNs = now;
	}
	private String last = "";

	private static void fields() {
		if (rightDelay == null) {
			rightDelay = ReflectionHelper.findField(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
			leftCounter = ReflectionHelper.findField(Minecraft.class, "leftClickCounter", "field_71429_W");
			hitting = ReflectionHelper.findField(PlayerControllerMP.class, "isHittingBlock", "field_78778_j");
			blockDelay = ReflectionHelper.findField(PlayerControllerMP.class, "blockHitDelay", "field_78781_i");
		}
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!on || mc.thePlayer == null || mc.playerController == null) {
			return;
		}
		tick++;
		try {
			fields();
			String s = String.format("%s slot=%d item=%s using=%b rcDelay=%d lcCounter=%d hitting=%b blockDelay=%d use=%b attack=%b screen=%s",
				event.phase, mc.thePlayer.inventory.currentItem,
				mc.thePlayer.getHeldItem() == null ? "-" : mc.thePlayer.getHeldItem().getItem().getRegistryName(),
				mc.thePlayer.isUsingItem(), rightDelay.getInt(mc), leftCounter.getInt(mc), hitting.getBoolean(mc.playerController),
				blockDelay.getInt(mc.playerController), mc.gameSettings.keyBindUseItem.isKeyDown(), mc.gameSettings.keyBindAttack.isKeyDown(),
				mc.currentScreen == null ? "-" : mc.currentScreen.getClass().getSimpleName());
			if (!s.equals(last)) {
				last = s;
				RagoClient.LOG.info("[input] t{} {}", tick, s);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			RagoClient.LOG.warn("[input] trace failed: {}", e.toString());
		}
	}
}
