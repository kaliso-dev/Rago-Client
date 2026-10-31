package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A countdown above lit TNT: seconds left before it explodes, from green to red as it gets close,
 * with or without tenths, through walls or not. The server never tells the client how long a fuse
 * is (a 1.8 client always assumes 4 seconds), so the length is a setting: 4 seconds for vanilla
 * TNT, less for servers that shorten it, counted from the moment the TNT appeared.
 */
public final class TntTimer extends Module {
	private final ChoiceOption format = addOption(new ChoiceOption("tnt_format", "tenths", "tenths", "seconds"));
	private final BooleanOption colorByTime = addOption(new BooleanOption("tnt_color_by_time", true));
	private final ColorOption color = addOption(new ColorOption("tnt_color", 0xFFFFFFFF));
	private final IntOption scale = addOption(new IntOption("tnt_scale", 150, 50, 400));
	private final BooleanOption background = addOption(new BooleanOption("background", true));
	private final BooleanOption throughWalls = addOption(new BooleanOption("tnt_through_walls", true));
	private final IntOption distance = addOption(new IntOption("tnt_distance", 48, 8, 128));
	/** Fuse length in ticks (80 is vanilla's 4 seconds). */
	private final IntOption fuse = addOption(new IntOption("tnt_fuse", 80, 10, 160));

	/** Lit TNT near you, gathered once per tick so a frame does not walk every entity. */
	private final List<Entity> lit = new ArrayList<>();

	public TntTimer() {
		super("tnt_timer", FeatureClass.VISUAL, true);
		color.showIf(() -> !colorByTime.get());
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	public void tick() {
		lit.clear();
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			return;
		}
		List<Entity> entities = mc.theWorld.loadedEntityList;
		for (int i = 0; i < entities.size(); i++) {
			if (entities.get(i) instanceof EntityTNTPrimed) {
				lit.add(entities.get(i));
			}
		}
	}

	@SubscribeEvent
	public void onWorldDrawn(RenderWorldLastEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || lit.isEmpty() || mc.theWorld == null || mc.getRenderViewEntity() == null) {
			return;
		}
		List<Entity> entities = lit;
		double max = distance.get() * (double) distance.get();
		Entity view = mc.getRenderViewEntity();
		RenderManager rm = mc.getRenderManager();
		float pt = event.partialTicks;
		for (int i = 0; i < entities.size(); i++) {
			Entity e = entities.get(i);
			if (e.isDead || e.getDistanceSqToEntity(view) > max) {
				continue;
			}
			float left = Math.max(0, (fuse.get() - e.ticksExisted - pt) / 20f);
			double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - rm.viewerPosX;
			double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - rm.viewerPosY + e.height + 0.5 + 0.15 * scale.get() / 100.0;
			double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - rm.viewerPosZ;
			draw(mc, rm, text(left), x, y, z, colorByTime.get() ? timeColor(left) : color.get());
		}
	}

	String text(float seconds) {
		return format.is("seconds")
			? String.valueOf((int) Math.ceil(seconds))
			: String.format(Locale.ROOT, "%.1f", seconds);
	}

	/** Green with 3 seconds or more left, then yellow, orange and red as the fuse runs out. */
	static int timeColor(float seconds) {
		float t = Math.max(0, Math.min(1, seconds / 3f));
		int r = t > 0.5f ? Math.round(255 * (1 - t) * 2) : 255;
		int g = t > 0.5f ? 255 : Math.round(255 * t * 2);
		return 0xFF000000 | r << 16 | g << 8 | 0x40;
	}

	private void draw(Minecraft mc, RenderManager rm, String text, double x, double y, double z, int argb) {
		FontRenderer font = mc.fontRendererObj;
		float s = WorldDraw.LABEL_SCALE * scale.get() / 100f;
		GlStateManager.pushMatrix();
		GlStateManager.translate((float) x, (float) y, (float) z);
		GL11.glNormal3f(0, 1, 0);
		GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
		GlStateManager.rotate(WorldDraw.viewPitch(), 1, 0, 0);
		GlStateManager.scale(-s, -s, s);
		GlStateManager.disableLighting();
		mc.entityRenderer.disableLightmap();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.depthMask(false);
		if (throughWalls.get()) {
			GlStateManager.disableDepth();
		}
		int half = font.getStringWidth(text) / 2;
		if (background.get()) {
			GlStateManager.disableTexture2D();
			Tessellator t = Tessellator.getInstance();
			WorldRenderer wr = t.getWorldRenderer();
			wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
			wr.pos(-half - 2, -2, 0).color(0, 0, 0, 0.35f).endVertex();
			wr.pos(-half - 2, 9, 0).color(0, 0, 0, 0.35f).endVertex();
			wr.pos(half + 2, 9, 0).color(0, 0, 0, 0.35f).endVertex();
			wr.pos(half + 2, -2, 0).color(0, 0, 0, 0.35f).endVertex();
			t.draw();
			GlStateManager.enableTexture2D();
		}
		font.drawString(text, -half, 0, argb, true);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.disableBlend();
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.popMatrix();
	}
}
