package gg.rago.optimize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PolyPatcher's display options, answered as PolyPatcher answers them on the game: which entities
 * are left out, how far each kind is drawn, the back faces of models, the enchantment glint and how
 * many chunk sections are rebuilt each second. The settings are copied into these fields once a
 * frame ({@link Optimizations}), so what the game asks for each entity is a few field reads.
 */
public final class RenderRules {
	/** Any entity rule on; when not, the game's own test runs at once. */
	static boolean entities;
	/** The custom render distances, squared, in blocks; only for living entities. */
	static boolean distances;
	static double globalSq = 64 * 64;
	static double playerSq = 64 * 64;
	static double passiveSq = 64 * 64;
	static double hostileSq = 64 * 64;
	/** How far tile entities are drawn, squared; 0 leaves the game's own 64 blocks. */
	static double tileSq;
	static boolean armorStands;
	static boolean seeThroughPlayers;
	static boolean itemFrames;
	static boolean mapFrames;
	static boolean paintings;
	static boolean xpOrbs;
	static boolean fireworks;
	static boolean fallingBlocks;
	static boolean groundedArrows;
	static boolean flyingArrows;
	static boolean entityBackFaces;
	static boolean playerBackFaces;
	static boolean noGlint;
	/** Chunk sections rebuilt in the background each second at most; 0 for no limit. Read by the builder threads. */
	static volatile int chunkLimit;

	private static final MethodHandle IN_GROUND = inGroundGetter();
	/** The sections whose rebuild waited, from the builder threads; marked again each frame. */
	private static final Queue<RenderChunk> REFUSED = new ConcurrentLinkedQueue<>();
	private static volatile Thread gameThreadForTests;

	private RenderRules() {
	}

	/**
	 * RenderManager.shouldRender, first thing: true leaves the entity out of this frame, as the hiding
	 * options and the render distances want. {@code x, y, z} is where the camera's entity stands (its
	 * feet, as the game passes it), not the third-person camera.
	 */
	public static boolean skip(Entity e, double x, double y, double z) {
		if (!entities) {
			return false;
		}
		if (e instanceof EntityLivingBase) {
			if (armorStands && e instanceof EntityArmorStand || seeThroughPlayers && e instanceof EntityPlayer && e.isInvisible()) {
				return true;
			}
			return distances && beyond(e, x, y, z);
		}
		if (e instanceof EntityArrow) {
			return arrowHidden((EntityArrow) e);
		}
		if (e instanceof EntityItemFrame) {
			return itemFrames || mapFrames && isMap(((EntityItemFrame) e).getDisplayedItem());
		}
		return paintings && e instanceof EntityPainting
			|| xpOrbs && e instanceof EntityXPOrb
			|| fireworks && e instanceof EntityFireworkRocket
			|| fallingBlocks && e instanceof EntityFallingBlock;
	}

	/**
	 * Farther than the distance of its kind, measured in three dimensions from feet to feet: players,
	 * hostile mobs ({@link IMob}), passive ones (animals, bats and squids), and every other living
	 * entity (villagers, golems, armor stands) with the global distance only, which also caps the others.
	 */
	static boolean beyond(Entity e, double x, double y, double z) {
		double max = globalSq;
		if (e instanceof EntityPlayer) {
			max = Math.min(max, playerSq);
		} else if (e instanceof IMob) {
			max = Math.min(max, hostileSq);
		} else if (e instanceof EntityAnimal || e instanceof EntityAmbientCreature || e instanceof EntityWaterMob) {
			max = Math.min(max, passiveSq);
		}
		double dx = e.posX - x;
		double dy = e.posY - y;
		double dz = e.posZ - z;
		return dx * dx + dy * dy + dz * dz > max;
	}

	/**
	 * An arrow in the ground (as this client last saw it) goes with "grounded arrows"; one that is not
	 * goes with "moving arrows" when it heads towards positive X, positive Z or up, as PolyPatcher
	 * decides it (measured on arrows launched every way: one heading west or north while coming down
	 * stays drawn there too). One falling straight down, one carried by another entity and the ones
	 * drawn stuck in a player stay.
	 */
	static boolean arrowHidden(EntityArrow a) {
		if (!groundedArrows && !flyingArrows) {
			return false;
		}
		if (inGround(a)) {
			return groundedArrows;
		}
		return flyingArrows && (a.motionX > 0 || a.motionY > 0 || a.motionZ > 0);
	}

	private static boolean isMap(ItemStack stack) {
		return stack != null && stack.getItem() instanceof ItemMap;
	}

	static boolean inGround(EntityArrow a) {
		if (IN_GROUND == null) {
			return false;
		}
		try {
			return (boolean) IN_GROUND.invokeExact(a);
		} catch (Throwable t) {
			return false;
		}
	}

	private static MethodHandle inGroundGetter() {
		try {
			Field f = ReflectionHelper.findField(EntityArrow.class, "field_70254_i", "inGround");
			return MethodHandles.lookup().unreflectGetter(f);
		} catch (Exception | LinkageError e) {
			return null;
		}
	}

	/**
	 * RendererLivingEntity.doRender, in place of its GlStateManager.disableCull(): with back-face
	 * culling (players and the other living entities each have their option), culling is turned on
	 * there instead, whatever the state the entity comes in with, so the faces turned away are not
	 * drawn; without, as vanilla. The game turns it back on as the entity ends either way.
	 */
	public static void livingCull(EntityLivingBase e) {
		if (e instanceof EntityPlayer ? playerBackFaces : entityBackFaces) {
			GlStateManager.enableCull();
		} else {
			GlStateManager.disableCull();
		}
	}

	/** RenderItem.renderEffect and LayerArmorBase.renderGlint, first thing: true draws no enchantment glint. */
	public static boolean noGlint() {
		return noGlint;
	}

	/**
	 * RenderChunk.rebuildChunk, first thing: true when a chunk section rebuilt in the background must
	 * wait, because this second's count (the game's own, which F3 shows and which restarts each
	 * second) has reached the limit. Its task is ended as the game ends a cancelled one, and the
	 * section is marked for another rebuild on the game's thread as the next frame starts
	 * ({@link #markRefused()}), so that it is asked for again from that frame on: marked from the
	 * builder thread, the mark could come before the game clears it once the task is queued, and be
	 * lost. The rebuilds the game makes at once on its own thread (the blocks you place, empty
	 * sections) never wait.
	 */
	public static boolean refuseRebuild(RenderChunk chunk, ChunkCompileTaskGenerator task) {
		int limit = chunkLimit;
		if (limit <= 0 || RenderChunk.renderChunksUpdated < limit || task == null || onGameThread()) {
			return false;
		}
		ReentrantLock lock = task.getLock();
		lock.lock();
		try {
			if (task.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING) {
				// cancelled meanwhile: the game's own check ends it
				return false;
			}
			// done before finish(), which then leaves the section's mark alone
			task.setStatus(ChunkCompileTaskGenerator.Status.DONE);
			task.finish();
		} finally {
			lock.unlock();
		}
		REFUSED.add(chunk);
		return true;
	}

	private static boolean onGameThread() {
		Thread game = gameThreadForTests;
		return game != null ? Thread.currentThread() == game : Minecraft.getMinecraft().isCallingFromMinecraftThread();
	}

	/** For tests: the thread taken for the game's; null asks the game. */
	static void gameThreadForTests(Thread thread) {
		gameThreadForTests = thread;
	}

	/** Each frame as it starts, on the game's thread: the sections refused since are marked for another rebuild. */
	static void markRefused() {
		for (RenderChunk chunk; (chunk = REFUSED.poll()) != null; ) {
			chunk.setNeedsUpdate(true);
		}
	}

	/** GatedTileRenderer: how far tile entities are drawn, squared; 0 for the game's own distance. */
	static double tileDistanceSq() {
		return tileSq;
	}
}
