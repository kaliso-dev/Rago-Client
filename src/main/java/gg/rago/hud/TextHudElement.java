package gg.rago.hud;

import gg.rago.core.FeatureClass;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;

/** A HUD element that shows one line of text, with an optional dark background box. */
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

	@Override
	public int width(TextRenderer text, boolean placeholder) {
		return text.getWidth(text(placeholder)) + pad() * 2;
	}

	@Override
	public int height(TextRenderer text, boolean placeholder) {
		return text.fontHeight + pad() * 2 - (background.get() ? 1 : 0);
	}

	@Override
	public void render(TextRenderer text, boolean placeholder) {
		String s = text(placeholder);
		if (background.get()) {
			GuiElement.fill(0, 0, width(text, placeholder), height(text, placeholder), BACKGROUND);
		}
		int p = pad();
		if (shadow.get()) {
			text.drawWithShadow(s, p, p, textColor.get());
		} else {
			text.draw(s, p, p, textColor.get());
		}
	}
}
