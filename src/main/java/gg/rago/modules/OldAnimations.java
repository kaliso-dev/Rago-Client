package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;

/**
 * 1.7 animations and held-item tweaks: the sword swings while you block-hit, the arm swings while
 * eating or drawing a bow, armour turns red with the player when hit, the arm swings when you click
 * while using an item, no lower-and-raise animation when the held item only changes (durability),
 * swing speed, the held item's position (its size is in Item Scale), and a camera that lowers smoothly when you sneak. All of it only changes how your own client
 * draws things; nothing here is sent to the server.
 */
public final class OldAnimations extends Module {
	private final BooleanOption blockHit = addOption(new BooleanOption("block_hit", true));
	private final BooleanOption armorTint = addOption(new BooleanOption("armor_damage_tint", true));
	private final BooleanOption swingWhileUsing = addOption(new BooleanOption("swing_while_using", true));
	private final BooleanOption eatSwing = addOption(new BooleanOption("eat_swing", true));
	private final BooleanOption bowSwing = addOption(new BooleanOption("bow_swing", true));
	private final BooleanOption noReequip = addOption(new BooleanOption("no_reequip", true));
	private final BooleanOption smoothSneak = addOption(new BooleanOption("smooth_sneak", true));
	private final IntOption swingSpeed;
	private final IntOption itemX;
	private final IntOption itemY;
	private final IntOption itemZ;
	private float eyeHeight = Float.NaN;
	private long lastEyeNs;

	public OldAnimations() {
		super("old_animations", FeatureClass.VISUAL, false);
		section("held_item");
		swingSpeed = addOption(new IntOption("swing_speed", 100, 50, 200));
		itemX = addOption(new IntOption("item_x", 0, -50, 50));
		itemY = addOption(new IntOption("item_y", 0, -50, 50));
		itemZ = addOption(new IntOption("item_z", 0, -50, 50));
		section(null);
	}

	/** Whether the arm swings while an item is in use: 0 eating or drinking, 1 blocking, 2 bow. */
	public boolean swingWhileUsing(int kind) {
		switch (kind) {
			case 0:
				return eatSwing.get();
			case 1:
				return blockHit.get();
			default:
				return bowSwing.get();
		}
	}

	/**
	 * The camera height used for drawing: follows the real eye height (which jumps by 0.08 blocks
	 * when sneaking) over about a fifth of a second. Aiming and hits keep the real height.
	 */
	public float cameraEyeHeight(float vanilla) {
		long now = System.nanoTime();
		float dt = lastEyeNs == 0 ? 0 : Math.min(0.1f, (now - lastEyeNs) / 1e9f);
		lastEyeNs = now;
		if (!smoothSneak.get() || Float.isNaN(eyeHeight) || Math.abs(vanilla - eyeHeight) > 0.3f) {
			eyeHeight = vanilla;
		} else {
			eyeHeight += (vanilla - eyeHeight) * (1 - (float) Math.exp(-dt * 15));
		}
		return eyeHeight;
	}

	public boolean noReequip() {
		return noReequip.get();
	}

	/** Swing length in ticks for the local player; higher speed means a shorter swing. */
	public int swingTicks(int vanilla) {
		return Math.max(1, Math.round(vanilla * 100f / swingSpeed.get()));
	}

	public void applyItemOffset() {
		if (itemX.get() != 0 || itemY.get() != 0 || itemZ.get() != 0) {
			GlStateManager.translate(itemX.get() / 100f, itemY.get() / 100f, itemZ.get() / 100f);
		}
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
		if (!p.isSwingInProgress || p.swingProgressInt >= swingTicks(6) / 2 || p.swingProgressInt < 0) {
			p.swingProgressInt = -1;
			p.isSwingInProgress = true;
		}
	}
}
