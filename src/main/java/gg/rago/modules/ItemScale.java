package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Item sizes: the item in your hand (first person) and items lying on the ground, each from small
 * to huge. Only changes how your client draws them.
 */
public final class ItemScale extends Module {
	private final IntOption held = addOption(new IntOption("held_item_scale", 100, 25, 400));
	private final IntOption dropped = addOption(new IntOption("dropped_item_scale", 100, 25, 400));

	public ItemScale() {
		super("item_scale", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** Called at the end of ItemRenderer.transformFirstPersonItem. */
	public void applyHeld() {
		scale(held.get());
	}

	/** Called at the end of RenderEntityItem's transform, before the item model is drawn. */
	public void applyDropped() {
		scale(dropped.get());
	}

	private static void scale(int percent) {
		if (percent != 100) {
			float f = percent / 100f;
			GlStateManager.scale(f, f, f);
		}
	}
}
