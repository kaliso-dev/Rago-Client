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
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * More hit particles when you hit something: critical and sharpness particles multiplied, and
 * optionally critical or sharpness particles on every hit; and no particles from blocks being
 * broken or mined, if you like. Particles only exist on your screen.
 */
public final class HitParticles extends Module {
	/** Tenths: 10 is the game's own amount, 15 one and a half times as many. */
	private final IntOption multiplier = addOption(new IntOption("particle_amount", 20, 10, 50).decimals(1));
	private final BooleanOption alwaysCrit = addOption(new BooleanOption("always_crit", false));
	private final BooleanOption alwaysSharpness = addOption(new BooleanOption("always_sharpness", false));
	private final BooleanOption hideBlockBreak = addOption(new BooleanOption("hide_block_break", false));
	private final BooleanOption hideBlockHit = addOption(new BooleanOption("hide_block_hit", false));
	private static final int BURST_PER_TICK = 16;
	private static final int BURST_TICKS = 3;
	private final List<Burst> bursts = new ArrayList<>();
	private final Random random = new Random();

	public HitParticles() {
		super("hit_particles", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	public boolean hideBlockBreak() {
		return hideBlockBreak.get();
	}

	public boolean hideBlockHit() {
		return hideBlockHit.get();
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
		float times = multiplier.get() / 10f;
		// vanilla already emits one burst of each it wants
		emit(target, EnumParticleTypes.CRIT, crit ? times - 1 : alwaysCrit.get() ? times : 0);
		emit(target, EnumParticleTypes.CRIT_MAGIC, sharp ? times - 1 : alwaysSharpness.get() ? times : 0);
	}

	/** A burst like the game's own (16 particles a tick for 3 ticks), {@code times} as big. */
	private void emit(Entity target, EnumParticleTypes type, float times) {
		if (times > 0) {
			bursts.add(new Burst(target, type, times * BURST_PER_TICK));
		}
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || bursts.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			bursts.clear();
			return;
		}
		for (Iterator<Burst> it = bursts.iterator(); it.hasNext(); ) {
			Burst b = it.next();
			// a fraction of a particle carries over to the next tick, so 1.1 really means 10 % more
			b.owed += b.perTick;
			int n = (int) b.owed;
			b.owed -= n;
			Entity e = b.target;
			for (int i = 0; i < n; i++) {
				double dx = random.nextFloat() * 2 - 1;
				double dy = random.nextFloat() * 2 - 1;
				double dz = random.nextFloat() * 2 - 1;
				if (dx * dx + dy * dy + dz * dz > 1) {
					continue;
				}
				double x = e.posX + dx * e.width / 4;
				double y = e.getEntityBoundingBox().minY + e.height / 2 + dy * e.height / 4;
				double z = e.posZ + dz * e.width / 4;
				mc.theWorld.spawnParticle(b.type, false, x, y, z, dx, dy + 0.2, dz);
			}
			if (++b.ticks >= BURST_TICKS || e.isDead) {
				it.remove();
			}
		}
	}

	private static final class Burst {
		final Entity target;
		final EnumParticleTypes type;
		final float perTick;
		float owed;
		int ticks;

		Burst(Entity target, EnumParticleTypes type, float perTick) {
			this.target = target;
			this.type = type;
			this.perTick = perTick;
		}
	}
}
