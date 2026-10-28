package gg.rago.core;

import gg.rago.config.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A feature that can be switched on and off and has its own options. Subclasses add
 * their options in the constructor with {@link #addOption}.
 */
public abstract class Module {
	private final String id;
	private final FeatureClass featureClass;
	private final boolean defaultEnabled;
	private final List<Option<?>> options = new ArrayList<>();
	private boolean enabled;

	protected Module(String id, FeatureClass featureClass, boolean defaultEnabled) {
		if (!id.matches("[a-z0-9_]+")) {
			throw new IllegalArgumentException("Module id must be lowercase letters, digits or _: " + id);
		}
		this.id = id;
		this.featureClass = featureClass;
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
	}

	public final String id() {
		return id;
	}

	public final FeatureClass featureClass() {
		return featureClass;
	}

	public final boolean defaultEnabled() {
		return defaultEnabled;
	}

	/** Menu category; HUD elements default to {@link Category#HUD}, everything else to visual. */
	public Category category() {
		return Category.VISUAL;
	}

	/** Translation key of the display name. */
	public String nameKey() {
		return "rago.module." + id;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public final List<Option<?>> options() {
		return Collections.unmodifiableList(options);
	}

	protected final <T extends Option<?>> T addOption(T option) {
		for (Option<?> o : options) {
			if (o.key().equals(option.key())) {
				throw new IllegalArgumentException("Duplicate option '" + option.key() + "' in module " + id);
			}
		}
		options.add(option);
		return option;
	}

	/** Restores the enabled state and every option to its default. */
	public void reset() {
		setEnabled(defaultEnabled);
		options.forEach(Option::reset);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called once per client tick while enabled. */
	public void tick() {
	}
}
