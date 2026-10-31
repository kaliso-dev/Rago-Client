package gg.rago.hud.elements;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;


/** The distance of your last hit, from your eyes to where your crosshair met the target. */
public final class ReachHud extends TextHudElement {
	/** The text shown, made again only when it changes. */
	private final Shown shown = new Shown();
	private final IntOption decimals = addOption(new IntOption("decimals", 2, 1, 3));
	private final IntOption showFor = addOption(new IntOption("show_seconds", 3, 1, 15));
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
		return time != 0 && System.nanoTime() - time < showFor.get() * 1_000_000_000L;
	}

	@Override
	protected String label(boolean after) {
		return after ? "blocks" : "Reach";
	}

	@Override
	protected String value(boolean placeholder) {
		double v = placeholder && !hasContent() ? 2.94 : reach;
		int d = decimals.get();
		if (!shown.same(Shown.key(v, d), d, 0)) {
			shown.set(Shown.fixed(v, d));
		}
		return shown.text();
	}
}
