package gg.rago.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Every row of the settings page, in display order, plus the values Rago stores itself. Own
 * values go to the {@code "settings"} object of {@code config/rago.json}.
 */
public final class Settings {
	private final List<Setting> rows = new ArrayList<>();
	private final Map<String, Option<?>> stored = new LinkedHashMap<>();

	public List<Setting> rows() {
		return Collections.unmodifiableList(rows);
	}

	public <T extends Setting> T add(T row) {
		for (Setting s : rows) {
			if (s.id().equals(row.id())) {
				throw new IllegalArgumentException("Duplicate setting " + row.id());
			}
		}
		rows.add(row);
		return row;
	}

	// ---- Rago's own settings --------------------------------------------------------------------

	public BooleanOption bool(Setting.Tab tab, String section, String id, boolean def, Consumer<Boolean> onChange) {
		BooleanOption option = store(new BooleanOption(id, def));
		add(new Toggle(id, tab, section, option::get, v -> {
			option.set(v);
			if (onChange != null) {
				onChange.accept(v);
			}
		}));
		return option;
	}

	public IntOption slider(Setting.Tab tab, String section, String id, int def, int min, int max, int step,
		IntFunction<String> format, IntConsumer onChange) {
		IntOption option = store(new IntOption(id, def, min, max));
		add(new Slider(id, tab, section, min, max, step, format, option::get, v -> {
			option.set(v);
			if (onChange != null) {
				onChange.accept(v);
			}
		}));
		return option;
	}

	public IntOption choice(Setting.Tab tab, String section, String id, int def, IntConsumer onChange, String... valueKeys) {
		IntOption option = store(new IntOption(id, def, 0, valueKeys.length - 1));
		add(new Choice(id, tab, section, valueKeys, option::get, v -> {
			option.set(v);
			if (onChange != null) {
				onChange.accept(v);
			}
		}));
		return option;
	}

	// ---- settings that live in the game's own fields --------------------------------------------

	public Toggle toggle(Setting.Tab tab, String section, String id, BooleanSupplier get, Consumer<Boolean> set) {
		return add(new Toggle(id, tab, section, get, set));
	}

	public Slider slider(Setting.Tab tab, String section, String id, int min, int max, int step,
		IntFunction<String> format, IntSupplier get, IntConsumer set) {
		return add(new Slider(id, tab, section, min, max, step, format, get, set));
	}

	public Choice choice(Setting.Tab tab, String section, String id, IntSupplier get, IntConsumer set, String... valueKeys) {
		return add(new Choice(id, tab, section, valueKeys, get, set));
	}

	public Button button(Setting.Tab tab, String section, String id, Runnable action) {
		return add(new Button(id, tab, section, action));
	}

	private <T extends Option<?>> T store(T option) {
		if (stored.containsKey(option.key())) {
			throw new IllegalArgumentException("Duplicate stored setting " + option.key());
		}
		stored.put(option.key(), option);
		return option;
	}

	// ---- persistence ----------------------------------------------------------------------------

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		for (Option<?> option : stored.values()) {
			o.add(option.key(), option.toJson());
		}
		return o;
	}

	public void fromJson(JsonObject o) {
		for (Option<?> option : stored.values()) {
			JsonElement v = o.get(option.key());
			if (v != null) {
				option.fromJson(v);
			}
		}
	}

	/** Puts every stored setting back to its default. Game-owned settings are not touched. */
	public void resetStored() {
		stored.values().forEach(Option::reset);
	}
}
