package gg.rago.settings;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** A value picked from a short list with ‹ › arrows; the values are translation keys. */
public final class Choice extends Setting {
	private final IntSupplier get;
	private final IntConsumer set;
	private final String[] valueKeys;

	public Choice(String id, Tab tab, String section, String[] valueKeys, IntSupplier get, IntConsumer set) {
		super(id, tab, section);
		this.get = get;
		this.set = set;
		this.valueKeys = valueKeys;
		wideRow();
	}

	public int index() {
		int i = get.getAsInt();
		return Math.max(0, Math.min(valueKeys.length - 1, i));
	}

	public void cycle(int dir) {
		set.accept(Math.floorMod(index() + dir, valueKeys.length));
	}

	public String valueKey() {
		return valueKeys[index()];
	}
}
