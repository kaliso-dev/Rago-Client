package gg.rago.config;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionTest {
	@Test
	void intOptionClamps() {
		IntOption o = new IntOption("n", 5, 1, 10);
		o.set(50);
		assertEquals(10, o.get());
		o.fromJson(new JsonPrimitive("text"));
		assertEquals(5, o.get());
	}

	@Test
	void colorParsesSixAndEightDigits() {
		ColorOption c = new ColorOption("c", 0xFFFFFFFF);
		c.fromJson(new JsonPrimitive("#00FF00"));
		assertEquals(0xFF00FF00, (int) c.get());
		c.fromJson(new JsonPrimitive("#8000FF00"));
		assertEquals(0x8000FF00, (int) c.get());
		c.fromJson(new JsonPrimitive("green"));
		assertEquals(0xFFFFFFFF, (int) c.get());
		assertEquals("#FFFFFFFF", c.toJson().getAsString());
	}

	@Test
	void colorCycleWalksPresets() {
		ColorOption c = new ColorOption("c", ColorOption.PRESETS[0]);
		c.cycle();
		assertEquals(ColorOption.PRESETS[1], (int) c.get());
		c.set(0x12345678);
		c.cycle();
		assertEquals(ColorOption.PRESETS[0], (int) c.get());
	}

	@Test
	void choiceKeepsKnownNamesOnly() {
		ChoiceOption c = new ChoiceOption("align", "left", "left", "center", "right");
		c.fromJson(new JsonPrimitive("center"));
		assertEquals("center", c.get());
		c.fromJson(new JsonPrimitive("diagonal"));
		assertEquals("left", c.get());
		c.set("nope");
		assertEquals("left", c.get());
		assertEquals("left", c.toJson().getAsString());
	}

	@Test
	void choiceSelectRunsActionAndCycleWraps() {
		StringBuilder picked = new StringBuilder();
		ChoiceOption c = new ChoiceOption("style", "a", "a", "b", "c").onSelect(picked::append);
		c.cycle(-1);
		assertEquals("c", c.get());
		c.cycle(1);
		assertEquals("a", c.get());
		c.set("b"); // loading or presets set quietly
		assertEquals("ca", picked.toString());
	}

	@Test
	void makeCurrentDefaultChangesReset() {
		IntOption o = new IntOption("n", 5, 1, 10);
		o.set(7);
		o.makeCurrentDefault();
		o.set(2);
		o.fromJson(new JsonPrimitive("bad"));
		assertEquals(7, o.get());
	}
}
