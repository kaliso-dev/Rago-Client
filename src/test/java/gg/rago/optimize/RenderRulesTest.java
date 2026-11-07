package gg.rago.optimize;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemEmptyMap;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PolyPatcher's hiding options, render distances and chunk update limit, as measured on the game. */
class RenderRulesTest {
	private static final World world = new World(null, new WorldInfo(new NBTTagCompound()), new WorldProviderSurface(), new Profiler(), true) {
		@Override
		protected IChunkProvider createChunkProvider() {
			return null;
		}

		@Override
		protected int getRenderDistanceChunks() {
			return 8;
		}
	};

	@AfterEach
	void off() {
		RenderRules.entities = false;
		RenderRules.distances = false;
		RenderRules.globalSq = RenderRules.playerSq = RenderRules.passiveSq = RenderRules.hostileSq = 64 * 64;
		RenderRules.armorStands = RenderRules.seeThroughPlayers = RenderRules.itemFrames = RenderRules.mapFrames = false;
		RenderRules.paintings = RenderRules.xpOrbs = RenderRules.fireworks = RenderRules.fallingBlocks = false;
		RenderRules.groundedArrows = RenderRules.flyingArrows = false;
		RenderRules.chunkLimit = 0;
		RenderRules.gameThreadForTests(null);
		RenderChunk.renderChunksUpdated = 0;
	}

	private static <T extends Entity> T at(T e, double x, double y, double z) {
		e.posX = x;
		e.posY = y;
		e.posZ = z;
		return e;
	}

	/**
	 * An object of the class made without its constructor, as some want the game's blocks or crafting
	 * recipes, and so the whole game: for the rules that read only the kind and the position.
	 */
	private static <T> T bare(Class<T> type) {
		try {
			Class<?> unsafe = Class.forName("sun.misc.Unsafe");
			Field f = unsafe.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), type));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	/** Another player, of which only the position and whether it is invisible are read. */
	static final class Player extends EntityOtherPlayerMP {
		boolean invisible;

		private Player() {
			super(null, null);
		}

		@Override
		public boolean isInvisible() {
			return invisible;
		}
	}

	private static Player player() {
		return bare(Player.class);
	}

	private static <T extends Entity> T bare(Class<T> type, double x, double y, double z) {
		return at(bare(type), x, y, z);
	}

	private static void distances(int global, int players, int passive, int hostile) {
		RenderRules.entities = true;
		RenderRules.distances = true;
		RenderRules.globalSq = global * global;
		RenderRules.playerSq = players * players;
		RenderRules.passiveSq = passive * passive;
		RenderRules.hostileSq = hostile * hostile;
	}

	@Test
	void nothingIsLeftOutWithEveryOptionOff() {
		assertFalse(RenderRules.skip(at(new EntityZombie(world), 500, 0, 0), 0, 0, 0));
		assertFalse(RenderRules.skip(new EntityArmorStand(world), 0, 0, 0));
		assertFalse(RenderRules.skip(new EntityPainting(world), 0, 0, 0));
	}

	@Test
	void eachKindHasItsDistanceCappedByTheGlobalOne() {
		distances(40, 30, 20, 16);
		// three dimensions, from feet to feet: 12 sideways and 9 up is 15 blocks, with 6 more the other way 16.2
		assertFalse(RenderRules.skip(bare(EntityZombie.class, 12, 9, 0), 0, 0, 0));
		assertTrue(RenderRules.skip(bare(EntityZombie.class, 12, 9, 6), 0, 0, 0), "hostile: 16");
		assertTrue(RenderRules.skip(bare(EntityZombie.class, 0, -16.01, 0), 0, 0, 0), "below too");
		assertFalse(RenderRules.skip(bare(EntityZombie.class, 16, 0, 0), 0, 0, 0), "exactly at the distance is drawn");
		assertFalse(RenderRules.skip(bare(EntityWolf.class, 19.9, 0, 0), 0, 0, 0));
		assertTrue(RenderRules.skip(bare(EntityWolf.class, 20.1, 0, 0), 0, 0, 0), "passive: animals, wolves too");
		assertTrue(RenderRules.skip(bare(EntityBat.class, 0, 20.1, 0), 0, 0, 0), "passive: bats");
		assertTrue(RenderRules.skip(bare(EntitySquid.class, 0, 0, 20.1), 0, 0, 0), "passive: squids");
		assertFalse(RenderRules.skip(bare(Player.class, 29.9, 0, 0), 0, 0, 0));
		assertTrue(RenderRules.skip(bare(Player.class, 30.1, 0, 0), 0, 0, 0), "players: 30");
		// villagers, golems and armor stands have the global distance only
		assertFalse(RenderRules.skip(bare(EntityIronGolem.class, 39.9, 0, 0), 0, 0, 0));
		assertTrue(RenderRules.skip(bare(EntityIronGolem.class, 40.1, 0, 0), 0, 0, 0));
		// the shopkeepers: a villager is none of the kinds (made, it would want the game's items)
		for (Class<?> kind : new Class<?>[] {EntityPlayer.class, IMob.class, EntityAnimal.class, EntityAmbientCreature.class, EntityWaterMob.class}) {
			assertFalse(kind.isAssignableFrom(EntityVillager.class), kind.getSimpleName());
		}
		assertTrue(RenderRules.skip(bare(EntityArmorStand.class, 40.1, 0, 0), 0, 0, 0));
		// measured from where the camera's entity stands
		assertFalse(RenderRules.skip(bare(EntityZombie.class, 110, 64, 100), 100, 64, 100));
		distances(5, 30, 20, 10);
		assertTrue(RenderRules.skip(bare(Player.class, 6, 0, 0), 0, 0, 0), "the global distance caps the others");
	}

	@Test
	void distancesAreForLivingEntitiesOnly() {
		distances(1, 1, 1, 1);
		assertFalse(RenderRules.skip(bare(EntityPainting.class, 50, 0, 0), 0, 0, 0));
		assertFalse(RenderRules.skip(bare(EntityArrow.class, 50, 0, 0), 0, 0, 0));
		assertFalse(RenderRules.skip(bare(EntityXPOrb.class, 50, 0, 0), 0, 0, 0));
	}

	@Test
	void armorStandsAndSeeThroughPlayers() {
		RenderRules.entities = true;
		RenderRules.armorStands = true;
		assertTrue(RenderRules.skip(new EntityArmorStand(world), 0, 0, 0), "every stand, names included");
		RenderRules.armorStands = false;
		RenderRules.seeThroughPlayers = true;
		Player invisible = player();
		invisible.invisible = true;
		assertTrue(RenderRules.skip(invisible, 0, 0, 0));
		assertFalse(RenderRules.skip(player(), 0, 0, 0));
		EntityZombie zombie = new EntityZombie(world);
		zombie.setInvisible(true);
		assertFalse(RenderRules.skip(zombie, 0, 0, 0), "players only");
	}

	private static EntityArrow arrow(boolean inGround, double mx, double my, double mz) {
		EntityArrow a = new EntityArrow(world);
		ReflectionHelper.setPrivateValue(EntityArrow.class, a, inGround, "field_70254_i", "inGround");
		a.motionX = mx;
		a.motionY = my;
		a.motionZ = mz;
		return a;
	}

	@Test
	void arrowsInTheGroundAndMovingOnes() {
		assertTrue(RenderRules.inGround(arrow(true, 0, 0, 0)), "the arrow's own flag is read");
		assertFalse(RenderRules.inGround(arrow(false, 0, 0, 0)));
		NBTTagCompound saved = new NBTTagCompound();
		arrow(true, 0, 0, 0).writeEntityToNBT(saved);
		assertEquals(1, saved.getByte("inGround"), "the one the game saves as inGround");
		RenderRules.entities = true;
		RenderRules.groundedArrows = true;
		assertTrue(RenderRules.skip(arrow(true, 0, 0, 0), 0, 0, 0));
		assertFalse(RenderRules.skip(arrow(false, 1, 0, 0), 0, 0, 0));
		RenderRules.groundedArrows = false;
		RenderRules.flyingArrows = true;
		assertFalse(RenderRules.skip(arrow(true, 0, 0, 0), 0, 0, 0));
		assertTrue(RenderRules.skip(arrow(false, 1.2, -0.3, 0.4), 0, 0, 0));
		assertTrue(RenderRules.skip(arrow(false, 0.0014, 0, 0), 0, 0, 0), "the least speed towards positive X");
		assertTrue(RenderRules.skip(arrow(false, -0.3, -0.2, 0.0016), 0, 0, 0), "or towards positive Z");
		assertTrue(RenderRules.skip(arrow(false, -0.3, 0.2, -0.3), 0, 0, 0), "rising");
		assertFalse(RenderRules.skip(arrow(false, -1.2, -0.3, -0.4), 0, 0, 0), "west and north while coming down stays");
		assertFalse(RenderRules.skip(arrow(false, -0.0013, -0.5, 0), 0, 0, 0), "as one bounced back does");
		assertFalse(RenderRules.skip(arrow(false, 0, -1.5, 0), 0, 0, 0), "falling straight down stays");
		assertFalse(RenderRules.skip(arrow(false, 0, -0.05, 0), 0, 0, 0), "carried by another entity");
		assertFalse(RenderRules.skip(arrow(false, 0, 0, 0), 0, 0, 0));
	}

	@Test
	void framesPaintingsOrbsFireworksAndFallingBlocks() {
		RenderRules.entities = true;
		EntityItemFrame map = new EntityItemFrame(world);
		// as the server's update sets it
		map.getDataWatcher().updateObject(8, new ItemStack(new ItemMap() { }));
		EntityItemFrame emptyMap = new EntityItemFrame(world);
		emptyMap.getDataWatcher().updateObject(8, new ItemStack(new ItemEmptyMap() { }));
		EntityItemFrame empty = new EntityItemFrame(world);
		RenderRules.mapFrames = true;
		assertTrue(RenderRules.skip(map, 0, 0, 0));
		assertFalse(RenderRules.skip(emptyMap, 0, 0, 0), "a blank map is not a map");
		assertFalse(RenderRules.skip(empty, 0, 0, 0));
		RenderRules.mapFrames = false;
		RenderRules.itemFrames = true;
		assertTrue(RenderRules.skip(empty, 0, 0, 0));
		assertTrue(RenderRules.skip(emptyMap, 0, 0, 0));
		RenderRules.itemFrames = false;
		Entity[] others = {new EntityPainting(world), new EntityXPOrb(world), new EntityFireworkRocket(world), new EntityFallingBlock(world)};
		for (Entity e : others) {
			assertFalse(RenderRules.skip(e, 0, 0, 0));
		}
		RenderRules.paintings = true;
		RenderRules.xpOrbs = true;
		RenderRules.fireworks = true;
		RenderRules.fallingBlocks = true;
		for (Entity e : others) {
			assertTrue(RenderRules.skip(e, 0, 0, 0), e.getClass().getSimpleName());
		}
	}

	@Test
	void tileEntitiesAreMeasuredToTheCentreOfTheBlock() {
		int[] drawn = new int[1];
		TileEntitySpecialRenderer<TileEntity> inner = new TileEntitySpecialRenderer<TileEntity>() {
			@Override
			public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage) {
				drawn[0]++;
			}
		};
		GatedTileRenderer<TileEntity> gated = new GatedTileRenderer<>(inner, () -> false, te -> false, () -> 12 * 12);
		TileEntityChest chest = new TileEntityChest();
		// the corner the game passes is 11.6 blocks away, the centre 12.1: past 12
		gated.renderTileEntityAt(chest, 11.6, -0.5, -0.5, 0, -1);
		assertEquals(0, drawn[0]);
		gated.renderTileEntityAt(chest, 11.4, -0.5, -0.5, 0, -1);
		assertEquals(1, drawn[0]);
		GatedTileRenderer<TileEntity> unlimited = new GatedTileRenderer<>(inner, () -> false, te -> false, () -> 0);
		unlimited.renderTileEntityAt(chest, 300, 0, 0, 0, -1);
		assertEquals(2, drawn[0], "0 leaves the game's own distance");
	}

	private static RenderChunk section() {
		// its constructor asks the graphics card for its matrix
		RenderChunk chunk = bare(RenderChunk.class);
		chunk.setNeedsUpdate(false);
		return chunk;
	}

	private static ChunkCompileTaskGenerator compiling(RenderChunk chunk) {
		ChunkCompileTaskGenerator task = new ChunkCompileTaskGenerator(chunk, ChunkCompileTaskGenerator.Type.REBUILD_CHUNK);
		task.setStatus(ChunkCompileTaskGenerator.Status.COMPILING);
		return task;
	}

	@Test
	void sectionsRebuiltInTheBackgroundWaitOnceTheSecondsCountIsReached() {
		RenderRules.gameThreadForTests(new Thread(() -> { }));
		RenderRules.chunkLimit = 50;
		RenderChunk chunk = section();
		RenderChunk.renderChunksUpdated = 49;
		assertFalse(RenderRules.refuseRebuild(chunk, compiling(chunk)));
		RenderChunk.renderChunksUpdated = 50;
		ChunkCompileTaskGenerator task = compiling(chunk);
		assertTrue(RenderRules.refuseRebuild(chunk, task));
		assertEquals(ChunkCompileTaskGenerator.Status.DONE, task.getStatus());
		assertTrue(task.isFinished(), "ended as the game ends a cancelled task, quietly");
		assertFalse(chunk.isNeedsUpdate(), "not marked from the builder's thread");
		RenderRules.markRefused();
		assertTrue(chunk.isNeedsUpdate(), "marked for another rebuild as the next frame starts");
		chunk.setNeedsUpdate(false);
		RenderRules.markRefused();
		assertFalse(chunk.isNeedsUpdate(), "once");
	}

	@Test
	void rebuildsThatNeverWait() {
		RenderRules.chunkLimit = 50;
		RenderChunk.renderChunksUpdated = 80;
		RenderChunk chunk = section();
		RenderRules.gameThreadForTests(Thread.currentThread());
		assertFalse(RenderRules.refuseRebuild(chunk, compiling(chunk)), "the game's own thread: the blocks you place");
		RenderRules.gameThreadForTests(new Thread(() -> { }));
		ChunkCompileTaskGenerator cancelled = compiling(chunk);
		cancelled.finish();
		chunk.setNeedsUpdate(false);
		assertFalse(RenderRules.refuseRebuild(chunk, cancelled), "a cancelled task is the game's to end");
		RenderRules.chunkLimit = 0;
		assertFalse(RenderRules.refuseRebuild(chunk, compiling(chunk)), "no limit");
		RenderRules.markRefused();
		assertFalse(chunk.isNeedsUpdate());
	}
}
