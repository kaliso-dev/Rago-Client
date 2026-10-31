package gg.rago.hud.elements;

import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;


/** Your horizontal speed in blocks per second, averaged over the last quarter second. */
public final class SpeedHud extends TextHudElement {
	/** The text shown, made again only when it changes. */
	private final Shown shown = new Shown();
	private static final int SAMPLES = 5;
	private final double[] samples = new double[SAMPLES];
	private int next;
	private final ChoiceOption unit = addOption(new ChoiceOption("speed_unit", "bps", "bps", "kmh"));
	private final IntOption decimals = addOption(new IntOption("decimals", 2, 0, 3));

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
	protected String label(boolean after) {
		if (!after) {
			return "Speed";
		}
		return unit.is("kmh") ? "km/h" : "m/s";
	}

	@Override
	protected String value(boolean placeholder) {
		double sum = 0;
		for (double s : samples) {
			sum += s;
		}
		double speed = placeholder ? 5.61 : sum / SAMPLES;
		boolean kmh = unit.is("kmh");
		double v = kmh ? speed * 3.6 : speed;
		int d = decimals.get();
		if (!shown.same(Shown.key(v, d), d, kmh ? 1 : 0)) {
			shown.set(Shown.fixed(v, d));
		}
		return shown.text();
	}
}
