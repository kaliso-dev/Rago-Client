package gg.rago.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;

/**
 * Where the view bobs, when Minecraft's view bobbing is on, as two options that can be on together.
 * "Minimal": in first person the world no longer sways, only the hand and the item it holds bob
 * (minimal view bobbing). "Not in F5": in third person (F5, behind or in front) the camera no longer
 * sways. One check per frame, nothing else: the camera's bobbing is skipped, the hand's is Minecraft's
 * own.
 */
public final class NoF5Bobbing extends Module {
	private final BooleanOption minimal = addOption(new BooleanOption("bobbing_minimal", false));
	private final BooleanOption notInF5 = addOption(new BooleanOption("bobbing_not_f5", true));

	public NoF5Bobbing() {
		super("no_f5_bobbing", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	/** Keeps the choice saved by 1.11.2's first build, a single "bobbing_mode" ("f5" or "minimal"). */
	public void migrate(JsonObject options) {
		JsonElement old = options.get("bobbing_mode");
		if (!options.has(minimal.key()) && old != null && old.isJsonPrimitive() && "minimal".equals(old.getAsString())) {
			minimal.set(true);
		}
	}

	/** Whether the camera bobs (view bobbing on) in this view (0 = first person). */
	public boolean cameraBobs(int thirdPersonView) {
		return cameraBobs(isEnabled(), minimal.get(), notInF5.get(), thirdPersonView);
	}

	static boolean cameraBobs(boolean enabled, boolean minimal, boolean notInF5, int thirdPersonView) {
		if (!enabled) {
			return true;
		}
		return thirdPersonView == 0 ? !minimal : !notInF5;
	}
}
