package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class IntOption extends Option<Integer> {
	private final int min;
	private final int max;
	private int decimals;

	public IntOption(String key, int defaultValue, int min, int max) {
		super(key, defaultValue);
		if (min > max || defaultValue < min || defaultValue > max) {
			throw new IllegalArgumentException("Bad range for " + key);
		}
		this.min = min;
		this.max = max;
	}

	/** Shows the value with {@code n} decimals: stored 15, shown 1.5 with one decimal. */
	public IntOption decimals(int n) {
		decimals = n;
		return this;
	}

	/** What a "%d" or "%s" format is given: the number itself, or the text with its decimals. */
	public Object formatValue() {
		return decimals == 0 ? (Object) get() : display();
	}

	/** The value as shown, with its decimals. */
	public String display() {
		if (decimals == 0) {
			return Integer.toString(get());
		}
		return String.format(java.util.Locale.ROOT, "%." + decimals + "f", get() / Math.pow(10, decimals));
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

	@Override
	protected Integer sanitize(Integer value) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	protected Integer parse(JsonElement json) {
		return json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber() ? json.getAsInt() : null;
	}
}
