package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.Pack3D;

/**
 * Blocks drawn in 3D without a resource pack: ladders with rails and rungs that stand off the
 * wall, made from the ladder texture of your pack, and beds with real legs, a frame, a raised pillow
 * and a folded blanket, in your pack's bed textures. Only changes how they look: the hitbox and the
 * game are vanilla's. Turning it on or off reloads the resources once.
 */
public final class Items3D extends Module {
	private final BooleanOption ladder = addOption(new BooleanOption("items_3d_ladder", true));
	private final BooleanOption bed = addOption(new BooleanOption("items_3d_bed", true));

	public Items3D() {
		super("items_3d", FeatureClass.VISUAL, false);
		Pack3D.wants(() -> isEnabled() && ladder.get(), () -> isEnabled() && bed.get());
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	@Override
	protected void onEnable() {
		Pack3D.sync();
	}

	@Override
	protected void onDisable() {
		Pack3D.sync();
	}

	@Override
	public void tick() {
		// the ladder or bed option turned on or off
		Pack3D.sync();
	}
}
