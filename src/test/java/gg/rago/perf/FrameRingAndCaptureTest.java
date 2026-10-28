package gg.rago.perf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameRingAndCaptureTest {
	@Test
	void ringKeepsNewestValuesOldestFirst() {
		FrameRing ring = new FrameRing(3);
		for (long v = 1; v <= 5; v++) {
			ring.add(v);
		}
		long[] out = new long[3];
		assertEquals(3, ring.copyTo(out));
		assertArrayEquals(new long[]{3, 4, 5}, out);
	}

	@Test
	void ringPartiallyFilled() {
		FrameRing ring = new FrameRing(4);
		ring.add(7);
		ring.add(8);
		long[] out = new long[4];
		assertEquals(2, ring.copyTo(out));
		assertEquals(7, out[0]);
		assertEquals(8, out[1]);
	}

	@Test
	void captureStopsWhenFull() {
		FrameCapture c = new FrameCapture(2, Long.MAX_VALUE);
		assertTrue(c.record(0, 10, 5));
		assertTrue(c.record(1, 10, 5));
		assertFalse(c.record(2, 10, 5));
		assertEquals(2, c.count());
	}

	@Test
	void captureStopsAfterDuration() {
		FrameCapture c = new FrameCapture(100, 1_000);
		assertTrue(c.record(5_000, 10, 5));
		assertTrue(c.record(5_999, 10, 5));
		assertFalse(c.record(6_000, 10, 5));
	}

	@Test
	void timerReportsFinishedCapture() {
		FrameTimer timer = new FrameTimer();
		timer.startCapture(new FrameCapture(1, Long.MAX_VALUE));
		long t = 0;
		timer.startFrame(t);
		assertEquals(null, timer.endFrame(t + 4));   // first frame: no interval yet
		timer.startFrame(t + 10);
		assertEquals(null, timer.endFrame(t + 14));  // fills the single slot
		timer.startFrame(t + 20);
		FrameCapture done = timer.endFrame(t + 24);  // no room -> finished
		assertEquals(1, done.count());
		assertFalse(timer.isCapturing());
		assertEquals(10, done.intervals()[0]);
	}

	@Test
	void csvHasMetadataHeaderAndRows(@TempDir Path dir) throws Exception {
		FrameCapture c = new FrameCapture(10, Long.MAX_VALUE);
		c.record(0, 6_944_200, 5_120_300);
		c.record(1, 7_000_000, 5_000_000);
		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("gpu", "Test GPU\nline2");
		Path file = dir.resolve("sub").resolve("frames.csv");
		c.writeCsv(file, meta);

		List<String> lines = Files.readAllLines(file);
		assertEquals(List.of(
			"# gpu=Test GPU line2",
			"frame,interval_ns,work_ns",
			"0,6944200,5120300",
			"1,7000000,5000000"
		), lines);
		assertFalse(Files.exists(dir.resolve("sub").resolve("frames.csv.tmp")));
	}
}
