package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;

/**
 * 1.7 animations: the sword swings while you block-hit, armour turns red with the player when hit,
 * and the arm swings when you click while using an item. All three only change how your own
 * client draws things; the swing while using an item is not sent to the server.
 */
public final class OldAnimations extends Module {
	private final BooleanOption blockHit = addOption(new BooleanOption("block_hit", true));
	private final BooleanOption armorTint = addOption(new BooleanOption("armor_damage_tint", true));
	private final BooleanOption swingWhileUsing = addOption(new BooleanOption("swing_while_using", true));

	public OldAnimations() {
		super("old_animations", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	public boolean blockHit() {
		return blockHit.get();
	}

	public boolean armorTint() {
		return armorTint.get();
	}

	@Override
	public void tick() {
		if (!swingWhileUsing.get()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayerSP p = mc.thePlayer;
		MovingObjectPosition target = mc.objectMouseOver;
		if (p == null || mc.currentScreen != null || !p.isUsingItem() || !mc.gameSettings.keyBindAttack.isKeyDown()
			|| target == null || target.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
			return;
		}
		// what EntityLivingBase.swingItem does locally, without the packet EntityPlayerSP adds
		if (!p.isSwingInProgress || p.swingProgressInt >= 3 || p.swingProgressInt < 0) {
			p.swingProgressInt = -1;
			p.isSwingInProgress = true;
		}
	}
}
