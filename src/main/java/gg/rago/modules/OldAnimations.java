package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

/**
 * 1.7 animations and held-item tweaks: the sword swings while you block-hit, the arm swings while
 * eating or drawing a bow, armour turns red with the player when hit, the arm swings when you click
 * while using an item, no lower-and-raise animation when the held item only changes (durability),
 * a new hotbar slot's item shown at once instead of lowered and raised (Instant item switch, off by
 * default), the body facing forward when walking backwards as from 1.15 (off by default),
 * swing speed, the held item's position (its size is in Item Scale), a camera that lowers smoothly when you sneak, and
 * 1.7's block seen in third person (F5, and other players): the sword held across the body instead of
 * out in front. All of it only changes how your own client draws things; nothing here is sent to the
 * server.
 */
public final class OldAnimations extends Module {
	private final BooleanOption blockHit = addOption(new BooleanOption("block_hit", true));
	private final BooleanOption armorTint = addOption(new BooleanOption("armor_damage_tint", true));
	private final BooleanOption swingWhileUsing = addOption(new BooleanOption("swing_while_using", true));
	private final BooleanOption eatSwing = addOption(new BooleanOption("eat_swing", true));
	private final BooleanOption bowSwing = addOption(new BooleanOption("bow_swing", true));
	private final BooleanOption noReequip = addOption(new BooleanOption("no_reequip", true));
	/** A new key: 1.5.4 to 1.5.6 had it on by default, and it is off now. */
	private final BooleanOption instantSwitch = addOption(new BooleanOption("instant_item_switch", false));
	private final BooleanOption backwardsWalk = addOption(new BooleanOption("backwards_walk", false));
	private final BooleanOption smoothSneak = addOption(new BooleanOption("smooth_sneak", true));
	private final BooleanOption thirdPersonBlock = addOption(new BooleanOption("block_third_person", true));
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

	public boolean instantSwitch() {
		return instantSwitch.get();
	}

	/**
	 * The way the body turns to while moving, given the way of the movement: walking backwards
	 * (the movement more than 95 degrees from where you look), it turns to face where you look,
	 * as in newer versions, instead of twisting sideways towards the movement as 1.8 does.
	 */
	public float walkYaw(float moving, float lookYaw) {
		return backwardsWalk.get() ? backwardsYaw(moving, lookYaw) : moving;
	}

	/** Newer versions' rule: a movement between 95 and 265 degrees from the look is walking backwards. */
	static float backwardsYaw(float moving, float lookYaw) {
		float off = Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(lookYaw) - moving);
		return off > 95f && off < 265f ? moving - 180f : moving;
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

	/** 1.8 turns a blocking arm 30° inwards; 1.7 keeps it straight. */
	private static final float BLOCK_ARM_TURN = 0.5235988f;

	/** ModelBiped.setRotationAngles, last: a blocking arm straight, as in 1.7 (the armour's arm follows). */
	public void blockArm(ModelBiped model) {
		if (thirdPersonBlock.get() && model.heldItemRight == 3) {
			model.bipedRightArm.rotateAngleY += BLOCK_ARM_TURN;
		}
	}

	/**
	 * LayerHeldItem, just before the item in a player's hand is drawn: while they block, 1.7's
	 * placement of the sword (RenderPlayer.renderEquippedItems and ItemRenderer.renderItem in 1.7.10),
	 * across the body, in place of 1.8's. It is worked out relative to the third-person transform
	 * of 1.8's sword model, which the game applies next, so a resource pack that changes how swords
	 * are held keeps its change.
	 */
	public void thirdPersonBlock(EntityLivingBase entity) {
		if (thirdPersonBlock.get() && entity instanceof EntityPlayer && ((EntityPlayer) entity).isBlocking()) {
			block17(GL);
		}
	}

	/** The steps of {@link #thirdPersonBlock}, apart so a test can follow them on a matrix. */
	interface Transform {
		void translate(float x, float y, float z);

		void rotate(float degrees, float x, float y, float z);

		void scale(float x, float y, float z);
	}

	private static final Transform GL = new Transform() {
		@Override
		public void translate(float x, float y, float z) {
			GlStateManager.translate(x, y, z);
		}

		@Override
		public void rotate(float degrees, float x, float y, float z) {
			GlStateManager.rotate(degrees, x, y, z);
		}

		@Override
		public void scale(float x, float y, float z) {
			GlStateManager.scale(x, y, z);
		}
	};

	static void block17(Transform t) {
		// 1.7: the sword brought across the body
		t.translate(0.05f, 0, -0.1f);
		t.rotate(-50, 0, 1, 0);
		t.rotate(-10, 1, 0, 0);
		t.rotate(-60, 0, 0, 1);
		// 1.7: a long item in the hand
		t.translate(0, 0.1875f, 0);
		t.scale(0.625f, -0.625f, 0.625f);
		t.rotate(-100, 1, 0, 0);
		t.rotate(45, 0, 1, 0);
		// 1.7: any flat item held by an entity
		t.translate(0, -0.3f, 0);
		t.scale(1.5f, 1.5f, 1.5f);
		t.rotate(50, 0, 1, 0);
		t.rotate(335, 0, 0, 1);
		t.translate(-0.9375f, -0.0625f, 0);
		// 1.7 drew the picture from 0 to 1, mirrored, 1/16 deep behind z = 0; 1.8's model is centred
		t.translate(0.5f, 0.5f, -1 / 32f);
		t.rotate(180, 0, 1, 0);
		// what the game does next, undone: x2 (RenderItem), the sword's "thirdperson" display
		// (translation 0, 1.25, -3.5 sixteenths; rotation 0, 90, -35; scale 0.85), x0.5 (RenderItem)
		t.scale(2 / 0.85f, 2 / 0.85f, 2 / 0.85f);
		t.rotate(35, 0, 0, 1);
		t.rotate(-90, 0, 1, 0);
		t.translate(0, -1.25f / 16, 3.5f / 16);
		t.scale(0.5f, 0.5f, 0.5f);
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
