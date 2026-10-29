package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;

/**
 * Hit delay fix: in 1.8, a left click that hits nothing (air, or out of reach) blocks the next
 * attack for 10 ticks (half a second). With this on, the next click can attack straight away.
 * Each attack is still one click and one normal attack packet; nothing about what is sent changes,
 * only that a missed click no longer locks the attack key. Off by default: check the rules of the
 * server you play on.
 */
public final class HitDelayFix extends Module {
	public HitDelayFix() {
		super("hit_delay_fix", FeatureClass.INPUT, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	/** Ticks the attack key stays locked after a missed click (vanilla 10). */
	public int missDelay(int vanilla) {
		return 0;
	}
}
