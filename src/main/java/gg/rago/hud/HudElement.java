package gg.rago.hud;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.gui.FontRenderer;

/**
 * A module that draws something on the in-game HUD. Elements draw in local coordinates
 * starting at (0, 0); {@link HudManager} applies position and scale.
 */
public abstract class HudElement extends Module {
	private final HudPosition position;
	protected final BooleanOption shadow;
	protected final BooleanOption background;
	protected final ColorOption textColor;

	protected HudElement(String id, FeatureClass featureClass, boolean defaultEnabled, Anchor anchor, int x, int y) {
		this(id, featureClass, defaultEnabled, anchor, x, y, 0xFFFFFFFF);
	}

	protected HudElement(String id, FeatureClass featureClass, boolean defaultEnabled, Anchor anchor, int x, int y, int defaultColor) {
		this(id, featureClass, defaultEnabled, anchor, x, y, defaultColor, false);
	}

	protected HudElement(String id, FeatureClass featureClass, boolean defaultEnabled, Anchor anchor, int x, int y,
						 int defaultColor, boolean defaultBackground) {
		super(id, featureClass, defaultEnabled);
		this.position = new HudPosition(anchor, x, y);
		this.shadow = addOption(new BooleanOption("shadow", true));
		this.background = addOption(new BooleanOption("background", defaultBackground));
		this.textColor = addOption(new ColorOption("text_color", defaultColor));
	}

	@Override
	public Category category() {
		return Category.HUD;
	}

	public final HudPosition position() {
		return position;
	}

	@Override
	public void reset() {
		super.reset();
		position.reset();
	}

	/** Unscaled width in GUI pixels. {@code placeholder} is true in the editor when there is no live data. */
	public abstract int width(FontRenderer text, boolean placeholder);

	public abstract int height(FontRenderer text, boolean placeholder);

	public abstract void render(FontRenderer text, boolean placeholder);

	/** Whether there is anything to draw right now outside the editor. */
	public boolean hasContent() {
		return true;
	}

	/** Draws text in the element's colour, with or without shadow as configured. */
	protected final void drawText(FontRenderer text, String s, int x, int y, int color) {
		if (shadow.get()) {
			text.drawStringWithShadow(s, x, y, color);
		} else {
			text.drawString(s, x, y, color);
		}
	}
}
