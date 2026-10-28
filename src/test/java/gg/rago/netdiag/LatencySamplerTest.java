package gg.rago.netdiag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencySamplerTest {
	@Test
	void jitterIsPopulationStandardDeviation() {
		LatencySampler s = new LatencySampler();
		s.add(40);
		s.add(60);
		assertEquals(60, s.latest());
		assertEquals(10.0, s.jitterMs(), 1e-9);
	}

	@Test
	void negativeSampleResetsWindow() {
		LatencySampler s = new LatencySampler();
		s.add(50);
		s.add(70);
		s.add(-1);
		assertEquals(-1, s.latest());
		assertEquals(0, s.size());
		assertEquals(0.0, s.jitterMs(), 1e-9);
	}

	@Test
	void windowIsBounded() {
		LatencySampler s = new LatencySampler();
		for (int i = 0; i < LatencySampler.WINDOW * 3; i++) {
			s.add(30);
		}
		assertEquals(LatencySampler.WINDOW, s.size());
		assertEquals(0.0, s.jitterMs(), 1e-9);
	}
}
