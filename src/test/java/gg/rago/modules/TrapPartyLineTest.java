package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The line the Trap Notifier sends to the party when a trap goes off. */
class TrapPartyLineTest {
	@Test
	void plainAsciiToTheParty() {
		assertEquals("/pc [Rago] Piege declenche : un ennemi est dans notre base !",
			TrapNotifier.partyLine("§c[Rago] Piège déclenché : un ennemi est dans notre base !"));
		String longLine = TrapNotifier.partyLine(new String(new char[200]).replace('\0', 'a'));
		assertTrue(longLine.startsWith("/pc "));
		assertEquals(BedwarsStats.CHAT_MAX, longLine.length());
	}
}
