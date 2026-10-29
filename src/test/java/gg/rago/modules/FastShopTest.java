package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FastShopTest {
	@Test
	void readsHypixelPrices() {
		assertArrayEquals(new String[] {"24", "iron"}, FastShop.price("Cost: 24 Iron"));
		assertArrayEquals(new String[] {"4", "emerald"}, FastShop.price("Cost: 4 Emeralds"));
		assertArrayEquals(new String[] {"2", "diamond"}, FastShop.price("Cost: 2 Diamonds"));
		assertArrayEquals(new String[] {"12", "gold"}, FastShop.price("Cost: 12 Gold"));
	}

	@Test
	void ignoresOtherLines() {
		assertNull(FastShop.price("Great for blocking enemies!"));
		assertNull(FastShop.price(null));
	}
}
