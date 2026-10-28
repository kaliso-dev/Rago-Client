package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Active potion effects with icon, level and time left, as in the inventory screen. */
public final class PotionHud extends HudElement {
	private static final Identifier INVENTORY = new Identifier("textures/gui/container/inventory.png");
	private static final int ICON = 18;
	private static final int ROW = 20;

	private final BooleanOption showIcons = addOption(new BooleanOption("show_icons", true));

	private List<StatusEffectInstance> sample;

	public PotionHud() {
		super("potions", FeatureClass.INFORMATIONAL, true, Anchor.MIDDLE_LEFT, 4, 0);
	}

	private List<StatusEffectInstance> effects(boolean placeholder) {
		if (placeholder) {
			if (sample == null) {
				sample = List.of(new StatusEffectInstance(StatusEffect.SPEED.getId(), 20 * 83, 1),
					new StatusEffectInstance(StatusEffect.STRENGTH.getId(), 20 * 45, 0));
			}
			return sample;
		}
		PlayerEntity player = Minecraft.getInstance().player;
		if (player == null) {
			return List.of();
		}
		Collection<StatusEffectInstance> active = player.getStatusEffects();
		List<StatusEffectInstance> list = new ArrayList<>(active.size());
		for (StatusEffectInstance e : active) {
			if (e.getId() >= 0 && e.getId() < StatusEffect.BY_ID.length && StatusEffect.BY_ID[e.getId()] != null) {
				list.add(e);
			}
		}
		return list;
	}

	@Override
	public boolean hasContent() {
		return !effects(false).isEmpty();
	}

	private int textX() {
		return showIcons.get() ? ICON + 3 : 0;
	}

	@Override
	public int width(TextRenderer text, boolean placeholder) {
		int w = 0;
		for (StatusEffectInstance e : effects(placeholder)) {
			w = Math.max(w, Math.max(text.getWidth(name(e)), text.getWidth(StatusEffect.getDurationString(e))));
		}
		return textX() + w;
	}

	@Override
	public int height(TextRenderer text, boolean placeholder) {
		return Math.max(1, effects(placeholder).size()) * ROW - 2;
	}

	@Override
	public void render(TextRenderer text, boolean placeholder) {
		List<StatusEffectInstance> effects = effects(placeholder);
		for (int i = 0; i < effects.size(); i++) {
			StatusEffectInstance e = effects.get(i);
			StatusEffect effect = StatusEffect.BY_ID[e.getId()];
			int y = i * ROW;
			if (showIcons.get() && effect.hasIcon()) {
				GlStateManager.color4f(1, 1, 1, 1);
				GlStateManager.enableBlend();
				Minecraft.getInstance().getTextureManager().bind(INVENTORY);
				int index = effect.getIconIndex();
				GuiElement.drawTexture(0, y, index % 8 * ICON, 198 + index / 8 * ICON, ICON, ICON, 256, 256);
			}
			drawText(text, name(e), textX(), y, effect.isHarmful() ? 0xFFFF5555 : textColor.get());
			drawText(text, StatusEffect.getDurationString(e), textX(), y + text.fontHeight + 1, 0xFFAAAAAA);
		}
		GlStateManager.color4f(1, 1, 1, 1);
	}

	private static String name(StatusEffectInstance e) {
		String name = I18n.translate(StatusEffect.BY_ID[e.getId()].getTranslationKey());
		int level = e.getAmplifier() + 1;
		if (level > 1) {
			name += " " + (level <= 10 ? I18n.translate("enchantment.level." + level) : Integer.toString(level));
		}
		return name;
	}
}
