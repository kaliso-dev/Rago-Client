package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import gg.rago.input.InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * Movement keys, mouse buttons and jump as boxes that light up while held. Labels follow the
 * player's own bindings. Only reads the held state vanilla already tracks.
 */
public final class KeystrokesHud extends HudElement {
	@Override
	public Category category() {
		return Category.PVP;
	}

	private static final int KEY = 22;
	private static final int GAP = 2;
	private static final int WIDTH = KEY * 3 + GAP * 2;
	private static final int SPACE_H = 12;
	private static final int IDLE = 0x90000000;

	private final InputState input;
	private final BooleanOption showMouse = addOption(new BooleanOption("show_mouse", true));
	private final BooleanOption showCps = addOption(new BooleanOption("show_cps", true));
	private final BooleanOption showSpace = addOption(new BooleanOption("show_space", true));
	private final ColorOption pressedColor = addOption(new ColorOption("pressed_color", 0xFFFFFFFF));

	public KeystrokesHud(InputState input) {
		super("keystrokes", FeatureClass.INFORMATIONAL, true, Anchor.TOP_RIGHT, -4, 18, 0xFFFFFFFF, true);
		this.input = input;
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		return WIDTH;
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		int h = KEY * 2 + GAP;
		if (showMouse.get()) {
			h += GAP + KEY;
		}
		if (showSpace.get()) {
			h += GAP + SPACE_H;
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
		key(text, o.keyBindForward, KEY + GAP, 0, KEY, KEY, placeholder, null);
		int y = KEY + GAP;
		key(text, o.keyBindLeft, 0, y, KEY, KEY, placeholder, null);
		key(text, o.keyBindBack, KEY + GAP, y, KEY, KEY, placeholder, null);
		key(text, o.keyBindRight, (KEY + GAP) * 2, y, KEY, KEY, placeholder, null);
		y += KEY + GAP;
		if (showMouse.get()) {
			int half = (WIDTH - GAP) / 2;
			String left = showCps.get() ? (placeholder ? 9 : input.attackCps(now)) + " CPS" : null;
			String right = showCps.get() ? (placeholder ? 4 : input.useCps(now)) + " CPS" : null;
			key(text, o.keyBindAttack, 0, y, half, KEY, placeholder, left);
			key(text, o.keyBindUseItem, half + GAP, y, WIDTH - half - GAP, KEY, placeholder, right);
			y += KEY + GAP;
		}
		if (showSpace.get()) {
			boolean down = !placeholder && o.keyBindJump.isKeyDown();
			box(0, y, WIDTH, SPACE_H, down);
			int bar = down ? 0xFF000000 : textColor.get();
			Gui.drawRect(WIDTH / 2 - 12, y + SPACE_H / 2 - 1, WIDTH / 2 + 12, y + SPACE_H / 2, bar);
		}
	}

	private void key(FontRenderer text, KeyBinding binding, int x, int y, int w, int h, boolean placeholder, String sub) {
		boolean down = !placeholder && binding.isKeyDown();
		box(x, y, w, h, down);
		int color = down ? 0xFF000000 : textColor.get();
		String label = fit(text, keyName(binding.getKeyCode()), w - 4);
		if (sub == null) {
			drawLabel(text, label, x + w / 2, y + (h - text.FONT_HEIGHT) / 2 + 1, color, down);
		} else {
			drawLabel(text, label, x + w / 2, y + 3, color, down);
			drawLabel(text, sub, x + w / 2, y + 3 + text.FONT_HEIGHT + 1, color, down);
		}
	}

	private void drawLabel(FontRenderer text, String s, int centerX, int y, int color, boolean down) {
		int x = centerX - text.getStringWidth(s) / 2;
		if (down) {
			text.drawString(s, x, y, color);
		} else {
			drawText(text, s, x, y, color);
		}
	}

	private void box(int x, int y, int w, int h, boolean down) {
		if (down) {
			Gui.drawRect(x, y, x + w, y + h, pressedColor.get());
		} else if (background.get()) {
			Gui.drawRect(x, y, x + w, y + h, IDLE);
		}
	}

	private static String fit(FontRenderer text, String s, int maxW) {
		while (s.length() > 1 && text.getStringWidth(s) > maxW) {
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
}
