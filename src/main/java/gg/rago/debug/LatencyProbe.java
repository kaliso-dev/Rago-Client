package gg.rago.debug;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Developer latency test ({@code -Drago.bench.latency=true}): logs the moment each frame that turned
 * the camera was finished, on the same clock as {@link System#nanoTime()} (the system's monotonic
 * clock), so a test that moves the mouse at known times can work out how long the game took to show
 * each movement. Off unless asked for; nothing is sent anywhere.
 */
public final class LatencyProbe {
	public static final boolean ON = Boolean.getBoolean("rago.bench.latency");
	private float yaw = Float.NaN;
	private float pitch = Float.NaN;

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.phase != TickEvent.Phase.END || mc.thePlayer == null) {
			return;
		}
		long now = System.nanoTime();
		float y = mc.thePlayer.rotationYaw;
		float p = mc.thePlayer.rotationPitch;
		if (y != yaw || p != pitch) {
			RagoClient.LOG.info("[latency] turn {} {}", now, y);
		}
		yaw = y;
		pitch = p;
	}
}
