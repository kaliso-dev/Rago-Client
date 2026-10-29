package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityCrit2FX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.BlockPos;

/**
 * Particles by kind: block particles (breaking, hitting, landing and sprinting on blocks), hit
 * particles on players (critical and sharpness) and all other particles. Each kind can be hidden,
 * resized and tinted; block-breaking particles can be multiplied. Particles only exist on your
 * screen, so none of this changes the game.
 */
public final class Particles extends Module {
	private final BooleanOption blocks;
	private final IntOption blockSize;
	private final IntOption blockMultiplier;
	private final ColorOption blockColor;
	private final BooleanOption crits;
	private final IntOption critSize;
	private final ColorOption critColor;
	private final BooleanOption sharpness;
	private final IntOption sharpnessSize;
	private final ColorOption sharpnessColor;
	private final BooleanOption others;
	private final IntOption otherSize;
	private boolean multiplying;

	public Particles() {
		super("particles", FeatureClass.VISUAL, false);
		section("particles_blocks");
		blocks = addOption(new BooleanOption("particles_show", true));
		blockSize = addOption(new IntOption("particles_size", 100, 25, 300));
		blockMultiplier = addOption(new IntOption("particles_break_multiplier", 1, 1, 5));
		blockColor = addOption(new ColorOption("particles_color", 0xFFFFFFFF));
		section("particles_crit");
		crits = addOption(new BooleanOption("particles_crit_show", true));
		critSize = addOption(new IntOption("particles_crit_size", 100, 25, 300));
		critColor = addOption(new ColorOption("particles_crit_color", 0xFFFFFFFF));
		section("particles_sharpness");
		sharpness = addOption(new BooleanOption("particles_sharpness_show", true));
		sharpnessSize = addOption(new IntOption("particles_sharpness_size", 100, 25, 300));
		sharpnessColor = addOption(new ColorOption("particles_sharpness_color", 0xFFFFFFFF));
		section("particles_other");
		others = addOption(new BooleanOption("particles_other_show", true));
		otherSize = addOption(new IntOption("particles_other_size", 100, 25, 300));
		section(null);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** Called as a particle is added; returns false to drop it. */
	public boolean onAdded(EntityFX fx) {
		if (fx instanceof EntityDiggingFX) {
			return style(fx, blocks, blockSize, blockColor);
		}
		if (fx instanceof EntityCrit2FX) {
			// sharpness particles are crits tinted blue by their factory
			boolean magic = fx.getBlueColorF() > fx.getRedColorF() * 1.5f;
			return magic ? style(fx, sharpness, sharpnessSize, sharpnessColor) : style(fx, crits, critSize, critColor);
		}
		if (!others.get()) {
			return false;
		}
		if (otherSize.get() != 100) {
			fx.multipleParticleScaleBy(otherSize.get() / 100f);
		}
		return true;
	}

	private static boolean style(EntityFX fx, BooleanOption show, IntOption size, ColorOption color) {
		if (!show.get()) {
			return false;
		}
		if (size.get() != 100) {
			fx.multipleParticleScaleBy(size.get() / 100f);
		}
		int c = color.get();
		if (c != 0xFFFFFFFF) {
			// a tint, so block textures keep their pattern
			fx.setRBGColorF(fx.getRedColorF() * (c >> 16 & 0xFF) / 255f,
				fx.getGreenColorF() * (c >> 8 & 0xFF) / 255f,
				fx.getBlueColorF() * (c & 0xFF) / 255f);
		}
		return true;
	}

	/** After a block's breaking particles: adds the extra rounds. */
	public void afterBlockBroken(EffectRenderer renderer, BlockPos pos, IBlockState state) {
		int extra = blockMultiplier.get() - 1;
		if (extra <= 0 || multiplying || !blocks.get()) {
			return;
		}
		multiplying = true;
		try {
			for (int i = 0; i < extra; i++) {
				renderer.addBlockDestroyEffects(pos, state);
			}
		} finally {
			multiplying = false;
		}
	}
}
