package gg.rago.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Applies a set of overrides to a JSON settings object and remembers the values they replaced,
 * so switching a profile off puts the player's own settings back exactly.
 *
 * <p>The backup file holds one object per settings source ({@code "vanilla"}, {@code "extras"}),
 * each mapping a key to the value it had before the profile was applied. A key that was absent
 * is stored as JSON null and removed again on restore.
 */
public final class ProfileBackup {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private ProfileBackup() {
	}

	/** Sets every override on {@code target} and returns the replaced values. */
	public static JsonObject apply(JsonObject target, Map<String, JsonElement> overrides) {
		JsonObject replaced = new JsonObject();
		for (Map.Entry<String, JsonElement> e : overrides.entrySet()) {
			replaced.add(e.getKey(), target.has(e.getKey()) ? target.get(e.getKey()) : null);
			target.add(e.getKey(), e.getValue());
		}
		return replaced;
	}

	/** Puts the values from {@link #apply} back; keys the player never had are removed. */
	public static void restore(JsonObject target, JsonObject replaced) {
		for (Map.Entry<String, JsonElement> e : replaced.entrySet()) {
			if (e.getValue() == null || e.getValue().isJsonNull()) {
				target.remove(e.getKey());
			} else {
				target.add(e.getKey(), e.getValue());
			}
		}
	}

	public static JsonObject read(Path file) throws IOException {
		if (!Files.exists(file)) {
			return null;
		}
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = new JsonParser().parse(in);
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			throw new IOException("Unreadable JSON in " + file, e);
		}
	}

	/** Writes through a temp file so a crash never leaves half a file. */
	public static void write(Path file, JsonObject json) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(json, out);
		}
		Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	/** {@code entity_shadow_distance} → {@code entityShadowDistance}, matching Argentum's field names. */
	public static String camelCase(String snake) {
		StringBuilder b = new StringBuilder(snake.length());
		boolean up = false;
		for (char c : snake.toCharArray()) {
			if (c == '_') {
				up = true;
			} else {
				b.append(up ? Character.toUpperCase(c) : c);
				up = false;
			}
		}
		return b.toString();
	}
}
