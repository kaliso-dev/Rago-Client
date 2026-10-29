package gg.rago.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's own folders for resource packs, kept in {@code config/rago-pack-folders.json}. They
 * only sort the list on the packs screen: the pack files stay where they are.
 */
final class PackFolders {
	private static final Logger LOG = LogManager.getLogger("Rago");

	private final Path file;
	private final List<String> folders = new ArrayList<>();
	/** Pack name to folder name. */
	private final Map<String, String> packs = new LinkedHashMap<>();

	PackFolders(Path file) {
		this.file = file;
		load();
	}

	List<String> folders() {
		return folders;
	}

	/** The pack's folder, or null. */
	String folderOf(String pack) {
		return packs.get(pack);
	}

	int count(String folder) {
		int n = 0;
		for (String f : packs.values()) {
			if (f.equals(folder)) {
				n++;
			}
		}
		return n;
	}

	/** Adds a folder; false when the name is blank or taken. */
	boolean add(String name) {
		String n = name.trim();
		if (n.isEmpty() || folders.contains(n)) {
			return false;
		}
		folders.add(n);
		save();
		return true;
	}

	/** Removes the folder; its packs go back to no folder. */
	void remove(String folder) {
		folders.remove(folder);
		packs.values().removeIf(folder::equals);
		save();
	}

	/** Puts the pack in a folder, or in none when folder is null. */
	void put(String pack, String folder) {
		if (folder == null || !folders.contains(folder)) {
			packs.remove(pack);
		} else {
			packs.put(pack, folder);
		}
		save();
	}

	private void load() {
		try {
			if (!Files.isRegularFile(file)) {
				return;
			}
			JsonObject root = new Gson().fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), JsonObject.class);
			if (root == null) {
				return;
			}
			if (root.has("folders")) {
				for (JsonElement e : root.getAsJsonArray("folders")) {
					String n = e.getAsString().trim();
					if (!n.isEmpty() && !folders.contains(n)) {
						folders.add(n);
					}
				}
			}
			if (root.has("packs")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("packs").entrySet()) {
					String f = e.getValue().getAsString();
					if (folders.contains(f)) {
						packs.put(e.getKey(), f);
					}
				}
			}
		} catch (Exception e) {
			LOG.warn("Could not read {}", file, e);
		}
	}

	private void save() {
		JsonObject root = new JsonObject();
		JsonArray list = new JsonArray();
		for (String f : folders) {
			list.add(new com.google.gson.JsonPrimitive(f));
		}
		root.add("folders", list);
		JsonObject map = new JsonObject();
		for (Map.Entry<String, String> e : packs.entrySet()) {
			map.addProperty(e.getKey(), e.getValue());
		}
		root.add("packs", map);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			LOG.warn("Could not save {}", file, e);
		}
	}
}
