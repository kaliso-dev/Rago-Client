package gg.rago.hud.elements;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Hits landed in a row: a hit counts when the target shows the hurt animation shortly after you
 * hit it; the combo ends when you are hurt or after a few seconds (3 by default) without a hit.
 */
public final class ComboHud extends TextHudElement {
	private static final long CONFIRM_NS = 500_000_000L;
	private EntityLivingBase pending;
	private long pendingTime;
	private int combo;
	private long lastHit;
	private final IntOption expire = addOption(new IntOption("combo_expire", 3, 1, 10));
	private final IntOption minimum = addOption(new IntOption("combo_minimum", 1, 1, 5));

	public ComboHud() {
		super("combo", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 125);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@SubscribeEvent
	public void onAttack(AttackEntityEvent event) {
		Entity target = event.target;
		if (isEnabled() && event.entityPlayer == Minecraft.getMinecraft().thePlayer && target instanceof EntityLivingBase) {
			pending = (EntityLivingBase) target;
			pendingTime = System.nanoTime();
		}
	}

	@Override
	public void tick() {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		long now = System.nanoTime();
		if (p == null || p.hurtTime > 0 && p.hurtTime >= p.maxHurtTime - 1) {
			combo = 0;
		}
		if (pending != null) {
			if (pending.hurtTime > 0 && pending.hurtTime >= pending.maxHurtTime - 1) {
				combo++;
				lastHit = now;
				pending = null;
			} else if (now - pendingTime > CONFIRM_NS) {
				pending = null;
			}
		}
		if (combo > 0 && now - lastHit > expire.get() * 1_000_000_000L) {
			combo = 0;
		}
	}

	@Override
	public boolean hasContent() {
		return combo >= minimum.get();
	}

	@Override
	protected String label(boolean after) {
		return after ? "hits" : "Combo";
	}

	@Override
	protected String value(boolean placeholder) {
		int n = placeholder && combo == 0 ? 4 : combo;
		return Shown.of(n);
	}
}
