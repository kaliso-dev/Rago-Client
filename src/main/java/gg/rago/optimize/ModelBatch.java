package gg.rago.optimize;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * PolyPatcher's Batch Model Rendering. Each part of a mob, player or block entity model
 * (ModelRenderer) is drawn from a display list made the first time it is drawn; the game puts in it
 * one draw per face of each box, six per box. Here the faces of all its boxes go into one draw, the
 * same vertices in the same order and format: the model looks the same and the list is faster to
 * replay. Not done with OptiFine's shaders (their own vertex format), with OptiFine's custom model
 * sprites, or for boxes of another kind than the game's (a mod's own boxes draw themselves).
 */
public final class ModelBatch {
	private static volatile BooleanSupplier setting = () -> false;
	private static boolean batching;
	private static Field sprites;
	private static boolean spritesLookedUp;

	private ModelBatch() {
	}

	public static void setSwitch(BooleanSupplier on) {
		setting = on;
	}

	/** ModelRenderer.compileDisplayList, once its list is open: one draw for all its boxes when it can. */
	public static void start(ModelRenderer part) {
		if (batching || !setting.getAsBoolean() || part.cubeList.isEmpty() || !plainBoxes(part.cubeList) || hasSprites(part)
			|| OptiFineFields.shadersOn()) {
			return;
		}
		try {
			Tessellator.getInstance().getWorldRenderer().begin(7, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
		} catch (IllegalStateException e) {
			return; // something else is being drawn: leave the faces to themselves
		}
		batching = true;
	}

	/** ModelRenderer.compileDisplayList, before its list closes: the batch is drawn into the list. */
	public static void end() {
		if (batching) {
			batching = false;
			Tessellator.getInstance().draw();
		}
	}

	/** TexturedQuad.draw: begins drawing its face, unless the face goes into the open batch. */
	public static void begin(WorldRenderer renderer, int mode, VertexFormat format) {
		if (!batching) {
			renderer.begin(mode, format);
		}
	}

	/** TexturedQuad.draw: draws its face, unless the face goes into the open batch. */
	public static void draw(Tessellator tessellator) {
		if (!batching) {
			tessellator.draw();
		}
	}

	static boolean plainBoxes(List<ModelBox> boxes) {
		for (ModelBox box : boxes) {
			if (box == null || box.getClass() != ModelBox.class) {
				return false;
			}
		}
		return true;
	}

	/** Whether OptiFine's custom entity model sprites are on the part (drawn with their own format). */
	private static boolean hasSprites(ModelRenderer part) {
		if (!spritesLookedUp) {
			spritesLookedUp = true;
			try {
				Field f = ModelRenderer.class.getDeclaredField("spriteList");
				f.setAccessible(true);
				sprites = f;
			} catch (ReflectiveOperationException | RuntimeException e) {
				sprites = null; // the game's own ModelRenderer: no sprites
			}
		}
		if (sprites == null) {
			return false;
		}
		try {
			Object list = sprites.get(part);
			return list instanceof List && !((List<?>) list).isEmpty();
		} catch (IllegalAccessException e) {
			return true;
		}
	}
}
