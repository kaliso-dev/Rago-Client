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
		return o;
	}

	public void fromJson(JsonObject o) {
		profiles.clear();
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
