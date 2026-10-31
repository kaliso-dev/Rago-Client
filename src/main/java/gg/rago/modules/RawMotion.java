package gg.rago.modules;

import net.java.games.input.Component;
import net.java.games.input.Mouse;

import java.util.Collections;
import java.util.List;

/**
 * The movement the mice reported that the camera has not used yet. Raw Input's thread reads the
 * mice every millisecond so JInput's queue never fills up, and each frame reads them once more
 * before taking the total: a frame turns by everything moved up to the moment it is drawn, not
 * only up to the thread's last pass (a millisecond or two earlier, which at a high frame rate
 * made the turn uneven from one frame to the next). Both reads take turns on this object, so no
 * movement is counted twice or lost.
 */
final class RawMotion {
	/**
	 * Mouse movements JInput keeps between two reads. Its own 32 is 4 ms of an 8000 Hz mouse: a
	 * longer pause (a garbage collection, a world loading) dropped what was moved meanwhile.
	 */
	static final int QUEUE = 1024;

	private List<Mouse> mice = Collections.emptyList();
	private int dx;
	private int dy;

	synchronized void use(List<Mouse> found) {
		for (Mouse m : found) {
			m.setEventQueueSize(QUEUE);
		}
		mice = found;
		read();
		dx = 0;
		dy = 0;
	}

	synchronized boolean reading() {
		return !mice.isEmpty();
	}

	/** Adds what the mice moved since the last read. */
	synchronized void read() {
		for (Mouse m : mice) {
			if (!m.poll()) {
				continue;
			}
			Component x = m.getX();
			Component y = m.getY();
			if (x != null) {
				dx += Math.round(x.getPollData());
			}
			if (y != null) {
				dy += Math.round(y.getPollData());
			}
		}
	}

	/** Reads the mice once more and hands over everything moved since the last call, as {x, y}. */
	synchronized void take(int[] out) {
		read();
		out[0] = dx;
		out[1] = dy;
		dx = 0;
		dy = 0;
	}

	/** Forgets the movement so far, as when the cursor is grabbed again. */
	synchronized void clear() {
		read();
		dx = 0;
		dy = 0;
	}
}
