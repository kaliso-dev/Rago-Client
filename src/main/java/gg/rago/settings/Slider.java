package gg.rago.settings;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public final class Slider extends Setting {
	private final IntSupplier get;
	private final IntConsumer set;
	private final int min;
	private final int max;
	private final int step;
	private final IntFunction<String> format;

	public Slider(String id, Tab tab, String section, int min, int max, int step, IntFunction<String> format,
		IntSupplier get, IntConsumer set) {
		super(id, tab, section);
		this.get = get;
		this.set = set;
		this.min = min;
		this.max = max;
		this.step = Math.max(1, step);
		this.format = format;
		wideRow();
	}

	public int get() {
		return get.getAsInt();
	}

	public void set(int value) {
		int v = Math.max(min, Math.min(max, value));
		v = min + Math.round((v - min) / (float) step) * step;
		set.accept(Math.min(max, v));
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

	public String display() {
		return format.apply(get());
	}
}
