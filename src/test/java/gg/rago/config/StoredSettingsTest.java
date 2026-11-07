package gg.rago.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredSettingsTest {
	@TempDir
	Path dir;

	private Path file(String json) throws IOException {
		Path file = dir.resolve("rago.json");
		Files.write(file, json.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	@Test
	void readsTheStoredValue() throws IOException {
		Path file = file("{\"schema\": 1, \"settings\": {\"small_pack_icons\": false, \"other\": true}}");
		assertFalse(StoredSettings.bool(file, "small_pack_icons", true));
		assertTrue(StoredSettings.bool(file, "other", false));
	}

	@Test
	void defaultWhenMissingOrUnreadable() throws IOException {
		assertTrue(StoredSettings.bool(dir.resolve("none.json"), "small_pack_icons", true));
		assertTrue(StoredSettings.bool(null, "small_pack_icons", true));
		assertTrue(StoredSettings.bool(file("{\"settings\": {}}"), "small_pack_icons", true));
		assertTrue(StoredSettings.bool(file("{\"settings\": {\"small_pack_icons\": \"no\"}}"), "small_pack_icons", true));
		assertTrue(StoredSettings.bool(file("{\"settings\": [1, 2]}"), "small_pack_icons", true));
		assertTrue(StoredSettings.bool(file("{broken"), "small_pack_icons", true));
		assertFalse(StoredSettings.bool(file("[]"), "small_pack_icons", false));
	}

	@Test
	void readsWhatTheStoreWrites() throws IOException {
		gg.rago.settings.Settings settings = new gg.rago.settings.Settings();
		BooleanOption option = settings.bool(gg.rago.settings.Setting.Tab.PERFORMANCE, "engine", "small_pack_icons", true, null);
		option.set(false);
		ConfigStore store = new ConfigStore(dir.resolve("rago.json"));
		store.save(new gg.rago.core.ModuleRegistry(), settings, null);
		assertFalse(StoredSettings.bool(store.file(), "small_pack_icons", true));
	}
}
