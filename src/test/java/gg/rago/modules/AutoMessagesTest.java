package gg.rago.modules;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMessagesTest {
	@Test
	void recognisesOwnKills() {
		assertTrue(AutoMessages.isOwnKill("Steve was killed by Rago_Player.", "Rago_Player"));
		assertTrue(AutoMessages.isOwnKill("Steve a été tué par Rago_Player.", "Rago_Player"));
		assertFalse(AutoMessages.isOwnKill("Rago_Player was killed by Steve.", "Rago_Player"));
		assertFalse(AutoMessages.isOwnKill("[VIP] Steve: Rago_Player is bad.", "Rago_Player"));
		assertFalse(AutoMessages.isOwnKill("Steve was killed by Rago_Player2.", "Rago_Player"));
	}

	@Test
	void picksAWrittenMessageAtRandomButNeverTheLastOneTwice() {
		Random random = new Random(1);
		Set<String> seen = new HashSet<>();
		String last = "";
		for (int i = 0; i < 300; i++) {
			String next = AutoMessages.pick(random, last, "gg", " gg wp ", "good game");
			assertNotEquals(last, next);
			seen.add(next);
			last = next;
		}
		assertEquals(new HashSet<>(Arrays.asList("gg", "gg wp", "good game")), seen);
	}

	@Test
	void emptyMessagesAreSkipped() {
		Random random = new Random(2);
		for (int i = 0; i < 50; i++) {
			assertEquals("ez", AutoMessages.pick(random, "", "", "ez", " "));
		}
		assertEquals("ez", AutoMessages.pick(random, "ez", "ez", "", ""));
		assertEquals("", AutoMessages.pick(random, "", "", null, ""));
		String two = AutoMessages.pick(random, "a", "a", "", "b");
		assertEquals("b", two);
	}
}
