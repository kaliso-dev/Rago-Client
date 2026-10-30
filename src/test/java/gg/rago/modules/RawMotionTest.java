package gg.rago.modules;

import net.java.games.input.AbstractComponent;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.Mouse;
import net.java.games.input.Rumbler;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RawMotionTest {
	/** A relative axis, its value coming from the events like a real mouse's. */
	private static final class Axis extends AbstractComponent {
		Axis(Component.Identifier.Axis id) {
			super(id.getName(), id);
		}

		@Override
		public boolean isRelative() {
			return true;
		}

		@Override
		protected float poll() {
			return 0;
		}
	}

	/** A mouse the test moves, read through JInput's own polling. */
	private static final class FakeMouse extends Mouse {
		private final ConcurrentLinkedQueue<Object[]> events = new ConcurrentLinkedQueue<>();

		FakeMouse() {
			super("fake", new Component[] {new Axis(Component.Identifier.Axis.X), new Axis(Component.Identifier.Axis.Y)},
				new Controller[0], new Rumbler[0]);
		}

		void move(int x, int y) {
			events.add(new Object[] {getX(), (float) x});
			events.add(new Object[] {getY(), (float) y});
		}

		@Override
		protected boolean getNextDeviceEvent(Event event) {
			Object[] e = events.poll();
			if (e == null) {
				return false;
			}
			event.set((Component) e[0], (Float) e[1], 0);
			return true;
		}
	}

	@Test
	void aFrameTurnsByEverythingMovedBeforeIt() {
		FakeMouse mouse = new FakeMouse();
		RawMotion motion = new RawMotion();
		motion.use(Collections.singletonList(mouse));
		motion.read();
		mouse.move(3, -2);
		mouse.move(4, 1); // after the thread's last read, before the frame
		int[] out = new int[2];
		motion.take(out);
		assertArrayEquals(new int[] {7, -1}, out);
		motion.take(out);
		assertArrayEquals(new int[] {0, 0}, out);
	}

	@Test
	void grabbingTheCursorAgainForgetsTheMovementSoFar() {
		FakeMouse mouse = new FakeMouse();
		RawMotion motion = new RawMotion();
		motion.use(Collections.singletonList(mouse));
		mouse.move(50, 50);
		motion.clear();
		mouse.move(1, 2);
		int[] out = new int[2];
		motion.take(out);
		assertArrayEquals(new int[] {1, 2}, out);
	}

	@Test
	void theThreadAndTheFramesTogetherNeitherLoseNorCountTwice() throws InterruptedException {
		FakeMouse mouse = new FakeMouse();
		RawMotion motion = new RawMotion();
		motion.use(Collections.singletonList(mouse));
		int moves = 50000;
		Thread hand = new Thread(() -> {
			for (int i = 0; i < moves; i++) {
				mouse.move(1, 2);
			}
		});
		AtomicBoolean stop = new AtomicBoolean();
		Thread poller = new Thread(() -> {
			while (!stop.get()) {
				motion.read();
			}
		});
		hand.start();
		poller.start();
		long x = 0;
		long y = 0;
		int[] out = new int[2];
		while (hand.isAlive()) {
			motion.take(out);
			x += out[0];
			y += out[1];
		}
		stop.set(true);
		poller.join();
		motion.take(out);
		x += out[0];
		y += out[1];
		assertEquals(moves, x);
		assertEquals(2L * moves, y);
	}
}
