package gg.rago.hud;

import gg.rago.core.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Draws every enabled HUD element at its saved position and scale. Elements still at their default
 * place in the left column are stacked so they never overlap, whatever their style's height,
 * and close up when one above is hidden.
 */
public final class HudManager {
	private final ModuleRegistry registry;

	public HudManager(ModuleRegistry registry) {
		this.registry = registry;
	}

	public List<HudElement> elements() {
		return registry.allOf(HudElement.class);
	}

	/**
	 * Called after the vanilla HUD is drawn while in a world. {@code onlyVanilla} draws only the
	 * elements that replace vanilla ones, for when Rago's own HUD is hidden.
	 */
	public void render(boolean onlyVanilla) {
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution window = new ScaledResolution(mc);
		List<HudElement> shown = new ArrayList<>();
		for (HudElement element : elements()) {
			if (element.isEnabled() && (!onlyVanilla || element.replacesVanilla()) && element.hasContent()) {
				shown.add(element);
			}
		}
		stack(shown, mc.fontRendererObj, window.getScaledHeight(), e -> false);
		for (HudElement element : shown) {
			long t = gg.rago.debug.FrameProfile.ON ? System.nanoTime() : 0;
			draw(element, mc.fontRendererObj, window.getScaledWidth(), window.getScaledHeight(), false);
			if (gg.rago.debug.FrameProfile.ON) {
				gg.rago.debug.FrameProfile.element(element.id(), System.nanoTime() - t);
			}
		}
	}

	/** Gap between stacked elements, in scaled pixels. */
	static final int STACK_GAP = 2;

	/**
	 * Moves the shown elements that sit at their default place on the left edge (top and middle)
	 * down just enough to clear the one above. Elements the player has moved keep their place exactly.
	 */
	public static void stack(List<HudElement> shown, FontRenderer text, int screenH, Predicate<HudElement> placeholder) {
		List<HudElement> column = new ArrayList<>();
		Map<HudElement, int[]> natural = new HashMap<>();
		for (HudElement e : shown) {
			e.stackShift = 0;
			HudPosition p = e.position();
			if (p.isDefault() && p.anchor().fx == 0 && p.anchor().fy < 1) {
				int h = Math.round(e.height(text, placeholder.test(e)) * p.scale());
				natural.put(e, new int[]{p.screenY(h, screenH), h});
				column.add(e);
			}
		}
		// stable sort: elements starting at the same height keep the registry's order
		column.sort(Comparator.comparingInt(e -> natural.get(e)[0]));
		int cursor = Integer.MIN_VALUE;
		for (HudElement e : column) {
			int[] yh = natural.get(e);
			int placed = Math.max(yh[0], cursor);
			e.stackShift = placed - yh[0];
			cursor = placed + yh[1] + STACK_GAP;
		}
	}

	/** Draws one element and returns its on-screen bounds as {x, y, w, h}. */
	public static int[] draw(HudElement element, FontRenderer text, int screenW, int screenH, boolean placeholder) {
		int[] b = bounds(element, text, screenW, screenH, placeholder);
		float scale = element.position().scale();
		GlStateManager.pushMatrix();
		GlStateManager.translate(b[0], b[1], 0);
		GlStateManager.scale(scale, scale, 1);
		element.render(text, placeholder);
		GlStateManager.popMatrix();
		GlStateManager.color(1, 1, 1, 1);
		return b;
	}

	public static int[] bounds(HudElement element, FontRenderer text, int screenW, int screenH, boolean placeholder) {
		float scale = element.position().scale();
		int w = Math.round(element.width(text, placeholder) * scale);
		int h = Math.round(element.height(text, placeholder) * scale);
		HudPosition p = element.position();
		return new int[]{p.screenX(w, screenW), Math.min(p.screenY(h, screenH) + element.stackShift, Math.max(0, screenH - h)), w, h};
	}
}
