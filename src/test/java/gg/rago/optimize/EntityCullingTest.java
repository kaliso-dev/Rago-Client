package gg.rago.optimize;

import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityCullingTest {
	private static final World WORLD = new World(null, new WorldInfo(new NBTTagCompound()), new WorldProviderSurface(), new Profiler(), true) {
		@Override
		protected IChunkProvider createChunkProvider() {
			return null;
		}

		@Override
		protected int getRenderDistanceChunks() {
			return 8;
		}
	};

	private static EntityArmorStand stand(boolean marker) {
		EntityArmorStand s = new EntityArmorStand(WORLD);
		NBTTagCompound tag = new NBTTagCompound();
		s.writeEntityToNBT(tag);
		tag.setBoolean("Marker", marker);
		s.readEntityFromNBT(tag);
		return s;
	}

	@Test
	void markerStandsDragonsAndWithersAreKeptAsSet() {
		EntityArmorStand marker = stand(true);
		EntityArmorStand plain = stand(false);
		assertTrue(EntityCulling.keptBySettings(marker, true, true, true), "the armor stand rules keep markers");
		assertFalse(EntityCulling.keptBySettings(marker, false, true, true));
		assertFalse(EntityCulling.keptBySettings(plain, true, true, true), "small, invisible or named stands are culled");
		EntityDragon dragon = new EntityDragon(WORLD);
		EntityWither wither = new EntityWither(WORLD);
		assertTrue(EntityCulling.keptBySettings(dragon, true, true, false));
		assertFalse(EntityCulling.keptBySettings(dragon, true, false, true));
		assertTrue(EntityCulling.keptBySettings(wither, true, false, true));
		assertFalse(EntityCulling.keptBySettings(wither, true, true, false));
	}

	@Test
	void namesOfCulledEntitiesFollowTheirSetting() {
		EntityWither mob = new EntityWither(WORLD);
		EntityArmorStand stand = stand(false);
		assertTrue(EntityCulling.keepsName(mob, false, true, false));
		assertFalse(EntityCulling.keepsName(mob, true, false, true));
		assertTrue(EntityCulling.keepsName(stand, false, false, true));
		assertFalse(EntityCulling.keepsName(stand, true, true, false));
	}

	@Test
	void intervalsArePolyPatchersChoices() {
		assertEquals(3, EntityCulling.INTERVAL_MS.length);
		assertEquals(50, EntityCulling.INTERVAL_MS[0], "50 ms by default");
		assertEquals(25, EntityCulling.INTERVAL_MS[1]);
		assertEquals(10, EntityCulling.INTERVAL_MS[2]);
	}
}
