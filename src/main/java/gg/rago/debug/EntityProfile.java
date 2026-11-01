package gg.rago.debug;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Developer timing with {@code -Drago.bench.frame=true}: how long drawing the other players takes
 * per frame (model, armour, layers and name), as the "players" line of {@link FrameProfile}, and
 * the armour stands with a name (Hypixel's floating texts) as its "holograms" line.
 */
public final class EntityProfile {
	private long start;

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void pre(RenderLivingEvent.Pre<?> event) {
		if (event.entity instanceof EntityOtherPlayerMP || event.entity instanceof EntityArmorStand) {
			start = System.nanoTime();
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void post(RenderLivingEvent.Post<?> event) {
		if (start == 0) {
			return;
		}
		if (event.entity instanceof EntityOtherPlayerMP) {
			FrameProfile.element("players", System.nanoTime() - start);
		} else if (event.entity instanceof EntityArmorStand) {
			FrameProfile.element("holograms", System.nanoTime() - start);
		}
		start = 0;
	}
}
