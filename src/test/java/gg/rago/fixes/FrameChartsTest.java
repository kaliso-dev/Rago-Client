package gg.rago.fixes;

import net.minecraft.util.FrameTimer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The charts' numbers, as measured on PolyPatcher's (colours read from its screenshots). */
class FrameChartsTest {
	@Test
	void frameBarsGoFromGreenThroughYellowAtFiftyToRedAtAHundred() {
		assertEquals(0xFF00FF00, FrameCharts.frameColor(0));
		assertEquals(0xFF7FFF00, FrameCharts.frameColor(25));
		assertEquals(0xFFFFFF00, FrameCharts.frameColor(50));
		// bars 54, 59 and 62 high in PolyPatcher's frame chart
		assertEquals(0xFFFFEA00, FrameCharts.frameColor(54));
		assertEquals(0xFFFFD100, FrameCharts.frameColor(59));
		assertEquals(0xFFFFC100, FrameCharts.frameColor(62));
		assertEquals(0xFFFF0000, FrameCharts.frameColor(100));
		assertEquals(0xFFFF0000, FrameCharts.frameColor(400));
		assertEquals(0xFF00FF00, FrameCharts.frameColor(-3));
	}

	@Test
	void tickBarsAreYellowAtThirtyAndRedAtSixty() {
		assertEquals(0xFF2AFF00, FrameCharts.tickColor(5));
		assertEquals(0xFF33FF00, FrameCharts.tickColor(6));
		assertEquals(0xFFFFFF00, FrameCharts.tickColor(30));
		assertEquals(0xFFFF7F00, FrameCharts.tickColor(45));
		assertEquals(0xFFFF0000, FrameCharts.tickColor(60));
		assertEquals(0xFFFF0000, FrameCharts.tickColor(61));
	}

	@Test
	void barsAreThirtyPerSixtiethOfASecondForFramesAndSixtyPerTickForTicks() {
		assertEquals(30, FrameCharts.frameHeight(16_666_666L));
		assertEquals(61, FrameCharts.frameHeight(34_400_000L));
		assertEquals(0, FrameCharts.frameHeight(0L));
		assertEquals(60, FrameCharts.tickHeight(50_000_000L));
		assertEquals(37, FrameCharts.tickHeight(30_850_000L));
		assertEquals(0, FrameCharts.tickHeight(740_000L));
	}

	@Test
	void aNarrowChartShowsTheNewestPlacesAndANewOneFillsFromTheLeft() {
		FrameTimer timer = new FrameTimer();
		for (int i = 0; i < 40; i++) {
			timer.addFrame(i * 1_000_000L);
		}
		// 213 wide (a 427 wide screen): the 27 oldest of 240 places left out
		assertEquals(213, FrameCharts.places(240, 213));
		assertEquals(27, FrameCharts.first(timer, 240, 213), "the place numbered 27 is drawn first, at the left edge");
		assertEquals(240, FrameCharts.places(240, 300));
		assertEquals(0, FrameCharts.first(timer, 240, 300));
		// the empty places count in the lowest and the average
		assertArrayEquals(new int[] {0, (27 + 39) * 13 / 2 / 213, 39}, FrameCharts.stats(timer, 27, 213));
	}

	@Test
	void aFullChartKeepsTheNewestAndCountsEveryPlace() {
		FrameTimer timer = new FrameTimer();
		for (int i = 0; i < 300; i++) {
			timer.addFrame(i * 1_000_000L + 999_999L);
		}
		int first = FrameCharts.first(timer, 240, 240);
		assertEquals(61, first, "past the place to be written next, as the game's frame timer keeps it");
		assertArrayEquals(new int[] {60, (60 + 299) * 240 / 2 / 240, 299}, FrameCharts.stats(timer, first, 240));
	}
}
