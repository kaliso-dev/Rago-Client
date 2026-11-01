package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.gui.ui.TextColors;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import gg.rago.hud.Shown;
import gg.rago.render.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

import java.util.ArrayList;
import java.util.Arrays;
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
			sample = Arrays.asList(new ItemStack(Items.diamond_helmet), new ItemStack(Items.diamond_chestplate),
				new ItemStack(Items.iron_leggings), new ItemStack(Items.diamond_boots), new ItemStack(Items.diamond_sword));
			sample.get(1).setItemDamage(300);
			sample.get(2).setItemDamage(200);
		}
		return sample;
	}

	private final List<ItemStack> worn = new ArrayList<>(5);

	private List<ItemStack> items(boolean placeholder) {
		if (placeholder) {
			return showHeld.get() ? sample() : sample().subList(0, 4);
		}
		// asked for several times a frame: the same list is filled again rather than a new one made
		List<ItemStack> list = worn;
		list.clear();
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null) {
			return list;
		}
		for (int slot = 3; slot >= 0; slot--) {
			ItemStack stack = player.inventory.armorInventory[slot];
			if (stack != null) {
				list.add(stack);
			}
		}
		if (showHeld.get()) {
			ItemStack held = player.inventory.getCurrentItem();
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
	public int width(FontRenderer text, boolean placeholder) {
		int w = ICON;
		if (showDurability.get()) {
			int label = 0;
			for (ItemStack stack : items(placeholder)) {
				label = Math.max(label, textWidth(label(stack)));
			}
			if (label > 0) {
				w += 3 + label;
			}
		}
		return w;
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		int n = Math.max(1, items(placeholder).size());
		return n * ROW - 1;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		Minecraft mc = Minecraft.getMinecraft();
		List<ItemStack> items = items(placeholder);
		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableRescaleNormal();
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			int y = i * ROW;
			mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, y);
			mc.getRenderItem().renderItemOverlayIntoGUI(text, stack, 0, y, "");
		}
		GlStateManager.disableRescaleNormal();
		RenderHelper.disableStandardItemLighting();
		if (showDurability.get()) {
			// every number in one draw call
			HudText.Batch batch = labels.start(font.get());
			boolean shade = shadow.get();
			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				String label = label(stack);
				if (!label.isEmpty()) {
					batch.add(label, ICON + 3, i * ROW + 4, durabilityColor(stack), shade);
				}
			}
			batch.end();
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	/** The durability numbers, reused every frame. */
	private final HudText.Batch labels = new HudText.Batch();

	/** Remaining uses for damageable items, the stack size for stacks, nothing otherwise. */
	private static String label(ItemStack stack) {
		if (stack.isItemStackDamageable()) {
			return Shown.of(stack.getMaxDamage() - stack.getItemDamage());
		}
		return stack.stackSize > 1 ? Shown.of(stack.stackSize) : "";
	}

	private int durabilityColor(ItemStack stack) {
		if (!stack.isItemStackDamageable()) {
			return textColor.get();
		}
		return durabilityColor(stack.getMaxDamage() - stack.getItemDamage(), stack.getMaxDamage(), textColor.get());
	}

	/** Green above half, yellow above a quarter, red below. */
	static int durabilityColor(int remaining, int max, int normal) {
		if (max <= 0) {
			return normal;
		}
		float f = (float) remaining / max;
		return f > 0.5f ? TextColors.argb('a') : f > 0.25f ? TextColors.argb('e') : TextColors.argb('c');
	}
}
