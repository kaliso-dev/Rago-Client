package gg.rago.gui.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiWordsTest {
	/** French puts a space before ":" and inside « »: a line must not start with ":" or "»", nor end with "«". */
	@Test
	void frenchPunctuationStaysWithItsWord() {
		assertEquals(Arrays.asList("Rago", "ne", "bloque", "rien :", "tu", "choisis."), Ui.words("Rago ne bloque rien : tu choisis."));
		assertEquals(Arrays.asList("Active", "« Bed", "Wars »,", "puis", "joue !"), Ui.words("Active « Bed Wars », puis joue !"));
		assertEquals(Arrays.asList("Risky:", "this"), Ui.words("Risky: this"));
		assertEquals(Arrays.asList(":", "first"), Ui.words(": first"));
	}
}
