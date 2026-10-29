package gg.rago.debug;

import com.mojang.authlib.GameProfile;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Developer benchmark, active only with {@code -Drago.bench.nametags=<players>}: adds that many
 * client-side players in front of the camera and logs the mean frame time with the Nametags
 * module off and on, alternating every 10 seconds. Nothing is sent to any server.
 */
public final class NametagBench {
	private static final long PHASE_NS = 10_000_000_000L;
	private final int players;
	private boolean spawned;
	private long phaseStart;
	private int phase;
	private long frames;
	private long last;
	private double sumMs;
	private long labelNs;
	private long labelStart;
	private long lastStart;

	private NametagBench(int players) {
		this.players = players;
	}

	public static NametagBench fromProperty() {
		int n = Integer.getInteger("rago.bench.nametags", 0);
		return n > 0 ? new NametagBench(n) : null;
	}

	// time spent drawing names: from the first handler of Specials.Pre to the last of Specials.Post
	// (vanilla's own drawing, or Rago's queueing), plus the batch flush after the world
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void namePre(RenderLivingEvent.Specials.Pre<EntityLivingBase> e) {
		labelStart = System.nanoTime();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void namePost(RenderLivingEvent.Specials.Post<EntityLivingBase> e) {
		labelNs += System.nanoTime() - labelStart;
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void lastPre(RenderWorldLastEvent e) {
		lastStart = System.nanoTime();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void lastPost(RenderWorldLastEvent e) {
		labelNs += System.nanoTime() - lastStart;
	}

	@SubscribeEvent
	public void onRender(TickEvent.RenderTickEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.phase != TickEvent.Phase.END || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
			return;
		}
		long now = System.nanoTime();
		if (!spawned) {
			spawned = true;
			for (int i = 0; i < players; i++) {
				EntityOtherPlayerMP p = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "Bench" + i));
				int row = i / 10;
				int col = i % 10;
				p.setPositionAndRotation(mc.thePlayer.posX - 9 + col * 2, mc.thePlayer.posY, mc.thePlayer.posZ + 4 + row * 3, 180, 0);
				mc.theWorld.addEntityToWorld(-1000 - i, p);
			}
			phaseStart = now;
			phase = -1;
			last = now;
			return;
		}
		mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 0;
		mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 10;
		if (phase >= 0) {
			sumMs += (now - last) / 1e6;
			frames++;
		}
		last = now;
		if (now - phaseStart > PHASE_NS || phase == -1 && now - phaseStart > PHASE_NS / 2) {
			Module tags = RagoClient.modules().get("nametags").orElse(null);
			if (phase >= 0 && frames > 0) {
				RagoClient.LOG.info(String.format(Locale.ROOT, "[bench] %d players, nametags %s: %.3f ms/frame (%.0f FPS), names %.1f us/frame, over %d frames",
					players, tags != null && tags.isEnabled() ? "on" : "off", sumMs / frames, 1000 * frames / sumMs, labelNs / 1000.0 / frames, frames));
			}
			phase++;
			if (tags != null) {
				tags.setEnabled(phase % 2 == 1);
			}
			frames = 0;
			sumMs = 0;
			labelNs = 0;
			phaseStart = now;
		}
	}
}
