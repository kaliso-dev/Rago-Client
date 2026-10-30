package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Block edit fix. In 1.8, a right click does nothing while the game thinks you are breaking a
 * block, and within one tick the left click is handled first: a left click on a block in the same
 * tick as your right click (clicking fast while you switch to wool) starts breaking it, and the
 * wool is placed one to four ticks later, or not at all while you keep clicking. With this on,
 * when both buttons act in the same tick with a block in hand, the right click is handled first.
 * Both clicks still happen, once each and as vanilla sends them; only their order within the
 * tick changes, and nothing is placed faster than vanilla's own right click allows. Off by
 * default: check the rules of the server you play on.
 */
public final class BlockEditFix extends Module {
	private static Field rightClickDelay;
	private static Method rightClick;
	private static boolean broken;

	public BlockEditFix() {
		super("block_edit_fix", FeatureClass.INPUT, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	/** Head of Minecraft.clickMouse: places the block first when the right click also acts this tick. */
	public void beforeLeftClick() {
		Minecraft mc = Minecraft.getMinecraft();
		if (broken || mc.thePlayer == null || mc.playerController == null || mc.thePlayer.isUsingItem()
			|| mc.playerController.getIsHittingBlock()) {
			return;
		}
		MovingObjectPosition over = mc.objectMouseOver;
		ItemStack held = mc.thePlayer.getHeldItem();
		// a left click on a player stays first: the hit is the point of it
		if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
			|| held == null || !(held.getItem() instanceof ItemBlock)) {
			return;
		}
		try {
			if (rightClick == null) {
				rightClickDelay = ReflectionHelper.findField(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
				rightClick = ReflectionHelper.findMethod(Minecraft.class, mc, new String[] {"rightClickMouse", "func_147121_ag"});
			}
			KeyBinding use = mc.gameSettings.keyBindUseItem;
			// what vanilla's right click would do later this tick: a new press, or the held button repeating
			boolean acts = use.isPressed() || use.isKeyDown() && rightClickDelay.getInt(mc) == 0;
			if (acts) {
				// sets the 4-tick right click delay, so vanilla's own right click this tick does not run again
				rightClick.invoke(mc);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			broken = true;
			RagoClient.LOG.warn("Block Edit Fix turned itself off: {}", e.toString());
		}
	}
}
