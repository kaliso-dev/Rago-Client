package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuickplayTest {
	@Test
	void aNamedCommandKeepsItsNameAndCommand() {
		Quickplay.Mode m = Quickplay.parseCustom("custom_1", "  Warp: /p warp ");
		assertEquals("Warp", m.label);
		assertEquals("/p warp", m.command);
		assertEquals("custom_1", m.id);
		assertEquals(Quickplay.OTHER, m.group);
		assertEquals("Party", Quickplay.parseCustom("custom_2", "Party = /p list").label);
	}

	@Test
	void aCommandAloneIsItsOwnName() {
		Quickplay.Mode m = Quickplay.parseCustom("custom_1", "/play bedwars_eight_one");
		assertEquals("/play bedwars_eight_one", m.label);
		assertEquals("/play bedwars_eight_one", m.command);
	}

	@Test
	void aSlashInTheNameIsNotTheCommand() {
		Quickplay.Mode m = Quickplay.parseCustom("custom_1", "1/2 mode: /play bedwars_eight_two");
		assertEquals("1/2 mode", m.label);
		assertEquals("/play bedwars_eight_two", m.command);
	}

	@Test
	void emptyTextOrNoCommandIsNoEntry() {
		assertNull(Quickplay.parseCustom("custom_1", ""));
		assertNull(Quickplay.parseCustom("custom_1", null));
		assertNull(Quickplay.parseCustom("custom_1", "just a name"));
		assertNull(Quickplay.parseCustom("custom_1", "Name: /"));
		assertNull(Quickplay.parseCustom("custom_1", "Name: / play"));
	}
}
