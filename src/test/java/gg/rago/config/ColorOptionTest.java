package gg.rago.config;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorOptionTest {
	@Test
	void fixedAlphaKeepsOpacityWhateverIsSetOrLoaded() {
		ColorOption c = new ColorOption("c", 0x40000000).fixedAlpha(0x40);
		c.set(0xFFF2392C);
		assertEquals(0x40F2392C, (int) c.get());
		c.fromJson(new JsonPrimitive("#00123456"));
		assertEquals(0x40123456, (int) c.get());
	}
}
