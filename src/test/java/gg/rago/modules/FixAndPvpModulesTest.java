package gg.rago.modules;

import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;
import gg.rago.core.Module;
import gg.rago.hud.elements.BlockCounterHud;
import gg.rago.hud.elements.ClockHud;
import gg.rago.hud.elements.DirectionHud;
import gg.rago.hud.elements.HeightLimitHud;
import gg.rago.hud.elements.PackDisplayHud;
import gg.rago.hud.elements.MemoryHud;
import gg.rago.hud.elements.ScoreboardHud;
import gg.rago.hypixel.HypixelApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixAndPvpModulesTest {
	@TempDir
	Path dir;

	private List<Module> modules() {
		Nametags nametags = new Nametags();
		return Arrays.asList(new NoHurtCam(), new OldAnimations(), new LowFire(), new NoPumpkin(), new NoWaterOverlay(),
			new HideAchievements(), new F5Crosshair(), new WeatherChanger(), new TimeChanger(), new HitParticles(),
			new BlockOverlay(), new TabPing(), new Chat(), nametags, new LevelHead(new HypixelApi(dir), nametags),
			new ClockHud(), new MemoryHud(), new ScoreboardHud(), new GameplayFixes(), new HitColor(),
			new MotionBlur(new Zoom(null)), new GlintColor(), new HeightLimitHud(dir), new BlockCounterHud(), new DirectionHud(),
			new PackDisplayHud(), new Vignette(), new Cosmetics());
	}

	/** Modules that are on from the start and change nothing in the game (Rago Network sends nothing without a server set). */
	private List<Module> onByDefault() {
		return Arrays.asList(new RagoCape(), new RagoNetwork());
	}

	/** Every new module has a name, a description and a label for each option, in both languages. */
	@Test
	void everyModuleIsTranslated() throws IOException {
		for (String file : new String[] {"en_US", "fr_FR"}) {
			Properties lang = new Properties();
			try (InputStream in = getClass().getResourceAsStream("/assets/rago/lang/" + file + ".lang")) {
				lang.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
			}
			List<Module> all = new java.util.ArrayList<>(modules());
			all.addAll(onByDefault());
			for (Module m : all) {
				assertTrue(lang.containsKey(m.nameKey()), file + ": " + m.nameKey());
				assertTrue(lang.containsKey("rago.module." + m.id() + ".desc"), file + ": desc of " + m.id());
				for (Option<?> o : m.options()) {
					assertTrue(lang.containsKey("rago.option." + o.key()), file + ": option " + o.key());
				}
			}
		}
	}

	/** Nothing in the new modules may change gameplay before the player turns it on. */
	@Test
	void newModulesStartOff() {
		for (Module m : modules()) {
			assertFalse(m.isEnabled(), m.id());
		}
	}

	@Test
	void choiceOptionsHaveALabelPerValue() throws IOException {
		Properties lang = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/assets/rago/lang/en_US.lang")) {
			lang.load(in);
		}
		for (String key : new String[] {"weather", "time_format", "level_display"}) {
			for (Module m : modules()) {
				for (Option<?> o : m.options()) {
					if (o.key().equals(key)) {
						IntOption io = (IntOption) o;
						for (int v = io.min(); v <= io.max(); v++) {
							assertTrue(lang.containsKey("rago.option." + key + "." + v), key + "." + v);
						}
					}
				}
			}
		}
	}

	@Test
	void timeChangerMapsHoursToTicks() {
		assertEquals(18000, TimeChanger.ticks(0));  // midnight
		assertEquals(0, TimeChanger.ticks(6));      // sunrise
		assertEquals(6000, TimeChanger.ticks(12));  // noon
		assertEquals(17000, TimeChanger.ticks(23));
	}

	@Test
	void chatNeverStacksSeparators() {
		assertFalse(Chat.hasWords("▬▬▬▬▬▬▬▬▬▬"));
		assertFalse(Chat.hasWords("   "));
		assertTrue(Chat.hasWords("You have 5 coins"));
	}

	@Test
	void pingColours() {
		assertEquals(0xFF55FF55, TabPing.color(20));
		assertEquals(0xFFFFFF55, TabPing.color(100));
		assertEquals(0xFFFFAA00, TabPing.color(200));
		assertEquals(0xFFFF5555, TabPing.color(400));
	}

	@Test
	void overlayAlpha() {
		assertEquals(0xFF123456, BlockOverlay.withAlpha(0xFF123456, 100));
		assertEquals(0x00123456, BlockOverlay.withAlpha(0xFF123456, 0) & 0xFFFFFFFF);
		assertEquals(0x80, BlockOverlay.withAlpha(0xFFFFFFFF, 50) >>> 24);
	}

	/** Every value of every choice has a label of its own or a shared one, in both languages. */
	@Test
	void choicesHaveLabels() throws IOException {
		for (String file : new String[] {"en_US", "fr_FR"}) {
			Properties lang = new Properties();
			try (InputStream in = getClass().getResourceAsStream("/assets/rago/lang/" + file + ".lang")) {
				lang.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
			}
			for (Module m : modules()) {
				for (Option<?> o : m.options()) {
					if (o instanceof ChoiceOption) {
						for (String id : ((ChoiceOption) o).ids()) {
							assertTrue(lang.containsKey("rago.option." + o.key() + "." + id) || lang.containsKey("rago.choice." + id),
								file + ": " + o.key() + "." + id);
						}
					}
				}
			}
		}
	}
}
