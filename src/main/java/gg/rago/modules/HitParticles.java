package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.potion.Potion;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * More hit particles when you hit something: critical and sharpness particles multiplied, and
 * optionally critical or sharpness particles on every hit. Particles only exist on your screen.
 */
public final class HitParticles extends Module {
	private final IntOption multiplier = addOption(new IntOption("particle_multiplier", 3, 1, 10));
	private final BooleanOption alwaysCrit = addOption(new BooleanOption("always_crit", false));
	private final BooleanOption alwaysSharpness = addOption(new BooleanOption("always_sharpness", false));

	public HitParticles() {
		super("hit_particles", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@SubscribeEvent
	public void onAttack(AttackEntityEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayerSP p = mc.thePlayer;
		Entity target = event.target;
		// the event also fires on the integrated server's thread for its own copy of the player
		if (!isEnabled() || p == null || event.entityPlayer != p || target == null || !target.canAttackWithItem()) {
			return;
		}
		boolean crit = p.fallDistance > 0 && !p.onGround && !p.isOnLadder() && !p.isInWater()
			&& !p.isPotionActive(Potion.blindness) && p.ridingEntity == null && target instanceof EntityLivingBase;
		EnumCreatureAttribute attr = target instanceof EntityLivingBase
			? ((EntityLivingBase) target).getCreatureAttribute() : EnumCreatureAttribute.UNDEFINED;
		boolean sharp = EnchantmentHelper.getModifierForCreature(p.getHeldItem(), attr) > 0;
		int extra = multiplier.get() - 1;
		// vanilla already emits one of each it wants
		emit(mc, target, EnumParticleTypes.CRIT, crit ? extra : alwaysCrit.get() ? multiplier.get() : 0);
		emit(mc, target, EnumParticleTypes.CRIT_MAGIC, sharp ? extra : alwaysSharpness.get() ? multiplier.get() : 0);
	}

	private static void emit(Minecraft mc, Entity target, EnumParticleTypes type, int times) {
		for (int i = 0; i < times; i++) {
			mc.effectRenderer.emitParticleAtEntity(target, type);
		}
	}
}
