package gg.rago.config;

import gg.rago.core.FeatureClass;
import gg.rago.core.ModuleRegistry;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.render.TextRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
		public int width(TextRenderer text, boolean placeholder) {
			return 10;
		}

		@Override
		public int height(TextRenderer text, boolean placeholder) {
			return 10;
		}

		@Override
		public void render(TextRenderer text, boolean placeholder) {
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
		assertTrue(Files.readString(file).contains("\"schema\": 1"));
	}

	@Test
	void badValuesFallBackToDefaultsAndUnknownKeysAreIgnored(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("rago.json");
		Files.writeString(file, """
			{ "schema": 1, "modules": {
			  "a": { "enabled": "yes", "options": { "size": 99, "shadow": 3, "text_color": "#FF0000" } },
			  "gone": { "enabled": false }
			} }""");
		TestHud hud = new TestHud("a");
		new ConfigStore(file).load(registry(hud));
		assertTrue(hud.isEnabled());
		assertEquals(10, hud.size.get());
		assertEquals(0xFFFF0000, (int) ((ColorOption) hud.options().stream().filter(o -> o.key().equals("text_color")).findFirst().orElseThrow()).get());
	}

	@Test
	void brokenFileIsKeptAside(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("rago.json");
		Files.writeString(file, "{ this is not json");
		TestHud hud = new TestHud("a");
		assertEquals(ConfigStore.LoadResult.BROKEN_FILE_REPLACED, new ConfigStore(file).load(registry(hud)));
		assertFalse(Files.exists(file));
		try (var files = Files.list(dir)) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("rago.json.broken-")));
		}
	}
}
