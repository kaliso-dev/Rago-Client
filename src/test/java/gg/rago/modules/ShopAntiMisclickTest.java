package gg.rago.modules;

import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shop clicks that are stopped, and the ones that go through. */
class ShopAntiMisclickTest {
	@Test
	void spamOnTheSameSwordBuysItOnce() {
		// a second click 150 ms after buying the same sword: stopped
		assertTrue(ShopAntiMisclick.blocked(true, true, 150, 1000));
		// a pause of a second without clicking it: bought again (a death, your sword lost)
		assertFalse(ShopAntiMisclick.blocked(true, true, 1200, 1000));
	}

	@Test
	void anotherItemInBetweenLetsYouBuyItAgain() {
		// sword, then wool, then the sword: the last purchase is the wool
		assertFalse(ShopAntiMisclick.blocked(true, false, 100, 1000));
	}

	@Test
	void onlyTheStoneIronAndDiamondSwordsForNow() {
		assertTrue(ShopAntiMisclick.guarded(new ItemSword(Item.ToolMaterial.STONE)));
		assertTrue(ShopAntiMisclick.guarded(new ItemSword(Item.ToolMaterial.IRON)));
		assertTrue(ShopAntiMisclick.guarded(new ItemSword(Item.ToolMaterial.EMERALD)));
		assertFalse(ShopAntiMisclick.guarded(new ItemSword(Item.ToolMaterial.WOOD)));
		assertFalse(ShopAntiMisclick.guarded(new ItemSword(Item.ToolMaterial.GOLD)));
		assertFalse(ShopAntiMisclick.guarded(null));
		// blocks and the rest are bought as fast as you click
		assertFalse(ShopAntiMisclick.blocked(false, true, 50, 1000));
	}
}
