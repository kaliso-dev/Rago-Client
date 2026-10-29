package gg.rago.hud.elements;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;

/** The distance of your last hit, from your eyes to where your crosshair met the target. */
public final class ReachHud extends TextHudElement {
	private static final long SHOW_NS = 3_000_000_000L;
	private double reach;
	private long time;

	public ReachHud() {
		super("reach", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 114);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@SubscribeEvent
	public void onAttack(AttackEntityEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayerSP p = mc.thePlayer;
		MovingObjectPosition hit = mc.objectMouseOver;
		if (!isEnabled() || p == null || event.entityPlayer != p || hit == null
			|| hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || hit.entityHit != event.target || hit.hitVec == null) {
			return;
		}
		reach = hit.hitVec.distanceTo(p.getPositionEyes(1f));
		time = System.nanoTime();
	}

	@Override
	public boolean hasContent() {
		return time != 0 && System.nanoTime() - time < SHOW_NS;
	}

	@Override
	protected String text(boolean placeholder) {
		return String.format(Locale.ROOT, "%.2f blocks", placeholder && !hasContent() ? 2.94 : reach);
	}
}
