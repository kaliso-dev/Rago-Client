package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.Entity;

import java.util.Locale;

/** Player position (the same numbers as F3) and the direction they face. */
public final class CoordsHud extends TextHudElement {
	private final IntOption decimals = addOption(new IntOption("decimals", 0, 0, 2));
	private final BooleanOption showFacing = addOption(new BooleanOption("show_facing", true));

	public CoordsHud() {
		super("coords", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 48);
	}

	@Override
	public boolean hasContent() {
		return Minecraft.getInstance().player != null;
	}

	@Override
	protected String text(boolean placeholder) {
		Entity p = Minecraft.getInstance().player;
		double x = placeholder || p == null ? 12.5 : p.x;
		double y = placeholder || p == null ? 64 : p.y;
		double z = placeholder || p == null ? -30.25 : p.z;
		float yaw = placeholder || p == null ? 180 : p.yaw;
		String fmt = "%." + decimals.get() + "f";
		String s = String.format(Locale.ROOT, "X " + fmt + "\nY " + fmt + "\nZ " + fmt, x, y, z);
		if (showFacing.get()) {
			s += "\n" + facing(yaw);
		}
		return s;
	}

	/** Cardinal direction for a yaw, matching vanilla: 0 = south (+Z), 90 = west (-X). */
	static String facingKey(float yaw) {
		int quadrant = Math.floorMod(Math.round(yaw / 90f), 4);
		return switch (quadrant) {
			case 0 -> "south";
			case 1 -> "west";
			case 2 -> "north";
			default -> "east";
		};
	}

	private static String facing(float yaw) {
		String key = facingKey(yaw);
		String axis = switch (key) {
			case "south" -> "+Z";
			case "west" -> "-X";
			case "north" -> "-Z";
			default -> "+X";
		};
		return I18n.translate("rago.direction." + key) + " (" + axis + ")";
	}
}
