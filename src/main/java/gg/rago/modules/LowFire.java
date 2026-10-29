package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Draws the flames in front of the camera lower and fainter while you burn, so you can still see. */
public final class LowFire extends Module {
	private final IntOption height = addOption(new IntOption("fire_height", 60, 0, 100));
	private final IntOption opacity = addOption(new IntOption("fire_opacity", 90, 10, 100));

	public LowFire() {
		super("low_fire", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	@SubscribeEvent
	public void onOverlay(RenderBlockOverlayEvent event) {
		if (!isEnabled() || event.overlayType != RenderBlockOverlayEvent.OverlayType.FIRE) {
			return;
		}
		event.setCanceled(true);
		draw(height.get() / 100f * 0.4f, opacity.get() / 100f);
	}

	/** The two tilted flame quads vanilla draws, moved down by {@code drop}. */
	private static void draw(float drop, float alpha) {
		Minecraft mc = Minecraft.getMinecraft();
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer wr = tessellator.getWorldRenderer();
		GlStateManager.color(1, 1, 1, alpha);
		GlStateManager.depthFunc(519);
		GlStateManager.depthMask(false);
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/fire_layer_1");
		mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
		for (int side = -1; side <= 1; side += 2) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(-side * 0.24f, -0.3f - drop, 0);
			GlStateManager.rotate(side * 10f, 0, 1, 0);
			wr.begin(7, DefaultVertexFormats.POSITION_TEX);
			wr.pos(-0.5, -0.5, -0.5).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
			wr.pos(0.5, -0.5, -0.5).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
			wr.pos(0.5, 0.5, -0.5).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
			wr.pos(-0.5, 0.5, -0.5).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
			tessellator.draw();
			GlStateManager.popMatrix();
		}
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.disableBlend();
		GlStateManager.depthMask(true);
		GlStateManager.depthFunc(515);
	}
}
