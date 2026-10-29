package gg.rago.config;

import gg.rago.core.FeatureClass;
import gg.rago.core.ModuleRegistry;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.gui.FontRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
	static final class TestHud extends HudElement {
		final IntOption size = addOption(new IntOption("size", 5, 1, 10));

		TestHud(String id) {
			super(id, FeatureClass.INFORMATIONAL, true, Anchor.TOP_LEFT, 4, 4);
		}

		@Override
		public int width(FontRenderer text, boolean placeholder) {
			return 10;
		}

		@Override
		public int height(FontRenderer text, boolean placeholder) {
			return 10;
		}

		@Override
		public void render(FontRenderer text, boolean placeholder) {
		}
	}

	private static ModuleRegistry registry(TestHud... huds) {
		ModuleRegistry r = new ModuleRegistry();
		for (TestHud h : huds) {
			r.register(h);
		}
		return r;
	}

	@Test
	void missingFileKeepsDefaults(@TempDir Path dir) throws Exception {
		TestHud hud = new TestHud("a");
		assertEquals(ConfigStore.LoadResult.MISSING, new ConfigStore(dir.resolve("rago.json")).load(registry(hud)));
		assertTrue(hud.isEnabled());
	}

	@Test
	void roundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("config").resolve("rago.json");
		TestHud hud = new TestHud("a");
		hud.setEnabled(false);
		hud.size.set(8);
		hud.position().moveTo(300, 250, 10, 10, 400, 300);
		hud.position().setScale(2);
		new ConfigStore(file).save(registry(hud));
		assertFalse(Files.exists(file.resolveSibling("rago.json.tmp")));

		TestHud loaded = new TestHud("a");
		assertEquals(ConfigStore.LoadResult.LOADED, new ConfigStore(file).load(registry(loaded)));
		assertFalse(loaded.isEnabled());
		assertEquals(8, loaded.size.get());
		assertEquals(hud.position().anchor(), loaded.position().anchor());
		assertEquals(2f, loaded.position().scale());
		assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("\"schema\": 1"));
	}

	@Test
	void badValuesFallBackToDefaultsAndUnknownKeysAreIgnored(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("rago.json");
		write(file, "{ \"schema\": 1, \"modules\": {\n"
			+ "  \"a\": { \"enabled\": \"yes\", \"options\": { \"size\": 99, \"shadow\": 3, \"text_color\": \"#FF0000\" } },\n"
			+ "  \"gone\": { \"enabled\": false }\n"
			+ "} }");
		TestHud hud = new TestHud("a");
		new ConfigStore(file).load(registry(hud));
		assertTrue(hud.isEnabled());
		assertEquals(10, hud.size.get());
		assertEquals(0xFFFF0000, (int) ((ColorOption) hud.options().stream().filter(o -> o.key().equals("text_color")).findFirst().get()).get());
	}

	@Test
	void brokenFileIsKeptAside(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("rago.json");
		write(file, "{ this is not json");
		TestHud hud = new TestHud("a");
		assertEquals(ConfigStore.LoadResult.BROKEN_FILE_REPLACED, new ConfigStore(file).load(registry(hud)));
		assertFalse(Files.exists(file));
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("rago.json.broken-")));
		}
	}

	private static void write(Path file, String text) throws java.io.IOException {
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
	}
}
