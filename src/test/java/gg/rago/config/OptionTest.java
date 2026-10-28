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
}
