package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * Shows the weather you choose instead of the server's. Only your client's copy of the world
 * changes; the server's weather resumes when switched off.
 */
public final class WeatherChanger extends Module {
	public static final int CLEAR = 0;
	public static final int RAIN = 1;
	public static final int THUNDER = 2;

	private final IntOption weather = addOption(new IntOption("weather", CLEAR, 0, 2));
	private World changed;

	public WeatherChanger() {
		super("weather_changer", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	@Override
	public void tick() {
		World world = Minecraft.getMinecraft().theWorld;
		if (world == null) {
			return;
		}
		int w = weather.get();
		world.setRainStrength(w == CLEAR ? 0 : 1);
		world.setThunderStrength(w == THUNDER ? 1 : 0);
		changed = world;
	}

	@Override
	protected void onDisable() {
		World world = Minecraft.getMinecraft().theWorld;
		if (world != null && world == changed) {
			// the server sends its own weather again only when it changes, so go back to what it last said
			boolean raining = world.getWorldInfo().isRaining();
			world.setRainStrength(raining ? 1 : 0);
			world.setThunderStrength(raining && world.getWorldInfo().isThundering() ? 1 : 0);
		}
		changed = null;
	}
}
