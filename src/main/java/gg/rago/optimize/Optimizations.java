package gg.rago.optimize;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.RagoClient;
import gg.rago.settings.Setting;
import gg.rago.settings.Settings;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerArrow;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityBannerRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityEnchantmentTableRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityEndPortalRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Rago's own performance settings: frame caps, entity and tile entity render distances, hiding
 * things that cost frames without helping in PvP, a particle cap and fog. Everything here only
 * changes what this client draws; nothing is sent to the server.
 */
public final class Optimizations {
	private static final String FPS = "rago.section.fps";
	private static final String ENTITIES = "rago.section.entities";
	private static final String TILES = "rago.section.tile_entities";
	private static final String WORLD = "rago.section.world";
	private static final String CULLING = "rago.section.culling";
	private static final String ENGINE = "rago.section.engine";

	private final IntOption menuFps;
	private final BooleanOption unfocusedCap;
	private final IntOption unfocusedFps;
	private final BooleanOption minimizedCap;

	private final BooleanOption occlusionCulling;
	private final BooleanOption cullPlayers;
	private final BooleanOption cullTiles;
	private final OcclusionCuller culler;

	private final BooleanOption entityDistance;
	private final IntOption playerDistance;
	private final IntOption hostileDistance;
	private final IntOption passiveDistance;
	private final IntOption otherDistance;
	private final BooleanOption hideStuckArrows;
	private final BooleanOption hideItemFrames;
	private final BooleanOption hidePaintings;
	private final BooleanOption hideArmorStands;
	private final BooleanOption hideXpOrbs;
	private final BooleanOption hideFireworks;
	private final BooleanOption hideFallingBlocks;
	private final BooleanOption lightGlint;

	private final IntOption tileDistance;
	private final BooleanOption hideSkulls;
	private final BooleanOption hideSigns;
	private final BooleanOption hideEnchantBooks;
	private final BooleanOption hideBanners;
	private final BooleanOption hideBeaconBeams;
	private final BooleanOption hideEndPortals;

	private final BooleanOption hideGrass;
	private final BooleanOption hideFlowers;
	private final BooleanOption hideDeadBushes;
	private final IntOption maxParticles;
	private final BooleanOption particleCollisions;
	private final BooleanOption fastWorldSwitch;
	private final BooleanOption unstackedItems;
	private final BooleanOption skipGlErrors;
	private final BooleanOption hudCache;
	private final IntOption hudCacheRate;
	private final EngineTweaks engine;
	private final BooleanOption disableFog;

	private final Map<Entity, Double> originalWeights = new WeakHashMap<>();
	private final List<RemovedLayer> removedArrowLayers = new ArrayList<>();
	private FoliagePack foliagePack;
	private Object lastWorld;
	private int ticks;

	private static final class RemovedLayer {
		final List<LayerRenderer<?>> owner;
		final LayerRenderer<?> layer;

		RemovedLayer(List<LayerRenderer<?>> owner, LayerRenderer<?> layer) {
			this.owner = owner;
			this.layer = layer;
		}
	}

	public Optimizations(Settings s) {
		Setting.Tab p = Setting.Tab.PERFORMANCE;
		menuFps = s.slider(p, FPS, "menu_fps", 120, 30, FrameLimit.UNLIMITED, 10, Optimizations::fpsLabel, v -> applyFrameCaps());
		unfocusedCap = s.bool(p, FPS, "unfocused_cap", true, v -> applyFrameCaps());
		unfocusedFps = s.slider(p, FPS, "unfocused_fps", 30, 5, 120, 5, v -> v + " FPS", v -> applyFrameCaps());
		minimizedCap = s.bool(p, FPS, "minimized_cap", true, v -> applyFrameCaps());

		occlusionCulling = s.bool(p, CULLING, "occlusion_culling", true, null);
		cullPlayers = s.bool(p, CULLING, "cull_players", false, null);
		cullTiles = s.bool(p, CULLING, "cull_tile_entities", true, null);
		visibleWhen(s, "cull_players", occlusionCulling::get);
		visibleWhen(s, "cull_tile_entities", occlusionCulling::get);
		culler = new OcclusionCuller(occlusionCulling::get, cullPlayers::get, cullTiles::get);

		entityDistance = s.bool(p, ENTITIES, "entity_distance", false, null);
		hideStuckArrows = s.bool(p, ENTITIES, "hide_stuck_arrows", false, v -> applyArrowLayers());
		playerDistance = s.slider(p, ENTITIES, "player_distance", 96, 8, 128, 8, Optimizations::blocks, null);
		hostileDistance = s.slider(p, ENTITIES, "hostile_distance", 64, 8, 128, 8, Optimizations::blocks, null);
		passiveDistance = s.slider(p, ENTITIES, "passive_distance", 48, 8, 128, 8, Optimizations::blocks, null);
		otherDistance = s.slider(p, ENTITIES, "other_distance", 48, 8, 128, 8, Optimizations::blocks, null);
		for (String id : new String[] {"player_distance", "hostile_distance", "passive_distance", "other_distance"}) {
			visibleWhen(s, id, entityDistance::get);
		}
		hideItemFrames = s.bool(p, ENTITIES, "hide_item_frames", false, null);
		hidePaintings = s.bool(p, ENTITIES, "hide_paintings", false, null);
		hideArmorStands = s.bool(p, ENTITIES, "hide_armor_stands", false, null);
		hideXpOrbs = s.bool(p, ENTITIES, "hide_xp_orbs", false, null);
		hideFireworks = s.bool(p, ENTITIES, "hide_fireworks", false, null);
		hideFallingBlocks = s.bool(p, ENTITIES, "hide_falling_blocks", false, null);
		lightGlint = s.bool(p, ENTITIES, "light_glint", false, null);

		tileDistance = s.slider(p, TILES, "tile_distance", 64, 8, 64, 4, Optimizations::blocks, null);
		hideSkulls = s.bool(p, TILES, "hide_skulls", false, null);
		hideSigns = s.bool(p, TILES, "hide_signs", false, null);
		hideEnchantBooks = s.bool(p, TILES, "hide_enchant_books", false, null);
		hideBanners = s.bool(p, TILES, "hide_banners", false, null);
		hideBeaconBeams = s.bool(p, TILES, "hide_beacon_beams", false, null);
		hideEndPortals = s.bool(p, TILES, "hide_end_portals", false, null);

		hideGrass = s.bool(p, WORLD, "hide_grass", false, v -> reloadFoliage());
		hideFlowers = s.bool(p, WORLD, "hide_flowers", false, v -> reloadFoliage());
		hideDeadBushes = s.bool(p, WORLD, "hide_dead_bushes", false, v -> reloadFoliage());
		disableFog = s.bool(p, WORLD, "disable_fog", false, null);
		maxParticles = s.slider(p, WORLD, "max_particles", 4000, 100, 4000, 100,
			v -> v >= 4000 ? I18n.format("rago.value.vanilla") : Integer.toString(v), null);
		particleCollisions = s.bool(p, WORLD, "particle_collisions", true, null);

		fastWorldSwitch = s.bool(p, ENGINE, "fast_world_switch", true, null);
		unstackedItems = s.bool(p, ENGINE, "unstacked_items", false, null);
		skipGlErrors = s.bool(p, ENGINE, "skip_gl_errors", true, v -> applyGlErrorChecking());
		hudCache = s.bool(p, ENGINE, "hud_cache", true, null);
		hudCacheRate = s.slider(p, ENGINE, "hud_cache_rate", 144, 30, 360, 6, v -> v + " FPS", null);
		visibleWhen(s, "hud_cache_rate", hudCache::get);
		engine = new EngineTweaks(fastWorldSwitch::get, unstackedItems::get, particleCollisions::get, lightGlint::get);
	}

	/** Whether Rago's HUD is drawn into an image a fixed number of times per second. */
	public boolean hudCache() {
		return hudCache.get();
	}

	/** How many times per second the cached HUD is drawn again. */
	public int hudCacheRate() {
		return hudCacheRate.get();
	}

	/** Settings read by the game hooks. */
	public EngineTweaks engine() {
		return engine;
	}

	/**
	 * Vanilla asks the driver for errors (glGetError) several times a frame and only logs them;
	 * on some drivers each call waits for the GPU. Patcher turns it off the same way.
	 */
	private void applyGlErrorChecking() {
		try {
			ObfuscationReflectionHelper.setPrivateValue(Minecraft.class, Minecraft.getMinecraft(), !skipGlErrors.get(),
				"enableGLErrorChecking", "field_175619_R");
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("Could not change GL error checking", e);
		}
	}

	private static void visibleWhen(Settings s, String id, BooleanSupplier condition) {
		for (Setting row : s.rows()) {
			if (row.id().equals(id)) {
				row.visibleWhen(condition);
			}
		}
	}

	private static String fpsLabel(int v) {
		return v >= FrameLimit.UNLIMITED ? I18n.format("rago.value.unlimited") : v + " FPS";
	}

	private static String blocks(int v) {
		return I18n.format("rago.value.blocks", v);
	}

	/** Applies settings that need it once the config is loaded and the game is up. */
	public void start() {
		applyFrameCaps();
		applyGlErrorChecking();
		Minecraft mc = Minecraft.getMinecraft();
		try {
			List<IResourcePack> packs = ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, mc, "defaultResourcePacks", "field_110449_ao");
			foliagePack = new FoliagePack(hideGrass::get, hideFlowers::get, hideDeadBushes::get);
			packs.add(foliagePack);
			if (hideGrass.get() || hideFlowers.get() || hideDeadBushes.get()) {
				mc.refreshResources();
			}
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("Could not add the foliage pack; hiding grass and flowers is unavailable", e);
		}
	}

	private void applyFrameCaps() {
		FrameLimit.setMenuFps(menuFps.get());
		FrameLimit.setUnfocused(unfocusedCap.get(), unfocusedFps.get());
		FrameLimit.setMinimizedCap(minimizedCap.get());
	}

	/** Entities and tile entities hidden behind blocks, for the class patches and other features. */
	public OcclusionCuller culler() {
		return culler;
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			long t = gg.rago.debug.FrameProfile.ON ? gg.rago.debug.FrameProfile.start() : 0;
			culler.update(Minecraft.getMinecraft(), event.renderTickTime);
			if (gg.rago.debug.FrameProfile.ON) {
				gg.rago.debug.FrameProfile.add(gg.rago.debug.FrameProfile.CULLING, System.nanoTime() - t);
				gg.rago.debug.FrameProfile.frame();
			}
		}
	}

	/** One line in the F3 screen, so the effect of culling can be checked. */
	@SubscribeEvent
	public void onDebugText(RenderGameOverlayEvent.Text event) {
		if (Minecraft.getMinecraft().gameSettings.showDebugInfo && occlusionCulling.get()) {
			event.left.add("");
			event.left.add("[Rago] Occlusion culling: " + culler.hiddenCount() + " hidden");
		}
	}

	private void reloadFoliage() {
		if (foliagePack != null) {
			Minecraft.getMinecraft().scheduleResourcesRefresh();
		}
	}

	// ---- per tick -------------------------------------------------------------------------------

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			return;
		}
		if (mc.theWorld != lastWorld) {
			// entity ids restart in a new world, and Entity.equals compares ids
			originalWeights.clear();
			lastWorld = mc.theWorld;
		}
		ticks++;
		if (ticks % 4 == 0) {
			updateEntities(mc);
		}
		if (ticks % 100 == 1) {
			wrapTileRenderers();
			applyArrowLayers();
		}
		capParticles(mc.effectRenderer);
	}

	private void updateEntities(Minecraft mc) {
		Entity viewer = mc.getRenderViewEntity();
		for (Entity e : mc.theWorld.loadedEntityList) {
			if (e == viewer || e == mc.thePlayer) {
				continue;
			}
			Double original = originalWeights.get(e);
			double vanilla = original != null ? original : e.renderDistanceWeight;
			Double want = desiredWeight(e, vanilla);
			if (want == null) {
				if (original != null) {
					e.renderDistanceWeight = original;
					originalWeights.remove(e);
				}
			} else {
				if (original == null) {
					originalWeights.put(e, e.renderDistanceWeight);
				}
				e.renderDistanceWeight = want;
			}
		}
	}

	/** The render distance weight to use, or null to keep the entity's own. */
	private Double desiredWeight(Entity e, double vanilla) {
		if (hidden(e)) {
			return 0.0;
		}
		if (!entityDistance.get()) {
			return null;
		}
		int blocks;
		if (e instanceof EntityPlayer) {
			blocks = playerDistance.get();
		} else if (e instanceof IMob) {
			blocks = hostileDistance.get();
		} else if (e instanceof EntityLivingBase && !(e instanceof EntityArmorStand)) {
			blocks = passiveDistance.get();
		} else {
			blocks = otherDistance.get();
		}
		// Entity.isInRangeToRenderDist: distance < averageEdge * 64 * weight
		AxisAlignedBB box = e.getEntityBoundingBox();
		double edge = box == null ? 1 : box.getAverageEdgeLength();
		if (Double.isNaN(edge) || edge <= 0) {
			edge = 1;
		}
		return Math.min(vanilla, blocks / (edge * 64));
	}

	private boolean hidden(Entity e) {
		return hideItemFrames.get() && e instanceof EntityItemFrame
			|| hidePaintings.get() && e instanceof EntityPainting
			|| hideArmorStands.get() && e instanceof EntityArmorStand
			|| hideXpOrbs.get() && e instanceof EntityXPOrb
			|| hideFireworks.get() && e instanceof EntityFireworkRocket
			|| hideFallingBlocks.get() && e instanceof EntityFallingBlock;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void wrapTileRenderers() {
		TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
		Map<Class<? extends TileEntity>, TileEntitySpecialRenderer<? extends TileEntity>> map = dispatcher.mapSpecialRenderers;
		for (Map.Entry<Class<? extends TileEntity>, TileEntitySpecialRenderer<? extends TileEntity>> entry : map.entrySet()) {
			TileEntitySpecialRenderer r = entry.getValue();
			if (r == null || r instanceof GatedTileRenderer) {
				continue;
			}
			BooleanSupplier hide = hideRule(r);
			GatedTileRenderer gated = new GatedTileRenderer(r, hide, (Predicate<TileEntity>) culler::isHidden, () -> {
				int d = tileDistance.get();
				return d >= 64 ? 0 : (double) d * d;
			});
			gated.setRendererDispatcher(dispatcher);
			entry.setValue(gated);
		}
	}

	private BooleanSupplier hideRule(TileEntitySpecialRenderer<?> r) {
		if (r instanceof TileEntitySkullRenderer) {
			return hideSkulls::get;
		}
		if (r instanceof TileEntitySignRenderer) {
			return hideSigns::get;
		}
		if (r instanceof TileEntityEnchantmentTableRenderer) {
			return hideEnchantBooks::get;
		}
		if (r instanceof TileEntityBannerRenderer) {
			return hideBanners::get;
		}
		if (r instanceof TileEntityBeaconRenderer) {
			return hideBeaconBeams::get;
		}
		if (r instanceof TileEntityEndPortalRenderer) {
			return hideEndPortals::get;
		}
		return () -> false;
	}

	/** Removes or puts back the layer that draws arrows stuck in players. */
	private void applyArrowLayers() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.getRenderManager() == null) {
			return;
		}
		if (hideStuckArrows.get()) {
			for (RenderPlayer render : mc.getRenderManager().getSkinMap().values()) {
				List<LayerRenderer<?>> layers = layers(render);
				if (layers == null) {
					continue;
				}
				for (int i = layers.size() - 1; i >= 0; i--) {
					if (layers.get(i) instanceof LayerArrow) {
						removedArrowLayers.add(new RemovedLayer(layers, layers.remove(i)));
					}
				}
			}
		} else if (!removedArrowLayers.isEmpty()) {
			for (RemovedLayer r : removedArrowLayers) {
				if (!r.owner.contains(r.layer)) {
					r.owner.add(r.layer);
				}
			}
			removedArrowLayers.clear();
		}
	}

	private static List<LayerRenderer<?>> layers(RendererLivingEntity<?> render) {
		try {
			return ObfuscationReflectionHelper.getPrivateValue(RendererLivingEntity.class, render, "layerRenderers", "field_177097_h");
		} catch (RuntimeException e) {
			return null;
		}
	}

	private List<EntityFX>[][] fxLayers;
	private EffectRenderer fxOwner;
	private boolean fxLayersFailed;

	private void capParticles(EffectRenderer renderer) {
		int cap = maxParticles.get();
		if (cap >= 4000 || renderer == null || fxLayersFailed) {
			return;
		}
		if (fxLayers == null || fxOwner != renderer) {
			fxOwner = renderer;
			try {
				fxLayers = ObfuscationReflectionHelper.getPrivateValue(EffectRenderer.class, renderer, "fxLayers", "field_78876_b");
			} catch (RuntimeException e) {
				fxLayersFailed = true;
				RagoClient.LOG.warn("Particle cap unavailable", e);
				return;
			}
		}
		for (List<EntityFX>[] layer : fxLayers) {
			for (List<EntityFX> list : layer) {
				int excess = list.size() - cap;
				if (excess > 0) {
					list.subList(0, excess).clear();
				}
			}
		}
	}

	// ---- fog ------------------------------------------------------------------------------------

	@SubscribeEvent
	public void onFogDensity(EntityViewRenderEvent.FogDensity event) {
		if (!disableFog.get() || keepsFog(event.block.getMaterial(), event.entity instanceof EntityLivingBase
			&& ((EntityLivingBase) event.entity).isPotionActive(net.minecraft.potion.Potion.blindness))) {
			return;
		}
		event.density = 0f;
		event.setCanceled(true);
	}

	/**
	 * Water, lava and blindness keep their fog: only the distance haze goes. Blindness is the black
	 * fog a few blocks around you (a Bed Wars trap gives it), and cancelling the fog would take it away.
	 */
	static boolean keepsFog(Material m, boolean blind) {
		return blind || m == Material.water || m == Material.lava;
	}
}
