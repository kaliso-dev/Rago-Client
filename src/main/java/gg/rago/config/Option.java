package gg.rago.config;

import com.google.gson.JsonElement;

/** A typed, persisted setting. Invalid stored values fall back to the default. */
public abstract class Option<T> {
	private final String key;
	private final T defaultValue;
	private T value;

	protected Option(String key, T defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public final String key() {
		return key;
	}

	public final T get() {
		return value;
	}

	public final T defaultValue() {
		return defaultValue;
	}

	public void set(T value) {
		this.value = value == null ? defaultValue : sanitize(value);
	}

	public final void reset() {
		value = defaultValue;
	}

	/** Lets subclasses clamp or normalise values; the default accepts anything. */
	protected T sanitize(T value) {
		return value;
	}

	public abstract JsonElement toJson();

	/** Reads a stored value; anything unreadable keeps the default. */
	public final void fromJson(JsonElement json) {
		try {
			T parsed = parse(json);
			if (parsed != null) {
				set(parsed);
				return;
			}
		} catch (RuntimeException ignored) {
			// wrong type or format in the file: keep the default
		}
		reset();
	}

	protected abstract T parse(JsonElement json);
}
