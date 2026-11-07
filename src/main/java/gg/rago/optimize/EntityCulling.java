package gg.rago.optimize;

import gg.rago.core.RagoClient;
import gg.rago.debug.FrameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.ARBOcclusionQuery;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Entity culling that gives what PolyPatcher's gives (made from its settings and from what it was
 * seen doing in game, never from its code): a mob, player or armor stand hidden behind the world
 * is not drawn, in first and third person, but its name still is, so names show through walls as
 * in vanilla. Marker armor stands (floating texts), Ender Dragons and Withers are always drawn,
 * each with its setting, and nothing is culled while an OptiFine shader pack is on. Other entities
 * (items, arrows, minecarts) are always drawn. Chests, skulls and the like are culled too, which
 * is Rago's own.
 *
 * <p>Whether something can be seen is asked of the graphics card, which knows exactly: just
 * before the entities, when only the world is in the depth buffer, a box a little larger than
 * each candidate is drawn without colour or depth inside an occlusion query, which counts the
 * pixels that would show. So pistons and other full blocks hide what is behind them, glass,
 * barriers and water do not, and a one block window shows what is in line with it. The count is
 * read on a later frame, once the card has it, without waiting for it. Something new in view is
 * drawn until its first count says it is hidden.
 */
public final class EntityCulling {
	/** Milliseconds between two counts for the same entity, by setting: PolyPatcher's choices, 50 first. */
	static final int[] INTERVAL_MS = {50, 25, 10};
	/** How far around its box an entity counts as seen, in blocks: a player's arms stick out by as much. */
	static final double GROW = 0.2;
	/** Frames without an answer before a count is given up (a driver that never answers). */
	private static final int ANSWER_FRAMES = 30;
	/** Frames out of view before an entity's query is let go. */
	private static final int FORGET_FRAMES = 64;
	private static final int POOL = 256;

	private final BooleanSupplier enabled;
	private final IntSupplier interval;
	private final BooleanSupplier smart;
	private final BooleanSupplier drawDragons;
	private final BooleanSupplier drawWithers;
	private final BooleanSupplier playerNames;
	private final BooleanSupplier entityNames;
	private final BooleanSupplier standNames;
	private final BooleanSupplier standRules;
	private final BooleanSupplier cullTiles;

	private final Map<Entity, Query> entities = new IdentityHashMap<>();
	private final Map<TileEntity, Query> tiles = new IdentityHashMap<>();
	private final int[] pool = new int[POOL];
	private int pooled;
	private World world;
	/** Counts the world's frames: one per entity pass. */
	private int frame;
	/** Whether this frame's entities go through the culling. */
	private boolean active;
	/** Between the start of this frame's entity pass and the end of its world: entities drawn elsewhere (a menu) are drawn. */
	private boolean inWorld;
	private int hiddenNow;
	/** 0 not checked yet, 1 OpenGL 1.5, 2 ARB_occlusion_query, -1 no occlusion queries. */
	private int api;
	private int cube;
	private MethodHandle renderOutlines;
	private boolean outlinesFailed;

	/** One entity's or tile entity's question to the graphics card. */
	static final class Query {
		int id;
		boolean pending;
		boolean hidden;
		long askedNs;
		int askedFrame;
		/** The last frame it was in view. */
		int seen = Integer.MIN_VALUE + FORGET_FRAMES;
		/** A tile entity's box (they do not move), or null: never culled. */
		AxisAlignedBB box;
	}

	public EntityCulling(BooleanSupplier enabled, IntSupplier interval, BooleanSupplier smart, BooleanSupplier drawDragons,
		BooleanSupplier drawWithers, BooleanSupplier playerNames, BooleanSupplier entityNames, BooleanSupplier standNames,
		BooleanSupplier standRules, BooleanSupplier cullTiles) {
		this.enabled = enabled;
		this.interval = interval;
		this.smart = smart;
		this.drawDragons = drawDragons;
		this.drawWithers = drawWithers;
		this.playerNames = playerNames;
		this.entityNames = entityNames;
		this.standNames = standNames;
		this.standRules = standRules;
		this.cullTiles = cullTiles;
	}

	/** How many entities and tile entities are culled this frame, for the debug screen. */
	public int hiddenCount() {
		return active ? hiddenNow : 0;
	}

	public boolean isOn() {
		return enabled.getAsBoolean();
	}

	// ---- the questions --------------------------------------------------------------------------

	/** RenderGlobal.renderEntities, as it starts: last answers read, new questions asked. */
	public void beforeEntities(Entity view, ICamera camera, float partialTicks) {
		if (MinecraftForgeClient.getRenderPass() != 0) {
			return;
		}
		frame++;
		inWorld = true;
		hiddenNow = 0;
		World w = view == null ? null : view.worldObj;
		active = w != null && camera != null && enabled.getAsBoolean() && !(smart.getAsBoolean() && OptiFineFields.shadersOn())
			&& supported();
		if (w != world || !active) {
			releaseAll();
			world = w;
			if (!active) {
				return;
			}
		}
		long t = FrameProfile.ON ? FrameProfile.start() : 0;
		// where the game puts the camera for this frame (RenderGlobal.renderEntities)
		double camX = view.prevPosX + (view.posX - view.prevPosX) * partialTicks;
		double camY = view.prevPosY + (view.posY - view.prevPosY) * partialTicks;
		double camZ = view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks;
		double rx = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
		double ry = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
		double rz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
		long now = System.nanoTime();
		long every = intervalNs();
		Minecraft mc = Minecraft.getMinecraft();
		RenderManager rm = mc.getRenderManager();
		boolean drawing = false;

		List<Entity> list = w.loadedEntityList;
		for (int i = 0, n = list.size(); i < n; i++) {
			Entity e = list.get(i);
			if (!(e instanceof EntityLivingBase) || alwaysDrawn(e, view, mc.thePlayer) || !rm.shouldRender(e, camera, camX, camY, camZ)) {
				continue;
			}
			Query q = entities.get(e);
			if (q == null) {
				q = new Query();
				entities.put(e, q);
			}
			if (update(q, now, every)) {
				if (!drawing) {
					drawing = begin();
					if (!drawing) {
						break;
					}
				}
				AxisAlignedBB b = e.getEntityBoundingBox();
				// the box where the entity is drawn this frame, not where it was at the last tick
				double dx = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks - e.posX - rx;
				double dy = e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks - e.posY - ry;
				double dz = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks - e.posZ - rz;
				ask(q, now, b.minX + dx - GROW, b.minY + dy - GROW, b.minZ + dz - GROW, b.maxX + dx + GROW, b.maxY + dy + GROW,
					b.maxZ + dz + GROW);
			}
			if (q.hidden) {
				hiddenNow++;
			}
		}

		if (cullTiles.getAsBoolean()) {
			TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
			List<TileEntity> tl = w.loadedTileEntityList;
			for (int i = 0, n = tl.size(); i < n; i++) {
				TileEntity te = tl.get(i);
				Query q = tiles.get(te);
				if (q == null) {
					q = new Query();
					q.box = dispatcher.getSpecialRenderer(te) == null ? null : tileBox(te);
					tiles.put(te, q);
				}
				if (q.box == null || te.isInvalid() || te.getDistanceSq(rx, ry, rz) >= te.getMaxRenderDistanceSquared()
					|| !camera.isBoundingBoxInFrustum(q.box)) {
					continue;
				}
				if (update(q, now, every)) {
					if (!drawing) {
						drawing = begin();
						if (!drawing) {
							break;
						}
					}
					AxisAlignedBB b = q.box;
					ask(q, now, b.minX - rx, b.minY - ry, b.minZ - rz, b.maxX - rx, b.maxY - ry, b.maxZ - rz);
				}
				if (q.hidden) {
					hiddenNow++;
				}
			}
		} else if (!tiles.isEmpty()) {
			release(tiles);
		}

		if (drawing) {
			end();
		}
		if ((frame & 63) == 0) {
			forget(entities);
			forget(tiles);
		}
		if (FrameProfile.ON) {
			FrameProfile.add(FrameProfile.CULLING, System.nanoTime() - t);
		}
	}

	/**
	 * Reads the last answer if the card has it, and says whether to ask again now. Back in view
	 * after a while (or new), an old answer no longer counts: drawn until a fresh one.
	 */
	private boolean update(Query q, long now, long every) {
		if (q.seen != frame - 1) {
			q.hidden = false;
			q.pending = false;
			q.askedNs = now - every;
		} else if (q.pending) {
			if (answered(q.id)) {
				q.hidden = samples(q.id) == 0;
				q.pending = false;
			} else if (frame - q.askedFrame > ANSWER_FRAMES) {
				q.hidden = false;
				q.pending = false;
			}
		}
		q.seen = frame;
		return !q.pending && now - q.askedNs >= every;
	}

	/** Draws the box (relative to the camera) inside an occlusion query. */
	private void ask(Query q, long now, double x0, double y0, double z0, double x1, double y1, double z1) {
		if (q.id == 0) {
			q.id = pooled > 0 ? pool[--pooled] : newQuery();
		}
		beginQuery(q.id);
		GL11.glPushMatrix();
		GL11.glTranslated(x0, y0, z0);
		GL11.glScaled(x1 - x0, y1 - y0, z1 - z0);
		GL11.glCallList(cube);
		GL11.glPopMatrix();
		endQuery();
		q.pending = true;
		q.askedNs = now;
		q.askedFrame = frame;
	}

	private long intervalNs() {
		int i = interval.getAsInt();
		return INTERVAL_MS[Math.max(0, Math.min(INTERVAL_MS.length - 1, i))] * 1_000_000L;
	}

	/** Never culled: the camera's own entity and what it rides, and the exceptions the settings keep. */
	private boolean alwaysDrawn(Entity e, Entity view, Entity player) {
		return e == view || e == player || e.riddenByEntity == view || e.ridingEntity == view
			|| keptBySettings(e, standRules.getAsBoolean(), drawDragons.getAsBoolean(), drawWithers.getAsBoolean());
	}

	/** Marker armor stands (with the armor stand rules), Ender Dragons and Withers, as set. */
	static boolean keptBySettings(Entity e, boolean standRules, boolean dragons, boolean withers) {
		return standRules && e instanceof EntityArmorStand && ((EntityArmorStand) e).hasMarker()
			|| dragons && e instanceof EntityDragon
			|| withers && e instanceof EntityWither;
	}

	/** The box drawn for a tile entity, or null to never cull it (no box: signs and banners; beams and portals reach far). */
	private static AxisAlignedBB tileBox(TileEntity te) {
		if (te instanceof TileEntityBeacon || te instanceof TileEntityEndPortal) {
			return null;
		}
		AxisAlignedBB box = te.getRenderBoundingBox();
		if (box == null || box == TileEntity.INFINITE_EXTENT_AABB || box.maxX - box.minX > 16 || box.maxY - box.minY > 16
			|| box.maxZ - box.minZ > 16) {
			return null;
		}
		return box;
	}

	// ---- the answers ----------------------------------------------------------------------------

	/** Forge's RenderLivingEvent.Pre, before anyone else: a hidden entity is skipped, its name kept as set. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onLivingPre(RenderLivingEvent.Pre<EntityLivingBase> event) {
		if (!active || !inWorld) {
			return;
		}
		Query q = entities.get(event.entity);
		if (q == null || !q.hidden || q.seen != frame || outlines(event.renderer)) {
			return;
		}
		event.setCanceled(true);
		// what RendererLivingEntity.doRender leaves behind, which what follows may count on
		GlStateManager.enableAlpha();
		GlStateManager.depthMask(true);
		GlStateManager.disableRescaleNormal();
		GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.enableTexture2D();
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.enableCull();
		if (keepsName(event.entity, playerNames.getAsBoolean(), entityNames.getAsBoolean(), standNames.getAsBoolean())) {
			event.renderer.renderName(event.entity, event.x, event.y, event.z);
		}
	}

	/** Whether a culled entity's name is still drawn: players, armor stands and other entities each have their setting. */
	static boolean keepsName(Entity e, boolean players, boolean others, boolean stands) {
		return e instanceof EntityPlayer ? players : e instanceof EntityArmorStand ? stands : others;
	}

	/** GatedTileRenderer: whether a tile entity is hidden this frame. */
	public boolean tileHidden(TileEntity te) {
		if (!active || !inWorld) {
			return false;
		}
		Query q = tiles.get(te);
		return q != null && q.hidden && q.seen == frame;
	}

	@SubscribeEvent
	public void onWorldDrawn(RenderWorldLastEvent event) {
		inWorld = false;
	}

	/** The renderer's renderOutlines: the spectators' glowing pass shows players through walls, so nothing is culled in it. */
	private boolean outlines(RendererLivingEntity<?> renderer) {
		if (renderOutlines == null) {
			if (outlinesFailed) {
				return false;
			}
			try {
				renderOutlines = MethodHandles.lookup()
					.unreflectGetter(ReflectionHelper.findField(RendererLivingEntity.class, "field_177098_i", "renderOutlines"))
					.asType(MethodType.methodType(boolean.class, RendererLivingEntity.class));
			} catch (RuntimeException | IllegalAccessException e) {
				outlinesFailed = true;
				return false;
			}
		}
		try {
			return (boolean) renderOutlines.invoke(renderer);
		} catch (Throwable e) {
			return false;
		}
	}

	// ---- OpenGL ---------------------------------------------------------------------------------

	private boolean supported() {
		if (api == 0) {
			api = -1;
			try {
				ContextCapabilities caps = GLContext.getCapabilities();
				if (caps.OpenGL15 && GL15.glGetQueryi(GL15.GL_SAMPLES_PASSED, GL15.GL_QUERY_COUNTER_BITS) > 0) {
					api = 1;
				} else if (caps.GL_ARB_occlusion_query
					&& ARBOcclusionQuery.glGetQueryiARB(ARBOcclusionQuery.GL_SAMPLES_PASSED_ARB, ARBOcclusionQuery.GL_QUERY_COUNTER_BITS_ARB) > 0) {
					api = 2;
				}
			} catch (RuntimeException | LinkageError e) {
				RagoClient.LOG.warn("Occlusion queries unavailable", e);
			}
			if (api < 0) {
				RagoClient.LOG.warn("This graphics driver cannot count pixels (occlusion queries): entity culling is off");
			}
		}
		return api > 0;
	}

	/** Colour and depth writes off, depth test on, nothing that could drop a pixel: the state for the boxes. */
	private boolean begin() {
		if (cube == 0) {
			cube = GL11.glGenLists(1);
			if (cube == 0) {
				api = -1;
				return false;
			}
			GL11.glNewList(cube, GL11.GL_COMPILE);
			GL11.glBegin(GL11.GL_QUADS);
			// a unit cube, every face (face culling is off while it is drawn)
			GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(1, 0, 0); GL11.glVertex3f(1, 0, 1); GL11.glVertex3f(0, 0, 1);
			GL11.glVertex3f(0, 1, 0); GL11.glVertex3f(0, 1, 1); GL11.glVertex3f(1, 1, 1); GL11.glVertex3f(1, 1, 0);
			GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(0, 1, 0); GL11.glVertex3f(1, 1, 0); GL11.glVertex3f(1, 0, 0);
			GL11.glVertex3f(0, 0, 1); GL11.glVertex3f(1, 0, 1); GL11.glVertex3f(1, 1, 1); GL11.glVertex3f(0, 1, 1);
			GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(0, 0, 1); GL11.glVertex3f(0, 1, 1); GL11.glVertex3f(0, 1, 0);
			GL11.glVertex3f(1, 0, 0); GL11.glVertex3f(1, 1, 0); GL11.glVertex3f(1, 1, 1); GL11.glVertex3f(1, 0, 1);
			GL11.glEnd();
			GL11.glEndList();
		}
		// pushed and popped as a whole, so GlStateManager's record of the state stays true
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LEQUAL);
		GL11.glDepthMask(false);
		GL11.glColorMask(false, false, false, false);
		return true;
	}

	private static void end() {
		GL11.glPopAttrib();
	}

	private int newQuery() {
		return api == 1 ? GL15.glGenQueries() : ARBOcclusionQuery.glGenQueriesARB();
	}

	private void beginQuery(int id) {
		if (api == 1) {
			GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, id);
		} else {
			ARBOcclusionQuery.glBeginQueryARB(ARBOcclusionQuery.GL_SAMPLES_PASSED_ARB, id);
		}
	}

	private void endQuery() {
		if (api == 1) {
			GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
		} else {
			ARBOcclusionQuery.glEndQueryARB(ARBOcclusionQuery.GL_SAMPLES_PASSED_ARB);
		}
	}

	private boolean answered(int id) {
		return (api == 1 ? GL15.glGetQueryObjecti(id, GL15.GL_QUERY_RESULT_AVAILABLE)
			: ARBOcclusionQuery.glGetQueryObjectiARB(id, ARBOcclusionQuery.GL_QUERY_RESULT_AVAILABLE_ARB)) != 0;
	}

	private int samples(int id) {
		return api == 1 ? GL15.glGetQueryObjecti(id, GL15.GL_QUERY_RESULT)
			: ARBOcclusionQuery.glGetQueryObjectiARB(id, ARBOcclusionQuery.GL_QUERY_RESULT_ARB);
	}

	private void deleteQuery(int id) {
		if (api == 1) {
			GL15.glDeleteQueries(id);
		} else if (api == 2) {
			ARBOcclusionQuery.glDeleteQueriesARB(id);
		}
	}

	private void free(Query q) {
		if (q.id != 0) {
			if (pooled < POOL) {
				pool[pooled++] = q.id;
			} else {
				deleteQuery(q.id);
			}
			q.id = 0;
		}
	}

	/** Lets go of what has been out of view for a while (or left the world). */
	private <K> void forget(Map<K, Query> map) {
		for (Iterator<Query> it = map.values().iterator(); it.hasNext(); ) {
			Query q = it.next();
			if (frame - q.seen > FORGET_FRAMES) {
				free(q);
				it.remove();
			}
		}
	}

	private <K> void release(Map<K, Query> map) {
		for (Query q : map.values()) {
			free(q);
		}
		map.clear();
	}

	private void releaseAll() {
		if (!entities.isEmpty()) {
			release(entities);
		}
		if (!tiles.isEmpty()) {
			release(tiles);
		}
	}
}
