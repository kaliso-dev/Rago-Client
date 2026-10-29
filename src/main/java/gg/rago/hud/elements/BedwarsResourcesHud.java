package gg.rago.hud.elements;

import gg.rago.core.Category;
import gg.rago.hud.Anchor;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

/** How many iron, gold, diamonds and emeralds you carry, for Bed Wars shopping. */
public final class BedwarsResourcesHud extends ItemCountHud {
	private static final String[] NAMES = {"Iron", "Gold", "Diamonds", "Emeralds"};

	public BedwarsResourcesHud() {
		super("bedwars_resources", Anchor.MIDDLE_LEFT, 4, 0,
			new Item[] {Items.iron_ingot, Items.gold_ingot, Items.diamond, Items.emerald}, new int[] {48, 12, 3, 2});
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	protected String name(int i) {
		return NAMES[i];
	}
}
