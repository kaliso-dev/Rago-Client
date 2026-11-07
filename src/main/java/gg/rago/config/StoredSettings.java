package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One of Rago's own settings read straight from {@code config/rago.json} (its {@code "settings"}
 * object, as {@link ConfigStore} writes it), for game code that runs before Rago has started: the
 * game reads every resource pack's icon as it opens, before any mod.
 */
public final class StoredSettings {
	private StoredSettings() {
	}

	/** The stored value of the on/off setting {@code id}, or {@code def} when the file or the value is missing or unreadable. */
	public static boolean bool(Path file, String id, boolean def) {
		if (file == null || !Files.isRegularFile(file)) {
			return def;
		}
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = new JsonParser().parse(in);
			JsonElement settings = root.isJsonObject() ? root.getAsJsonObject().get("settings") : null;
			JsonElement value = settings != null && settings.isJsonObject() ? ((JsonObject) settings).get(id) : null;
			if (value != null && value.isJsonPrimitive() && ((JsonPrimitive) value).isBoolean()) {
				return value.getAsBoolean();
			}
			return def;
		} catch (IOException | RuntimeException e) {
			return def;
		}
	}
}
