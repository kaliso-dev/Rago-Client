package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.resource.language.I18n;

/**
 * Toggle sprint and toggle sneak: one press of the vanilla key keeps it held until the next
 * press. It works by making the binding read as held, exactly as if the key were kept down,
 * so the game and the server see nothing a held key would not produce. Input class: off by default.
 */
public final class ToggleSprintHud extends TextHudElement {
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
		GameOptions o = Minecraft.getInstance().options;
		while (o.sprintKey.consumeClick()) {
			sprintToggled = sprint.get() && !sprintToggled;
		}
		while (o.sneakKey.consumeClick()) {
			sneakToggled = sneak.get() && !sneakToggled;
		}
		if (!sprint.get()) {
			sprintToggled = false;
		}
		if (!sneak.get()) {
			sneakToggled = false;
		}
	}

	@Override
	protected void onDisable() {
		sprintToggled = false;
		sneakToggled = false;
	}

	/** Whether this binding should read as held although the key is up. */
	public boolean forceHeld(KeyBinding binding) {
		if (!isEnabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.options == null) {
			return false;
		}
		return (sprintToggled && binding == mc.options.sprintKey) || (sneakToggled && binding == mc.options.sneakKey);
	}

	@Override
	public boolean hasContent() {
		return showStatus.get() && (sprintToggled || sneakToggled);
	}

	@Override
	protected String text(boolean placeholder) {
		if (!placeholder && sneakToggled) {
			return I18n.translate(sprintToggled ? "rago.status.sprinting_sneaking" : "rago.status.sneaking");
		}
		return I18n.translate("rago.status.sprinting");
	}
}
