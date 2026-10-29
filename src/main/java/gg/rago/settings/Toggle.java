package gg.rago.settings;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class Toggle extends Setting {
	private final BooleanSupplier get;
	private final Consumer<Boolean> set;

	public Toggle(String id, Tab tab, String section, BooleanSupplier get, Consumer<Boolean> set) {
		super(id, tab, section);
		this.get = get;
		this.set = set;
	}

	public boolean get() {
		return get.getAsBoolean();
	}

	public void set(boolean value) {
		set.accept(value);
	}
}
