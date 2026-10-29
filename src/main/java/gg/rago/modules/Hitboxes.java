package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.hooks.Hooks;
import gg.rago.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Hitboxes like F3+B, but configurable: which kinds of entities, colour, line width, the eye
 * height line and the look direction. Shows the same boxes vanilla's debug view shows.
 */
public final class Hitboxes extends Module {
	private static final double MAX_DISTANCE_SQ = 64 * 64;

	private final BooleanOption players = addOption(new BooleanOption("hitbox_players", true));
	private final BooleanOption mobs = addOption(new BooleanOption("hitbox_mobs", false));
	private final BooleanOption items = addOption(new BooleanOption("hitbox_items", false));
	private final BooleanOption projectiles = addOption(new BooleanOption("hitbox_projectiles", true));
	private final ColorOption color = addOption(new ColorOption("hitbox_color", 0xFFFFFFFF));
	private final IntOption width = addOption(new IntOption("line_width", 2, 1, 10));
	private final BooleanOption eyeLine = addOption(new BooleanOption("eye_line", true));
	private final BooleanOption lookLine = addOption(new BooleanOption("look_line", false));
	private final Frustum frustum = new Frustum();

	public Hitboxes() {
		super("hitboxes", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@SubscribeEvent
	public void onWorldRender(RenderWorldLastEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || mc.theWorld == null || mc.getRenderViewEntity() == null) {
			return;
		}
		long t = gg.rago.debug.FrameProfile.ON ? System.nanoTime() : 0;
		draw(mc, event.partialTicks);
		if (gg.rago.debug.FrameProfile.ON) {
			gg.rago.debug.FrameProfile.add(gg.rago.debug.FrameProfile.HITBOXES, System.nanoTime() - t);
		}
	}

	private void draw(Minecraft mc, float pt) {
		RenderManager rm = mc.getRenderManager();
		Entity viewer = mc.getRenderViewEntity();
		frustum.setPosition(rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
		int c = color.get();
		WorldRenderer wr = null;
		// every box, eye line and look line in one batch: one draw call for the whole frame
		for (Entity e : mc.theWorld.loadedEntityList) {
			if (e == viewer && mc.gameSettings.thirdPersonView == 0 || e.isInvisible() || !wanted(e)
				|| e.getDistanceSqToEntity(viewer) > MAX_DISTANCE_SQ || !frustum.isBoundingBoxInFrustum(e.getEntityBoundingBox())
				|| Hooks.skipEntity(e)) {
				continue;
			}
			if (wr == null) {
				wr = WorldDraw.beginLines(width.get());
			}
			double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - rm.viewerPosX;
			double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - rm.viewerPosY;
			double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - rm.viewerPosZ;
			AxisAlignedBB b = e.getEntityBoundingBox();
			double ox = x - e.posX;
			double oy = y - e.posY;
			double oz = z - e.posZ;
			WorldDraw.box(wr, b.minX + ox, b.minY + oy, b.minZ + oz, b.maxX + ox, b.maxY + oy, b.maxZ + oz, c);
			if (e instanceof EntityLivingBase) {
				double eye = y + e.getEyeHeight();
				double r = e.width / 2.0;
				if (eyeLine.get()) {
					WorldDraw.box(wr, x - r, eye - 0.01, z - r, x + r, eye + 0.01, z + r, 0xFFFF5555);
				}
				if (lookLine.get()) {
					Vec3 look = e.getLook(pt);
					WorldDraw.line(wr, x, eye, z, x + look.xCoord * 2, eye + look.yCoord * 2, z + look.zCoord * 2, 0xFF5555FF);
				}
			}
		}
		if (wr != null) {
			WorldDraw.endLines();
		}
	}

	private boolean wanted(Entity e) {
		if (e instanceof EntityPlayer) {
			return players.get();
		}
		if (e instanceof EntityLivingBase) {
			return mobs.get();
		}
		if (e instanceof EntityItem) {
			return items.get();
		}
		if (e instanceof IProjectile || e instanceof EntityFireball) {
			return projectiles.get();
		}
		return false;
	}
}
