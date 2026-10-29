package gg.rago.optimize;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Skips drawing entities and tile entities that are completely hidden behind solid blocks, the
 * way occlusion culling mods do. For each one it casts rays from the camera to the centre and the
 * corners of its box through the block grid; if every ray hits an opaque block it is hidden.
 *
 * <p>The tests run on the render thread (reading the world from another thread is not safe in
 * 1.8) with a small time budget per frame, walking the entity and tile entity lists round-robin,
 * so a scene with hundreds of entities refreshes every few frames. Anything not tested yet, and
 * everything in third person, counts as visible: culling can only ever skip what was checked.
 */
public final class OcclusionCuller {
	private static final long BUDGET_NS = 350_000L;
	/**
	 * Least time between two passes. At a few hundred FPS a pass on every frame would cost a large
	 * share of each frame for no visible gain; 120 passes a second keep up with the camera.
	 */
	private static final long PASS_INTERVAL_NS = 8_000_000L;
	private long lastPassNs;
	/** Closer than this, things are always drawn: the camera can be inside their box. */
	private static final double ALWAYS_VISIBLE_SQ = 3.5 * 3.5;
	private static final int MAX_RAY_STEPS = 512;

	private final BooleanSupplier enabled;
	private final BooleanSupplier cullPlayers;
	private final BooleanSupplier cullTiles;
	private final Set<Entity> hiddenEntities = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<TileEntity> hiddenTiles = Collections.newSetFromMap(new IdentityHashMap<>());
	private World world;
	private int entityCursor;
	private int tileCursor;
	private int frames;

	// camera and the chunk last read by a ray
	private double cx;
	private double cy;
	private double cz;
	private Chunk chunk;
	private int chunkX;
	private int chunkZ;

	public OcclusionCuller(BooleanSupplier enabled, BooleanSupplier cullPlayers, BooleanSupplier cullTiles) {
		this.enabled = enabled;
		this.cullPlayers = cullPlayers;
		this.cullTiles = cullTiles;
	}

	public boolean isHidden(Entity e) {
		return !hiddenEntities.isEmpty() && hiddenEntities.contains(e);
	}

	public boolean isHidden(TileEntity te) {
		return !hiddenTiles.isEmpty() && hiddenTiles.contains(te);
	}

	/** How many entities and tile entities are culled right now, for the debug HUD. */
	public int hiddenCount() {
		return hiddenEntities.size() + hiddenTiles.size();
	}

	/** Called at the start of every frame. */
	public void update(Minecraft mc, float partialTicks) {
		Entity viewer = mc.getRenderViewEntity();
		if (!enabled.getAsBoolean() || mc.theWorld == null || viewer == null || mc.gameSettings.thirdPersonView != 0) {
			clear();
			world = mc.theWorld;
			return;
		}
		if (mc.theWorld != world) {
			clear();
			world = mc.theWorld;
		}
		long now = System.nanoTime();
		if (now - lastPassNs < PASS_INTERVAL_NS) {
			return;
		}
		lastPassNs = now;
		cx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
		cy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks + viewer.getEyeHeight();
		cz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
		chunk = null;
		long deadline = now + BUDGET_NS;

		List<Entity> entities = world.loadedEntityList;
		int n = entities.size();
		boolean players = cullPlayers.getAsBoolean();
		for (int i = 0; i < n; i++) {
			if ((i & 3) == 0 && System.nanoTime() > deadline) {
				break;
			}
			entityCursor = entityCursor >= n - 1 ? 0 : entityCursor + 1;
			Entity e = entities.get(entityCursor);
			if (neverCulled(e, viewer, players) || visible(e.getEntityBoundingBox(), 0.1)) {
				hiddenEntities.remove(e);
			} else {
				hiddenEntities.add(e);
			}
		}

		if (cullTiles.getAsBoolean()) {
			List<TileEntity> tiles = world.loadedTileEntityList;
			int t = tiles.size();
			for (int i = 0; i < t; i++) {
				if ((i & 3) == 0 && System.nanoTime() > deadline) {
					break;
				}
				tileCursor = tileCursor >= t - 1 ? 0 : tileCursor + 1;
				TileEntity te = tiles.get(tileCursor);
				AxisAlignedBB box = tileBox(te);
				if (box == null || visible(box, -0.01)) {
					hiddenTiles.remove(te);
				} else {
					hiddenTiles.add(te);
				}
			}
		} else if (!hiddenTiles.isEmpty()) {
			hiddenTiles.clear();
		}

		if (++frames % 600 == 0) {
			// forget entities and tile entities that left the world
			hiddenEntities.removeIf(e -> e.isDead || e.worldObj != world);
			hiddenTiles.removeIf(TileEntity::isInvalid);
		}
	}

	private void clear() {
		hiddenEntities.clear();
		hiddenTiles.clear();
	}

	private static boolean neverCulled(Entity e, Entity viewer, boolean cullPlayers) {
		return e == viewer || e.ignoreFrustumCheck || e.riddenByEntity == viewer || e.ridingEntity == viewer
			|| e instanceof EntityPlayer && !cullPlayers;
	}

	/** The box drawn for a tile entity, or null to never cull it (beams and portals reach far). */
	private static AxisAlignedBB tileBox(TileEntity te) {
		if (te instanceof TileEntityBeacon || te instanceof TileEntityEndPortal) {
			return null;
		}
		AxisAlignedBB box = te.getRenderBoundingBox();
		if (box == null || box == TileEntity.INFINITE_EXTENT_AABB || box.maxX - box.minX > 16) {
			return null;
		}
		return box;
	}

	/** Whether any of the box's centre and eight corners (grown by {@code grow}) can be seen. */
	private boolean visible(AxisAlignedBB box, double grow) {
		if (box == null) {
			return true;
		}
		double x0 = box.minX - grow;
		double y0 = box.minY - grow;
		double z0 = box.minZ - grow;
		double x1 = box.maxX + grow;
		double y1 = box.maxY + grow;
		double z1 = box.maxZ + grow;
		double mx = (x0 + x1) / 2;
		double my = (y0 + y1) / 2;
		double mz = (z0 + z1) / 2;
		double dx = mx - cx;
		double dy = my - cy;
		double dz = mz - cz;
		if (dx * dx + dy * dy + dz * dz < ALWAYS_VISIBLE_SQ
			|| cx >= x0 && cx <= x1 && cy >= y0 && cy <= y1 && cz >= z0 && cz <= z1) {
			return true;
		}
		if (clear(mx, my, mz)) {
			return true;
		}
		for (int i = 0; i < 8; i++) {
			if (clear((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a block is opaque; lets tests run the traversal without a world. */
	interface Blocks {
		boolean opaque(int x, int y, int z);
	}

	private boolean clear(double tx, double ty, double tz) {
		return rayClear(cx, cy, cz, tx, ty, tz, this::opaque);
	}

	/**
	 * Walks the blocks on the line from (ax, ay, az) to (tx, ty, tz) (Amanatides-Woo traversal)
	 * and returns false at the first opaque one. The start and end blocks are not checked.
	 */
	static boolean rayClear(double ax, double ay, double az, double tx, double ty, double tz, Blocks blocks) {
		double dx = tx - ax;
		double dy = ty - ay;
		double dz = tz - az;
		int x = floor(ax);
		int y = floor(ay);
		int z = floor(az);
		int ex = floor(tx);
		int ey = floor(ty);
		int ez = floor(tz);
		int sx = dx > 0 ? 1 : -1;
		int sy = dy > 0 ? 1 : -1;
		int sz = dz > 0 ? 1 : -1;
		double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
		double tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
		double tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
		double tmx = dx == 0 ? Double.MAX_VALUE : (sx > 0 ? x + 1 - ax : ax - x) * tdx;
		double tmy = dy == 0 ? Double.MAX_VALUE : (sy > 0 ? y + 1 - ay : ay - y) * tdy;
		double tmz = dz == 0 ? Double.MAX_VALUE : (sz > 0 ? z + 1 - az : az - z) * tdz;
		for (int step = 0; step < MAX_RAY_STEPS; step++) {
			if (Math.min(Math.min(tmx, tmy), tmz) > 1) {
				return true; // the next block boundary is past the target: we are in its block
			}
			if (tmx < tmy && tmx < tmz) {
				x += sx;
				tmx += tdx;
			} else if (tmy < tmz) {
				y += sy;
				tmy += tdy;
			} else {
				z += sz;
				tmz += tdz;
			}
			if (x == ex && y == ey && z == ez) {
				return true;
			}
			if (blocks.opaque(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	private boolean opaque(int x, int y, int z) {
		if (y < 0 || y > 255) {
			return false;
		}
		int ccx = x >> 4;
		int ccz = z >> 4;
		if (chunk == null || ccx != chunkX || ccz != chunkZ) {
			chunk = world.getChunkFromChunkCoords(ccx, ccz);
			chunkX = ccx;
			chunkZ = ccz;
		}
		Block block = chunk.getBlock(x, y, z);
		return block.isOpaqueCube();
	}

	private static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}
}
