package gg.rago.overlays;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Packs' custom skies read as OptiFine reads them, and overlay packs told apart from full packs. */
class PackSkyTest {
	private static final String DIR = "mcpatcher/sky/world0/";

	@Test
	void timesOfDay() {
		assertEquals(0, PackSky.time("6:00"));
		assertEquals(12000, PackSky.time("18:00"));
		assertEquals(18000, PackSky.time("0:00"));
		assertEquals(6500, PackSky.time("12:30"));
		assertEquals(-1, PackSky.time("25:00"));
		assertEquals(-1, PackSky.time("noon"));
		assertEquals(-1, PackSky.time(null));
	}

	@Test
	void nightLayerFadesInAndOut() {
		Properties p = new Properties();
		p.setProperty("startFadeIn", "17:00");
		p.setProperty("endFadeIn", "18:00");
		p.setProperty("endFadeOut", "6:00");
		PackSky.Layer l = PackSky.parse(p, DIR, "sky1.png", "mcpatcher");
		assertEquals(new ResourceLocation("minecraft", "mcpatcher/sky/world0/sky1.png"), l.source);
		assertEquals(0.5f, l.fade(PackSky.time("17:30")), 0.01f);
		assertEquals(1f, l.fade(PackSky.time("0:00")), 1e-6f);
		assertEquals(0f, l.fade(PackSky.time("12:00")), 1e-6f);
		// fade-out as long as the fade-in, ending at 6:00
		assertEquals(0.5f, l.fade(PackSky.time("5:30")), 0.01f);
		assertEquals(PackSky.ADD, l.blend);
		assertTrue(l.rotate);
	}

	@Test
	void noTimesMeansAlwaysAndBadTimesAreSkipped() {
		Properties always = new Properties();
		always.setProperty("blend", "alpha");
		always.setProperty("rotate", "false");
		PackSky.Layer l = PackSky.parse(always, DIR, "sky1.png", "mcpatcher");
		assertTrue(l.always);
		assertEquals(PackSky.ALPHA, l.blend);
		assertFalse(l.rotate);
		assertEquals(1f, l.fade(9000), 1e-6f);

		Properties bad = new Properties();
		bad.setProperty("startFadeIn", "17:00");
		bad.setProperty("endFadeIn", "18:00");
		assertNull(PackSky.parse(bad, DIR, "sky1.png", "mcpatcher"), "no end of fade-out: OptiFine skips it");
	}

	@Test
	void axisAndWeather() {
		Properties p = new Properties();
		p.setProperty("axis", "0 1 0");
		p.setProperty("weather", "rain thunder");
		PackSky.Layer l = PackSky.parse(p, DIR, "sky2.png", "mcpatcher");
		assertEquals(Arrays.toString(new float[] {0, 1, -0f}), Arrays.toString(l.axis));
		assertFalse(l.clear);
		assertEquals(1f, l.weather(1, 1), 1e-6f);
		assertEquals(0f, l.weather(0, 0), 1e-6f);
	}

	@Test
	void picturePaths() {
		assertEquals(new ResourceLocation("minecraft", "mcpatcher/sky/world0/stars.png"), PackSky.source("./stars.png", DIR, "mcpatcher"));
		assertEquals(new ResourceLocation("minecraft", "optifine/sky/clouds.png"), PackSky.source("~/sky/clouds.png", "optifine/sky/world0/", "optifine"));
		assertEquals(new ResourceLocation("minecraft", "textures/environment/sun.png"), PackSky.source("/textures/environment/sun.png", DIR, "mcpatcher"));
		assertEquals(new ResourceLocation("mypack", "sky/a.png"), PackSky.source("mypack:sky/a.png", DIR, "mcpatcher"));
	}

	@Test
	void overlaysAreToldApartFromFullPacks() {
		EnumSet<UserOverlays.Part> parts = EnumSet.noneOf(UserOverlays.Part.class);
		List<String> wool = Arrays.asList("pack.mcmeta", "pack.png", "assets/minecraft/textures/blocks/wool_colored_red.png",
			"assets/minecraft/textures/blocks/wool_colored_blue.png", "assets/minecraft/textures/blocks/hardened_clay_stained_red.png");
		assertEquals(0, UserOverlays.classify(wool, parts));
		assertEquals(EnumSet.of(UserOverlays.Part.WOOL, UserOverlays.Part.CLAY), parts);

		parts.clear();
		List<String> sky = Arrays.asList("assets/minecraft/mcpatcher/sky/world0/sky1.properties", "assets/minecraft/mcpatcher/sky/world0/sky1.png");
		assertEquals(0, UserOverlays.classify(sky, parts));
		assertEquals(EnumSet.of(UserOverlays.Part.SKY), parts);

		parts.clear();
		List<String> sword = Arrays.asList("assets/minecraft/textures/items/diamond_sword.png", "assets/minecraft/models/item/diamond_sword.json");
		UserOverlays.classify(sword, parts);
		assertEquals(EnumSet.of(UserOverlays.Part.SWORD), parts);

		parts.clear();
		List<String> full = new java.util.ArrayList<>(wool);
		for (int i = 0; i < 200; i++) {
			full.add("assets/minecraft/textures/blocks/stone_" + i + ".png");
		}
		assertTrue(UserOverlays.classify(full, parts) > UserOverlays.MAX_OTHER, "a full pack has much more than wool");
	}
}
