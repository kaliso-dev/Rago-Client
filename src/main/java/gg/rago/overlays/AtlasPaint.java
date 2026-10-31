package gg.rago.overlays;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.function.IntUnaryOperator;

/**
 * Puts Rago's wool and clay straight into the game's block texture atlas, over the pack's, at the
 * size the pack uses: a change shows at once, without reloading every resource as a pack would.
 * Turning a style off puts the pack's own picture back. Run again after each resource reload (the
 * atlas is made anew then).
 */
public final class AtlasPaint {
	private static Field mipmapLevels;

	private AtlasPaint() {
	}

	/**
	 * Paints the sixteen wools in {@code wool} and stained clays in {@code clay} (null: the pack's own),
	 * the team dyes in {@code teamRgb.applyAsInt(dye)} when it is not -1. Returns how many were painted.
	 */
	public static int paint(BlockArt.Style wool, BlockArt.Style clay, IntUnaryOperator teamRgb) {
		Minecraft mc = Minecraft.getMinecraft();
		TextureMap atlas = mc.getTextureMapBlocks();
		if (atlas == null) {
			return 0;
		}
		int levels = mipmapLevels(atlas);
		int painted = 0;
		GlStateManager.bindTexture(atlas.getGlTextureId());
		for (int dye = 0; dye < BlockArt.DYES.length; dye++) {
			int team = teamRgb.applyAsInt(dye);
			painted += one(atlas, "wool_colored_" + BlockArt.DYES[dye], wool, BlockArt.color(dye, false, team), false, levels);
			painted += one(atlas, "hardened_clay_stained_" + BlockArt.DYES[dye], clay, BlockArt.color(dye, true, team), true, levels);
		}
		return painted;
	}

	private static int one(TextureMap atlas, String name, BlockArt.Style style, int rgb, boolean clay, int levels) {
		TextureAtlasSprite sprite = atlas.getAtlasSprite("minecraft:blocks/" + name);
		if (sprite == null || sprite == atlas.getMissingSprite()) {
			return 0;
		}
		int w = sprite.getIconWidth();
		int h = sprite.getIconHeight();
		if (w != h || w <= 0) {
			return 0;
		}
		int[] pixels = style != null ? BlockArt.pixels(style, rgb, w, clay) : packPicture(name, w);
		if (pixels == null) {
			return 0;
		}
		int[][] mips = new int[levels + 1][];
		mips[0] = pixels;
		mips = TextureUtil.generateMipmapData(levels, w, mips);
		TextureUtil.uploadTextureMipmap(mips, w, h, sprite.getOriginX(), sprite.getOriginY(), false, false);
		return style != null ? 1 : 0;
	}

	/** The packs' own picture of a block, first frame, at {@code size}, or null if it cannot be read. */
	private static int[] packPicture(String name, int size) {
		BufferedImage img = packFrame(name);
		if (img == null || img.getWidth() != size) {
			return null; // not what the atlas holds: leave it
		}
		int[] out = new int[size * size];
		img.getRGB(0, 0, size, size, out, 0, size);
		return out;
	}

	/**
	 * The first frame of a block's picture in the packs in use (an animated one is a column of square
	 * frames), or null if it cannot be read.
	 */
	public static BufferedImage packFrame(String name) {
		ResourceLocation rl = new ResourceLocation("minecraft", "textures/blocks/" + name + ".png");
		try {
			IResource r = Minecraft.getMinecraft().getResourceManager().getResource(rl);
			BufferedImage img;
			try (InputStream in = r.getInputStream()) {
				img = TextureUtil.readBufferedImage(in);
			}
			int w = img.getWidth();
			if (w <= 0 || img.getHeight() < w) {
				return null;
			}
			return img.getHeight() == w ? img : img.getSubimage(0, 0, w, w);
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.debug("Overlays: could not read {}", rl, e);
			return null;
		}
	}

	/** The atlas's mipmap levels (what it was made with), or the setting when unknown. */
	private static int mipmapLevels(TextureMap atlas) {
		try {
			if (mipmapLevels == null) {
				mipmapLevels = ReflectionHelper.findField(TextureMap.class, "field_147636_j", "mipmapLevels");
			}
			return Math.max(0, mipmapLevels.getInt(atlas));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Math.max(0, Minecraft.getMinecraft().gameSettings.mipmapLevels);
		}
	}
}
