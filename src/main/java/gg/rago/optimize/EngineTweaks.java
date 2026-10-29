package gg.rago.optimize;

import java.util.function.BooleanSupplier;

/** Engine settings read by game hooks, from the Performance settings. */
public final class EngineTweaks {
	private final BooleanSupplier fastWorldSwitch;
	private final BooleanSupplier unstackedItems;

	EngineTweaks(BooleanSupplier fastWorldSwitch, BooleanSupplier unstackedItems) {
		this.fastWorldSwitch = fastWorldSwitch;
		this.unstackedItems = unstackedItems;
	}

	/** Skip the full garbage collection vanilla forces on every world change (the freeze when switching servers). */
	public boolean fastWorldSwitch() {
		return fastWorldSwitch.getAsBoolean();
	}

	/** Draw one copy of a dropped stack instead of up to five (generators in Bed Wars). */
	public boolean unstackedItems() {
		return unstackedItems.getAsBoolean();
	}
}
