package gg.rago.optimize;

import gg.rago.core.RagoClient;
import gg.rago.debug.FrameProfile;
import gg.rago.render.LabelBatch;
import gg.rago.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * Floating texts (the lines over Hypixel's generators and shop keepers: invisible armour stands
 * whose name is always shown) drawn all together, in a few draw calls, instead of one by one
 * among the entities with some twenty GL calls and three draws each. They look the
 * same: vanilla's box, faint copy through walls and text, at vanilla's size, place and distance,
 * shaded by the light where the stand is and fogged like the world. They are drawn as the entity
 * pass ends, before chests, particles, water and stained glass, which cover them as they would
 * cover the game's own. A text the batch cannot draw exactly (styles other than colours and bold,
 * characters outside the Minecraft font's ASCII page, the Unicode font), a stand another mod draws,
 * and shaders are left to the game.
 *
 * <p>An invisible stand with nothing to wear or hold shows nothing but its text, yet the game
 * still sets up and undoes a whole model for it (matrices, culling, lighting, three empty layers):
 * that part is skipped, leaving the GL state as the game would, and only the text is drawn.
 */
public final class HologramBatch {
	/** Vanilla's label scale, as Render.renderLivingLabel works it out. */
	private static final float SCALE = 0.016666668f * 1.6f;
	/** Texts are made again at least this often, for a team prefix that changes without a new name. */
	private static final int TEXT_REFRESH_TICKS = 20;

	private final BooleanSupplier on;
	private final LabelBatch batch = new LabelBatch();
	private final Map<EntityArmorStand, Text> texts = new WeakHashMap<>();
	private World textsWorld;
	/** Whether fog was on while the entities were drawn this frame. */
	private boolean fog;
	private int[] lightmap;
	private boolean unavailable;
	private Field fogState;
	private Field fogEnabled;
	private Field booleanState;
	/** The renderer's canRenderName, which mods (Patcher's F1 option) change. */
	private MethodHandle canRenderName;
	/** RendererLivingEntity's own renderOutlines and layer list. */
	private MethodHandle renderOutlines;
	private MethodHandle layers;
	/** The armour stand renderer whose layers were checked, and whether they are vanilla's three. */
	private Object checkedRenderer;
	private int checkedLayerCount = -1;
	private boolean vanillaLayers;
	/** Between the start of a frame and the end of its world: stands drawn elsewhere (a menu) are the game's. */
	private boolean inWorld;

	public HologramBatch(BooleanSupplier on) {
		this.on = on;
	}

	/** A stand's text as the game makes it (Entity.getDisplayName), what it was made from, and when. */
	private static final class Text {
		String name;
		int tick;
		String text;
		int width;
		boolean exact;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onNamePre(RenderLivingEvent.Specials.Pre<EntityLivingBase> event) {
		if (!inWorld || !(event.entity instanceof EntityArmorStand) || unavailable || !on.getAsBoolean()
			|| event.renderer.getClass() != ArmorStandRenderer.class) {
			return;
		}
		EntityArmorStand stand = (EntityArmorStand) event.entity;
		if (stand.isSneaking() || shaders() || !ready() || !canRenderName(event.renderer, stand)) {
			return; // vanilla's sneaking label, shaders, or no name to show: the game's own way
		}
		Minecraft mc = Minecraft.getMinecraft();
		RenderManager rm = mc.getRenderManager();
		FontRenderer fr = mc.fontRendererObj;
		if (rm.livingPlayer == null || rm.getFontRenderer() != fr || fr.getUnicodeFlag() || fr.getBidiFlag()) {
			return;
		}
		Text text = text(stand, fr);
		if (!text.exact || "deadmau5".equals(text.text)) {
			return;
		}
		event.setCanceled(true);
		double d = stand.getDistanceSqToEntity(rm.livingPlayer);
		float range = RendererLivingEntity.NAME_TAG_RANGE;
		if (d < range * range && d <= 64 * 64) {
			if (batch.isEmpty()) {
				fog = fogOn();
			}
			double y = event.y - (stand.isChild() ? stand.height / 2.0 : 0) + stand.height + 0.5;
			batch.flat(false);
			batch.begin(event.x, y, event.z, SCALE, 0);
			batch.vanillaLabel(text.text, text.width, tint(stand));
		}
		// cancelling skips the Post event: send it as the game would have
		MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Specials.Post<>(event.entity, event.renderer, event.x, event.y, event.z));
	}

	/**
	 * An invisible stand with nothing on: the game's model set-up would draw nothing, so it is
	 * skipped. The GL state is left as that set-up leaves it, then the name goes through the
	 * renderer as usual (onNamePre above), and the events are sent as the game sends them.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onLivingPre(RenderLivingEvent.Pre<EntityLivingBase> event) {
		if (!inWorld || !(event.entity instanceof EntityArmorStand) || unavailable || !on.getAsBoolean()
			|| event.renderer.getClass() != ArmorStandRenderer.class) {
			return;
		}
		EntityArmorStand stand = (EntityArmorStand) event.entity;
		Minecraft mc = Minecraft.getMinecraft();
		if (!stand.isInvisible() || mc.thePlayer == null || !stand.isInvisibleToPlayer(mc.thePlayer) || shaders() || !ready()) {
			return;
		}
		for (int slot = 0; slot < 5; slot++) {
			if (stand.getEquipmentInSlot(slot) != null) {
				return;
			}
		}
		RendererLivingEntity<EntityLivingBase> renderer = event.renderer;
		if (!vanillaLayers(renderer) || outlines(renderer)) {
			return;
		}
		event.setCanceled(true);
		// what RendererLivingEntity.doRender leaves behind for such a stand
		GlStateManager.enableAlpha();
		GlStateManager.depthMask(true);
		GlStateManager.disableRescaleNormal();
		GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.enableTexture2D();
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.enableCull();
		renderer.renderName(stand, event.x, event.y, event.z);
		MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Post<>(stand, renderer, event.x, event.y, event.z));
	}

	/** Whether the stand renderer still has exactly vanilla's armour, held item and head layers. */
	private boolean vanillaLayers(RendererLivingEntity<?> renderer) {
		List<?> list;
		try {
			list = (List<?>) layers.invoke(renderer);
		} catch (Throwable e) {
			return false;
		}
		if (renderer != checkedRenderer || list.size() != checkedLayerCount) {
			checkedRenderer = renderer;
			checkedLayerCount = list.size();
			vanillaLayers = list.size() == 3;
			for (Object layer : list) {
				Class<?> c = layer.getClass();
				vanillaLayers &= c == LayerBipedArmor.class || c == LayerHeldItem.class || c == LayerCustomHead.class;
			}
		}
		return vanillaLayers;
	}

	/** The renderer's renderOutlines (the spectators' glowing pass), when doRender draws no name. */
	private boolean outlines(RendererLivingEntity<?> renderer) {
		try {
			return (boolean) renderOutlines.invoke(renderer);
		} catch (Throwable e) {
			return true;
		}
	}

	/** The renderer's own canRenderName, as the game asks it before drawing a name. */
	private boolean canRenderName(Render<?> renderer, Entity entity) {
		try {
			return (boolean) canRenderName.invoke(renderer, entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * The stand's text: the game builds a chat message (with a hover text made from NBT) each time
	 * it is asked, so it is made again only when the name changes, and once a second whatever.
	 */
	private Text text(EntityArmorStand stand, FontRenderer fr) {
		if (stand.worldObj != textsWorld) {
			textsWorld = stand.worldObj;
			texts.clear();
		}
		Text text = texts.get(stand);
		if (text == null) {
			text = new Text();
			texts.put(stand, text);
		}
		String name = stand.getCustomNameTag();
		int age = stand.ticksExisted - text.tick;
		if (text.text == null || text.name != name || age < 0 || age >= TEXT_REFRESH_TICKS) {
			text.name = name;
			text.tick = stand.ticksExisted;
			text.text = stand.getDisplayName().getFormattedText();
			text.width = fr.getStringWidth(text.text);
			text.exact = LabelBatch.canDrawExact(text.text);
		}
		return text;
	}

	/** The light map's colour where the stand is: the game shades labels with it. */
	private int tint(EntityArmorStand stand) {
		int light = stand.isBurning() ? 0xF000F0 : stand.getBrightnessForRender(WorldDraw.partialTicks());
		int block = Math.min(15, Math.max(0, (light % 65536 + 8) >> 4));
		int sky = Math.min(15, Math.max(0, (light / 65536 + 8) >> 4));
		return lightmap[sky * 16 + block] | 0xFF000000;
	}

	/** Finds what is read through reflection; false (and the game draws the texts) if any is missing. */
	private boolean ready() {
		if (lightmap != null) {
			return true;
		}
		try {
			lightmap = ReflectionHelper.getPrivateValue(EntityRenderer.class, Minecraft.getMinecraft().entityRenderer, "lightmapColors", "field_78504_Q");
			fogState = ReflectionHelper.findField(GlStateManager.class, "fogState", "field_179155_g");
			Object state = fogState.get(null);
			fogEnabled = ReflectionHelper.findField(state.getClass(), "fog", "field_179049_a");
			Object flag = fogEnabled.get(state);
			booleanState = ReflectionHelper.findField(flag.getClass(), "currentState", "field_179201_b");
			booleanState.getBoolean(flag);
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			Method can = ReflectionHelper.findMethod(Render.class, null, new String[] {"func_177070_b", "canRenderName"}, Entity.class);
			canRenderName = lookup.unreflect(can).asType(java.lang.invoke.MethodType.methodType(boolean.class, Render.class, Entity.class));
			renderOutlines = lookup.unreflectGetter(ReflectionHelper.findField(RendererLivingEntity.class, "field_177098_i", "renderOutlines"))
				.asType(java.lang.invoke.MethodType.methodType(boolean.class, RendererLivingEntity.class));
			layers = lookup.unreflectGetter(ReflectionHelper.findField(RendererLivingEntity.class, "field_177097_h", "layerRenderers"))
				.asType(java.lang.invoke.MethodType.methodType(List.class, RendererLivingEntity.class));
			if (lightmap == null || lightmap.length < 256) {
				throw new IllegalStateException("light map of " + (lightmap == null ? 0 : lightmap.length));
			}
			return true;
		} catch (RuntimeException | ReflectiveOperationException e) {
			RagoClient.LOG.warn("Floating texts are drawn one by one: {}", e.toString());
			lightmap = null;
			canRenderName = null;
			unavailable = true;
			return false;
		}
	}

	/** Whether the game has fog on right now (GlStateManager's own record, no question to the driver). */
	private boolean fogOn() {
		try {
			return booleanState.getBoolean(fogEnabled.get(fogState.get(null)));
		} catch (IllegalAccessException e) {
			return false;
		}
	}

	private static Boolean shadersClass;
	private static Field shaderPackLoaded;

	/** Whether an OptiFine shader pack is in use: labels then go through its programs, left to the game. */
	private static boolean shaders() {
		if (shadersClass == null) {
			shadersClass = false;
			for (String name : new String[] {"shadersmod.client.Shaders", "net.optifine.shaders.Shaders"}) {
				try {
					shaderPackLoaded = Class.forName(name).getField("shaderPackLoaded");
					shadersClass = true;
					break;
				} catch (ReflectiveOperationException | LinkageError ignored) {
					// not this OptiFine, or none
				}
			}
		}
		try {
			return shadersClass && shaderPackLoaded.getBoolean(null);
		} catch (IllegalAccessException e) {
			return true;
		}
	}

	@SubscribeEvent
	public void onRenderStart(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			batch.clear();
			inWorld = Minecraft.getMinecraft().theWorld != null;
		}
	}

	/** RenderGlobal.renderEntities, as the block entities start: the texts queued among the entities. */
	public void entitiesDrawn() {
		if (batch.isEmpty()) {
			return;
		}
		long t = FrameProfile.ON ? System.nanoTime() : 0;
		batch.flushAmongEntities();
		if (FrameProfile.ON) {
			// with the time of each stand, measured around it (EntityProfile)
			FrameProfile.element("holograms", System.nanoTime() - t);
		}
	}

	/** After the world: whatever is left (if the entity pass hook is missing), then menus are the game's. */
	@SubscribeEvent
	public void onWorldDrawn(RenderWorldLastEvent event) {
		inWorld = false;
		if (batch.isEmpty()) {
			return;
		}
		long t = FrameProfile.ON ? System.nanoTime() : 0;
		batch.flush(fog);
		if (FrameProfile.ON) {
			FrameProfile.element("holograms", System.nanoTime() - t);
		}
	}
}
