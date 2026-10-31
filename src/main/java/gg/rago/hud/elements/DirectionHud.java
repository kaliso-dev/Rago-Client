package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/** The direction you face as a compass point (N, NE, E...) and optionally in degrees. */
public final class DirectionHud extends TextHudElement {
	private static final String[] POINTS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	private final BooleanOption degrees = addOption(new BooleanOption("show_degrees", true));

	public DirectionHud() {
		super("direction", FeatureClass.INFORMATIONAL, false, Anchor.TOP_CENTER, 0, 24);
	}

	@Override
	public boolean hasContent() {
		return Minecraft.getMinecraft().thePlayer != null;
	}

	/** Compass point for a yaw, matching vanilla: 0 = south (+Z), 90 = west (-X). */
	static String point(float yaw) {
		return POINTS[Math.floorMod(Math.round(yaw / 45f), 8)];
	}

	/** Bearing as on a compass: 0 = north, 90 = east. */
	static int bearing(float yaw) {
		return Math.floorMod(Math.round(yaw) + 180, 360);
	}

	private final Shown shown = new Shown();

	@Override
	protected String value(boolean placeholder) {
		Entity p = Minecraft.getMinecraft().thePlayer;
		float yaw = placeholder || p == null ? 225 : p.rotationYaw;
		if (!degrees.get()) {
			return point(yaw);
		}
		int bearing = bearing(yaw);
		if (!shown.same(bearing, Math.floorMod(Math.round(yaw / 45f), 8), 0)) {
			shown.set(point(yaw) + " " + bearing + "°");
		}
		return shown.text();
	}
}
