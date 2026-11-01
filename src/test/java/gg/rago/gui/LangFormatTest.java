package gg.rago.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IllegalFormatException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.fail;

class LangFormatTest {
	/**
	 * Help texts and descriptions are shown through I18n.format, which runs String.format on them
	 * without arguments: a lone % ("70 % of") turns the whole text into "Format error: ...". A percent
	 * sign in them has to be written %%.
	 */
	@Test
	void helpTextsSurviveFormatting() throws IOException {
		for (String file : new String[] {"en_US", "fr_FR"}) {
			Properties lang = new Properties();
			try (InputStream in = getClass().getResourceAsStream("/assets/rago/lang/" + file + ".lang")) {
				lang.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
			for (String key : lang.stringPropertyNames()) {
				if (!key.endsWith(".help") && !key.endsWith(".desc")) {
					continue;
				}
				try {
					String.format(lang.getProperty(key));
				} catch (IllegalFormatException e) {
					fail(file + ": " + key + " cannot be formatted (" + e + ")");
				}
			}
		}
	}
}
