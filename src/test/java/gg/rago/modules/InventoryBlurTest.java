package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBlurTest {
	@Test
	void theBlurFadesInOverTheChosenTime() {
		assertEquals(0f, InventoryBlur.fadeIn(0, 150));
		assertEquals(1f, InventoryBlur.fadeIn(150_000_000L, 150));
		assertEquals(1f, InventoryBlur.fadeIn(2_000_000_000L, 150));
		float half = InventoryBlur.fadeIn(75_000_000L, 150);
		assertTrue(half > 0.5f && half < 1f, "eased: past half way at half the time, " + half);
		// no fade: at once
		assertEquals(1f, InventoryBlur.fadeIn(0, 0));
	}

	@Test
	void theShadeKeepsItsColourAndScalesItsOpacity() {
		// vanilla's two gradient colours
		assertEquals(0xC0101010, InventoryBlur.shaded(0xC0101010, 100));
		assertEquals(0x00101010, InventoryBlur.shaded(0xD0101010, 0));
		assertEquals(0x60101010, InventoryBlur.shaded(0xC0101010, 50));
		assertEquals(0x68101010, InventoryBlur.shaded(0xD0101010, 50));
	}
}
