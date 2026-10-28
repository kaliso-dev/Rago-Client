package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Worn armour (helmet first) and the held item, each with its remaining durability. */
public final class ArmorHud extends HudElement {
	@Override
	public Category category() {
		return Category.PVP;
	}

	private static final int ROW = 17;
	private static final int ICON = 16;

	private final BooleanOption showHeld = addOption(new BooleanOption("show_held_item", true));
	private final BooleanOption showDurability = addOption(new BooleanOption("show_durability", true));

	private List<ItemStack> sample;

	public ArmorHud() {
		super("armor", FeatureClass.INFORMATIONAL, true, Anchor.BOTTOM_RIGHT, -4, -4);
	}

	private List<ItemStack> sample() {
		if (sample == null) {
			sample = List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_SWORD));
			sample.get(1).setDamage(300);
			sample.get(2).setDamage(200);
		}
		return sample;
	}

	private List<ItemStack> items(boolean placeholder) {
		if (placeholder) {
			return showHeld.get() ? sample() : sample().subList(0, 4);
		}
		List<ItemStack> list = new ArrayList<>(5);
		PlayerEntity player = Minecraft.getInstance().player;
		if (player == null) {
			return list;
		}
		for (int slot = 3; slot >= 0; slot--) {
			ItemStack stack = player.inventory.armor[slot];
			if (stack != null) {
				list.add(stack);
			}
		}
		if (showHeld.get()) {
			ItemStack held = player.inventory.getSelectedItem();
			if (held != null) {
				list.add(held);
			}
		}
		return list;
	}

	@Override
	public boolean hasContent() {
		return !items(false).isEmpty();
	}

	@Override
	public int width(TextRenderer text, boolean placeholder) {
		int w = ICON;
		if (showDurability.get()) {
			int label = 0;
			for (ItemStack stack : items(placeholder)) {
				label = Math.max(label, text.getWidth(label(stack)));
			}
			if (label > 0) {
				w += 3 + label;
			}
		}
		return w;
	}

	@Override
	public int height(TextRenderer text, boolean placeholder) {
		int n = Math.max(1, items(placeholder).size());
		return n * ROW - 1;
	}

	@Override
	public void render(TextRenderer text, boolean placeholder) {
		Minecraft mc = Minecraft.getInstance();
		List<ItemStack> items = items(placeholder);
		Lighting.turnOnGui();
		GlStateManager.enableRescaleNormal();
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			int y = i * ROW;
			mc.getItemRenderer().renderGuiItem(stack, 0, y);
			mc.getItemRenderer().renderGuiItemDecorations(text, stack, 0, y, "");
		}
		GlStateManager.disableRescaleNormal();
		Lighting.turnOff();
		if (showDurability.get()) {
			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				String label = label(stack);
				if (!label.isEmpty()) {
					drawText(text, label, ICON + 3, i * ROW + 4, durabilityColor(stack));
				}
			}
		}
		GlStateManager.color4f(1, 1, 1, 1);
	}

	/** Remaining uses for damageable items, the stack size for stacks, nothing otherwise. */
	private static String label(ItemStack stack) {
		if (stack.isDamageable()) {
			return Integer.toString(stack.getMaxDamage() - stack.getDamage());
		}
		return stack.size > 1 ? Integer.toString(stack.size) : "";
	}

	private int durabilityColor(ItemStack stack) {
		if (!stack.isDamageable()) {
			return textColor.get();
		}
		return durabilityColor(stack.getMaxDamage() - stack.getDamage(), stack.getMaxDamage(), textColor.get());
	}

	/** Green above half, yellow above a quarter, red below. */
	static int durabilityColor(int remaining, int max, int normal) {
		if (max <= 0) {
			return normal;
		}
		float f = (float) remaining / max;
		return f > 0.5f ? 0xFF55FF55 : f > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
	}
}
