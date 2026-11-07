package gg.rago.optimize;

import gg.rago.config.StoredSettings;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.BooleanSupplier;

/**
 * PolyPatcher's Downscale Pack Images: a resource pack icon (its pack.png) bigger than 64x64 is
 * shrunk to 64x64 as it is read, so a pack with a huge icon no longer keeps a huge image in memory
 * and in the graphics card. Called from AbstractResourcePack.getPackImage, so the default icon the
 * game shows for a pack without one stays as it is, as with PolyPatcher. The game reads the icons of
 * all packs as it opens, before Rago starts: until then the setting comes from config/rago.json.
 */
public final class PackIcons {
	/** The largest icon kept, in pixels each way. */
	static final int MAX = 64;

	private static volatile BooleanSupplier setting;
	private static volatile Boolean stored;

	private PackIcons() {
	}

	public static void setSwitch(BooleanSupplier on) {
		setting = on;
	}

	/** The icon a pack gives, shrunk to 64x64 when it is bigger and the setting is on. */
	public static BufferedImage packIcon(BufferedImage icon) {
		if (icon == null || icon.getWidth() <= MAX && icon.getHeight() <= MAX || !on()) {
			return icon;
		}
		return shrink(icon);
	}

	/** Stretched to 64x64 whatever its shape, nearest pixel, as PolyPatcher does. */
	static BufferedImage shrink(BufferedImage icon) {
		BufferedImage small = new BufferedImage(MAX, MAX, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = small.createGraphics();
		g.drawImage(icon, 0, 0, MAX, MAX, null);
		g.dispose();
		return small;
	}

	private static boolean on() {
		BooleanSupplier s = setting;
		if (s != null) {
			return s.getAsBoolean();
		}
		Boolean b = stored;
		if (b == null) {
			File home = net.minecraft.launchwrapper.Launch.minecraftHome;
			File file = new File(new File(home != null ? home : new File("."), "config"), "rago.json");
			b = StoredSettings.bool(file.toPath(), "small_pack_icons", true);
			stored = b;
		}
		return b;
	}
}
