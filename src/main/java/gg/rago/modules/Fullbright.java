package gg.rago.modules;

import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

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
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		if (o == null) {
			return;
		}
		if (o.gammaSetting <= 1f) {
			saved = o.gammaSetting;
		}
		o.gammaSetting = GAMMA;
	}

	@Override
	protected void onDisable() {
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		if (o != null && o.gammaSetting > 1f) {
			o.gammaSetting = saved;
		}
	}
}
