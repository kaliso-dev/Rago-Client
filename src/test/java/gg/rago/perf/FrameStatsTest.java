package gg.rago.perf;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FrameStatsTest {
	private static final long MS = 1_000_000L;

	private static FrameStats of(long... framesNs) {
		return FrameStats.compute(framesNs, framesNs.length, new long[framesNs.length]);
	}

	@Test
	void emptyInputGivesEmptyStats() {
		assertSame(FrameStats.EMPTY, FrameStats.compute(new long[0], 0, new long[0]));
	}

	@Test
	void constantFrameTimes() {
		long[] frames = new long[100];
		Arrays.fill(frames, 10 * MS);
		FrameStats s = of(frames);
		assertEquals(100.0, s.avgFps, 1e-9);
		assertEquals(10.0, s.p50Ms, 1e-9);
		assertEquals(10.0, s.p99Ms, 1e-9);
		assertEquals(100.0, s.onePercentLowFps, 1e-9);
	}

	@Test
	void averageFpsIsFramesOverTimeNotMeanOfFps() {
		// 1 frame of 100 ms + 9 frames of 10 ms = 10 frames in 190 ms
		long[] frames = new long[10];
		Arrays.fill(frames, 10 * MS);
		frames[3] = 100 * MS;
		FrameStats s = of(frames);
		assertEquals(10 / 0.190, s.avgFps, 1e-9);
		assertEquals(100.0, s.maxMs, 1e-9);
	}

	@Test
	void onePercentLowUsesSlowestOnePercent() {
		// 200 frames: 198 at 5 ms, 2 at 50 ms -> slowest 1% = 2 frames, mean 50 ms -> 20 FPS
		long[] frames = new long[200];
		Arrays.fill(frames, 5 * MS);
		frames[17] = 50 * MS;
		frames[150] = 50 * MS;
		FrameStats s = of(frames);
		assertEquals(20.0, s.onePercentLowFps, 1e-9);
		assertEquals(5.0, s.p50Ms, 1e-9);
		// nearest rank: ceil(0.99 * 200) = 198th value, which is still a 5 ms frame
		assertEquals(5.0, s.p99Ms, 1e-9);
		assertEquals(50.0, s.maxMs, 1e-9);
	}

	@Test
	void onePercentLowUsesAtLeastOneFrame() {
		FrameStats s = of(4 * MS, 8 * MS, 2 * MS);
		assertEquals(125.0, s.onePercentLowFps, 1e-9);
	}

	@Test
	void nearestRankPercentile() {
		long[] sorted = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
		assertEquals(5, FrameStats.percentile(sorted, 10, 0.50));
		assertEquals(10, FrameStats.percentile(sorted, 10, 0.99));
		assertEquals(1, FrameStats.percentile(sorted, 10, 0.0));
	}

	@Test
	void inputArrayIsNotModified() {
		long[] frames = {30, 10, 20};
		FrameStats.compute(frames, 3, new long[3]);
		assertEquals(30, frames[0]);
		assertEquals(10, frames[1]);
	}
}
