package gg.rago.modules;

import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;

/**
 * Raises the brightness (gamma) setting beyond the slider's maximum so dark areas are lit,
 * and puts the player's own value back when switched off.
 */
public final class Fullbright extends Module {
	private static final float GAMMA = 10f;

	/** The player's brightness before Rago raised it; the slider's maximum if never seen. */
	private float saved = 1f;

	public Fullbright() {
		super("fullbright", FeatureClass.VISUAL, false);
	}

	@Override
	public void tick() {
		GameOptions o = Minecraft.getInstance().options;
		if (o == null) {
			return;
		}
		if (o.gamma <= 1f) {
			saved = o.gamma;
		}
		o.gamma = GAMMA;
	}

	@Override
	protected void onDisable() {
		GameOptions o = Minecraft.getInstance().options;
		if (o != null && o.gamma > 1f) {
			o.gamma = saved;
		}
	}
}
