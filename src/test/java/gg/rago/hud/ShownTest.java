package gg.rago.hud;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShownTest {
	@Test
	void fixedReadsLikeStringFormat() {
		Random r = new Random(7);
		for (int i = 0; i < 20_000; i++) {
			// values a HUD shows: coordinates, speed, reach, frame times, FPS
			double v = (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(7));
			for (int d = 0; d <= 3; d++) {
				String expected = String.format(Locale.ROOT, "%." + d + "f", v);
				if (expected.matches("-0\\.?0*")) {
					expected = expected.substring(1); // rounds to zero: no minus sign
				}
				assertEquals(expected, Shown.fixed(v, d), v + " with " + d);
			}
		}
	}

	@Test
	void fixedExamples() {
		assertEquals("2.94", Shown.fixed(2.94, 2));
		assertEquals("13", Shown.fixed(12.5, 0));
		assertEquals("-30.3", Shown.fixed(-30.25, 1));
		assertEquals("0.0", Shown.fixed(-0.04, 1));
		assertEquals("860", Shown.fixed(860, 0));
		assertEquals("1.160", Shown.fixed(1.16, 3));
		assertEquals("NaN", Shown.fixed(Double.NaN, 1));
	}

	@Test
	void smallNumbersAreMadeOnce() {
		assertSame(Shown.of(42), Shown.of(42));
		assertEquals("1561", Shown.of(1561));
		assertEquals("-3", Shown.of(-3));
		assertEquals("123456", Shown.of(123456));
	}

	@Test
	void theTextIsMadeAgainOnlyWhenTheKeysChange() {
		Shown s = new Shown();
		assertFalse(s.same(42, 35, 0), "nothing made yet");
		s.set("42 ±3.5");
		assertTrue(s.same(42, 35, 0));
		assertEquals("42 ±3.5", s.text());
		assertFalse(s.same(43, 35, 0));
		s.set("43 ±3.5");
		assertFalse(s.same(43, 36, 0));
		s.set("43 ±3.6");
		assertFalse(s.same(43, 36, 1));
	}

	@Test
	void keysFollowWhatIsShown() {
		assertEquals(Shown.key(2.941, 2), Shown.key(2.944, 2));
		assertTrue(Shown.key(2.94, 2) != Shown.key(2.95, 2));
		assertEquals(Shown.key(59.6, 0), Shown.key(60.4, 0));
	}
}
