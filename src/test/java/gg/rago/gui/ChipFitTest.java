package gg.rago.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChipFitTest {
	@Test
	void chipsKeepTheirPaddingWhenThereIsRoom() {
		assertArrayEquals(new float[] {24, 6}, RagoMenuScreen.chipFit(200, 8, 600));
	}

	@Test
	void chipsShrinkInsteadOfRunningUnderTheSearchField() {
		// the French labels in the 1.6.4 menu: 468 wide at full padding for 444 of room
		float[] fit = RagoMenuScreen.chipFit(234, 8, 444);
		float need = 234 + 8 * fit[0] + 7 * fit[1];
		assertTrue(need <= 444 + 0.01f, "fits: " + need);
		assertTrue(fit[0] >= 12, "still padded: " + fit[0]);
	}
}
