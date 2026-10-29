package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Replaces the vanilla crosshair with one drawn in real screen pixels, so thickness and gap stay
 * sharp at every GUI scale. It shows exactly when vanilla would show its own.
 */
public final class Crosshair extends Module {
	public static final int STYLE_CROSS = 0;
	public static final int STYLE_DOT = 1;
	public static final int STYLE_CROSS_DOT = 2;
	public static final int STYLE_T = 3;

	private final IntOption style = addOption(new IntOption("crosshair_style", STYLE_CROSS, 0, 3));
	private final IntOption size = addOption(new IntOption("crosshair_size", 5, 1, 15));
	private final IntOption gap = addOption(new IntOption("crosshair_gap", 2, 0, 8));
	private final IntOption thickness = addOption(new IntOption("crosshair_thickness", 2, 1, 4));
	private final ColorOption color = addOption(new ColorOption("crosshair_color", 0xFFFFFFFF));
	private final BooleanOption outline = addOption(new BooleanOption("crosshair_outline", true));

	public Crosshair() {
		super("crosshair", FeatureClass.VISUAL, false);
	}

	/**
	 * Called when vanilla is about to draw its crosshair (Forge only asks when it would).
	 * Draws Rago's instead and returns true so vanilla's is skipped; false when switched off.
	 */
	public boolean replaceVanilla() {
		if (!isEnabled()) {
			return false;
		}
		render();
		return true;
	}

	private void render() {
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution window = new ScaledResolution(mc);
		int scale = window.getScaleFactor();
		// Centre of vanilla's crosshair: the GUI pixel at (w/2, h/2), in real pixels.
		int cx = window.getScaledWidth() / 2 * scale + scale / 2;
		int cy = window.getScaledHeight() / 2 * scale + scale / 2;
		GlStateManager.pushMatrix();
		GlStateManager.scale(1f / scale, 1f / scale, 1f);
		for (int[] r : rects(style.get(), size.get() * scale / 2 + 1, gap.get() * scale / 2, thickness.get(), cx, cy)) {
			if (outline.get()) {
				Gui.drawRect(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 0xFF000000);
			}
		}
		for (int[] r : rects(style.get(), size.get() * scale / 2 + 1, gap.get() * scale / 2, thickness.get(), cx, cy)) {
			Gui.drawRect(r[0], r[1], r[2], r[3], color.get());
		}
		GlStateManager.popMatrix();
		GlStateManager.color(1, 1, 1, 1);
	}

	/**
	 * Rectangles {x1, y1, x2, y2} (x2/y2 exclusive) for a crosshair centred on the pixel (cx, cy).
	 * Arms are {@code length} pixels long and start {@code gap} pixels from the centre pixel.
	 */
	static int[][] rects(int style, int length, int gap, int thickness, int cx, int cy) {
		int lo = -(thickness - 1) / 2;       // thickness spread around the centre pixel
		int hi = lo + thickness;
		int[] dot = {cx + lo, cy + lo, cx + hi, cy + hi};
		int[] up = {cx + lo, cy - gap - length, cx + hi, cy - gap};
		int[] down = {cx + lo, cy + gap + 1, cx + hi, cy + gap + 1 + length};
		int[] left = {cx - gap - length, cy + lo, cx - gap, cy + hi};
		int[] right = {cx + gap + 1, cy + lo, cx + gap + 1 + length, cy + hi};
		switch (style) {
			case STYLE_DOT:
				return new int[][]{dot};
			case STYLE_CROSS_DOT:
				return new int[][]{up, down, left, right, dot};
			case STYLE_T:
				return new int[][]{down, left, right};
			default:
				return new int[][]{up, down, left, right};
		}
	}
}
