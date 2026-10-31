package gg.rago.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ladder3DTest {
	/** A ladder drawn like the usual ones, {@code scale} pixels to a 16th: rails at 2-3 and 12-13, four rungs reaching 1 to 14. */
	private static BufferedImage ladder(int scale) {
		BufferedImage img = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 16 * scale; x++) {
			for (int y = 0; y < 16 * scale; y++) {
				int px = x / scale;
				int py = y / scale;
				boolean rail = px >= 2 && px <= 3 || px >= 12 && px <= 13;
				boolean rung = py % 4 == 1 || py % 4 == 2;
				if (rail || rung && px >= 1 && px <= 14) {
					img.setRGB(x, y, 0xFF8B5A2B);
				}
			}
		}
		return img;
	}

	private static double[] box(JsonObject element) {
		JsonArray from = element.getAsJsonArray("from");
		JsonArray to = element.getAsJsonArray("to");
		return new double[] {from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
			to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()};
	}

	@Test
	void railsAndRungsComeFromTheTexture() {
		JsonArray elements = Ladder3D.model(ladder(1)).getAsJsonArray("elements");
		assertEquals(2 + 4, elements.size());
		// x runs the other way to the texture on the north side: columns 2-3 are x 12 to 14
		double[] rail = box(elements.get(0).getAsJsonObject());
		assertEquals(12, rail[0], 1e-9);
		assertEquals(14, rail[3], 1e-9);
		assertEquals(0, rail[1], 1e-9);
		assertEquals(16, rail[4], 1e-9);
		assertEquals(16 - Ladder3D.RAIL_DEPTH, rail[2], 1e-9, "as deep as the ladder's hitbox");
		assertEquals(16, rail[5], 1e-9);
		// the top rung is rows 1-2: y 13 to 15, from x 1 to 15 (columns 1 to 14)
		double[] rung = box(elements.get(2).getAsJsonObject());
		assertEquals(13, rung[1], 1e-9);
		assertEquals(15, rung[4], 1e-9);
		assertEquals(1, rung[0], 1e-9);
		assertEquals(15, rung[3], 1e-9);
		assertTrue(rung[2] > rail[2] && rung[5] < rail[5], "rungs between the rails' front and the wall");
	}

	@Test
	void aHighResolutionPackGivesTheSameShape() {
		assertEquals(Ladder3D.model(ladder(1)).toString(), Ladder3D.model(ladder(4)).toString());
	}

	@Test
	void aTextureWithoutRailsStaysFlat() {
		BufferedImage dots = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < 16; i += 2) {
			dots.setRGB(i, i, 0xFFFFFFFF);
		}
		assertNull(Ladder3D.model(dots));
		assertEquals(1, Ladder3D.flat().getAsJsonArray("elements").size());
	}
}
