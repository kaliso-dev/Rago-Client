package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One of a fixed list of named values, stored by name so that reordering the list keeps saved
 * settings. Labels come from {@code rago.option.<key>.<id>}, or {@code rago.choice.<id>} for shared
 * names such as "none".
 */
public final class ChoiceOption extends Option<String> {
	private final List<String> ids;
	private Consumer<String> onSelect;
	private Predicate<String> allowed = id -> true;

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

	/**
	 * Offers a value in the menu only while {@code allowed} says so (a cape only its owner wears). A
	 * value saved earlier is kept even when it is not offered.
	 */
	public ChoiceOption allowIf(Predicate<String> allowed) {
		this.allowed = allowed;
		return this;
	}

	/** The values the menu offers now: all of them, but those {@link #allowIf} holds back. */
	public List<String> choices() {
		List<String> out = new ArrayList<>(ids.size());
		for (String id : ids) {
			if (allowed.test(id)) {
				out.add(id);
			}
		}
		return out;
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

	/** The next value offered ({@code step} 1) or the one before (-1). */
	public void cycle(int step) {
		List<String> offered = choices();
		if (offered.isEmpty()) {
			return;
		}
		int at = offered.indexOf(get());
		// a value that is not offered (saved before) goes to the first or the last one offered
		select(at < 0 ? offered.get(step > 0 ? 0 : offered.size() - 1) : offered.get(Math.floorMod(at + step, offered.size())));
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
