package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Active potion effects with icon, level and time left, as in the inventory screen. */
public final class PotionHud extends HudElement {
	@Override
	public Category category() {
		return Category.PVP;
	}

	private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
	private static final int ICON = 18;
	private static final int ROW = 20;

	private final BooleanOption showIcons = addOption(new BooleanOption("show_icons", true));

	private List<PotionEffect> sample;

	public PotionHud() {
		super("potions", FeatureClass.INFORMATIONAL, true, Anchor.MIDDLE_LEFT, 4, 0);
	}

	private List<PotionEffect> effects(boolean placeholder) {
		if (placeholder) {
			if (sample == null) {
				sample = Arrays.asList(new PotionEffect(Potion.moveSpeed.getId(), 20 * 83, 1),
					new PotionEffect(Potion.damageBoost.getId(), 20 * 45, 0));
			}
			return sample;
		}
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null) {
			return Collections.emptyList();
		}
		Collection<PotionEffect> active = player.getActivePotionEffects();
		List<PotionEffect> list = new ArrayList<>(active.size());
		for (PotionEffect e : active) {
			if (e.getPotionID() >= 0 && e.getPotionID() < Potion.potionTypes.length && Potion.potionTypes[e.getPotionID()] != null) {
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
	public int width(FontRenderer text, boolean placeholder) {
		int w = 0;
		for (PotionEffect e : effects(placeholder)) {
			w = Math.max(w, Math.max(textWidth(name(e)), textWidth(Potion.getDurationString(e))));
		}
		return textX() + w;
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		return Math.max(1, effects(placeholder).size()) * ROW - 2;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		List<PotionEffect> effects = effects(placeholder);
		for (int i = 0; i < effects.size(); i++) {
			PotionEffect e = effects.get(i);
			Potion effect = Potion.potionTypes[e.getPotionID()];
			int y = i * ROW;
			if (showIcons.get() && effect.hasStatusIcon()) {
				GlStateManager.color(1, 1, 1, 1);
				GlStateManager.enableBlend();
				Minecraft.getMinecraft().getTextureManager().bindTexture(INVENTORY);
				int index = effect.getStatusIconIndex();
				Gui.drawModalRectWithCustomSizedTexture(0, y, index % 8 * ICON, 198 + index / 8 * ICON, ICON, ICON, 256, 256);
			}
			drawText(text, name(e), textX(), y, effect.isBadEffect() ? 0xFFFF5555 : textColor.get());
			drawText(text, Potion.getDurationString(e), textX(), y + text.FONT_HEIGHT + 1, 0xFFAAAAAA);
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	private static String name(PotionEffect e) {
		String name = I18n.format(Potion.potionTypes[e.getPotionID()].getName());
		int level = e.getAmplifier() + 1;
		if (level > 1) {
			name += " " + (level <= 10 ? I18n.format("enchantment.level." + level) : Integer.toString(level));
		}
		return name;
	}
}
