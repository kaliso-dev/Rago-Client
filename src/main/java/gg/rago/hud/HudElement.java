package gg.rago.hud;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.render.TextRenderer;

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
		super(id, featureClass, defaultEnabled);
		this.position = new HudPosition(anchor, x, y);
		this.shadow = addOption(new BooleanOption("shadow", true));
		this.background = addOption(new BooleanOption("background", false));
		this.textColor = addOption(new ColorOption("text_color", defaultColor));
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
	public abstract int width(TextRenderer text, boolean placeholder);

	public abstract int height(TextRenderer text, boolean placeholder);

	public abstract void render(TextRenderer text, boolean placeholder);

	/** Whether there is anything to draw right now outside the editor. */
	public boolean hasContent() {
		return true;
	}
}
