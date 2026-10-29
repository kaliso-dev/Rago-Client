package gg.rago.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SniperCheckTest {
	@Test
	void namesWithAltOrCheatWords() {
		assertTrue(SniperCheck.suspiciousName("mcalt_x92"));
		assertTrue(SniperCheck.suspiciousName("VapeUser"));
		assertTrue(SniperCheck.suspiciousName("best_alt"));
		assertFalse(SniperCheck.suspiciousName("Walter"));
		assertFalse(SniperCheck.suspiciousName("Technoblade"));
	}
}
