package gg.rago.core;

import gg.rago.config.BooleanOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRegistryTest {
	static final class TestModule extends Module {
		final BooleanOption flag = addOption(new BooleanOption("flag", false));
		int ticks;
		int enables;

		TestModule(String id, FeatureClass featureClass, boolean defaultEnabled) {
			super(id, featureClass, defaultEnabled);
		}

		@Override
		protected void onEnable() {
			enables++;
		}

		@Override
		public void tick() {
			ticks++;
		}
	}

	@Test
	void packetModulesAreRefused() {
		ModuleRegistry r = new ModuleRegistry();
		assertThrows(IllegalArgumentException.class, () -> r.register(new TestModule("x", FeatureClass.PACKET, false)));
	}

	@Test
	void inputModulesMustDefaultOff() {
		ModuleRegistry r = new ModuleRegistry();
		assertThrows(IllegalArgumentException.class, () -> r.register(new TestModule("sprint", FeatureClass.INPUT, true)));
		r.register(new TestModule("sprint", FeatureClass.INPUT, false));
	}

	@Test
	void duplicateIdsAreRefused() {
		ModuleRegistry r = new ModuleRegistry();
		r.register(new TestModule("a", FeatureClass.VISUAL, true));
		assertThrows(IllegalArgumentException.class, () -> r.register(new TestModule("a", FeatureClass.VISUAL, true)));
	}

	@Test
	void invalidIdIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> new TestModule("Bad Id", FeatureClass.VISUAL, true));
	}

	@Test
	void onlyEnabledModulesTick() {
		ModuleRegistry r = new ModuleRegistry();
		TestModule on = r.register(new TestModule("on", FeatureClass.VISUAL, true));
		TestModule off = r.register(new TestModule("off", FeatureClass.VISUAL, false));
		r.tick();
		assertEquals(1, on.ticks);
		assertEquals(0, off.ticks);
	}

	@Test
	void resetRestoresDefaults() {
		TestModule m = new TestModule("m", FeatureClass.VISUAL, false);
		m.setEnabled(true);
		m.flag.set(true);
		m.reset();
		assertFalse(m.isEnabled());
		assertFalse(m.flag.get());
	}

	@Test
	void enableHookRunsOnlyOnChange() {
		TestModule m = new TestModule("m", FeatureClass.VISUAL, false);
		m.setEnabled(true);
		m.setEnabled(true);
		assertTrue(m.isEnabled());
		assertEquals(1, m.enables);
	}
}
