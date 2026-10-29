package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.hud.Anchor;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The fight items you carry: golden apples, ender pearls, fireballs, TNT, arrows, bridge eggs,
 * snowballs and potions, each one turned on or off, with a warning colour when one runs low.
 */
public final class ItemCounterHud extends ItemCountHud {
	private static final String[] IDS = {"gapples", "pearls", "fireballs", "tnt", "arrows", "eggs", "snowballs", "potions"};
	private static final String[] NAMES = {"Gapples", "Pearls", "Fireballs", "TNT", "Arrows", "Eggs", "Snowballs", "Potions"};

	private final BooleanOption[] wanted = new BooleanOption[IDS.length];
	private final IntOption lowAt = addOption(new IntOption("low_items", 1, 0, 16));

	public ItemCounterHud() {
		super("item_counter", Anchor.MIDDLE_LEFT, 4, 40, new Item[] {Items.golden_apple, Items.ender_pearl, Items.fire_charge,
			Item.getItemFromBlock(Blocks.tnt), Items.arrow, Items.egg, Items.snowball, Items.potionitem}, new int[] {3, 2, 4, 1, 32, 1, 8, 2});
		section("items");
		for (int i = 0; i < IDS.length; i++) {
			wanted[i] = addOption(new BooleanOption("count_" + IDS[i], i != 6));
		}
		section(null);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	protected boolean wanted(int i) {
		return wanted[i].get();
	}

	@Override
	protected String name(int i) {
		return NAMES[i];
	}

	@Override
	protected boolean counts(int i, ItemStack s) {
		// splash and drinkable potions alike, but not water bottles
		return super.counts(i, s) && (s.getItem() != Items.potionitem || s.getMetadata() != 0);
	}

	@Override
	protected int countColor(int i, int count) {
		int alpha = textColor.get() & 0xFF000000;
		if (count == 0) {
			return alpha | 0xFF5555;
		}
		return count <= lowAt.get() ? alpha | 0xFFAA00 : textColor.get();
	}
}
