package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * One of a fixed list of named values, stored by name so that reordering the list keeps saved
 * settings. Labels come from {@code rago.option.<key>.<id>}, or {@code rago.choice.<id>} for shared
 * names such as "none".
 */
public final class ChoiceOption extends Option<String> {
	private final List<String> ids;
	private Consumer<String> onSelect;

	public ChoiceOption(String key, String defaultId, String... ids) {
		super(key, defaultId);
		this.ids = Collections.unmodifiableList(Arrays.asList(ids));
		if (!this.ids.contains(defaultId)) {
			throw new IllegalArgumentException("Default " + defaultId + " is not a choice of " + key);
		}
	}

	public List<String> ids() {
		return ids;
	}

	public int index() {
		return Math.max(0, ids.indexOf(get()));
	}

	public boolean is(String id) {
		return get().equals(id);
	}

	/** Runs when the player picks a value in the menu (not when a saved value is loaded). */
	public ChoiceOption onSelect(Consumer<String> action) {
		this.onSelect = action;
		return this;
	}

	/** Picks a value as the player would, running the {@link #onSelect} action. */
	public void select(String id) {
		set(id);
		if (onSelect != null) {
			onSelect.accept(get());
		}
	}

	public void cycle(int step) {
		select(ids.get(Math.floorMod(index() + step, ids.size())));
	}

	@Override
	protected String sanitize(String value) {
		return ids.contains(value) ? value : defaultValue();
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	protected String parse(JsonElement json) {
		return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() && ids.contains(json.getAsString()) ? json.getAsString() : null;
	}
}
