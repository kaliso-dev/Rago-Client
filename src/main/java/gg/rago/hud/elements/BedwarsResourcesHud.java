package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** How many iron, gold, diamonds and emeralds you carry, for Bed Wars shopping. */
public final class BedwarsResourcesHud extends HudElement {
	private static final Item[] ITEMS = {Items.iron_ingot, Items.gold_ingot, Items.diamond, Items.emerald};
	private static final int[] SAMPLE = {48, 12, 3, 2};
	private static final int ROW = 17;
	private static final int ICON = 16;

	private final BooleanOption hideZero = addOption(new BooleanOption("hide_zero", true));
	private final BooleanOption horizontal = addOption(new BooleanOption("horizontal", false));
	private final ItemStack[] stacks = new ItemStack[ITEMS.length];

	public BedwarsResourcesHud() {
		super("bedwars_resources", FeatureClass.INFORMATIONAL, false, Anchor.MIDDLE_LEFT, 4, 0);
		for (int i = 0; i < ITEMS.length; i++) {
			stacks[i] = new ItemStack(ITEMS[i]);
		}
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	private int[] counts(boolean placeholder) {
		if (placeholder) {
			return SAMPLE;
		}
		int[] n = new int[ITEMS.length];
		EntityPlayer p = Minecraft.getMinecraft().thePlayer;
		if (p == null) {
			return n;
		}
		for (ItemStack s : p.inventory.mainInventory) {
			if (s == null) {
				continue;
			}
			for (int i = 0; i < ITEMS.length; i++) {
				if (s.getItem() == ITEMS[i]) {
					n[i] += s.stackSize;
				}
			}
		}
		return n;
	}

	/** Indexes of the resources to show. */
	private List<Integer> shown(int[] counts) {
		List<Integer> list = new ArrayList<>(ITEMS.length);
		for (int i = 0; i < ITEMS.length; i++) {
			if (counts[i] > 0 || !hideZero.get()) {
				list.add(i);
			}
		}
		return list;
	}

	@Override
	public boolean hasContent() {
		return !shown(counts(false)).isEmpty();
	}

	private int cell(FontRenderer text, int count) {
		return ICON + 2 + text.getStringWidth(Integer.toString(count));
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		int[] counts = counts(placeholder);
		int w = 0;
		for (int i : shown(counts)) {
			w = horizontal.get() ? w + cell(text, counts[i]) + 6 : Math.max(w, cell(text, counts[i]));
		}
		return Math.max(ICON, horizontal.get() ? w - 6 : w);
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		int n = Math.max(1, shown(counts(placeholder)).size());
		return horizontal.get() ? ICON : n * ROW - 1;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		Minecraft mc = Minecraft.getMinecraft();
		int[] counts = counts(placeholder);
		List<Integer> shown = shown(counts);
		int x = 0;
		int y = 0;
		int[][] at = new int[shown.size()][];
		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableRescaleNormal();
		for (int k = 0; k < shown.size(); k++) {
			int i = shown.get(k);
			mc.getRenderItem().renderItemAndEffectIntoGUI(stacks[i], x, y);
			at[k] = new int[] {x, y, counts[i]};
			if (horizontal.get()) {
				x += cell(text, counts[i]) + 6;
			} else {
				y += ROW;
			}
		}
		GlStateManager.disableRescaleNormal();
		RenderHelper.disableStandardItemLighting();
		for (int[] a : at) {
			drawText(text, Integer.toString(a[2]), a[0] + ICON + 2, a[1] + 4, textColor.get());
		}
		GlStateManager.color(1, 1, 1, 1);
	}
}
