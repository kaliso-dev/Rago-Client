package gg.rago.hud;

import gg.rago.core.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

/** Draws every enabled HUD element at its saved position and scale. */
public final class HudManager {
	private final ModuleRegistry registry;

	public HudManager(ModuleRegistry registry) {
		this.registry = registry;
	}

	public List<HudElement> elements() {
		return registry.allOf(HudElement.class);
	}

	/** Called after the vanilla HUD is drawn while in a world. */
	public void render() {
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution window = new ScaledResolution(mc);
		for (HudElement element : elements()) {
			if (element.isEnabled() && element.hasContent()) {
				draw(element, mc.fontRendererObj, window.getScaledWidth(), window.getScaledHeight(), false);
			}
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
		return new int[]{p.screenX(w, screenW), p.screenY(h, screenH), w, h};
	}
}
