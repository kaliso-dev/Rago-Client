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
		// Hypixel in other languages
		assertArrayEquals(new String[] {"24", "iron"}, FastShop.price("Coût : 24 Fer"));
		assertArrayEquals(new String[] {"4", "emerald"}, FastShop.price("Coût: 4 Émeraudes"));
		assertArrayEquals(new String[] {"8", "gold"}, FastShop.price("Prix : 8 Or"));
		assertArrayEquals(new String[] {"2", "diamond"}, FastShop.price("Kosten: 2 Diamanten"));
		assertNull(FastShop.price("Durée : 30 secondes"));
	}

	@Test
	void ignoresOtherLines() {
		assertNull(FastShop.price("Great for blocking enemies!"));
		assertNull(FastShop.price(null));
	}
}
