package gg.rago.hud;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.gui.Theme;
import gg.rago.render.HudText;
import gg.rago.render.Paint;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A HUD element that shows a value with an optional label ("144" + "FPS"), styled the way the
 * player likes: a preset style (Rago, Vanilla, Box, Classic, Minimal) fills in every setting, and
 * each one can then be changed on its own: font, label before or after, separator, brackets, the
 * colour of each part, rainbow text, alignment, and a background with round corners and border.
 * Values with several lines (coordinates) show each line on its own, without label or brackets.
 */
public abstract class TextHudElement extends HudElement implements Styled {
	public static final String[] STYLES = {"rago", "vanilla", "box", "classic", "minimal"};
	/** The look of a text element that has never been styled. */
	public static final String DEFAULT_STYLE = "rago";

	private final ChoiceOption style;
	private final ChoiceOption labelPosition;
	private final ChoiceOption separator;
	private final ChoiceOption brackets;
	private final ColorOption labelColor;
	private final ColorOption bracketColor;
	private final BooleanOption chroma;
	private final ChoiceOption align;
	private final ColorOption backgroundColor;
	private final IntOption radius;
	private final IntOption padding;
	private final IntOption minWidth;
	private final BooleanOption border;
	private final ColorOption borderColor;

	protected TextHudElement(String id, FeatureClass featureClass, boolean defaultEnabled, Anchor anchor, int x, int y) {
		this(id, featureClass, defaultEnabled, anchor, x, y, 0xFFFFFFFF);
	}

	protected TextHudElement(String id, FeatureClass featureClass, boolean defaultEnabled, Anchor anchor, int x, int y, int defaultColor) {
		super(id, featureClass, defaultEnabled, anchor, x, y, defaultColor);
		boolean labelled = label(false) != null;
		section("style");
		style = addOption(new ChoiceOption("style", "vanilla", STYLES).onSelect(this::applyStyle));
		section("text");
		labelPosition = addOption(new ChoiceOption("label_position", labelled ? "after" : "none", "none", "before", "after"));
		labelPosition.showIf(() -> labelled);
		separator = addOption(new ChoiceOption("separator", "space", "space", "colon", "dash", "pipe"));
		separator.showIf(() -> labelled && !labelPosition.is("none"));
		brackets = addOption(new ChoiceOption("brackets", "none", "none", "square", "round", "angle", "curly"));
		labelColor = addOption(new ColorOption("label_color", 0xFFFFFFFF));
		labelColor.showIf(() -> labelled && !labelPosition.is("none"));
		bracketColor = addOption(new ColorOption("bracket_color", 0xFFAAAAAA));
		bracketColor.showIf(() -> !brackets.is("none"));
		chroma = addOption(new BooleanOption("chroma", false));
		align = addOption(new ChoiceOption("align", "left", "left", "center", "right"));
		section("background");
		backgroundColor = addOption(new ColorOption("background_color", 0x90000000));
		backgroundColor.showIf(background::get);
		radius = addOption(new IntOption("corner_radius", 0, 0, 8));
		radius.showIf(background::get);
		padding = addOption(new IntOption("padding", 3, 0, 10));
		padding.showIf(background::get);
		minWidth = addOption(new IntOption("min_width", 0, 0, 120));
		border = addOption(new BooleanOption("border", false));
		border.showIf(background::get);
		borderColor = addOption(new ColorOption("border_color", 0x40FFFFFF));
		borderColor.showIf(() -> background.get() && border.get());
		section(null);
		applyStyle(DEFAULT_STYLE);
		for (gg.rago.config.Option<?> o : options()) {
			o.makeCurrentDefault();
		}
	}

	/**
	 * Short label shown with the value, or null for none. {@code after} asks for the form that reads
	 * after the number ("42 ms") rather than before it ("Ping 42").
	 */
	protected String label(boolean after) {
		return null;
	}

	/** The value to show; {@code placeholder} asks for a sample value for the editor. May hold several lines. */
	protected abstract String value(boolean placeholder);

	/** Colour of the value; elements can colour it by the value itself (ping, FPS). */
	protected int valueColor(boolean placeholder) {
		return textColor.get();
	}

	@Override
	public String defaultStyle() {
		return DEFAULT_STYLE;
	}

	/** Fills in every style setting from a preset; the player can change any of them afterwards. */
	@Override
	public void applyStyle(String preset) {
		style.set(preset);
		switch (preset) {
			case "rago":
				font.set(HudText.POPPINS);
				shadow.set(false);
				textColor.set(0xFFFFFFFF);
				labelPosition.set("before");
				separator.set("space");
				brackets.set("none");
				labelColor.set(Theme.ACCENT);
				chroma.set(false);
				align.set("left");
				background.set(true);
				backgroundColor.set(0xB0101014);
				radius.set(4);
				padding.set(5);
				minWidth.set(0);
				border.set(true);
				borderColor.set(0x22FFFFFF);
				break;
			case "box":
				font.set(HudText.MINECRAFT);
				shadow.set(true);
				textColor.set(0xFFFFFFFF);
				labelPosition.set("after");
				separator.set("space");
				brackets.set("none");
				labelColor.set(0xFFFFFFFF);
				chroma.set(false);
				align.set("center");
				background.set(true);
				backgroundColor.set(0x6F000000);
				radius.set(0);
				padding.set(4);
				minWidth.set(56);
				border.set(false);
				break;
			case "classic":
				font.set(HudText.MINECRAFT);
				shadow.set(true);
				textColor.set(0xFFFFFFFF);
				labelPosition.set("before");
				separator.set("colon");
				brackets.set("square");
				labelColor.set(0xFFFFFFFF);
				bracketColor.set(0xFFAAAAAA);
				chroma.set(false);
				align.set("left");
				background.set(false);
				minWidth.set(0);
				break;
			case "minimal":
				font.set(HudText.INTER);
				shadow.set(false);
				textColor.set(0xFFFFFFFF);
				labelPosition.set("after");
				separator.set("space");
				brackets.set("none");
				labelColor.set(0xFF9A98A0);
				chroma.set(false);
				align.set("left");
				background.set(false);
				minWidth.set(0);
				break;
			default: // vanilla
				font.set(HudText.MINECRAFT);
				shadow.set(true);
				textColor.set(0xFFFFFFFF);
				labelPosition.set("after");
				separator.set("space");
				brackets.set("none");
				labelColor.set(0xFFFFFFFF);
				chroma.set(false);
				align.set("left");
				background.set(false);
				backgroundColor.set(0x90000000);
				radius.set(0);
				padding.set(3);
				minWidth.set(0);
				border.set(false);
		}
		if (label(false) == null) {
			labelPosition.set("none");
		}
	}

	// ---- layout ---------------------------------------------------------------------------------

	private static final int BRACKET = 0;
	private static final int LABEL = 1;
	private static final int VALUE = 2;

	/** A piece of a line and which colour it takes. */
	private static final class Seg {
		final String text;
		final int role;

		Seg(String text, int role) {
			this.text = text;
			this.role = role;
		}
	}

	/** The lines and width worked out for this frame: one element asks for them several times a frame. */
	private List<List<Seg>> frameLines;
	private int frameWidth = -1;
	private int frameId = -1;
	private boolean framePlaceholder;

	private List<List<Seg>> lines(boolean placeholder) {
		int frame = HudManager.frame();
		if (frameLines == null || frame != frameId || placeholder != framePlaceholder) {
			frameLines = buildLines(placeholder);
			frameWidth = -1;
			frameId = frame;
			framePlaceholder = placeholder;
		}
		return frameLines;
	}

	/** Each line as coloured pieces: [ label separator value ] or [ value separator label ]. */
	private List<List<Seg>> buildLines(boolean placeholder) {
		String[] raw = value(placeholder).split("\n", -1);
		List<List<Seg>> out = new ArrayList<>(raw.length);
		if (raw.length > 1) {
			for (String line : raw) {
				out.add(Collections.singletonList(new Seg(line, VALUE)));
			}
			return out;
		}
		List<Seg> line = new ArrayList<>(5);
		String[] br = bracketPair();
		if (br != null) {
			line.add(new Seg(br[0], BRACKET));
		}
		boolean after = labelPosition.is("after");
		String label = labelPosition.is("none") ? null : label(after);
		if (label != null && !label.isEmpty() && !after) {
			line.add(new Seg(label + separatorText(false), LABEL));
		}
		line.add(new Seg(raw[0], VALUE));
		if (label != null && !label.isEmpty() && after) {
			line.add(new Seg(separatorText(true) + label, LABEL));
		}
		if (br != null) {
			line.add(new Seg(br[1], BRACKET));
		}
		out.add(line);
		return out;
	}

	private String[] bracketPair() {
		switch (brackets.get()) {
			case "square":
				return new String[] {"[", "]"};
			case "round":
				return new String[] {"(", ")"};
			case "angle":
				return new String[] {"<", ">"};
			case "curly":
				return new String[] {"{", "}"};
			default:
				return null;
		}
	}

	private String separatorText(boolean labelAfter) {
		switch (separator.get()) {
			case "colon":
				return labelAfter ? " " : ": ";
			case "dash":
				return " - ";
			case "pipe":
				return " | ";
			default:
				return " ";
		}
	}

	private int lineWidth(List<Seg> line) {
		int w = 0;
		for (Seg s : line) {
			w += textWidth(s.text);
		}
		return w;
	}

	private int pad() {
		return background.get() ? padding.get() : 0;
	}

	private int vpad() {
		return background.get() ? Math.max(1, Math.round(padding.get() * 0.7f)) : 0;
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		return boxWidth(placeholder);
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		return boxHeight(placeholder);
	}

	/** Width of the text and its background, whatever a subclass adds around them. */
	protected final int boxWidth(boolean placeholder) {
		List<List<Seg>> lines = lines(placeholder);
		if (frameWidth < 0) {
			int w = 0;
			for (List<Seg> line : lines) {
				w = Math.max(w, lineWidth(line));
			}
			frameWidth = w;
		}
		return Math.max(frameWidth + pad() * 2, minWidth.get());
	}

	protected final int boxHeight(boolean placeholder) {
		return lines(placeholder).size() * 10 - 1 + vpad() * 2 - (background.get() ? 1 : 0);
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		List<List<Seg>> lines = lines(placeholder);
		int w = boxWidth(placeholder);
		int h = boxHeight(placeholder);
		if (background.get()) {
			Paint.round(0, 0, w, h, radius.get(), backgroundColor.get());
			if (border.get()) {
				Paint.outline(0, 0, w, h, radius.get(), 1, borderColor.get());
			}
		}
		int[] colors = {bracketColor.get(), labelColor.get(), valueColor(placeholder)};
		int y = vpad();
		for (List<Seg> line : lines) {
			int lw = lineWidth(line);
			float x = align.is("center") ? (w - lw) / 2f : align.is("right") ? w - pad() - lw : pad();
			for (Seg seg : line) {
				int color = colors[seg.role];
				if (chroma.get() && seg.role != BRACKET) {
					color = Paint.chroma(x / 160f, color >>> 24);
				}
				drawText(text, seg.text, x, y, color);
				x += textWidth(seg.text);
			}
			y += 10;
		}
	}
}
