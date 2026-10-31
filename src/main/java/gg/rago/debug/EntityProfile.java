package gg.rago.debug;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Developer timing with {@code -Drago.bench.frame=true}: how long drawing the other players takes
 * per frame (model, armour, layers and name), as the "players" line of {@link FrameProfile}.
 */
public final class EntityProfile {
	private long start;

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void pre(RenderLivingEvent.Pre<?> event) {
		if (event.entity instanceof EntityOtherPlayerMP) {
			start = System.nanoTime();
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void post(RenderLivingEvent.Post<?> event) {
		if (event.entity instanceof EntityOtherPlayerMP && start != 0) {
			FrameProfile.element("players", System.nanoTime() - start);
			start = 0;
		}
	}
}
