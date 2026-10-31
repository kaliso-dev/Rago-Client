package gg.rago.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.ModelBlock;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bed3DTest {
	/** A bed side drawn as vanilla's, {@code scale} pixels to a 16th: legs {@code leg} wide at the head's end (right) or foot's (left). */
	private static BufferedImage side(int scale, int leg, boolean head) {
		BufferedImage img = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 16 * scale; x++) {
			for (int y = 0; y < 16 * scale; y++) {
				int px = x / scale;
				int py = y / scale;
				boolean legs = head ? px >= 16 - leg : px < leg;
				if (py >= 7 && py <= 12 || py >= 13 && legs) {
					img.setRGB(x, y, 0xFF8B1A1A);
				}
			}
		}
		return img;
	}

	@Test
	void theLegsAreAsWideAsThePackDrawsThem() {
		assertEquals(3, Bed3D.legWidth(side(1, 3, true), true));
		assertEquals(3, Bed3D.legWidth(side(1, 3, false), false));
		assertEquals(2, Bed3D.legWidth(side(1, 2, true), true));
		assertEquals(3, Bed3D.legWidth(side(4, 3, true), true), "a 64x pack");
		assertEquals(Bed3D.LEG, Bed3D.legWidth(side(1, 0, true), true), "no legs drawn: vanilla's");
		assertEquals(Bed3D.LEG, Bed3D.legWidth(side(1, 12, true), true), "a full bottom row: vanilla's");
	}

	/** Minecraft reads both parts (a model it cannot read shows as a black and purple cube). */
	@Test
	void minecraftReadsTheModels() {
		for (boolean head : new boolean[] {true, false}) {
			JsonObject json = Bed3D.model(head, side(1, 3, head));
			ModelBlock model = ModelBlock.deserialize(json.toString());
			assertEquals(json.getAsJsonArray("elements").size(), model.getElements().size());
			for (BlockPart part : model.getElements()) {
				float[] from = {part.positionFrom.x, part.positionFrom.y, part.positionFrom.z};
				float[] to = {part.positionTo.x, part.positionTo.y, part.positionTo.z};
				for (int i = 0; i < 3; i++) {
					assertTrue(from[i] >= 0 && to[i] <= 16 && from[i] <= to[i]);
				}
			}
		}
	}

	@Test
	void everyFaceShowsPixelsOfItsTexture() {
		for (boolean head : new boolean[] {true, false}) {
			for (JsonElement e : Bed3D.model(head, null).getAsJsonArray("elements")) {
				for (Map.Entry<String, JsonElement> f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
					JsonArray uv = f.getValue().getAsJsonObject().getAsJsonArray("uv");
					for (JsonElement c : uv) {
						assertTrue(c.getAsDouble() >= 0 && c.getAsDouble() <= 16, f.getKey() + " " + uv);
					}
				}
			}
		}
	}

	/** The head has the pillow above the mattress; the foot, the blanket over its end. */
	@Test
	void theHeadHasThePillow() {
		assertTrue(highest(Bed3D.model(true, null)) >= Bed3D.TOP + 2);
		assertTrue(highest(Bed3D.model(false, null)) < Bed3D.TOP + 1);
	}

	private static double highest(JsonObject model) {
		double top = 0;
		for (JsonElement e : model.getAsJsonArray("elements")) {
			top = Math.max(top, e.getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble());
		}
		return top;
	}
}
