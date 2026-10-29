package gg.rago.optimize;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformancePresetTest {
	/** Every id the preset sets must be a real settings row, which all have a translated label. */
	@Test
	void everyPresetIdIsAKnownSetting() throws IOException {
		Properties lang = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/assets/rago/lang/en_US.lang")) {
			lang.load(in);
		}
		for (String id : PerformancePreset.ids()) {
			assertTrue(lang.containsKey("rago.setting." + id), "no settings row named " + id);
		}
	}
}
