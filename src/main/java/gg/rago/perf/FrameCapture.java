package gg.rago.perf;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * One fixed-length recording of frame timings. Arrays are allocated once when the
 * capture starts; recording a frame is two array stores.
 *
 * <p>CSV format (read by {@code bench/analyse.py}):
 * <pre>
 * # key=value          metadata lines (java, os, gpu, render distance, mods...)
 * frame,interval_ns,work_ns
 * 0,6944200,5120300
 * </pre>
 * {@code interval_ns} is the time from the start of one frame to the start of the next
 * (what the player sees, including any frame-cap sleep or vsync wait).
 * {@code work_ns} is the time spent inside {@code Minecraft.runGame} for that frame.
 */
public final class FrameCapture {
	private final long[] intervals;
	private final long[] work;
	private final long durationNs;
	private int count;
	private long startedAtNs = -1;

	public FrameCapture(int maxFrames, long durationNs) {
		this.intervals = new long[maxFrames];
		this.work = new long[maxFrames];
		this.durationNs = durationNs;
	}

	/** Records one frame. Returns false once the capture is full or its duration has elapsed. */
	public boolean record(long nowNs, long intervalNs, long workNs) {
		if (startedAtNs < 0) {
			startedAtNs = nowNs;
		}
		if (count >= intervals.length || nowNs - startedAtNs >= durationNs) {
			return false;
		}
		intervals[count] = intervalNs;
		work[count] = workNs;
		count++;
		return true;
	}

	public int count() {
		return count;
	}

	public long[] intervals() {
		return intervals;
	}

	public long elapsedNs(long nowNs) {
		return startedAtNs < 0 ? 0 : nowNs - startedAtNs;
	}

	/** Writes to a temp file first and moves it into place, so a crash never leaves a half CSV under the final name. */
	public void writeCsv(Path file, Map<String, String> metadata) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (BufferedWriter out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			for (Map.Entry<String, String> e : metadata.entrySet()) {
				out.write("# " + e.getKey() + "=" + sanitize(e.getValue()));
				out.newLine();
			}
			out.write("frame,interval_ns,work_ns");
			out.newLine();
			for (int i = 0; i < count; i++) {
				out.write(i + "," + intervals[i] + "," + work[i]);
				out.newLine();
			}
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
	}

	private static String sanitize(String value) {
		return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
	}
}
