package gg.rago.hooks;

import gg.rago.modules.Chat;
import gg.rago.modules.Cosmetics;
import gg.rago.modules.Emotes;
import gg.rago.modules.FastShop;
import gg.rago.modules.GameplayFixes;
import gg.rago.modules.GlintColor;
import gg.rago.modules.HitColor;
import gg.rago.modules.HitDelayFix;
import gg.rago.modules.BlockEditFix;
import gg.rago.modules.NoHurtCam;
import gg.rago.modules.OldAnimations;
import gg.rago.modules.Overlays;
import gg.rago.modules.HitParticles;
import gg.rago.modules.ItemPhysics;
import gg.rago.modules.ItemScale;
import gg.rago.modules.LowLatency;
import gg.rago.modules.NickHider;
import gg.rago.modules.BedwarsStats;
import gg.rago.modules.RagoBadge;
import gg.rago.modules.AntiArmor;
import gg.rago.modules.ClientFixes;
import gg.rago.modules.RagoCape;
import gg.rago.modules.RagoNetwork;
import gg.rago.modules.Screenshots;
import gg.rago.modules.SkinChanger;
import gg.rago.modules.TabList;
import gg.rago.modules.TabPing;
import gg.rago.modules.TitleMod;
import gg.rago.optimize.EngineTweaks;
import gg.rago.optimize.OcclusionCuller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
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
	public static volatile Overlays overlays;
	public static volatile TabPing tabPing;
	public static volatile TabList tabList;
	public static volatile HitParticles hitParticles;
	public static volatile ItemScale itemScale;
	public static volatile ItemPhysics itemPhysics;
	public static volatile HitDelayFix hitDelay;
	public static volatile BlockEditFix blockEdit;
	public static volatile FastShop fastShop;
	public static volatile Chat chat;
	public static volatile GameplayFixes fixes;
	public static volatile HitColor hitColor;
	public static volatile EngineTweaks engine;
	public static volatile RagoCape cape;
	public static volatile SkinChanger skins;
	public static volatile NickHider nickHider;
	public static volatile BedwarsStats bedwarsStats;
	public static volatile ClientFixes clientFixes;
	public static volatile Screenshots screenshots;
	public static volatile Emotes emotes;
	public static volatile RagoBadge ragoBadge;
	public static volatile AntiArmor antiArmor;
	public static volatile TitleMod titles;
	public static volatile LowLatency lowLatency;
	public static volatile RagoNetwork network;
	public static volatile Cosmetics cosmetics;

	/** Minecraft.runGameLoop, first thing: waits for the graphics card when Low Latency is on. */
	public static void frameStart() {
		LowLatency l = lowLatency;
		if (l != null) {
			l.frameStart();
		}
	}

	/** GuiIngameForge.renderTitle, first thing: true draws no title or subtitle. */
	public static boolean hideTitles() {
		TitleMod t = titles;
		return t != null && t.isEnabled() && t.hides();
	}

	/** GuiIngameForge.renderTitle: the title's scale (vanilla 4) or the subtitle's (vanilla 2). */
	public static float titleScale(float vanilla, int width) {
		TitleMod t = titles;
		return t != null && t.isEnabled() ? t.scale(vanilla, width) : vanilla;
	}

	/** LayerArmorBase.doRenderLayer: true to draw no armour on this entity. */
	public static boolean hideArmor(EntityLivingBase entity) {
		AntiArmor a = antiArmor;
		return a != null && a.isEnabled() && a.hides(entity);
	}
	public static volatile GlintColor glint;

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
		Overlays o = overlays;
		if (kind == 1 && o != null) {
			Overlays.BlockStyle style = o.blockStyle();
			if (style != null) {
				return o.blockSwing(style, swing);
			}
		}
		OldAnimations a = animations;
		return a != null && a.isEnabled() && a.swingWhileUsing(kind) ? swing : 0f;
	}

	/** ItemRenderer.doBlockTransformations, first thing: the block-hit style's turn of the sword. */
	public static void blockTransform() {
		Overlays o = overlays;
		if (o != null) {
			o.blockTransform();
		}
	}

	/** RenderGlobal.renderSky, after it turns for the sun: the packs' custom skies, without OptiFine. */
	public static void packSky(float partialTicks) {
		Overlays o = overlays;
		if (o != null && o.isEnabled()) {
			o.packSky(partialTicks);
		}
	}

	/** EntityRenderer.orientCamera: the eye height the camera is placed at (smooth sneak). */
	public static float cameraEyeHeight(float vanilla) {
		OldAnimations a = animations;
		return a != null && a.isEnabled() ? a.cameraEyeHeight(vanilla) : vanilla;
	}

	/** LayerHeldItem.doRenderLayer, just before the item is drawn: 1.7's block in third person. */
	public static void thirdPersonItem(net.minecraft.entity.EntityLivingBase entity) {
		OldAnimations a = animations;
		if (a != null && a.isEnabled()) {
			a.thirdPersonBlock(entity);
		}
	}

	/** ItemRenderer.transformFirstPersonItem, first thing: moves the held item. */
	public static void heldItemOffset() {
		OldAnimations a = animations;
		if (a != null && a.isEnabled()) {
			a.applyItemOffset();
		}
		Overlays o = overlays;
		if (o != null) {
			o.blockOffset();
		}
	}

	/** ItemRenderer.transformFirstPersonItem, last thing: scales the held item. */
	public static void heldItemScale() {
		ItemScale s = itemScale;
		if (s != null && s.isEnabled()) {
			s.applyHeld();
		}
	}

	/**
	 * RenderEntityItem's transform of a dropped item, last thing: item physics replaces the transform,
	 * then the item is scaled. Returns the number of copies to draw, unchanged.
	 */
	public static int droppedItem(int copies, EntityItem item, double x, double y, double z, float partialTicks, IBakedModel model) {
		ItemPhysics p = itemPhysics;
		if (p != null && p.isEnabled()) {
			p.apply(item, x, y, z, partialTicks, model);
		}
		ItemScale s = itemScale;
		if (s != null && s.isEnabled()) {
			s.applyDropped();
		}
		return copies;
	}

	/** ItemRenderer.updateEquippedItem: whether a changed held item plays the lower-and-raise animation. */
	public static boolean reequip(Item item, ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		OldAnimations a = animations;
		if (a != null && a.isEnabled() && a.noReequip() && !slotChanged && newStack != null && newStack.getItem() == item) {
			return false;
		}
		return item.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
	}

	/**
	 * At the start of ItemRenderer.updateEquippedItem: with Instant item switch, a new hotbar slot
	 * shows its item at once, fully raised, instead of lowering the old item and raising the new one
	 * over about a third of a second. True when done, and vanilla's update is skipped this tick.
	 */
	public static boolean instantSwitch(net.minecraft.client.renderer.ItemRenderer renderer) {
		OldAnimations a = animations;
		net.minecraft.entity.player.EntityPlayer p = Minecraft.getMinecraft().thePlayer;
		if (a == null || !a.isEnabled() || !a.instantSwitch() || p == null) {
			return false;
		}
		return gg.rago.render.HeldItem.switchAtOnce(renderer, p.inventory.currentItem, p.inventory.getCurrentItem());
	}

	/** EntityLivingBase.onUpdate: the way a moving body turns to (1.15+ backwards walking, in 1.7 animations). */
	public static float walkYaw(float moving, net.minecraft.entity.EntityLivingBase entity) {
		OldAnimations a = animations;
		return a != null && a.isEnabled() ? a.walkYaw(moving, entity.rotationYaw) : moving;
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

	/** EffectRenderer.addEffect, first thing: a new particle skips block collisions when they are off. */
	public static void particleAdded(EntityFX fx) {
		EngineTweaks e = engine;
		if (e != null && !e.particleCollisions()) {
			fx.noClip = true;
		}
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

	/** EntityPlayer.onUpdate: whether the item in use is still the one in the hand (vanilla: same object). */
	public static boolean keepItemInUse(ItemStack current, ItemStack inUse, EntityPlayer player) {
		if (current == inUse) {
			return true;
		}
		GameplayFixes f = fixes;
		return f != null && f.isEnabled() && f.keepUsing(current, inUse, player);
	}

	/** GuiContainer.handleMouseClick: true when the fast shop sent (or dropped) the click itself. */
	public static boolean containerClick(GuiContainer gui, Slot slot, int slotId, int button, int type) {
		FastShop f = fastShop;
		return f != null && f.isEnabled() && f.onClick(gui, slot, slotId, button, type);
	}

	/** GuiContainer.drawSlot, first thing. */
	public static void beforeSlot(GuiContainer gui, Slot slot) {
		FastShop f = fastShop;
		if (f != null && f.isEnabled()) {
			f.beforeSlot(gui, slot);
		}
	}

	/** Minecraft.clickMouse, first thing: with Block Edit Fix, a right click of the same tick goes first. */
	public static void beforeLeftClick() {
		BlockEditFix b = blockEdit;
		if (b != null && b.isEnabled()) {
			b.beforeLeftClick();
		}
	}

	/** Minecraft.clickMouse: ticks the attack key is locked after a click that hits nothing. */
	public static int missDelay(int vanilla) {
		HitDelayFix h = hitDelay;
		return h != null && h.isEnabled() ? h.missDelay(vanilla) : vanilla;
	}

	/** EffectRenderer.addBlockDestroyEffects: true to skip the particles of a broken block. */
	public static boolean hideBlockBreak() {
		HitParticles h = hitParticles;
		return h != null && h.isEnabled() && h.hideBlockBreak();
	}

	/** ModelBiped.setRotationAngles, at its end: 1.7's blocking arm, then the pose of a dance on your own player. */
	public static void emotePose(ModelBiped model, Entity entity) {
		OldAnimations a = animations;
		if (a != null && a.isEnabled()) {
			a.blockArm(model);
		}
		Emotes e = emotes;
		if (e != null && (e.playing() != null || e.posed())) {
			e.pose(model, entity);
		}
	}

	/** ModelRenderer.render, first: true when the part was drawn bent (an emote's elbow, knee or waist). */
	public static boolean renderBent(net.minecraft.client.model.ModelRenderer part, float scale) {
		if (!gg.rago.debug.FrameProfile.ON) {
			return gg.rago.render.BentModel.render(part, scale);
		}
		long t = System.nanoTime();
		boolean bent = gg.rago.render.BentModel.render(part, scale);
		if (bent) {
			gg.rago.debug.FrameProfile.element("bent limbs", System.nanoTime() - t);
		}
		return bent;
	}

	/** ModelRenderer.postRender, at its end: the held item follows a bent arm's hand. */
	public static void postRenderBent(net.minecraft.client.model.ModelRenderer part, float scale) {
		gg.rago.render.BentModel.postRender(part, scale);
	}

	/** EffectRenderer.emitParticleAtEntity: true when Hit Particles makes the burst itself, in its size. */
	public static boolean emitParticles(Entity entity, EnumParticleTypes type) {
		HitParticles h = hitParticles;
		return h != null && h.isEnabled() && h.emitParticles(entity, type);
	}

	/** EffectRenderer.addBlockHitEffects: true to skip the particles of a block being mined. */
	public static boolean hideBlockHit() {
		HitParticles h = hitParticles;
		return h != null && h.isEnabled() && h.hideBlockHit();
	}

	/** GuiPlayerTabOverlay.renderPlayerlist, first thing. */
	public static void tabBegin(int width) {
		TabList t = tabList;
		if (t != null && t.isEnabled()) {
			t.begin(width);
		}
	}

	/** GuiPlayerTabOverlay.renderPlayerlist, before returning (always called after tabBegin). */
	public static void tabEnd() {
		TabList t = tabList;
		if (t != null) {
			t.end();
		}
	}

	/** GuiPlayerTabOverlay.renderPlayerlist: colour of the background and row rectangles. */
	public static int tabRect(int vanilla) {
		TabList t = tabList;
		return t != null && t.isEnabled() ? t.rectColor(vanilla) : vanilla;
	}

	/** GuiPlayerTabOverlay.renderPlayerlist: the header ({@code first}) or footer, null to hide it. */
	public static IChatComponent tabHeaderFooter(IChatComponent text, boolean first) {
		TabList t = tabList;
		return t != null && t.isEnabled() ? t.headerFooter(text, first) : text;
	}

	/** GuiPlayerTabOverlay.renderPlayerlist: draws a name, header or footer line. */
	public static int tabText(FontRenderer font, String text, float x, float y, int color) {
		TabList t = tabList;
		boolean custom = t != null && t.isEnabled();
		RagoBadge r = ragoBadge;
		if (r != null && r.tabLine(font, text, x, y, color, !custom || t.shadow())) {
			return 0; // the game does not use what this returns
		}
		return custom ? t.drawText(font, text, x, y, color) : font.drawStringWithShadow(text, x, y, color);
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

	/**
	 * GuiNewChat.drawChat: draws a chat line's text, with or without shadow, before the game's own
	 * call (left in place with an empty string); returns the font for that call.
	 */
	public static FontRenderer chatText(FontRenderer font, String text, float x, float y, int color) {
		Chat c = chat;
		boolean shadow = c == null || !c.isEnabled() || c.textShadow();
		font.drawString(text, x, y, color, shadow);
		return font;
	}

	/** ScreenShotHelper.saveScreenshot: copy, open, folder and delete buttons after the chat line. */
	public static IChatComponent screenshot(IChatComponent line) {
		Screenshots s = screenshots;
		return s != null && s.isEnabled() ? s.decorate(line) : line;
	}

	/** EntityRenderer.orientCamera: the first-person camera offset (parallax fix). */
	public static float cameraOffset(float vanilla) {
		ClientFixes f = clientFixes;
		return f != null && f.isEnabled() ? f.cameraOffset(vanilla) : vanilla;
	}

	/** S48PacketResourcePackSend.getURL: unsafe server pack links become a pack that does not exist. */
	public static String resourcePackUrl(String url) {
		ClientFixes f = clientFixes;
		return f != null && f.isEnabled() ? f.resourcePackUrl(url) : url;
	}

	/**
	 * GuiPlayerTabOverlay.renderPlayerlist, the players in vanilla's order: without Hypixel's bots
	 * (Tab List), then strongest first with Bed Wars Stats.
	 */
	public static java.util.List<NetworkPlayerInfo> tabOrder(java.util.List<NetworkPlayerInfo> vanilla) {
		TabList t = tabList;
		java.util.List<NetworkPlayerInfo> list = t != null && t.isEnabled() ? t.withoutBots(vanilla) : vanilla;
		BedwarsStats s = bedwarsStats;
		return s != null && s.isEnabled() ? s.tabOrder(list) : list;
	}

	/** GuiPlayerTabOverlay.getPlayerName: Bed Wars star and ratio around the name. */
	public static String tabName(String vanilla, NetworkPlayerInfo info) {
		BedwarsStats s = bedwarsStats;
		String name = s != null && s.isEnabled() ? s.tabName(vanilla, info) : vanilla;
		RagoBadge r = ragoBadge;
		return r != null ? r.tabName(name, info) : name;
	}

	/** AbstractClientPlayer.getLocationSkin: the Nick Hider's or the locker skin on your own player. */
	public static ResourceLocation skin(ResourceLocation vanilla, AbstractClientPlayer player) {
		NickHider n = nickHider;
		if (n != null && n.isEnabled()) {
			ResourceLocation r = n.skinFor(player, null);
			if (r != null) {
				return r;
			}
		}
		SkinChanger s = skins;
		return s != null && s.isEnabled() ? s.skinFor(player, vanilla) : vanilla;
	}

	/** AbstractClientPlayer.getSkinType: slim or wide arms to go with that skin. */
	public static String skinModel(String vanilla, AbstractClientPlayer player) {
		NickHider n = nickHider;
		if (n != null && n.isEnabled()) {
			String m = n.modelFor(player);
			if (m != null) {
				return m;
			}
		}
		SkinChanger s = skins;
		return s != null && s.isEnabled() ? s.modelFor(player, vanilla) : vanilla;
	}

	/** NetworkPlayerInfo.getLocationSkin: your head in the tab list with the Nick Hider's skin. */
	public static ResourceLocation tabSkin(ResourceLocation vanilla, NetworkPlayerInfo info) {
		NickHider n = nickHider;
		return n != null && n.isEnabled() ? n.skinFor(info, vanilla) : vanilla;
	}

	/** Text about to be drawn with its § codes: your name replaced while the Nick Hider is on. */
	public static String nick(String s) {
		NickHider n = nickHider;
		return n != null && s != null && n.isEnabled() ? n.formatted(s) : s;
	}

	/** {@link #nick} for text drawn without § codes (Rago's menus). */
	public static String nickPlain(String s) {
		NickHider n = nickHider;
		return n != null && s != null && n.isEnabled() ? n.plain(s) : s;
	}

	/** AbstractClientPlayer.getLocationCape: the Rago cape for players who wear it. */
	public static ResourceLocation cape(ResourceLocation vanilla, AbstractClientPlayer player) {
		RagoCape c = cape;
		return c != null ? c.capeFor(player, vanilla) : vanilla;
	}

	/** RenderItem.renderEffect: the colour of the glint on items (vanilla -8372020, purple). */
	public static int glintColor(int vanilla) {
		GlintColor g = glint;
		return g != null && g.isEnabled() ? g.argb() : vanilla;
	}

	/** LayerArmorBase.renderGlint: how many shimmer layers an enchanted armour piece gets (vanilla 2). */
	public static int glintPasses() {
		EngineTweaks e = engine;
		return e != null && e.lightGlint() ? 1 : 2;
	}

	private static java.lang.invoke.MethodHandle itemGlintModel;
	private static boolean itemGlintModelMissing;

	/**
	 * RenderItem.renderEffect, in place of its second RenderItem.renderModel call: the second shimmer
	 * layer of an enchanted item, skipped with the light glint.
	 */
	public static void secondGlintPass(net.minecraft.client.renderer.entity.RenderItem renderer,
		net.minecraft.client.resources.model.IBakedModel model, int color) throws Throwable {
		if (glintPasses() < 2) {
			return;
		}
		java.lang.invoke.MethodHandle h = itemGlintModel;
		if (h == null) {
			if (itemGlintModelMissing) {
				return;
			}
			h = itemGlintModel = renderModel();
			if (h == null) {
				itemGlintModelMissing = true;
				return;
			}
		}
		h.invokeExact(renderer, model, color);
	}

	/** RenderItem's private renderModel(IBakedModel, int), which the second layer calls. */
	private static java.lang.invoke.MethodHandle renderModel() {
		for (java.lang.reflect.Method m : net.minecraft.client.renderer.entity.RenderItem.class.getDeclaredMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if ((m.getName().equals("func_175035_a") || m.getName().equals("renderModel")) && p.length == 2
				&& p[0] == net.minecraft.client.resources.model.IBakedModel.class && p[1] == int.class) {
				try {
					m.setAccessible(true);
					return java.lang.invoke.MethodHandles.lookup().unreflect(m);
				} catch (ReflectiveOperationException | RuntimeException e) {
					gg.rago.core.RagoClient.LOG.warn("Light glint: RenderItem.renderModel unavailable, items keep one layer", e);
					return null;
				}
			}
		}
		gg.rago.core.RagoClient.LOG.warn("Light glint: RenderItem.renderModel not found, items keep one layer");
		return null;
	}

	/** LayerArmorBase.renderLayer, after the glint colour is set: sets the chosen one instead. */
	public static void armorGlint() {
		GlintColor g = glint;
		if (g != null && g.isEnabled()) {
			int c = g.argb();
			// vanilla dims the armour glint to 76 %
			float f = 0.76f / 255f;
			net.minecraft.client.renderer.GlStateManager.color((c >> 16 & 0xFF) * f, (c >> 8 & 0xFF) * f, (c & 0xFF) * f, 1f);
		}
	}
}
