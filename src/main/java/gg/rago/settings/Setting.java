package gg.rago.settings;

import java.util.function.BooleanSupplier;

/**
 * One row on the settings page. Rago's own settings are stored in {@code config/rago.json};
 * settings that mirror Minecraft or OptiFine read and write the game's own fields, so they stay in
 * {@code options.txt} / {@code optionsof.txt}.
 */
public abstract class Setting {
	public enum Tab { GENERAL, PERFORMANCE, CONTROLS }

	private final String id;
	private final Tab tab;
	private final String section;
	private BooleanSupplier visible = () -> true;
	private boolean wide;

	protected Setting(String id, Tab tab, String section) {
		this.id = id;
		this.tab = tab;
		this.section = section;
	}

	public final String id() {
		return id;
	}

	public final Tab tab() {
		return tab;
	}

	/** Translation key of the section header this row sits under. */
	public final String section() {
		return section;
	}

	public String labelKey() {
		return "rago.setting." + id;
	}

	/** Optional one-line help shown under the label; its translation key. */
	public String helpKey() {
		return "rago.setting." + id + ".help";
	}

	public final boolean visible() {
		return visible.getAsBoolean();
	}

	public final Setting visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
		return this;
	}

	/** Sliders and choices take the full width; toggles sit two per row. */
	public boolean wide() {
		return wide;
	}

	public final Setting wideRow() {
		this.wide = true;
		return this;
	}
}
