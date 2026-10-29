package gg.rago.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.core.Module;
import gg.rago.core.ModuleRegistry;
import gg.rago.hud.HudElement;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Reads and writes {@code config/rago.json}.
 *
 * <pre>
 * {
 *   "schema": 1,
 *   "modules": {
 *     "fps": { "enabled": true, "options": { "shadow": true }, "hud": { "anchor": "TOP_LEFT", "x": 4, "y": 4, "scale": 1.0 } }
 *   }
 * }
 * </pre>
 * Unknown modules and options are ignored, bad values fall back to defaults, and a file
 * that cannot be parsed at all is kept as {@code rago.json.broken-<time>} before defaults are used.
 * Writes go to a temp file and are moved into place.
 */
public final class ConfigStore {
	public static final int SCHEMA = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;

	public ConfigStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/** Result of {@link #load}, so callers can tell the player what happened. */
	public enum LoadResult { MISSING, LOADED, BROKEN_FILE_REPLACED }

	public LoadResult load(ModuleRegistry registry) throws IOException {
		if (!Files.exists(file)) {
			return LoadResult.MISSING;
		}
		JsonObject root;
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = new JsonParser().parse(in);
			if (!parsed.isJsonObject()) {
				throw new IllegalStateException("root is not an object");
			}
			root = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Files.move(file, file.resolveSibling(file.getFileName() + ".broken-" + stamp), StandardCopyOption.REPLACE_EXISTING);
			return LoadResult.BROKEN_FILE_REPLACED;
		}

		root = migrate(root);
		JsonObject modules = object(root, "modules");
		if (modules == null) {
			return LoadResult.LOADED;
		}
		for (Module module : registry.all()) {
			JsonObject m = object(modules, module.id());
			if (m == null) {
				continue;
			}
			JsonElement enabled = m.get("enabled");
			if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
				module.setEnabled(enabled.getAsBoolean());
			}
			JsonObject options = object(m, "options");
			if (options != null) {
				for (Option<?> option : module.options()) {
					JsonElement v = options.get(option.key());
					if (v != null) {
						option.fromJson(v);
					}
				}
			}
			if (module instanceof HudElement) {
			HudElement hud = (HudElement) module;
				JsonObject pos = object(m, "hud");
				if (pos != null) {
					hud.position().fromJson(pos);
				}
			}
		}
		return LoadResult.LOADED;
	}

	public void save(ModuleRegistry registry) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("schema", SCHEMA);
		JsonObject modules = new JsonObject();
		for (Module module : registry.all()) {
			JsonObject m = new JsonObject();
			m.addProperty("enabled", module.isEnabled());
			JsonObject options = new JsonObject();
			for (Option<?> option : module.options()) {
				options.add(option.key(), option.toJson());
			}
			m.add("options", options);
			if (module instanceof HudElement) {
			HudElement hud = (HudElement) module;
				m.add("hud", hud.position().toJson());
			}
			modules.add(module.id(), m);
		}
		root.add("modules", modules);

		Path dir = file.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path tmp = dir.resolve(file.getFileName() + ".tmp");
		try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(root, out);
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Upgrades older schema versions in place. There is only version 1 so far. */
	static JsonObject migrate(JsonObject root) {
		return root;
	}

	private static JsonObject object(JsonObject parent, String key) {
		JsonElement e = parent.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}
}
