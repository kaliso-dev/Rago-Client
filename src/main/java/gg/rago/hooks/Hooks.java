package gg.rago.hooks;

import gg.rago.modules.Chat;
import gg.rago.modules.GameplayFixes;
import gg.rago.modules.HitColor;
import gg.rago.modules.NoHurtCam;
import gg.rago.modules.OldAnimations;
import gg.rago.modules.TabPing;
import gg.rago.optimize.EngineTweaks;
import gg.rago.optimize.OcclusionCuller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

/**
 * What the patched game methods call (see {@code gg.rago.asm.RagoTransformer}). Each method returns
 * vanilla's own value until the feature behind it is installed and switched on, so the game behaves
 * as without Rago before the mod is loaded or when a feature is off.
 */
public final class Hooks {
	/** Vanilla keeps 100 chat lines. */
	public static final int VANILLA_CHAT_LINES = 100;

	public static volatile OcclusionCuller culler;
	public static volatile NoHurtCam noHurtCam;
	public static volatile OldAnimations animations;
	public static volatile TabPing tabPing;
	public static volatile Chat chat;
	public static volatile GameplayFixes fixes;
	public static volatile HitColor hitColor;
	public static volatile EngineTweaks engine;

	private Hooks() {
	}

	/** RenderManager.shouldRender: true skips an entity hidden behind blocks. */
	public static boolean skipEntity(Entity e) {
		OcclusionCuller c = culler;
		return c != null && c.isHidden(e);
	}

	/** EntityRenderer.hurtCameraEffect: the camera tilt in degrees when hurt (vanilla 14). */
	public static float hurtShake(float vanilla) {
		NoHurtCam m = noHurtCam;
		return m != null && m.isEnabled() ? vanilla * m.strength() : vanilla;
	}

	/** LayerArmorBase.shouldCombineTextures: true tints armour red with the player, as in 1.7. */
	public static boolean armorDamageTint(boolean vanilla) {
		OldAnimations a = animations;
		return vanilla || a != null && a.isEnabled() && a.armorTint();
	}

	/**
	 * ItemRenderer.renderItemInFirstPerson: the swing used while an item is in use (vanilla 0, 1.7 the
	 * real one). {@code kind}: 0 eating or drinking, 1 blocking, 2 drawing a bow.
	 */
	public static float useSwing(float swing, int kind) {
		OldAnimations a = animations;
		return a != null && a.isEnabled() && a.swingWhileUsing(kind) ? swing : 0f;
	}

	/** ItemRenderer.transformFirstPersonItem, first thing: moves the held item. */
	public static void heldItemOffset() {
		OldAnimations a = animations;
		if (a != null && a.isEnabled()) {
			a.applyItemOffset();
		}
	}

	/** ItemRenderer.transformFirstPersonItem, last thing: scales the held item. */
	public static void heldItemScale() {
		OldAnimations a = animations;
		if (a != null && a.isEnabled()) {
			a.applyItemScale();
		}
	}

	/** ItemRenderer.updateEquippedItem: whether a changed held item plays the lower-and-raise animation. */
	public static boolean reequip(Item item, ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		OldAnimations a = animations;
		if (a != null && a.isEnabled() && a.noReequip() && !slotChanged && newStack != null && newStack.getItem() == item) {
			return false;
		}
		return item.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
	}

	/** The same call in OptiFine's ItemRenderer, made through OptiFine's reflection helper with boxed arguments. */
	public static boolean reequipArgs(Object item, Object reflectorMethod, Object[] args) {
		return reequip((Item) item, (ItemStack) args[0], (ItemStack) args[1], (Boolean) args[2]);
	}

	/** EntityLivingBase.getArmSwingAnimationEnd: how many ticks a swing lasts (vanilla 6, more with fatigue). */
	public static int swingDuration(int ticks, EntityLivingBase entity) {
		OldAnimations a = animations;
		if (a != null && a.isEnabled() && entity == Minecraft.getMinecraft().thePlayer) {
			return a.swingTicks(ticks);
		}
		return ticks;
	}

	/** RendererLivingEntity.setBrightness: one component (r, g, b, strength) of the red hurt tint. */
	public static float hitColor(float vanilla, int index) {
		HitColor h = hitColor;
		return h != null && h.isEnabled() ? h.component(index) : vanilla;
	}

	/** RenderEntityItem: how many copies of a dropped stack are drawn (1 to 5). */
	public static int itemCopies(int vanilla) {
		EngineTweaks e = engine;
		return e != null && e.unstackedItems() ? 1 : vanilla;
	}

	/** Minecraft.loadWorld: vanilla forces a full garbage collection on every world change. */
	public static void worldSwitchGc() {
		EngineTweaks e = engine;
		if (e == null || !e.fastWorldSwitch()) {
			System.gc();
		}
	}

	/** Minecraft.toggleFullscreen: true when Rago switched to or from borderless fullscreen itself. */
	public static boolean toggleFullscreen() {
		GameplayFixes f = fixes;
		return f != null && f.isEnabled() && f.toggleBorderless();
	}

	/**
	 * EntityLivingBase.getLook: for the local player, the direction the camera faces. Vanilla uses the
	 * head yaw, which follows the camera one tick late, so the crosshair could target what was in
	 * front of you a tick ago. Null keeps vanilla's answer.
	 */
	public static Vec3 look(EntityLivingBase entity, float partialTicks) {
		GameplayFixes f = fixes;
		if (f == null || !f.isEnabled() || !f.mouseDelayFix() || entity != Minecraft.getMinecraft().thePlayer) {
			return null;
		}
		float pitch;
		float yaw;
		if (partialTicks == 1f) {
			pitch = entity.rotationPitch;
			yaw = entity.rotationYaw;
		} else {
			pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
			yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
		}
		// Entity.getVectorForRotation
		float cosYaw = net.minecraft.util.MathHelper.cos(-yaw * 0.017453292f - (float) Math.PI);
		float sinYaw = net.minecraft.util.MathHelper.sin(-yaw * 0.017453292f - (float) Math.PI);
		float cosPitch = -net.minecraft.util.MathHelper.cos(-pitch * 0.017453292f);
		float sinPitch = net.minecraft.util.MathHelper.sin(-pitch * 0.017453292f);
		return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
	}

	/** GuiPlayerTabOverlay.drawPing: true when Rago drew the ping itself. */
	public static boolean replaceTabPing(int width, int x, int y, NetworkPlayerInfo info) {
		TabPing t = tabPing;
		return t != null && t.isEnabled() && t.draw(width, x, y, info);
	}

	/** GuiNewChat.setChatLine: how many lines the chat keeps. */
	public static int chatHistory() {
		Chat c = chat;
		return c != null && c.isEnabled() ? c.historyLines() : VANILLA_CHAT_LINES;
	}

	/** GuiNewChat.drawChat: colour of a chat line's background (vanilla black at half the line's opacity). */
	public static int chatBackground(int vanilla) {
		Chat c = chat;
		return c != null && c.isEnabled() ? c.background(vanilla >>> 24) : vanilla;
	}

	/** GuiNewChat.drawChat: draws a chat line's text, with or without shadow. */
	public static int chatText(FontRenderer font, String text, float x, float y, int color) {
		Chat c = chat;
		boolean shadow = c == null || !c.isEnabled() || c.textShadow();
		return font.drawString(text, x, y, color, shadow);
	}
}
