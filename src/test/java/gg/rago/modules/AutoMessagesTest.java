package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
