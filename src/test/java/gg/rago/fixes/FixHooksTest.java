package gg.rago.fixes;

import gg.rago.config.BooleanOption;
import gg.rago.config.Option;
import gg.rago.hooks.Hooks;
import gg.rago.modules.ClientFixes;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector3f;

import javax.vecmath.Matrix4f;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixHooksTest {
	@AfterEach
	void noModule() {
		Hooks.clientFixes = null;
	}

	/** Four vertices of Forge's item format (x, y, z, colour, u, v, normal). */
	private static int[] quad(float[][] xyz) {
		int[] data = new int[28];
		for (int v = 0; v < 4; v++) {
			data[v * 7] = Float.floatToRawIntBits(xyz[v][0]);
			data[v * 7 + 1] = Float.floatToRawIntBits(xyz[v][1]);
			data[v * 7 + 2] = Float.floatToRawIntBits(xyz[v][2]);
		}
		return data;
	}

	@Test
	void itemEdges() {
		float front = 7.5f / 16, back = 8.5f / 16;
		assertTrue(FixHooks.isItemEdge(quad(new float[][] {{0.25f, 0.5f, front}, {0.25f, 0.5f, back}, {0.25f, 0.5625f, back}, {0.25f, 0.5625f, front}})),
			"the side of one pixel of a flat item");
		assertFalse(FixHooks.isItemEdge(quad(new float[][] {{0.25f, 0, 0}, {0.25f, 0, 1}, {0.25f, 1, 1}, {0.25f, 1, 0}})),
			"the side of a full block");
		assertFalse(FixHooks.isItemEdge(quad(new float[][] {{0, 0, front}, {1, 0, front}, {1, 1, front}, {0, 1, front}})),
			"the face of an item");
		assertFalse(FixHooks.isItemEdge(null));
		assertFalse(FixHooks.isItemEdge(new int[5]));
	}

	@Test
	void mirroredTransforms() {
		assertFalse(FixHooks.mirrors(new ItemTransformVec3f(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1))));
		assertTrue(FixHooks.mirrors(new ItemTransformVec3f(new Vector3f(), new Vector3f(), new Vector3f(-1, 1, 1))));
		assertFalse(FixHooks.mirrors(new ItemTransformVec3f(new Vector3f(), new Vector3f(), new Vector3f(-1, -1, 1))));
		assertTrue(FixHooks.mirrors(new ItemTransformVec3f(new Vector3f(), new Vector3f(), new Vector3f(-1, -1, -1))));
		assertFalse(FixHooks.mirrors(null));
		Matrix4f m = new Matrix4f();
		m.setIdentity();
		assertEquals(1f, FixHooks.determinant(m), 1e-6);
		m.m00 = -0.5f;
		assertTrue(FixHooks.determinant(m) < 0);
		m.m11 = -1f;
		assertTrue(FixHooks.determinant(m) > 0);
	}

	@Test
	void serverAddressesAreTrimmedWithTheFixesOn() {
		assertEquals(" mc.hypixel.net ", FixHooks.serverAddress(" mc.hypixel.net "), "without the module, vanilla");
		ClientFixes fixes = new ClientFixes();
		Hooks.clientFixes = fixes;
		assertEquals("mc.hypixel.net", FixHooks.serverAddress(" mc.hypixel.net "));
		assertEquals(null, FixHooks.serverAddress(null));
		option(fixes, "internal_fixes").set(false);
		assertEquals(" mc.hypixel.net ", FixHooks.serverAddress(" mc.hypixel.net "));
	}

	@Test
	void horizonAtTheBottomOfTheWorld() {
		assertEquals(101.62 - 63, FixHooks.skyHeight(101.62, 63), 1e-9, "without the module, vanilla");
		assertEquals(-1.38 - 63, FixHooks.skyHeight(-1.38, 63), 1e-9);
		assertEquals(-1, FixHooks.voidBox(-1));
		ClientFixes fixes = new ClientFixes();
		Hooks.clientFixes = fixes;
		assertEquals(101.62, FixHooks.skyHeight(101.62, 63), 1e-9, "the horizon at y 0");
		assertEquals(51.62, FixHooks.skyHeight(51.62, 0), 1e-9, "flat worlds too");
		assertEquals(-1.38, FixHooks.skyHeight(-1.38, 63), 1e-9, "in the void as well");
		assertEquals(1, FixHooks.voidBox(-1), "no black box in the void");
		option(fixes, "void_rendering").set(false);
		assertEquals(-1.38, FixHooks.skyHeight(-1.38, 63), 1e-9, "the horizon does not depend on void rendering");
		assertEquals(-1, FixHooks.voidBox(-1), "the black box is back");
		assertEquals(0, FixHooks.voidBox(0));
		option(fixes, "internal_fixes").set(false);
		assertEquals(-1.38 - 63, FixHooks.skyHeight(-1.38, 63), 1e-9);
	}

	@Test
	void skyPlanesOfVanillaSizeWithTheCustomSkyFix() {
		assertEquals(256, FixHooks.skyPlaneSize(256), "without the module, OptiFine's size (8 chunks)");
		ClientFixes fixes = new ClientFixes();
		Hooks.clientFixes = fixes;
		assertEquals(384, FixHooks.skyPlaneSize(256), "8 chunks");
		assertEquals(384, FixHooks.skyPlaneSize(192), "4 chunks");
		assertEquals(384, FixHooks.skyPlaneSize(384), "16 chunks");
		assertEquals(384, FixHooks.skyPlaneSize(640), "32 chunks: smaller than OptiFine's, as PolyPatcher makes them");
		option(fixes, "custom_sky_fix").set(false);
		assertEquals(256, FixHooks.skyPlaneSize(256));
	}

	@Test
	void hatLayerForPlayersNotLoaded() {
		assertFalse(FixHooks.tabHat(null, EnumPlayerModelParts.HAT), "without the module, vanilla");
		ClientFixes fixes = new ClientFixes();
		Hooks.clientFixes = fixes;
		assertTrue(FixHooks.tabHat(null, EnumPlayerModelParts.HAT));
		option(fixes, "tab_hat_layer").set(false);
		assertFalse(FixHooks.tabHat(null, EnumPlayerModelParts.HAT));
	}

	@Test
	void lightSpreadsAsFarAsTheLoadedChunksAllow() {
		int[] loaded = {18};
		World world = new World(null, new WorldInfo(new NBTTagCompound()), new WorldProviderSurface(), new Profiler(), false) {
			@Override
			protected IChunkProvider createChunkProvider() {
				return null;
			}

			@Override
			protected int getRenderDistanceChunks() {
				return 8;
			}

			@Override
			public boolean isAreaLoaded(BlockPos center, int radius, boolean allowEmpty) {
				return radius <= loaded[0];
			}
		};
		BlockPos pos = new BlockPos(0, 64, 0);
		int[][] vanilla = {{18, 17}, {17, 17}, {16, -1}, {15, -1}, {0, -1}};
		for (int[] c : vanilla) {
			loaded[0] = c[0];
			assertEquals(c[1], FixHooks.lightSpread(world, pos, 17, false), "without the module, vanilla: loaded within " + c[0]);
		}
		ClientFixes fixes = new ClientFixes();
		Hooks.clientFixes = fixes;
		int[][] fixed = {{40, 17}, {18, 17}, {17, 15}, {16, 15}, {15, -1}, {0, -1}};
		for (int[] c : fixed) {
			loaded[0] = c[0];
			assertEquals(c[1], FixHooks.lightSpread(world, pos, 17, false), "loaded within " + c[0]);
		}
		option(fixes, "internal_fixes").set(false);
		loaded[0] = 16;
		assertEquals(-1, FixHooks.lightSpread(world, pos, 17, false));
	}

	@Test
	void columnRelitFromTheNewBlockWithTheFix() {
		assertEquals(71, FixHooks.relightFrom(70), "without the module, vanilla: from the block above");
		ClientFixes fixes = new ClientFixes();
		Hooks.clientFixes = fixes;
		assertEquals(70, FixHooks.relightFrom(70));
		option(fixes, "internal_fixes").set(false);
		assertEquals(71, FixHooks.relightFrom(70));
	}

	private static BooleanOption option(ClientFixes fixes, String key) {
		for (Option<?> o : fixes.options()) {
			if (o.key().equals(key)) {
				return (BooleanOption) o;
			}
		}
		throw new AssertionError(key);
	}

	@Test
	void optiFinesLowestFrameRateLeavesItsShowFpsLine() {
		assertEquals("60 fps, C: 120, E: 12+3, U: 4", FixHooks.withoutMinFps("60/45 fps, C: 120, E: 12+3, U: 4"));
		assertEquals("28 fps, C: 14, E: 0+0, U: 17", FixHooks.withoutMinFps("28/24 fps, C: 14, E: 0+0, U: 17"));
		assertEquals("60 fps, C: 120", FixHooks.withoutMinFps("60 fps, C: 120"), "already simple");
		assertEquals("C: 60/45 fps", FixHooks.withoutMinFps("C: 60/45 fps"), "only at the start");
		assertEquals(null, FixHooks.withoutMinFps(null));
	}
}
