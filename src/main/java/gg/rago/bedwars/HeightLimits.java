package gg.rago.bedwars;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Learns the build height limit of each map from what the server does with your blocks: a block
 * it keeps proves that height is allowed, and a block it takes back twice at the same height,
 * above every block it kept, marks the limit. Nothing is guessed from outside data; the limits
 * learned are kept in {@code config/rago-height-limits.json} so the next game on that map knows.
 */
public final class HeightLimits {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final Type MAP_TYPE = new TypeToken<TreeMap<String, Integer>>() { }.getType();
	/** Rejections at the same height needed before it counts as the limit. */
	static final int REJECTIONS_NEEDED = 2;

	private final Path file;
	private final Map<String, Integer> saved = new TreeMap<>();
	private String map;
	private int highestKept = -1;
	private final TreeMap<Integer, Integer> rejected = new TreeMap<>();

	public HeightLimits(Path file) {
		this.file = file;
		load();
	}

	/** Switches to another map (or none); what was seen on the previous one is forgotten. */
	public void setMap(String map) {
		if (map == null ? this.map == null : map.equals(this.map)) {
			return;
		}
		this.map = map;
		highestKept = -1;
		rejected.clear();
	}

	public String map() {
		return map;
	}

	/** The server kept a block at height {@code y}. */
	public void kept(int y) {
		if (y <= highestKept) {
			return;
		}
		highestKept = y;
		rejected.keySet().removeIf(r -> r <= y);
		Integer known = map == null ? null : saved.get(map);
		if (known != null && y > known) {
			// the map changed or the old value was wrong
			saved.remove(map);
			save();
		}
	}

	/** The server took back a block at height {@code y}. */
	public void rejected(int y) {
		if (y <= highestKept) {
			// something else stopped it (a protected area), not the height
			return;
		}
		int n = rejected.merge(y, 1, Integer::sum);
		if (n >= REJECTIONS_NEEDED && map != null) {
			Integer known = saved.get(map);
			if (known == null || known != y - 1) {
				saved.put(map, y - 1);
				save();
			}
		}
	}

	/** The highest height a block can be placed at on this map, or -1 if not known yet. */
	public int limit() {
		if (map != null && saved.containsKey(map)) {
			return saved.get(map);
		}
		for (Map.Entry<Integer, Integer> e : rejected.entrySet()) {
			if (e.getValue() >= REJECTIONS_NEEDED) {
				return e.getKey() - 1;
			}
		}
		return -1;
	}

	private void load() {
		if (file == null || !Files.isRegularFile(file)) {
			return;
		}
		try {
			Map<String, Integer> m = new Gson().fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), MAP_TYPE);
			if (m != null) {
				saved.putAll(m);
			}
		} catch (IOException | RuntimeException e) {
			LOG.warn("Could not read {}", file, e);
		}
	}

	private void save() {
		if (file == null) {
			return;
		}
		try {
			Files.write(file, new Gson().toJson(saved, MAP_TYPE).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			LOG.warn("Could not save {}", file, e);
		}
	}
}
