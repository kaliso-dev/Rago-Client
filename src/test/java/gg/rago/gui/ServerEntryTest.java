package gg.rago.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The message of the day in the home screen's server entry, narrower than the multiplayer list. */
class ServerEntryTest {
	@Test
	void atTheListsWidthNothingChanges() {
		String motd = "          §aHypixel Network\n    §6BED WARS";
		assertEquals(motd, RagoTitleScreen.fitMotd(motd, 271));
		assertEquals(motd, RagoTitleScreen.fitMotd(motd, 300));
	}

	@Test
	void centringSpacesShrinkWithTheWidth() {
		// 8 spaces for 271 points are 4 for 135; the colour codes stay in front
		assertEquals("§a    Hi", RagoTitleScreen.fitMotd("§a        Hi", 135));
		assertEquals("    Hi\n  Yo", RagoTitleScreen.fitMotd("        Hi\n    Yo", 135));
	}

	@Test
	void linesWithoutSpacesInFrontAndEmptyLinesStay() {
		assertEquals("\nHi there", RagoTitleScreen.fitMotd("\nHi there", 135));
		assertEquals("Hi there\n", RagoTitleScreen.fitMotd("Hi there\n", 135));
	}
}
