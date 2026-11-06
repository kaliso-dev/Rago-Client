package gg.rago.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.config.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoF5BobbingTest {
	private static final int FIRST = 0;
	private static final int BEHIND = 1;
	private static final int FRONT = 2;

	@Test
	void offChangesNothing() {
		assertTrue(NoF5Bobbing.cameraBobs(false, true, true, FIRST));
		assertTrue(NoF5Bobbing.cameraBobs(false, true, true, BEHIND));
	}

	@Test
	void notInF5StopsOnlyThirdPerson() {
		assertTrue(NoF5Bobbing.cameraBobs(true, false, true, FIRST));
		assertFalse(NoF5Bobbing.cameraBobs(true, false, true, BEHIND));
		assertFalse(NoF5Bobbing.cameraBobs(true, false, true, FRONT));
	}

	@Test
	void minimalStopsOnlyFirstPerson() {
		assertFalse(NoF5Bobbing.cameraBobs(true, true, false, FIRST));
		assertTrue(NoF5Bobbing.cameraBobs(true, true, false, BEHIND));
	}

	@Test
	void bothTogether() {
		assertFalse(NoF5Bobbing.cameraBobs(true, true, true, FIRST));
		assertFalse(NoF5Bobbing.cameraBobs(true, true, true, BEHIND));
		assertFalse(NoF5Bobbing.cameraBobs(true, true, true, FRONT));
	}

	@Test
	void keepsTheMinimalChoiceSavedBefore() {
		assertEquals(true, minimalAfter("{\"bobbing_mode\":\"minimal\"}"));
		assertEquals(false, minimalAfter("{\"bobbing_mode\":\"f5\"}"));
		assertEquals(false, minimalAfter("{\"bobbing_mode\":\"minimal\",\"bobbing_minimal\":false}"));
	}

	private static Object minimalAfter(String json) {
		NoF5Bobbing m = new NoF5Bobbing();
		JsonObject options = new JsonParser().parse(json).getAsJsonObject();
		for (Option<?> o : m.options()) {
			if (options.has(o.key())) {
				o.fromJson(options.get(o.key()));
			}
		}
		m.migrate(options);
		for (Option<?> o : m.options()) {
			if (o.key().equals("bobbing_minimal")) {
				return o.get();
			}
		}
		throw new AssertionError("no bobbing_minimal option");
	}
}
