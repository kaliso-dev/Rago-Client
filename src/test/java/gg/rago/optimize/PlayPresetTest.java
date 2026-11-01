package gg.rago.optimize;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayPresetTest {
	/** A row a preset names but the page does not have would be skipped without a word. */
	@Test
	void everyPresetRowIsAKnownSetting() throws IOException {
		Properties lang = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/assets/rago/lang/en_US.lang")) {
			lang.load(in);
		}
		for (String id : PlayPreset.rowIds()) {
			assertTrue(lang.containsKey("rago.setting." + id), "no settings row named " + id);
		}
	}

	/** The savings measured for 1.9.0 are part of Ranked Bedwars. */
	@Test
	void rankedBedwarsTurnsOnTheMeasuredSavings() {
		java.util.Map<String, Integer> rows = PlayPreset.rows(PlayPreset.Kind.RANKED_BEDWARS);
		for (String id : new String[] {"light_glint", "hud_cache", "batch_holograms", "batch_vanilla_hud"}) {
			assertEquals(Integer.valueOf(1), rows.get(id), id);
		}
	}
}
