package gg.rago.config;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/** A typed, persisted setting. Invalid stored values fall back to the default. */
public abstract class Option<T> {
	private final String key;
	private T defaultValue;
	private T value;
	private String section;
	private BooleanSupplier visible = () -> true;

	protected Option(String key, T defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public final String key() {
		return key;
	}

	/** The heading this option is listed under in the menu, or null. */
	public final String section() {
		return section;
	}

	public final void setSection(String section) {
		this.section = section;
	}

	/** Shows the option in the menu only while {@code condition} holds (for options that depend on another). */
	@SuppressWarnings("unchecked")
	public final <O extends Option<T>> O showIf(BooleanSupplier condition) {
		this.visible = condition;
		return (O) this;
	}

	public final boolean visible() {
		return visible.getAsBoolean();
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

	/** Makes the current value the one {@link #reset} goes back to (for presets applied at start-up). */
	public final void makeCurrentDefault() {
		defaultValue = value;
	}

	public final void reset() {
		value = defaultValue;
	}

	/** Lets subclasses clamp or normalise values; the default accepts anything. */
	protected T sanitize(T value) {
		return value;
	}

	public abstract JsonElement toJson();

	/** The default value as it is stored, without changing the current one. */
	public final JsonElement defaultJson() {
		T now = value;
		value = defaultValue;
		try {
			return toJson();
		} finally {
			value = now;
		}
	}

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
