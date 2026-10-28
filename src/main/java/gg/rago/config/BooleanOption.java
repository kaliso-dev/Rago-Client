package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanOption extends Option<Boolean> {
	public BooleanOption(String key, boolean defaultValue) {
		super(key, defaultValue);
	}

	public void toggle() {
		set(!get());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	protected Boolean parse(JsonElement json) {
		return json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean() ? json.getAsBoolean() : null;
	}
}
