package gg.rago.gui;

import gg.rago.config.Option;
import gg.rago.core.Module;
import gg.rago.hypixel.HypixelApi;
import gg.rago.modules.AutoMessages;
import gg.rago.modules.AutoText;
import gg.rago.modules.BedwarsStats;
import gg.rago.modules.BlockEditFix;
import gg.rago.modules.FastShop;
import gg.rago.modules.HitDelayFix;
import gg.rago.modules.TntTimer;
import gg.rago.modules.TrapNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Risky on Hypixel" is a warning on these mods and options; nothing is locked. */
class HypixelRiskTest {
	@TempDir
	Path dir;

	private List<Module> modules() {
		return Arrays.asList(new HitDelayFix(), new BlockEditFix(), new FastShop(), new AutoMessages(), new AutoText(),
			new TntTimer(), new BedwarsStats(new HypixelApi(dir)), new TrapNotifier());
	}

	@Test
	void everyRiskyModAndOptionExistsAndSaysWhy() throws IOException {
		Set<String> ids = new HashSet<>();
		Set<String> keys = new HashSet<>();
		for (Module m : modules()) {
			ids.add(m.id());
			for (Option<?> o : m.options()) {
				keys.add(o.key());
			}
		}
		assertTrue(ids.containsAll(ModuleInfo.RISKY_MODULES), "risky mods: " + ModuleInfo.RISKY_MODULES);
		assertTrue(keys.containsAll(ModuleInfo.RISKY_OPTIONS), "risky options: " + ModuleInfo.RISKY_OPTIONS);
		for (String file : new String[] {"en_US", "fr_FR"}) {
			Properties lang = new Properties();
			try (InputStream in = getClass().getResourceAsStream("/assets/rago/lang/" + file + ".lang")) {
				lang.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
			for (String key : new String[] {"title", "note", "tooltip", "tooltip_option"}) {
				assertTrue(lang.containsKey("rago.risk." + key), file + ": rago.risk." + key);
			}
			assertEquals(1, lang.getProperty("rago.risk.tooltip").split("%s", -1).length - 1, file);
			assertEquals(2, lang.getProperty("rago.risk.tooltip_option").split("%s", -1).length - 1, file);
			Set<String> all = new HashSet<>(ModuleInfo.RISKY_MODULES);
			all.addAll(ModuleInfo.RISKY_OPTIONS);
			for (String id : all) {
				assertTrue(lang.containsKey(ModuleInfo.riskKey(id)), file + ": reason for " + id);
			}
		}
	}

	/** Nothing risky is on until the player turns it on (the TNT timer is, without seeing through walls). */
	@Test
	void nothingRiskyIsOnAtFirst() {
		for (Module m : modules()) {
			if (ModuleInfo.risky(m)) {
				assertFalse(m.isEnabled(), m.id());
			}
			assertNull(ModuleInfo.riskyOptionOn(m), m.id());
		}
	}

	@Test
	void aRiskyOptionWarnsOnceItIsOn() {
		TntTimer tnt = new TntTimer();
		for (Option<?> o : tnt.options()) {
			if (o.key().equals("tnt_see_through_walls")) {
				((gg.rago.config.BooleanOption) o).set(true);
			}
		}
		Option<?> on = ModuleInfo.riskyOptionOn(tnt);
		assertEquals("tnt_see_through_walls", on == null ? null : on.key());
		assertFalse(ModuleInfo.risky(tnt), "the timer itself is fine");
	}
}
