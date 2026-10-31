package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;


/** Player position (the same numbers as F3) and the direction they face. */
public final class CoordsHud extends TextHudElement {
	private final IntOption decimals = addOption(new IntOption("decimals", 0, 0, 2));
	private final BooleanOption showFacing = addOption(new BooleanOption("show_facing", true));

	public CoordsHud() {
		super("coords", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 48);
	}

	@Override
	public boolean hasContent() {
		return Minecraft.getMinecraft().thePlayer != null;
	}

	private final Shown shown = new Shown();

	@Override
	protected String value(boolean placeholder) {
		Entity p = Minecraft.getMinecraft().thePlayer;
		double x = placeholder || p == null ? 12.5 : p.posX;
		double y = placeholder || p == null ? 64 : p.posY;
		double z = placeholder || p == null ? -30.25 : p.posZ;
		float yaw = placeholder || p == null ? 180 : p.rotationYaw;
		int d = decimals.get();
		int facing = showFacing.get() ? 4 + Math.floorMod(Math.round(yaw / 90f), 4) : 0;
		if (!shown.same(Shown.key(x, d), Shown.key(y, d), (Shown.key(z, d) * 4 + d) * 8 + facing)) {
			String s = "X " + Shown.fixed(x, d) + "\nY " + Shown.fixed(y, d) + "\nZ " + Shown.fixed(z, d);
			if (facing != 0) {
				s += "\n" + facing(yaw);
			}
			shown.set(s);
		}
		return shown.text();
	}

	/** Cardinal direction for a yaw, matching vanilla: 0 = south (+Z), 90 = west (-X). */
	static String facingKey(float yaw) {
		int quadrant = Math.floorMod(Math.round(yaw / 90f), 4);
		switch (quadrant) {
			case 0:
				return "south";
			case 1:
				return "west";
			case 2:
				return "north";
			default:
				return "east";
		}
	}

	private static String facing(float yaw) {
		String key = facingKey(yaw);
		String axis;
		switch (key) {
			case "south":
				axis = "+Z";
				break;
			case "west":
				axis = "-X";
				break;
			case "north":
				axis = "-Z";
				break;
			default:
				axis = "+X";
		}
		return I18n.format("rago.direction." + key) + " (" + axis + ")";
	}
}
