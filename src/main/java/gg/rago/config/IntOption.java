package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class IntOption extends Option<Integer> {
	private final int min;
	private final int max;

	public IntOption(String key, int defaultValue, int min, int max) {
		super(key, defaultValue);
		if (min > max || defaultValue < min || defaultValue > max) {
			throw new IllegalArgumentException("Bad range for " + key);
		}
		this.min = min;
		this.max = max;
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
