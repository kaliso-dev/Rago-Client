package gg.rago.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A line of text the player types, such as a chat message; cut to {@code maxLength} characters. */
public final class StringOption extends Option<String> {
	private final int maxLength;

	public StringOption(String key, String defaultValue, int maxLength) {
		super(key, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String value) {
		String s = value == null ? "" : value.replace('\n', ' ').replace('§', '&');
		return s.length() > maxLength ? s.substring(0, maxLength) : s;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	protected String parse(JsonElement json) {
		return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() ? json.getAsString() : null;
	}
}
