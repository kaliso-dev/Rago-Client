package gg.rago.core;

/** Where a module is listed in the Rago menu. Unlike {@link FeatureClass}, this is only presentation. */
public enum Category {
	HUD, PVP, HYPIXEL, VISUAL, FIXES, PERFORMANCE;

	public String key() {
		return "rago.category." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
