package gg.rago.hud;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.HudText;
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
	protected final ChoiceOption font;

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
		section("style");
		this.font = addOption(new ChoiceOption("font", HudText.MINECRAFT, HudText.FONTS));
		this.shadow = addOption(new BooleanOption("shadow", true));
		this.textColor = addOption(new ColorOption("text_color", defaultColor));
		section("background");
		this.background = addOption(new BooleanOption("background", defaultBackground));
		section(null);
	}

	@Override
	public Category category() {
		return Category.HUD;
	}

	public final HudPosition position() {
		return position;
	}

	/** How far {@link HudManager#stack} moved this element down this frame, in scaled pixels. */
	int stackShift;
	/** Where the element would sit unstacked, and its height, while {@link HudManager#stack} runs. */
	int stackY;
	int stackH;

	@Override
	public void reset() {
		super.reset();
		position.reset();
	}

	/** Unscaled width in GUI pixels. {@code placeholder} is true in the editor when there is no live data. */
	public abstract int width(FontRenderer text, boolean placeholder);

	public abstract int height(FontRenderer text, boolean placeholder);

	public abstract void render(FontRenderer text, boolean placeholder);

	/**
	 * Whether this element stands in for part of the vanilla HUD (scoreboard, boss bar), so it stays
	 * visible when Rago's own HUD is hidden or the F3 screen is open.
	 */
	public boolean replacesVanilla() {
		return false;
	}

	/** Whether there is anything to draw right now outside the editor. */
	public boolean hasContent() {
		return true;
	}

	/** Whether the element is drawn on the screen at all: false when it is shown elsewhere (the tab list). */
	public boolean onScreen() {
		return true;
	}

	/** Draws text in the chosen font, with or without shadow as configured. */
	protected final void drawText(FontRenderer text, String s, float x, float y, int color) {
		HudText.draw(font.get(), s, x, y, color, shadow.get());
	}

	/** Width of {@code s} in the chosen font, in GUI pixels. */
	protected final int textWidth(String s) {
		return HudText.width(font.get(), s);
	}
}
