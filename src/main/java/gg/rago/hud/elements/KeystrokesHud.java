package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.gui.Theme;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import gg.rago.hud.Styled;
import gg.rago.input.InputState;
import gg.rago.render.HudText;
import gg.rago.render.Paint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Movement keys, mouse buttons and jump as boxes that light up while held, with a smooth fade
 * between idle and pressed. Labels follow the player's own bindings. A style preset (Rago, Box,
 * Classic, Minimal) fills in sizes and colours, which can then be changed one by one. Only reads
 * the held state vanilla already tracks.
 */
public final class KeystrokesHud extends HudElement implements Styled {
	@Override
	public Category category() {
		return Category.PVP;
	}

	public static final String[] STYLES = {"rago", "box", "classic", "minimal"};

	private final InputState input;
	private final ChoiceOption style;
	private final BooleanOption showMouse;
	private final BooleanOption showCps;
	private final BooleanOption showSpace;
	private final IntOption keySize;
	private final IntOption gap;
	private final IntOption radius;
	private final IntOption fade;
	private final ColorOption idleColor;
	private final ColorOption pressedColor;
	private final ColorOption pressedText;
	private final BooleanOption border;
	private final ColorOption borderColor;
	private final Map<KeyBinding, float[]> levels = new IdentityHashMap<>();
	private long lastFrame;

	public KeystrokesHud(InputState input) {
		super("keystrokes", FeatureClass.INFORMATIONAL, true, Anchor.TOP_RIGHT, -4, 18, 0xFFFFFFFF, true);
		this.input = input;
		showMouse = addOption(new BooleanOption("show_mouse", true));
		showCps = addOption(new BooleanOption("show_cps", true));
		showCps.showIf(showMouse::get);
		showSpace = addOption(new BooleanOption("show_space", true));
		section("style");
		style = addOption(new ChoiceOption("style", "rago", STYLES).onSelect(this::applyStyle));
		section("layout");
		keySize = addOption(new IntOption("key_size", 22, 16, 32));
		gap = addOption(new IntOption("gap", 2, 0, 6));
		radius = addOption(new IntOption("corner_radius", 4, 0, 8));
		fade = addOption(new IntOption("fade_ms", 90, 0, 300));
		section("background");
		idleColor = addOption(new ColorOption("idle_color", 0xB0101014));
		idleColor.showIf(background::get);
		pressedColor = addOption(new ColorOption("pressed_color", Theme.ACCENT));
		pressedText = addOption(new ColorOption("pressed_text_color", 0xFFFFFFFF));
		border = addOption(new BooleanOption("border", true));
		borderColor = addOption(new ColorOption("border_color", 0x22FFFFFF));
		borderColor.showIf(border::get);
		section(null);
		applyStyle("rago");
		for (Option<?> o : options()) {
			o.makeCurrentDefault();
		}
	}

	@Override
	public String defaultStyle() {
		return "rago";
	}

	/** Fills in sizes and colours from a preset; each can be changed afterwards. */
	@Override
	public void applyStyle(String preset) {
		style.set(preset);
		switch (preset) {
			case "box":
				font.set(HudText.MINECRAFT);
				shadow.set(false);
				textColor.set(0xFFFFFFFF);
				background.set(true);
				idleColor.set(0x66000000);
				pressedColor.set(0x66FFFFFF);
				pressedText.set(0xFF000000);
				radius.set(0);
				gap.set(2);
				keySize.set(22);
				fade.set(60);
				border.set(false);
				break;
			case "classic":
				font.set(HudText.MINECRAFT);
				shadow.set(true);
				textColor.set(0xFFFFFFFF);
				background.set(true);
				idleColor.set(0x90000000);
				pressedColor.set(0xFFFFFFFF);
				pressedText.set(0xFF000000);
				radius.set(0);
				gap.set(2);
				keySize.set(22);
				fade.set(0);
				border.set(false);
				break;
			case "minimal":
				font.set(HudText.INTER);
				shadow.set(false);
				textColor.set(0xFFFFFFFF);
				background.set(false);
				pressedColor.set(0x50FFFFFF);
				pressedText.set(0xFFFFFFFF);
				radius.set(3);
				gap.set(3);
				keySize.set(22);
				fade.set(120);
				border.set(true);
				borderColor.set(0x60FFFFFF);
				break;
			default: // rago
				font.set(HudText.POPPINS);
				shadow.set(false);
				textColor.set(0xFFE4E4E7);
				background.set(true);
				idleColor.set(0xB0101014);
				pressedColor.set(Theme.ACCENT);
				pressedText.set(0xFFFFFFFF);
				radius.set(4);
				gap.set(2);
				keySize.set(22);
				fade.set(90);
				border.set(true);
				borderColor.set(0x22FFFFFF);
		}
	}

	private int width() {
		return keySize.get() * 3 + gap.get() * 2;
	}

	private int spaceHeight() {
		return Math.max(8, keySize.get() / 2 + 1);
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		return width();
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		int k = keySize.get();
		int g = gap.get();
		int h = k * 2 + g;
		if (showMouse.get()) {
			h += g + k;
		}
		if (showSpace.get()) {
			h += g + spaceHeight();
		}
		return h;
	}

	@Override
	public boolean hasContent() {
		return Minecraft.getMinecraft().gameSettings != null;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 1e9f);
		lastFrame = now;
		int k = keySize.get();
		int g = gap.get();
		int w = width();
		int n = 0;
		keys[n++].set(o.keyBindForward, k + g, 0, k, k, null);
		int y = k + g;
		keys[n++].set(o.keyBindLeft, 0, y, k, k, null);
		keys[n++].set(o.keyBindBack, k + g, y, k, k, null);
		keys[n++].set(o.keyBindRight, (k + g) * 2, y, k, k, null);
		y += k + g;
		if (showMouse.get()) {
			int half = (w - g) / 2;
			String left = showCps.get() ? cps(placeholder ? 9 : input.attackCps(now)) : null;
			String right = showCps.get() ? cps(placeholder ? 4 : input.useCps(now)) : null;
			keys[n++].set(o.keyBindAttack, 0, y, half, k, left);
			keys[n++].set(o.keyBindUseItem, half + g, y, w - half - g, k, right);
			y += k + g;
		}
		for (int i = 0; i < n; i++) {
			keys[i].level = level(keys[i].binding, !placeholder && keys[i].binding.isKeyDown(), dt);
		}
		float space = showSpace.get() ? level(o.keyBindJump, !placeholder && o.keyBindJump.isKeyDown(), dt) : 0;

		// every box and border in one draw call, then every label in one more (two with CPS)
		WorldRenderer shapes = Paint.beginShapes();
		for (int i = 0; i < n; i++) {
			Key key = keys[i];
			box(shapes, key.x, key.y, key.w, key.h, key.level);
		}
		if (showSpace.get()) {
			int sh = spaceHeight();
			box(shapes, 0, y, w, sh, space);
			int bar = Paint.mix(textColor.get(), pressedText.get(), space);
			Paint.roundInto(shapes, w / 2f - k * 0.55f, y + sh / 2f - 0.75f, k * 1.1f, 1.5f, 0.75f, bar);
		}
		Paint.endShapes();

		HudText.Batch labels = this.labels.start(font.get());
		boolean subs = false;
		for (int i = 0; i < n; i++) {
			Key key = keys[i];
			int color = Paint.mix(textColor.get(), pressedText.get(), key.level);
			String label = fit(keyName(key.binding.getKeyCode()), key.w - 4);
			float top = key.sub == null ? key.y + (key.h - 8) / 2f : key.y + (key.h - 17) / 2f;
			labels.add(label, key.x + key.w / 2f - textWidth(label) / 2f, top, color, shadow.get());
			subs |= key.sub != null;
		}
		labels.end();
		if (subs) {
			// the CPS lines at 70 %, scaled around their own line so they stay centred under the label
			float lineY = 0;
			for (int i = 0; i < n; i++) {
				if (keys[i].sub != null) {
					lineY = keys[i].y + (keys[i].h - 17) / 2f + 10;
				}
			}
			GlScale.push(0, lineY, SUB_SCALE);
			HudText.Batch small = this.small.start(font.get());
			for (int i = 0; i < n; i++) {
				Key key = keys[i];
				if (key.sub != null) {
					int color = Paint.mix(textColor.get(), pressedText.get(), key.level);
					float cx = key.x + key.w / 2f;
					// scaling around (0, lineY) instead of the key's centre moves x; this undoes it
					float x = cx - textWidth(key.sub) / 2f + cx * (1 - SUB_SCALE) / SUB_SCALE;
					small.add(key.sub, x, lineY, color, shadow.get());
				}
			}
			small.end();
			GlScale.pop();
		}
	}

	private static final float SUB_SCALE = 0.7f;
	private final Key[] keys = {new Key(), new Key(), new Key(), new Key(), new Key(), new Key()};
	/** The two text batches, reused every frame. */
	private final HudText.Batch labels = new HudText.Batch();
	private final HudText.Batch small = new HudText.Batch();
	/** "9 CPS" for each count, made once. */
	private final String[] cpsTexts = new String[64];

	private String cps(int n) {
		if (n < 0 || n >= cpsTexts.length) {
			return n + " CPS";
		}
		String s = cpsTexts[n];
		if (s == null) {
			s = n + " CPS";
			cpsTexts[n] = s;
		}
		return s;
	}

	/** One key of the current frame, reused from frame to frame. */
	private static final class Key {
		KeyBinding binding;
		int x;
		int y;
		int w;
		int h;
		String sub;
		float level;

		void set(KeyBinding binding, int x, int y, int w, int h, String sub) {
			this.binding = binding;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.sub = sub;
		}
	}

	/** Eases the key's highlight towards pressed (1) or idle (0) over the fade time. */
	private float level(KeyBinding binding, boolean down, float dt) {
		float[] l = levels.computeIfAbsent(binding, b -> new float[1]);
		float target = down ? 1 : 0;
		int ms = fade.get();
		if (ms <= 0 || down && l[0] < 0.01f) {
			// presses show at once so the overlay never lags the click; releases fade out
			l[0] = target;
		} else {
			float step = dt * 1000f / ms;
			l[0] = target > l[0] ? Math.min(target, l[0] + step) : Math.max(target, l[0] - step);
		}
		return l[0];
	}

	private void box(WorldRenderer b, float x, float y, float w, float h, float lv) {
		int idle = background.get() ? idleColor.get() : idleColor.get() & 0x00FFFFFF;
		int fill = Paint.mix(idle, pressedColor.get(), lv);
		Paint.roundInto(b, x, y, w, h, radius.get(), fill);
		if (border.get()) {
			Paint.outlineInto(b, x, y, w, h, radius.get(), 1, borderColor.get());
		}
	}

	private String fit(String s, int maxW) {
		while (s.length() > 1 && textWidth(s) > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	/** Vanilla stores mouse buttons as negative codes (button - 100). */
	static String keyName(int code) {
		switch (code) {
			case -100:
				return "LMB";
			case -99:
				return "RMB";
			case -98:
				return "MMB";
			default:
				if (code < 0) {
					return "M" + (code + 101);
				}
				String name = Keyboard.getKeyName(code);
				return name == null ? "?" : name;
		}
	}

	/** Scales drawing around a point, for the small CPS line under the mouse buttons. */
	private static final class GlScale {
		static void push(float cx, float cy, float s) {
			net.minecraft.client.renderer.GlStateManager.pushMatrix();
			net.minecraft.client.renderer.GlStateManager.translate(cx, cy, 0);
			net.minecraft.client.renderer.GlStateManager.scale(s, s, 1);
			net.minecraft.client.renderer.GlStateManager.translate(-cx, -cy, 0);
		}

		static void pop() {
			net.minecraft.client.renderer.GlStateManager.popMatrix();
		}
	}
}
