package gg.rago.network;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutTest {
	private static Loadout parse(String json) {
		return Loadout.fromJson(new JsonParser().parse(json));
	}

	@Test
	void nothingSentIsNothingShown() {
		assertEquals(Loadout.NONE.toJson(), parse("{}").toJson());
		assertSame(Loadout.NONE, Loadout.fromJson(new JsonPrimitive("cape")));
		assertSame(Loadout.NONE, Loadout.fromJson(null));
		assertTrue(Loadout.NONE.empty());
	}

	@Test
	void aLoadoutComesBackAsItWasSent() {
		Loadout l = new Loadout("rago", "blue", true, "crown", "pink", 0xFF123456, 130, "dragon_small", "green", 0xFFABCDEF, 60,
			false, 250, "frost", "white", 140, false);
		assertEquals(l.toJson(), Loadout.fromJson(l.toJson()).toJson());
		assertTrue(Loadout.fromJson(l.toJson()).capeOverOwn);
		assertFalse(l.empty());
		assertEquals("#123456", l.toJson().get("hat_color").getAsString());
	}

	@Test
	void whatIsNotAllowedFallsBackToItsDefault() {
		Loadout l = parse("{\"cape\": \"diamond\", \"cape_neon\": \"blue\", \"hat\": \"halo\", \"hat_scale\": 500,"
			+ " \"wings_scale\": 75.5, \"wings_flap\": \"yes\", \"hat_color\": \"red\", \"wings_color\": \"#-12345\","
			+ " \"shield\": 3, \"neon_glow\": false, \"extra\": 1}");
		assertEquals("none", l.cape);
		assertEquals("blue", l.capeNeon);
		assertEquals("halo", l.hat);
		assertEquals(100, l.hatScale);
		assertEquals(100, l.wingsScale);
		assertTrue(l.wingsFlap);
		assertEquals(Loadout.NO_TINT, l.hatColor);
		assertEquals(Loadout.NO_TINT, l.wingsColor);
		assertEquals("none", l.shield);
		assertFalse(l.neonGlow);
	}

	/** Older clients do not send it: their Rago cape only shows on players without another cape, as before. */
	@Test
	void theRagoCapeGoesOverAnotherOnlyWhenAsked() {
		assertFalse(parse("{\"cape\": \"rago\"}").capeOverOwn);
		assertFalse(parse("{\"cape\": \"rago\", \"cape_over_own\": \"yes\"}").capeOverOwn);
		assertTrue(parse("{\"cape\": \"rago\", \"cape_over_own\": true}").capeOverOwn);
	}

	@Test
	void coloursAreOpaqueWhicheverCaseTheyAreWrittenIn() {
		assertEquals(0xFFFF8800, parse("{\"hat_color\": \"#ff8800\"}").hatColor);
		assertEquals(0xFFFF8800, parse("{\"hat_color\": \"#FF8800\"}").hatColor);
		assertEquals("#FF8800", Loadout.hex(0x80FF8800));
	}

	@Test
	void sizesAtTheirLimitsAreKept() {
		Loadout l = parse("{\"hat_scale\": 70, \"wings_scale\": 160, \"wings_flap_speed\": 25, \"shield_scale\": 150}");
		assertEquals(70, l.hatScale);
		assertEquals(160, l.wingsScale);
		assertEquals(25, l.wingsFlapSpeed);
		assertEquals(150, l.shieldScale);
		assertEquals(100, parse("{\"hat_scale\": 69}").hatScale);
	}
}
