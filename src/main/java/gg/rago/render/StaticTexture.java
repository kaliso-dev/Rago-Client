package gg.rago.render;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;

import java.awt.image.BufferedImage;

/**
 * A texture uploaded once from an image. Unlike vanilla's DynamicTexture it does not keep a copy of
 * the pixels in memory after the upload, since Rago's generated textures never change.
 */
public final class StaticTexture extends AbstractTexture {
	public StaticTexture(BufferedImage image) {
		TextureUtil.uploadTextureImageAllocate(getGlTextureId(), image, false, false);
	}

	@Override
	public void loadTexture(IResourceManager manager) {
	}
}
