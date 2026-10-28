package gg.rago.hud;

import gg.rago.core.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;

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

	/** Called from GameGui.render while in a world. */
	public void render() {
		Minecraft mc = Minecraft.getInstance();
		Window window = new Window(mc);
		for (HudElement element : elements()) {
			if (element.isEnabled() && element.hasContent()) {
				draw(element, mc.textRenderer, window.getWidth(), window.getHeight(), false);
			}
		}
	}

	/** Draws one element and returns its on-screen bounds as {x, y, w, h}. */
	public static int[] draw(HudElement element, TextRenderer text, int screenW, int screenH, boolean placeholder) {
		int[] b = bounds(element, text, screenW, screenH, placeholder);
		float scale = element.position().scale();
		GlStateManager.pushMatrix();
		GlStateManager.translatef(b[0], b[1], 0);
		GlStateManager.scalef(scale, scale, 1);
		element.render(text, placeholder);
		GlStateManager.popMatrix();
		GlStateManager.color4f(1, 1, 1, 1);
		return b;
	}

	public static int[] bounds(HudElement element, TextRenderer text, int screenW, int screenH, boolean placeholder) {
		float scale = element.position().scale();
		int w = Math.round(element.width(text, placeholder) * scale);
		int h = Math.round(element.height(text, placeholder) * scale);
		HudPosition p = element.position();
		return new int[]{p.screenX(w, screenW), p.screenY(h, screenH), w, h};
	}
}
