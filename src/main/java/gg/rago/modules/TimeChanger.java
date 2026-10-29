package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Shows the sky at the hour you choose. Only your client's clock changes; the server corrects it
 * again within a second when switched off.
 */
public final class TimeChanger extends Module {
	private final IntOption hour = addOption(new IntOption("hour", 12, 0, 23));

	public TimeChanger() {
		super("time_changer", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	/** Set before every frame, since the server sends its own time every second. */
	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (!isEnabled() || event.phase != TickEvent.Phase.START) {
			return;
		}
		World world = Minecraft.getMinecraft().theWorld;
		if (world != null) {
			world.setWorldTime(ticks(hour.get()));
		}
	}

	/** Minecraft's day starts at 6:00, 1,000 ticks per hour. */
	static long ticks(int hour) {
		return ((hour + 18) % 24) * 1000L;
	}
}
