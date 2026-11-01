package gg.rago.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.rago.config.ConfigStore;
import gg.rago.core.ModuleRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named sets of mod states (on/off, options and HUD positions), e.g. one for Bedwars and one for
 * practice. The live modules always hold the active profile; switching first stores them into
 * the profile being left.
 */
public final class ModProfiles {
	private static final String[][] DEFAULTS = {
		{"Default", "user"}, {"Bedwars", "bed"}, {"Practice", "swords"}, {"Skywars", "cloud"}
	};

	/**
	 * Profiles that come with Rago, offered once to every player (new or not) without being
	 * switched to: {name, icon, share code resource}. A player who deletes one does not get it back.
	 */
	static final String[][] BUILT_IN = {
		{"qRago Official", "official:qrago", "/assets/rago/profiles/qrago_official.txt"}
	};

	/** Built-in profiles already offered to this player. */
	private final java.util.Set<String> offered = new java.util.LinkedHashSet<>();

	private final Map<String, Entry> profiles = new LinkedHashMap<>();
	private String active = "Default";

	private static final class Entry {
		String icon;
		JsonObject modules;

		Entry(String icon, JsonObject modules) {
			this.icon = icon;
			this.modules = modules;
		}
	}

	/** Creates the default profiles from the current mod states when the file had none. */
	public void ensureDefaults(ModuleRegistry registry) {
		if (!profiles.isEmpty()) {
			if (!profiles.containsKey(active)) {
				active = profiles.keySet().iterator().next();
			}
			return;
		}
		JsonObject now = ConfigStore.modulesToJson(registry);
		for (String[] d : DEFAULTS) {
			profiles.put(d[0], new Entry(d[1], copy(now)));
		}
		active = DEFAULTS[0][0];
	}

	/**
	 * Adds each built-in profile not yet offered to this player, after the others, keeping the
	 * active one: Rago's defaults with the code's changes, the personal mods as the player has them.
	 */
	public void ensureBuiltIn(ModuleRegistry registry) {
		for (String[] b : BUILT_IN) {
			offer(registry, b[0], b[1], resource(b[2]));
		}
	}

	/**
	 * Adds one built-in profile unless this player was already offered it. Returns false only
	 * when the code cannot be read: the profile is then offered again by a later Rago.
	 */
	boolean offer(ModuleRegistry registry, String name, String icon, String code) {
		if (offered.contains(name)) {
			return true;
		}
		ProfileShare.Shared shared = ProfileShare.decode(code);
		if (shared == null) {
			return false;
		}
		offered.add(name);
		if (!profiles.containsKey(name)) {
			capture(registry);
			JsonObject modules = ProfileShare.merge(ConfigStore.defaultsToJson(registry),
				ConfigStore.modulesToJson(registry), shared.changes);
			profiles.put(name, new Entry(icon, modules));
		}
		return true;
	}

	/** Whether a profile's icon is a picture that comes with Rago rather than a drawn icon. */
	public static boolean isPicture(String icon) {
		for (String[] b : BUILT_IN) {
			if (b[1].equals(icon)) {
				return true;
			}
		}
		return false;
	}

	static String resource(String path) {
		try (java.io.InputStream in = ModProfiles.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			for (int n; (n = in.read(buf)) > 0; ) {
				out.write(buf, 0, n);
			}
			return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			return null;
		}
	}

	public List<String> names() {
		return Collections.unmodifiableList(new ArrayList<>(profiles.keySet()));
	}

	public String active() {
		return active;
	}

	public String icon(String name) {
		Entry e = profiles.get(name);
		return e == null || e.icon == null ? "user" : e.icon;
	}

	public void switchTo(String name, ModuleRegistry registry) {
		Entry target = profiles.get(name);
		if (target == null || name.equals(active)) {
			return;
		}
		capture(registry);
		ConfigStore.modulesFromJson(registry, target.modules);
		active = name;
	}

	/** Stores the live mod states into the active profile. */
	public void capture(ModuleRegistry registry) {
		Entry e = profiles.get(active);
		if (e != null) {
			e.modules = ConfigStore.modulesToJson(registry);
		}
	}

	/** Saves the live mod states as a new profile and makes it active; returns its name. */
	public String saveAsNew(ModuleRegistry registry) {
		capture(registry);
		int n = profiles.size() + 1;
		String name = "Profile " + n;
		while (profiles.containsKey(name)) {
			name = "Profile " + ++n;
		}
		profiles.put(name, new Entry("user", ConfigStore.modulesToJson(registry)));
		active = name;
		return name;
	}

	/** Renames a profile, keeping its position; returns false for an empty or taken name. */
	public boolean rename(String from, String to) {
		String name = to == null ? "" : to.trim();
		if (name.isEmpty() || name.length() > 24 || !profiles.containsKey(from) || (profiles.containsKey(name) && !name.equals(from))) {
			return false;
		}
		Map<String, Entry> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Entry> e : profiles.entrySet()) {
			copy.put(e.getKey().equals(from) ? name : e.getKey(), e.getValue());
		}
		profiles.clear();
		profiles.putAll(copy);
		if (active.equals(from)) {
			active = name;
		}
		return true;
	}

	/** Deletes a profile other than the last one; the active one falls back to the first. */
	public boolean delete(String name, ModuleRegistry registry) {
		if (profiles.size() <= 1 || !profiles.containsKey(name)) {
			return false;
		}
		profiles.remove(name);
		if (active.equals(name)) {
			active = profiles.keySet().iterator().next();
			ConfigStore.modulesFromJson(registry, profiles.get(active).modules);
		}
		return true;
	}

	/** A share code for a profile (see {@link ProfileShare}); null when there is no such profile. */
	public String exportCode(String name, ModuleRegistry registry) {
		Entry e = profiles.get(name);
		if (e == null) {
			return null;
		}
		if (name.equals(active)) {
			capture(registry);
		}
		return ProfileShare.encode(name, e.icon,
			ProfileShare.changes(e.modules, ConfigStore.defaultsToJson(registry)));
	}

	/**
	 * Adds the profile in a friend's code (also found inside a longer text) as a new profile and
	 * switches to it; the personal mods keep the settings the player has now. Returns the new
	 * profile's name, or null when the text holds no readable code.
	 */
	public String importCode(String text, ModuleRegistry registry) {
		ProfileShare.Shared shared = ProfileShare.decode(text);
		if (shared == null) {
			return null;
		}
		capture(registry);
		JsonObject modules = ProfileShare.merge(ConfigStore.defaultsToJson(registry),
			ConfigStore.modulesToJson(registry), shared.changes);
		String name = ProfileShare.freeName(shared.name, profiles.keySet());
		profiles.put(name, new Entry(shared.icon, modules));
		ConfigStore.modulesFromJson(registry, modules);
		active = name;
		return name;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("active", active);
		JsonObject list = new JsonObject();
		for (Map.Entry<String, Entry> e : profiles.entrySet()) {
			JsonObject p = new JsonObject();
			p.addProperty("icon", e.getValue().icon);
			p.add("modules", e.getValue().modules);
			list.add(e.getKey(), p);
		}
		o.add("list", list);
		com.google.gson.JsonArray given = new com.google.gson.JsonArray();
		for (String name : offered) {
			given.add(new com.google.gson.JsonPrimitive(name));
		}
		o.add("offered", given);
		return o;
	}

	public void fromJson(JsonObject o) {
		profiles.clear();
		offered.clear();
		JsonElement given = o.get("offered");
		if (given != null && given.isJsonArray()) {
			for (JsonElement e : given.getAsJsonArray()) {
				if (e.isJsonPrimitive()) {
					offered.add(e.getAsString());
				}
			}
		}
		JsonElement list = o.get("list");
		if (list != null && list.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : list.getAsJsonObject().entrySet()) {
				if (!e.getValue().isJsonObject()) {
					continue;
				}
				JsonObject p = e.getValue().getAsJsonObject();
				JsonElement icon = p.get("icon");
				JsonElement modules = p.get("modules");
				profiles.put(e.getKey(), new Entry(
					icon != null && icon.isJsonPrimitive() ? icon.getAsString() : "user",
					modules != null && modules.isJsonObject() ? modules.getAsJsonObject() : new JsonObject()));
			}
		}
		JsonElement a = o.get("active");
		if (a != null && a.isJsonPrimitive() && profiles.containsKey(a.getAsString())) {
			active = a.getAsString();
		}
	}

	private static JsonObject copy(JsonObject o) {
		return new com.google.gson.JsonParser().parse(o.toString()).getAsJsonObject();
	}
}
