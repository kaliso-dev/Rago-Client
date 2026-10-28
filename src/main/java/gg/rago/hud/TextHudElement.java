package gg.rago.hud;

import gg.rago.core.FeatureClass;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;

/** A HUD element that shows text (one or more lines split on {@code \n}), with an optional dark background box. */
public abstract class TextHudElement extends HudElement {
	private static final int PAD = 3;
	private static final int BACKGROUND = 0x90000000;

	protected TextHudElement(String id, FeatureClass featureClass, boolean defaultEnabled, Anchor anchor, int x, int y) {
		this(id, featureClass, defaultEnabled, anchor, x, y, 0xFFFFFFFF);
	}

	protected TextHudElement(String id, FeatureClass featureClass, boolean defaultEnabled, Anchor anchor, int x, int y, int defaultColor) {
		super(id, featureClass, defaultEnabled, anchor, x, y, defaultColor);
	}

	/** The text to show; {@code placeholder} asks for a sample value for the editor. */
	protected abstract String text(boolean placeholder);

	private int pad() {
		return background.get() ? PAD : 0;
	}

	private static String[] lines(String s) {
		return s.split("\n", -1);
	}

	@Override
	public int width(TextRenderer text, boolean placeholder) {
		int w = 0;
		for (String line : lines(text(placeholder))) {
			w = Math.max(w, text.getWidth(line));
		}
		return w + pad() * 2;
	}

	@Override
	public int height(TextRenderer text, boolean placeholder) {
		int n = lines(text(placeholder)).length;
		return n * (text.fontHeight + 1) - 1 + pad() * 2 - (background.get() ? 1 : 0);
	}

	@Override
	public void render(TextRenderer text, boolean placeholder) {
		String[] lines = lines(text(placeholder));
		if (background.get()) {
			GuiElement.fill(0, 0, width(text, placeholder), height(text, placeholder), BACKGROUND);
		}
		int p = pad();
		for (int i = 0; i < lines.length; i++) {
			drawText(text, lines[i], p, p + i * (text.fontHeight + 1), textColor.get());
		}
	}
}
