package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NickHiderTest {
	@Test
	void theNameIsReplacedAsAWholeWordInAnyCase() {
		assertEquals("[MVP+] Streamer: gg", NickHider.replace("[MVP+] Sofiene: gg", "Sofiene", "Streamer", false));
		assertEquals("Streamer was killed by Streamer", NickHider.replace("sofiene was killed by SOFIENE", "Sofiene", "Streamer", false));
		assertEquals("Streamer's bed", NickHider.replace("Sofiene's bed", "Sofiene", "Streamer", false));
	}

	@Test
	void longerNamesAndOtherWordsAreLeftAlone() {
		String s = "Sofiene2 and xSofiene and Sofiene_ played";
		assertSame(s, NickHider.replace(s, "Sofiene", "Streamer", false));
		assertSame("no name here", NickHider.replace("no name here", "Sofiene", "Streamer", false));
	}

	@Test
	void colourCodesAroundTheNameAreKept() {
		assertEquals("§a[VIP] §aStreamer§f: hi", NickHider.replace("§a[VIP] §aSofiene§f: hi", "Sofiene", "Streamer", true));
		// a colour code right before the name: its letter is not part of the name
		assertEquals("§cStreamer", NickHider.replace("§cSofiene", "Sofiene", "Streamer", true));
	}

	@Test
	void aColouredNickGivesBackTheColoursAfterIt() {
		String nick = NickHider.colours("&b&lStream");
		assertEquals("§b§lStream", nick);
		assertEquals("§e§b§lStream§r§e killed you", NickHider.replace("§eSofiene killed you", "Sofiene", nick, true));
		assertEquals("Stream killed you", NickHider.replace("Sofiene killed you", "Sofiene", NickHider.strip(nick), false));
	}

	@Test
	void ampersandsThatAreNotCodesStay() {
		assertEquals("Tom & Jerry &z §cred", NickHider.colours("Tom & Jerry &z &cred"));
	}
}
