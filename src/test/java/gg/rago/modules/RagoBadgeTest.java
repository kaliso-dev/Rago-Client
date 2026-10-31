package gg.rago.modules;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Finding the creator's name in a tab line or name tag, and its shades of pink. */
class RagoBadgeTest {
	@Test
	void findsTheNameAsPlainText() {
		assertEquals(2, RagoBadge.nameAt("§7qRago", "qRago"));
		assertEquals(13, RagoBadge.nameAt("§b[MVP§c+§b] qRago§r", "qRago"));
		assertEquals(-1, RagoBadge.nameAt("§7Steve", "qRago"));
		assertEquals(-1, RagoBadge.nameAt("§7qRago", null));
		// a name that starts with a letter used in § codes is not taken from inside a code
		assertEquals(-1, RagoBadge.nameAt("§ab", "ab"));
		assertEquals(3, RagoBadge.nameAt("§abab", "ab"));
	}

	@Test
	void codesInForceAtTheEnd() {
		assertEquals("§c§l", RagoBadge.codesAt("§c§lR §c§l"));
		assertEquals("§c", RagoBadge.codesAt("§a§lR §c"));
		assertEquals("", RagoBadge.codesAt("§a§r"));
		assertEquals("§l", RagoBadge.stylesAt("§c§lR "));
		assertEquals("", RagoBadge.stylesAt("§c[1✫] §7"));
		assertEquals("§b", RagoBadge.codesAt("§b[MVP§c+§b] "));
	}

	@Test
	void creatorShadesArePinkAndMove() {
		for (int i = 0; i < 5; i++) {
			int c = RagoBadge.creatorShade(i, 5, 1000);
			int r = c >> 16 & 0xFF;
			int g = c >> 8 & 0xFF;
			int b = c & 0xFF;
			assertEquals(0xFF, r, "pink keeps its red");
			assertTrue(g <= 0xC8 && g >= 0x3E && b >= 0x9E && b <= 0xE4, Integer.toHexString(c));
		}
		assertNotEquals(RagoBadge.creatorShade(0, 5, 0), RagoBadge.creatorShade(0, 5, 600));
		assertNotEquals(RagoBadge.creatorShade(0, 5, 0), RagoBadge.creatorShade(3, 5, 0));
	}

	@Test
	void theCreatorsTexturesAreThere() {
		for (String t : new String[] {"badge_creator.png", "badge_icons.png"}) {
			assertTrue(Files.exists(Paths.get("src/main/resources/assets/rago/textures", t)), t);
			assertTrue(Files.exists(Paths.get("src/main/resources/assets/rago/textures", t + ".mcmeta")), t);
		}
	}
}
