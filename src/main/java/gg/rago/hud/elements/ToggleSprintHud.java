package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Toggle sprint and toggle sneak: one press of the vanilla key keeps it held until the next
 * press. It works by making the binding read as held, exactly as if the key were kept down,
 * so the game and the server see nothing a held key would not produce. Input class: off by default.
 */
public final class ToggleSprintHud extends TextHudElement {
	/** The text shown, made again only when it changes. */
	private final Shown shown = new Shown();
	@Override
	public Category category() {
		return Category.PVP;
	}

	private final BooleanOption sprint = addOption(new BooleanOption("toggle_sprint", true));
	private final BooleanOption sneak = addOption(new BooleanOption("toggle_sneak", false));
	private final BooleanOption showStatus = addOption(new BooleanOption("show_status", true));

	private boolean sprintToggled;
	private boolean sneakToggled;

	public ToggleSprintHud() {
		super("toggle_sprint", FeatureClass.INPUT, false, Anchor.BOTTOM_LEFT, 4, -4);
	}

	@Override
	public void tick() {
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		while (o.keyBindSprint.isPressed()) {
			sprintToggled = sprint.get() && !sprintToggled;
		}
		while (o.keyBindSneak.isPressed()) {
			sneakToggled = sneak.get() && !sneakToggled;
		}
		if (!sprint.get()) {
			sprintToggled = false;
		}
		if (!sneak.get()) {
			sneakToggled = false;
		}
		hold(o.keyBindSprint);
		hold(o.keyBindSneak);
	}

	/**
	 * Keeps a toggled binding held. When it is not forced, its state follows the physical key
	 * again, so releasing the toggle behaves like releasing the key.
	 */
	private void hold(KeyBinding binding) {
		int code = binding.getKeyCode();
		if (forceHeld(binding)) {
			KeyBinding.setKeyBindState(code, true);
		} else if (code > 0 && !Keyboard.isKeyDown(code)) {
			KeyBinding.setKeyBindState(code, false);
		}
	}

	@Override
	protected void onDisable() {
		sprintToggled = false;
		sneakToggled = false;
		GameSettings o = Minecraft.getMinecraft().gameSettings;
		if (o != null) {
			hold(o.keyBindSprint);
			hold(o.keyBindSneak);
		}
	}

	/** Whether this binding should read as held although the key is up. */
	public boolean forceHeld(KeyBinding binding) {
		if (!isEnabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.currentScreen != null || mc.gameSettings == null) {
			return false;
		}
		return (sprintToggled && binding == mc.gameSettings.keyBindSprint) || (sneakToggled && binding == mc.gameSettings.keyBindSneak);
	}

	@Override
	public boolean hasContent() {
		return showStatus.get() && (sprintToggled || sneakToggled);
	}

	@Override
	protected String value(boolean placeholder) {
		int state = !placeholder && sneakToggled ? sprintToggled ? 2 : 1 : 0;
		// the text is made again when the state or the language changes, not every frame
		if (!shown.same(state, Minecraft.getMinecraft().gameSettings.language.hashCode(), 0)) {
			shown.set(I18n.format(state == 2 ? "rago.status.sprinting_sneaking" : state == 1 ? "rago.status.sneaking" : "rago.status.sprinting"));
		}
		return shown.text();
	}
}
