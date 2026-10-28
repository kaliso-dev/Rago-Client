package gg.rago.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClickTrackerTest {
	private static final long MS = 1_000_000L;

	@Test
	void countsPressesInTheLastSecond() {
		ClickTracker t = new ClickTracker();
		for (int i = 0; i < 10; i++) {
			t.press(i * 100 * MS);
		}
		assertEquals(10, t.cps(950 * MS));
		assertEquals(5, t.cps(1450 * MS));   // presses at 500..900 ms remain
		assertEquals(0, t.cps(2000 * MS));
	}

	@Test
	void pressExactlyOneSecondOldIsDropped() {
		ClickTracker t = new ClickTracker();
		t.press(0);
		assertEquals(1, t.cps(ClickTracker.WINDOW_NS - 1));
		assertEquals(0, t.cps(ClickTracker.WINDOW_NS));
	}

	@Test
	void capacityIsBoundedWithoutBreakingCount() {
		ClickTracker t = new ClickTracker();
		for (int i = 0; i < 200; i++) {
			t.press(i * MS);
		}
		assertEquals(64, t.cps(200 * MS));
	}

	@Test
	void clearForgetsEverything() {
		ClickTracker t = new ClickTracker();
		t.press(0);
		t.clear();
		assertEquals(0, t.cps(1));
	}
}
