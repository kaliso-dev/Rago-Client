package gg.rago.fixes;

import gg.rago.core.RagoClient;
import gg.rago.hooks.Hooks;
import gg.rago.modules.ClientFixes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3i;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.IPerspectiveAwareModel;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Matrix4f;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * The fixes for 1.8.9 and Forge bugs that PolyPatcher makes, rewritten for Rago from the game's own
 * code: each method here is called from a game method that {@code RagoTransformer} changed, and
 * does exactly what the game did unless the matching Client Fixes option is on. Options that
 * PolyPatcher shows have their own switch; the fixes it applies without a switch share "internal
 * fixes".
 */
public final class FixHooks {
	private static final EnumFacing[] FACINGS = EnumFacing.values();

	private FixHooks() {
	}

	private static ClientFixes fixes() {
		ClientFixes f = Hooks.clientFixes;
		return f != null && f.isEnabled() ? f : null;
	}

	private static boolean internal() {
		ClientFixes f = fixes();
		return f != null && f.internalFixes();
	}

	// ---- shaders and the F5 key ------------------------------------------------------------------

	/**
	 * Minecraft.runTick, F5: vanilla loads the shader of the entity you watch through (creeper,
	 * spider, enderman, or none), which also throws away any other shader in use. With Keep Shaders
	 * that is skipped while you are yourself, so the shader stays; spectating a mob still works.
	 */
	public static void perspectiveShader(EntityRenderer renderer, Entity entity) {
		ClientFixes f = fixes();
		if (f != null && f.keepShaders()) {
			Entity view = Minecraft.getMinecraft().getRenderViewEntity();
			if (view == null || view instanceof EntityPlayer) {
				return;
			}
		}
		renderer.loadEntityShader(entity);
	}

	/**
	 * Minecraft.setRenderViewEntity: the entity whose shader applies. A spectator who enters a mob
	 * in third person gets no shader: vanilla only removes it when the view changes, so it showed
	 * in third person.
	 */
	public static Entity viewShader(Entity entity) {
		return entity != null && internal() && Minecraft.getMinecraft().gameSettings.thirdPersonView != 0 ? null : entity;
	}

	// ---- culling fix (MC-63020, MC-70850) ---------------------------------------------------------

	private static boolean cullOn;
	private static int cullFrame;
	private static float lastFovX;
	private static float lastFovY;
	/** Per render chunk (by its index): the frame its extra entry sides belong to, and those sides. */
	private static int[] cullStamp = new int[0];
	private static int[] cullSides = new int[0];
	private static final MethodHandle CHUNK_INDEX = getter(RenderChunk.class, "field_178596_j", "index");
	private static final MethodHandle CLIPPING = staticGetter(ClippingHelperImpl.class, "field_78563_e", "instance");

	/**
	 * RenderGlobal.setupTerrain, first: whether the culling fix runs this frame. A zoom or a bow
	 * changes the field of view without moving the camera; vanilla then keeps the chunks it chose
	 * for the old view until you move, so new ones at the edges stay hidden.
	 */
	public static void cullingFrame(RenderGlobal renderGlobal, int frame) {
		ClientFixes f = fixes();
		cullOn = f != null && f.cullingFix();
		if (!cullOn) {
			return;
		}
		cullFrame = frame;
		ClippingHelper clipping = clipping();
		if (clipping != null) {
			float x = clipping.projectionMatrix[0];
			float y = clipping.projectionMatrix[5];
			if (x != lastFovX || y != lastFovY) {
				lastFovX = x;
				lastFovY = y;
				renderGlobal.setDisplayListEntitiesDirty();
			}
		}
	}

	/**
	 * RenderGlobal.setupTerrain: with only one side of your chunk open, vanilla drops the side
	 * behind the camera and then draws your chunk alone, though a wide view or the third-person
	 * camera still sees through that side (MC-63020). With the fix the side is kept.
	 */
	public static boolean cullingDropSide(Set<?> sides, Object side) {
		return !cullOn && sides.remove(side);
	}

	/**
	 * RenderGlobal.setupTerrain, going from a chunk to its neighbour: vanilla enters each chunk
	 * once per frame and remembers only that first way in, so a chunk seen through another of its
	 * sides could hide what is behind it (MC-70850). With the fix every way in is remembered.
	 */
	public static boolean cullingEnter(RenderChunk next, int frame, EnumFacing direction) {
		if (next.setFrameIndex(frame)) {
			return true;
		}
		if (cullOn) {
			int i = index(next);
			if (i >= 0) {
				if (cullStamp[i] != frame) {
					cullStamp[i] = frame;
					cullSides[i] = 0;
				}
				cullSides[i] |= 1 << direction.ordinal();
			}
		}
		return false;
	}

	/** RenderGlobal.setupTerrain: whether the chunk lets you see from where you came in to {@code out}, through any way in with the fix. */
	public static boolean cullingVisible(CompiledChunk compiled, EnumFacing in, EnumFacing out, RenderChunk chunk) {
		if (compiled.isVisible(in, out)) {
			return true;
		}
		if (!cullOn) {
			return false;
		}
		int i = index(chunk);
		if (i < 0 || cullStamp[i] != cullFrame) {
			return false;
		}
		int sides = cullSides[i];
		for (EnumFacing direction : FACINGS) {
			if ((sides & 1 << direction.ordinal()) != 0 && compiled.isVisible(direction.getOpposite(), out)) {
				return true;
			}
		}
		return false;
	}

	private static int index(RenderChunk chunk) {
		if (CHUNK_INDEX == null) {
			return -1;
		}
		int i;
		try {
			i = (int) CHUNK_INDEX.invokeExact(chunk);
		} catch (Throwable t) {
			return -1;
		}
		if (i < 0) {
			return -1;
		}
		if (i >= cullStamp.length) {
			int size = Math.max(i + 1, cullStamp.length * 2);
			cullStamp = Arrays.copyOf(cullStamp, size);
			cullSides = Arrays.copyOf(cullSides, size);
		}
		return i;
	}

	private static ClippingHelper clipping() {
		if (CLIPPING == null) {
			return null;
		}
		try {
			return (ClippingHelper) (ClippingHelperImpl) CLIPPING.invokeExact();
		} catch (Throwable t) {
			return null;
		}
	}

	// ---- sky ------------------------------------------------------------------------------------

	/**
	 * RenderGlobal.renderSky: how high your eyes are above the horizon. The dark plane under the
	 * sky is drawn that far under your feet, so it stays at the horizon's height (less your eye
	 * height), and with your eyes under the horizon vanilla blacks out the lower sky (see
	 * {@link #voidBox}). Vanilla's horizon is sea level (63) in normal worlds, so this happens on
	 * every low map and whenever you fall. PolyPatcher puts the horizon at the bottom of the world
	 * (0) whatever its options say; Rago does it with the internal fixes.
	 */
	public static double skyHeight(double eyes, double horizon) {
		return internal() ? eyes : eyes - horizon;
	}

	/**
	 * RenderGlobal.renderSky, "are your eyes below the horizon": vanilla then draws a black plane
	 * under you and a black box around you. Player Void Rendering leaves both out (with the horizon
	 * at 0 that is only in the void).
	 */
	public static int voidBox(int belowHorizon) {
		if (belowHorizon < 0) {
			ClientFixes f = fixes();
			if (f != null && f.voidRendering()) {
				return 1;
			}
		}
		return belowHorizon;
	}

	/** How far vanilla's flat sky planes reach on each side (on the positive side, 64 blocks more). */
	static final int VANILLA_SKY_PLANE = 384;

	/**
	 * RenderGlobal.renderSky(WorldRenderer, float, boolean) as OptiFine changed it: how far the flat
	 * planes of the sky reach. OptiFine sizes them from the render distance, (blocks / 64 + 1) * 64
	 * + 64: 256 at 8 chunks, against 384 in vanilla. A pack's custom sky then shows past the edges
	 * of the dark plane under the horizon, which looks like a box at the bottom of the sky. The
	 * custom sky fix builds the planes with vanilla's size, as PolyPatcher does.
	 */
	public static int skyPlaneSize(int size) {
		ClientFixes f = fixes();
		return f != null && f.customSkyFix() ? VANILLA_SKY_PLANE : size;
	}

	private static final MethodHandle GENERATE_SKY = invoker(RenderGlobal.class, "func_174980_p", "generateSky");
	private static final MethodHandle GENERATE_SKY2 = invoker(RenderGlobal.class, "func_174964_o", "generateSky2");

	/**
	 * Builds the sky planes again, once the custom sky fix or the module was turned on or off (the
	 * game builds them only when the renderers are loaded again).
	 */
	public static void rebuildSkyPlanes() {
		RenderGlobal renderGlobal = Minecraft.getMinecraft().renderGlobal;
		if (renderGlobal == null || GENERATE_SKY == null || GENERATE_SKY2 == null) {
			return;
		}
		try {
			GENERATE_SKY.invokeExact(renderGlobal);
			GENERATE_SKY2.invokeExact(renderGlobal);
		} catch (Throwable t) {
			RagoClient.LOG.warn("Client Fixes: could not build the sky planes again: {}", t.toString());
		}
	}

	// ---- player model, book -------------------------------------------------------------

	private static final MethodHandle SMALL_ARMS = getter(ModelPlayer.class, "field_178735_y", "smallArms");

	/**
	 * ModelPlayer.setRotationAngles, first: the arms of slim (Alex) skins hang half a pixel lower
	 * than Steve's in 1.8; from 1.14 on they hang at the same height. Alex Arm Position puts them
	 * there, and back when it is turned off.
	 */
	public static void alexArms(ModelPlayer model) {
		if (SMALL_ARMS == null) {
			return;
		}
		boolean slim;
		try {
			slim = (boolean) SMALL_ARMS.invokeExact(model);
		} catch (Throwable t) {
			return;
		}
		if (slim) {
			ClientFixes f = fixes();
			float y = f != null && f.alexArms() ? 2.0f : 2.5f;
			model.bipedLeftArm.rotationPointY = y;
			model.bipedRightArm.rotationPointY = y;
		}
	}

	/** GuiScreenBook.drawScreen, first: the dark background other menus have, behind an open book. */
	public static void bookBackground(GuiScreen screen) {
		ClientFixes f = fixes();
		if (f != null && f.bookBackground()) {
			screen.drawDefaultBackground();
		}
	}

	// ---- items ----------------------------------------------------------------------------------

	/**
	 * RenderItem.renderQuads, after a quad is drawn: Forge builds the left and right edges of flat
	 * items with the normal of the opposite side, so those edges catch the light as if they faced
	 * inwards; vanilla lights them as facing out, and so does Vanilla Held Item Lighting.
	 */
	public static void itemQuadNormal(WorldRenderer renderer, BakedQuad quad) {
		if (quad instanceof UnpackedBakedQuad) {
			EnumFacing face = quad.getFace();
			if ((face == EnumFacing.EAST || face == EnumFacing.WEST) && isItemEdge(quad.getVertexData())) {
				ClientFixes f = fixes();
				if (f != null && f.vanillaItemLighting()) {
					Vec3i out = face.getOpposite().getDirectionVec();
					renderer.putNormal(out.getX(), out.getY(), out.getZ());
				}
			}
		}
	}

	/**
	 * Whether a quad is the side of one pixel of a flat item, as Forge builds them: four item
	 * vertices in a plane across x, as deep as the item (one sixteenth of a block and a hair).
	 */
	static boolean isItemEdge(int[] data) {
		if (data == null || data.length < 12 || data.length % 4 != 0) {
			return false;
		}
		int stride = data.length / 4;
		float x = Float.intBitsToFloat(data[0]);
		float zMin = Float.MAX_VALUE;
		float zMax = -Float.MAX_VALUE;
		for (int v = 0; v < 4; v++) {
			if (Float.intBitsToFloat(data[v * stride]) != x) {
				return false;
			}
			float z = Float.intBitsToFloat(data[v * stride + 2]);
			zMin = Math.min(zMin, z);
			zMax = Math.max(zMax, z);
		}
		float depth = zMax - zMin;
		return depth > 0.0624f && depth < 0.0645f;
	}

	/**
	 * RenderItem.renderItemModelTransform: Forge's camera transform of a held or worn item, plus
	 * vanilla's check Forge left out: a transform that mirrors the item (an odd number of negative
	 * scales, which packs use to flip held items) turns its faces inside out, so the back faces are
	 * the ones to hide. The caller turns culling back to normal after drawing.
	 */
	public static IBakedModel cameraTransforms(IBakedModel model, ItemCameraTransforms.TransformType type) {
		if (!internal()) {
			return ForgeHooksClient.handleCameraTransforms(model, type);
		}
		boolean mirrored = false;
		if (model instanceof IPerspectiveAwareModel) {
			Pair<? extends IBakedModel, Matrix4f> pair = ((IPerspectiveAwareModel) model).handlePerspective(type);
			if (pair.getRight() != null) {
				ForgeHooksClient.multiplyCurrentGlMatrix(pair.getRight());
				mirrored = determinant(pair.getRight()) < 0;
			}
			model = pair.getLeft();
		} else {
			ItemCameraTransforms transforms = model.getItemCameraTransforms();
			transforms.applyTransform(type);
			mirrored = mirrors(transforms.getTransform(type));
		}
		if (mirrored) {
			GlStateManager.cullFace(GL11.GL_FRONT);
		}
		return model;
	}

	/** The same, when OptiFine calls Forge's method through its reflection helper ({@code Reflector.call(method, model, type)}). */
	public static Object reflectedCameraTransforms(Object method, Object[] args) {
		return cameraTransforms((IBakedModel) args[0], (ItemCameraTransforms.TransformType) args[1]);
	}

	static float determinant(Matrix4f m) {
		return m.m00 * (m.m11 * m.m22 - m.m12 * m.m21) - m.m01 * (m.m10 * m.m22 - m.m12 * m.m20)
			+ m.m02 * (m.m10 * m.m21 - m.m11 * m.m20);
	}

	static boolean mirrors(ItemTransformVec3f transform) {
		return transform != null && (transform.scale.x < 0 ^ transform.scale.y < 0 ^ transform.scale.z < 0);
	}

	// ---- tab list -------------------------------------------------------------------------------

	/**
	 * GuiPlayerTabOverlay.renderPlayerlist: whether a head gets its hat layer. Vanilla draws it only
	 * for players loaded around you; Layers In Tab draws it for the others too, as 1.9+ does.
	 */
	public static boolean tabHat(EntityPlayer player, EnumPlayerModelParts part) {
		if (player != null) {
			return player.isWearing(part);
		}
		ClientFixes f = fixes();
		return f != null && f.tabHatLayer();
	}

	// ---- memory leaks and log spam --------------------------------------------------------------

	private static final MethodHandle TEXTURES = getter(TextureManager.class, "field_110585_a", "mapTextureObjects");

	/** TextureManager.deleteTexture, last: forget the deleted texture, which vanilla keeps forever. */
	public static void textureDeleted(TextureManager manager, ResourceLocation location) {
		if (TEXTURES != null && location != null && internal()) {
			try {
				((Map<?, ?>) TEXTURES.invokeExact(manager)).remove(location);
			} catch (Throwable ignored) {
				// keep vanilla's behaviour
			}
		}
	}

	/** Util.runTask: a task that failed, logged once in full, then counted. */
	public static void taskError(Logger logger, String message, Throwable error) {
		if (!internal()) {
			logger.fatal(message, error);
			return;
		}
		TaskErrors.report(logger, message, error, System.currentTimeMillis());
	}

	/** TileEntityRendererDispatcher.renderTileEntity, first: a removed block entity is not drawn. */
	public static boolean skipTileEntity(TileEntity tile) {
		return tile.isInvalid() && internal();
	}

	// ---- light ----------------------------------------------------------------------------------

	/**
	 * World.checkLightFor, first: how many blocks the light update at {@code pos} may spread, or -1
	 * to skip it. Vanilla needs the chunks within 17 blocks loaded and spreads 17; light updated
	 * while a chunk's neighbours are still generating so reads light from chunks that do not exist
	 * yet at its far edge, and is skipped altogether closer to them, which leaves dark patches
	 * along new chunk borders. With the fix (PolyPatcher's "light initializing too early") an
	 * update only reads loaded chunks: it spreads 17 blocks when those within 18 are loaded, else
	 * 15, the reach of the brightest light, when those within 16 are, else waits like vanilla.
	 * Measured in the same new world as PolyPatcher: every light write the same, in the same order.
	 */
	public static int lightSpread(World world, BlockPos pos, int radius, boolean allowEmpty) {
		if (radius == 17 && internal()) {
			if (world.isAreaLoaded(pos, 18, allowEmpty)) {
				return 17;
			}
			return world.isAreaLoaded(pos, 16, allowEmpty) ? 15 : -1;
		}
		return world.isAreaLoaded(pos, radius, allowEmpty) ? radius : -1;
	}

	/**
	 * Chunk.setBlockState: the height a column is relit from when a block that stops light is put at
	 * or above the column's height map. Vanilla relights from the block above the new one, so the
	 * height map rises over it and its sky shadow appears at once. With the fix (PolyPatcher's change
	 * to the sky light calculation) the column is relit from the new block itself, which leaves the
	 * new block out: the height map only rises to the blocks already below it, so the new block casts
	 * no sky shadow until something else relights the column, as with PolyPatcher. That height map
	 * is also where world generation puts the next tree, snow or flower, so new single player worlds
	 * come out as they do with PolyPatcher. Measured in the same new normal world as PolyPatcher,
	 * with the light fix above: every light write until the player joins the same, in the same order.
	 */
	public static int relightFrom(int y) {
		return internal() ? y : y + 1;
	}

	// ---- menus ----------------------------------------------------------------------------------

	/** EntityRenderer.renderWorldDirections: the F3 crosshair is drawn from vanilla's camera spot. */
	private static boolean debugCrosshair;

	public static void debugCrosshair(boolean drawing) {
		debugCrosshair = drawing;
	}

	/**
	 * The first-person camera offset: vanilla's while the F3 crosshair is drawn, since the parallax
	 * fix's offset puts the crosshair behind the camera, where it disappears.
	 */
	public static float cameraOffset(float vanilla, float fixed) {
		return debugCrosshair && internal() ? vanilla : fixed;
	}

	/** GuiScreenAddServer and GuiScreenServerList: a server address without spaces around it (names keep theirs). */
	public static String serverAddress(String address) {
		return address != null && internal() ? address.trim() : address;
	}

	private static String languageBefore;

	/** GuiLanguage$List.elementClicked, first: the language before the click. */
	public static void languageClick() {
		languageBefore = Minecraft.getMinecraft().gameSettings.language;
	}

	private static final MethodHandle OPTIFINE_LANG = optiFine("net.optifine.Lang", "resourcesReloaded");

	/**
	 * GuiLanguage$List.elementClicked: vanilla reloads every resource (textures, models, sounds)
	 * after a click on a language, even the current one. With the fix only the translations
	 * reload, Minecraft's and OptiFine's, and only when the language changed.
	 */
	public static void languageChanged(Minecraft mc) {
		if (!internal()) {
			mc.refreshResources();
			return;
		}
		String now = mc.gameSettings.language;
		if (now != null && now.equals(languageBefore)) {
			return;
		}
		mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
		if (OPTIFINE_LANG != null) {
			try {
				OPTIFINE_LANG.invokeExact();
			} catch (Throwable t) {
				RagoClient.LOG.warn("OptiFine's translations did not reload: {}", t.toString());
			}
		}
	}

	private static boolean soundMenuLogged;

	/** GuiScreenOptionsSounds' sliders: an error from the sound system no longer closes the game. */
	public static void soundMenuError(Throwable error) {
		if (!soundMenuLogged) {
			soundMenuLogged = true;
			RagoClient.LOG.warn("Sound slider error, ignored", error);
		}
	}

	// ---- reflection -----------------------------------------------------------------------------

	private static MethodHandle getter(Class<?> owner, String srg, String mcp) {
		try {
			Field f = ReflectionHelper.findField(owner, srg, mcp);
			MethodHandle h = MethodHandles.lookup().unreflectGetter(f);
			return h.asType(h.type().changeParameterType(0, owner));
		} catch (RuntimeException | IllegalAccessException | LinkageError e) {
			RagoClient.LOG.warn("Client Fixes: {}.{} not found, the fix that reads it is off", owner.getSimpleName(), mcp);
			return null;
		}
	}

	/** A private method of {@code owner} that takes nothing, as (owner)void. */
	private static MethodHandle invoker(Class<?> owner, String srg, String mcp) {
		for (String name : new String[] {srg, mcp}) {
			try {
				Method m = owner.getDeclaredMethod(name);
				m.setAccessible(true);
				return MethodHandles.lookup().unreflect(m);
			} catch (NoSuchMethodException e) {
				// the other name
			} catch (RuntimeException | IllegalAccessException | LinkageError e) {
				break;
			}
		}
		RagoClient.LOG.warn("Client Fixes: {}.{} not found, the fix that calls it is off", owner.getSimpleName(), mcp);
		return null;
	}

	private static MethodHandle staticGetter(Class<?> owner, String srg, String mcp) {
		try {
			Field f = ReflectionHelper.findField(owner, srg, mcp);
			return MethodHandles.lookup().unreflectGetter(f);
		} catch (RuntimeException | IllegalAccessException | LinkageError e) {
			RagoClient.LOG.warn("Client Fixes: {}.{} not found, the fix that reads it is off", owner.getSimpleName(), mcp);
			return null;
		}
	}

	/** A public static method of OptiFine, or null without OptiFine. */
	private static MethodHandle optiFine(String className, String name, Class<?>... parameters) {
		try {
			Class<?> c = Class.forName(className, false, FixHooks.class.getClassLoader());
			Method m = c.getMethod(name, parameters);
			return MethodHandles.lookup().unreflect(m);
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			return null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			RagoClient.LOG.warn("OptiFine's {}.{} not found: {}", className, name, e.toString());
			return null;
		}
	}
}
