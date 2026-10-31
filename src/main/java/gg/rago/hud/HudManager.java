package gg.rago.hud;

import gg.rago.core.ModuleRegistry;
import net.minecraft.client.Minecraft;
import gg.rago.render.HudText;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.List;
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

	private List<HudElement> elements = new ArrayList<>();
	private int elementsFor = -1;
	/** Reused every frame so drawing the HUD allocates nothing. */
	private final List<HudElement> shown = new ArrayList<>();

	public List<HudElement> elements() {
		// modules are only registered at start-up: no need to filter the registry every frame
		if (elementsFor != registry.size()) {
			elements = registry.allOf(HudElement.class);
			elementsFor = registry.size();
		}
		return elements;
	}

	/**
	 * Called after the vanilla HUD is drawn while in a world. {@code onlyVanilla} draws only the
	 * elements that replace vanilla ones, for when Rago's own HUD is hidden.
	 */
	public void render(boolean onlyVanilla) {
		nextFrame();
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution window = new ScaledResolution(mc);
		shown.clear();
		for (HudElement element : elements()) {
			if (element.isEnabled() && (!onlyVanilla || element.replacesVanilla()) && element.hasContent()) {
				shown.add(element);
			}
		}
		stack(shown, mc.fontRendererObj, window.getScaledHeight(), NO_PLACEHOLDER);
		for (HudElement element : shown) {
			long t = gg.rago.debug.FrameProfile.ON ? System.nanoTime() : 0;
			int[] b = draw(element, mc.fontRendererObj, window.getScaledWidth(), window.getScaledHeight(), false);
			if (collect != null) {
				if (collected * 4 + 4 <= collect.length) {
					System.arraycopy(b, 0, collect, collected * 4, 4);
				}
				collected++;
			}
			if (gg.rago.debug.FrameProfile.ON) {
				gg.rago.debug.FrameProfile.element(element.id(), System.nanoTime() - t);
			}
		}
	}

	private static int frame;
	private static int[] collect;
	private static int collected;

	/**
	 * Until {@link #collected()}, the bounds of every element drawn go into {@code into}, four
	 * numbers each (x, y, width, height in GUI pixels), for the HUD cache to lay over the frame only
	 * where the HUD is.
	 */
	public static void collectBounds(int[] into) {
		collect = into;
		collected = 0;
	}

	/** How many elements were drawn since {@link #collectBounds} (more than fit when it overflowed); stops collecting. */
	public static int collected() {
		int n = collected;
		collect = null;
		collected = 0;
		return n;
	}

	/** Counts HUD frames, so elements can work out their text once per frame. */
	public static int frame() {
		return frame;
	}

	public static void nextFrame() {
		frame++;
	}

	/** Gap between stacked elements, in scaled pixels. */
	static final int STACK_GAP = 2;

	/**
	 * Moves the shown elements that sit at their default place on the left edge (top and middle)
	 * down just enough to clear the one above. Elements the player has moved keep their place exactly.
	 */
	public static void stack(List<HudElement> shown, FontRenderer text, int screenH, Predicate<HudElement> placeholder) {
		List<HudElement> column = COLUMN;
		column.clear();
		for (HudElement e : shown) {
			e.stackShift = 0;
			HudPosition p = e.position();
			if (p.isDefault() && p.anchor().fx == 0 && p.anchor().fy < 1) {
				int h = Math.round(e.height(text, placeholder.test(e)) * p.scale());
				e.stackY = p.screenY(h, screenH);
				e.stackH = h;
				column.add(e);
			}
		}
		// stable insertion sort (a handful of elements): same height keeps the registry's order
		for (int i = 1; i < column.size(); i++) {
			HudElement e = column.get(i);
			int j = i - 1;
			while (j >= 0 && column.get(j).stackY > e.stackY) {
				column.set(j + 1, column.get(j));
				j--;
			}
			column.set(j + 1, e);
		}
		int cursor = Integer.MIN_VALUE;
		for (HudElement e : column) {
			int placed = Math.max(e.stackY, cursor);
			e.stackShift = placed - e.stackY;
			cursor = placed + e.stackH + STACK_GAP;
		}
		column.clear();
	}

	private static final List<HudElement> COLUMN = new ArrayList<>();
	private static final Predicate<HudElement> NO_PLACEHOLDER = e -> false;

	/** Draws one element and returns its on-screen bounds as {x, y, w, h}. */
	public static int[] draw(HudElement element, FontRenderer text, int screenW, int screenH, boolean placeholder) {
		int[] b = bounds(element, text, screenW, screenH, placeholder);
		float scale = element.position().scale();
		GlStateManager.pushMatrix();
		GlStateManager.translate(b[0], b[1], 0);
		GlStateManager.scale(scale, scale, 1);
		HudText.inElement(scale);
		try {
			element.render(text, placeholder);
		} finally {
			HudText.inElement(0);
		}
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
