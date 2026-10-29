package gg.rago.debug;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/**
 * Diagnostic trace, off unless turned on with {@code /rago debug} (or {@code -Drago.debug.input=true}):
 * logs to latest.log, each tick, the state that decides whether a click or a block placement goes
 * through, every right click the game handles, and frames slower than 50 ms, so a delay a player
 * feels can be found from their log. Nothing is sent anywhere.
 */
public final class InputTrace {
	private static volatile boolean on = Boolean.getBoolean("rago.debug.input");
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
	private Field rightDelay;
	private Field leftCounter;
	private Field hitting;
	private Field blockDelay;
	private String last = "";
	private long tick;

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!on || mc.thePlayer == null || mc.playerController == null) {
			return;
		}
		tick++;
		try {
			if (rightDelay == null) {
				rightDelay = ReflectionHelper.findField(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
				leftCounter = ReflectionHelper.findField(Minecraft.class, "leftClickCounter", "field_71429_W");
				hitting = ReflectionHelper.findField(PlayerControllerMP.class, "isHittingBlock", "field_78778_j");
				blockDelay = ReflectionHelper.findField(PlayerControllerMP.class, "blockHitDelay", "field_78781_i");
			}
			String s = String.format("%s slot=%d item=%s using=%b rcDelay=%d lcCounter=%d hitting=%b blockDelay=%d use=%b attack=%b",
				event.phase, mc.thePlayer.inventory.currentItem,
				mc.thePlayer.getHeldItem() == null ? "-" : mc.thePlayer.getHeldItem().getItem().getRegistryName(),
				mc.thePlayer.isUsingItem(), rightDelay.getInt(mc), leftCounter.getInt(mc), hitting.getBoolean(mc.playerController),
				blockDelay.getInt(mc.playerController), mc.gameSettings.keyBindUseItem.isKeyDown(), mc.gameSettings.keyBindAttack.isKeyDown());
			if (!s.equals(last)) {
				last = s;
				RagoClient.LOG.info("[input] t{} {}", tick, s);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			RagoClient.LOG.warn("[input] trace failed: {}", e.toString());
		}
	}
}
