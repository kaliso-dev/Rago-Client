package gg.rago.optimize;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FontBatchTest {
	@AfterEach
	void off() {
		FontBatch.setSwitch(() -> false, () -> false);
		FontBatch.clientThreadForTests(null);
	}

	@Test
	void theGlyphPageIsTheFontsOwnString() throws IOException {
		List<String> pages = new ArrayList<>();
		try (InputStream in = ClassLoader.getSystemResourceAsStream("net/minecraft/client/gui/FontRenderer.class")) {
			assertNotNull(in, "the game's font class on the test class path");
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, 0);
			for (MethodNode m : node.methods) {
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String
						&& ((String) ((LdcInsnNode) insn).cst).startsWith("ÀÁÂ")) {
						pages.add((String) ((LdcInsnNode) insn).cst);
					}
				}
			}
		}
		assertFalse(pages.isEmpty());
		for (String page : pages) {
			assertEquals(FontBatch.PAGE, page);
			// literals are interned: the font's string is the very same object, which the lookup checks
			assertSame(FontBatch.PAGE, page.intern());
		}
	}

	@Test
	void theTableFindsEachCharacterWhereTheSearchDoes() {
		FontBatch.setSwitch(() -> true, () -> false);
		for (int ch = 0; ch < 65536; ch++) {
			assertEquals(FontBatch.PAGE.indexOf(ch), FontBatch.indexOf(FontBatch.PAGE, ch), "character " + ch);
		}
		assertEquals("0123456789abcdefklmnor".indexOf('k'), FontBatch.indexOf("0123456789abcdefklmnor", 'k'));
		assertEquals(-1, FontBatch.indexOf("0123456789abcdefklmnor", 'z'));
	}

	@Test
	void colourChannelsRoundAsTheGraphicsCardDoes() {
		for (int c = 0; c < 256; c++) {
			assertEquals(c, FontBatch.channel(c / 255.0f));
		}
		assertEquals(0, FontBatch.channel(-0.5f));
		assertEquals(255, FontBatch.channel(1.5f));
		assertEquals(128, FontBatch.channel(0.5f));
	}

	@Test
	void theLowerCaseCopyIsMadeOncePerString() {
		FontBatch.setSwitch(() -> true, () -> false);
		FontBatch.clientThreadForTests(Thread.currentThread());
		String text = "§AHello §LWorld";
		String lower = FontBatch.lower(text, Locale.ENGLISH);
		assertEquals(text.toLowerCase(Locale.ENGLISH), lower);
		assertSame(lower, FontBatch.lower(text, Locale.ENGLISH));
		String other = new String(text);
		assertEquals(lower, FontBatch.lower(other, Locale.ENGLISH));
		assertEquals("i", FontBatch.lower("I", Locale.ENGLISH));
		assertEquals("ı", FontBatch.lower("I", new Locale("tr")));
	}

	@Test
	void layoutAsPolyPatcherOnlyWhenOn() {
		FontBatch.setSwitch(() -> true, () -> false);
		assertEquals(5.0f, FontBatch.advance(5.5f));
		assertEquals(4.0f, FontBatch.advance(4.99f));
		assertEquals(1.0f, FontBatch.boldAdvance(0.5f));
		assertEquals(32, FontBatch.spaceWidthIndex(160));
		assertEquals(65, FontBatch.spaceWidthIndex(65));
		assertEquals(73, FontBatch.measured(73.5f));
		assertEquals(121, FontBatch.measured(121.75f));
		FontBatch.setSwitch(() -> false, () -> false);
		assertEquals(74, FontBatch.measured(73.5f));
		assertEquals(5.5f, FontBatch.advance(5.5f));
		assertEquals(0.5f, FontBatch.boldAdvance(0.5f));
		assertEquals(160, FontBatch.spaceWidthIndex(160));
	}

	@Test
	void nothingIsKeptWithTheCacheOff() {
		FontBatch.clientThreadForTests(Thread.currentThread());
		assertEquals(-1, FontBatch.keptWidth(null, "text"));
		assertEquals(42, FontBatch.keepWidth(42, null, "text"));
		assertEquals(0, FontBatch.keptWidths());
	}
}
