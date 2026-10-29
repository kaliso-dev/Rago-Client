package gg.rago.hud.elements;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import java.util.Locale;

/** Your horizontal speed in blocks per second, averaged over the last quarter second. */
public final class SpeedHud extends TextHudElement {
	private static final int SAMPLES = 5;
	private final double[] samples = new double[SAMPLES];
	private int next;

	public SpeedHud() {
		super("speed", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 92);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@Override
	public void tick() {
		Entity e = Minecraft.getMinecraft().getRenderViewEntity();
		if (e == null) {
			return;
		}
		double dx = e.posX - e.prevPosX;
		double dz = e.posZ - e.prevPosZ;
		samples[next] = Math.sqrt(dx * dx + dz * dz) * 20;
		next = (next + 1) % SAMPLES;
	}

	@Override
	protected String text(boolean placeholder) {
		double sum = 0;
		for (double s : samples) {
			sum += s;
		}
		return String.format(Locale.ROOT, "%.2f m/s", placeholder ? 5.61 : sum / SAMPLES);
	}
}
