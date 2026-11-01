package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.config.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NametagsTest {
	private static IntOption option(Nametags m, String key) {
		for (Option<?> o : m.options()) {
			if (o.key().equals(key)) {
				return (IntOption) o;
			}
		}
		throw new AssertionError("no option " + key);
	}

	/** Vanilla's 25 % is its 0x40 black; the ends of the range are clear and solid. */
	@Test
	void backgroundOpacityMatchesVanilla() {
		assertEquals(0x40, Nametags.backgroundAlpha(25));
		assertEquals(0, Nametags.backgroundAlpha(0));
		assertEquals(0xFF, Nametags.backgroundAlpha(100));
		assertEquals(25, option(new Nametags(), "tag_background_opacity").get());
	}

	/**
	 * Through walls, no background opacity shows a tag more than vanilla's does, and where the
	 * player is in sight the faint copy and the rest add up to the chosen opacity.
	 */
	@Test
	void throughWallsNeverMoreThanVanilla() {
		float wall = 0.5f;
		float vanilla = Nametags.wallAlpha(Nametags.backgroundAlpha(25) / 255f, wall);
		for (int percent = 0; percent <= 100; percent++) {
			float a = Nametags.backgroundAlpha(percent) / 255f;
			float faint = Nametags.wallAlpha(a, wall);
			assertTrue(faint <= vanilla + 1e-6f, percent + " %");
			float rest = Nametags.restAlpha(a, faint);
			assertEquals(a, 1 - (1 - faint) * (1 - rest), 1e-5f, percent + " %");
		}
		assertEquals(0, Nametags.wallAlpha(1, 0), 0);
	}

	/** The tag moves only down, a fifth of a block at most, and stays at most a quarter bigger. */
	@Test
	void placeAndSizeStayLegit() {
		Nametags m = new Nametags();
		IntOption lower = option(m, "nametag_lower");
		assertEquals(0, lower.min());
		assertEquals(20, lower.max());
		assertEquals(0, lower.get());
		assertEquals(0, m.lowerBy(), 0); // off: Level Head keeps vanilla's place
		assertEquals(125, option(m, "nametag_scale").max());
	}
}
