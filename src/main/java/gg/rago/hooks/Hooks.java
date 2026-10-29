package gg.rago.hooks;

import gg.rago.modules.Chat;
import gg.rago.modules.NoHurtCam;
import gg.rago.modules.OldAnimations;
import gg.rago.modules.TabPing;
import gg.rago.optimize.OcclusionCuller;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;

/**
 * What the patched game methods call (see {@code gg.rago.asm.RagoTransformer}). Each method returns
 * vanilla's own value until the feature behind it is installed and switched on, so the game behaves
 * as without Rago before the mod is loaded or when a feature is off.
 */
public final class Hooks {
	/** Vanilla keeps 100 chat lines. */
	public static final int VANILLA_CHAT_LINES = 100;

	private static volatile OcclusionCuller culler;
	private static volatile NoHurtCam noHurtCam;
	private static volatile OldAnimations animations;
	private static volatile TabPing tabPing;
	private static volatile Chat chat;

	private Hooks() {
	}

	public static void install(OcclusionCuller culler, NoHurtCam noHurtCam, OldAnimations animations, TabPing tabPing, Chat chat) {
		Hooks.culler = culler;
		Hooks.noHurtCam = noHurtCam;
		Hooks.animations = animations;
		Hooks.tabPing = tabPing;
		Hooks.chat = chat;
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

	/** ItemRenderer.renderItemInFirstPerson: the swing used while blocking (vanilla 0, 1.7 the real one). */
	public static float blockHitSwing(float swing) {
		OldAnimations a = animations;
		return a != null && a.isEnabled() && a.blockHit() ? swing : 0f;
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
}
