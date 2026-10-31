package gg.rago.modules;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabListTest {
	private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
	private static final UUID NPC = UUID.fromString("3c8a1f2e-5b6d-2a7e-9f10-1b2c3d4e5f60");

	@Test
	void hypixelNpcsAreBotsWhateverTheirName() {
		assertTrue(TabList.bot(NPC, "Shopkeeper", false));
		assertTrue(TabList.bot(null, "anything", false));
	}

	@Test
	void aTeamlessMadeUpNameInAGameIsABot() {
		assertTrue(TabList.bot(PLAYER, "38qx60s71a", true));
		assertTrue(TabList.bot(PLAYER, "o971kqw44d", true));
	}

	@Test
	void playersStay() {
		// the same name in a team, or out of a game, is a player
		assertFalse(TabList.bot(PLAYER, "38qx60s71a", false));
		// not ten lower-case letters and digits
		assertFalse(TabList.bot(PLAYER, "St_n1ck", true));
		assertFalse(TabList.bot(PLAYER, "SwagAdde12", true));
		assertFalse(TabList.bot(PLAYER, "abcdefghijk", true));
	}
}
