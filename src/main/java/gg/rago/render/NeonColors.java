package gg.rago.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The colours of Rago's neon red cosmetics (the Rago cape, the neon cap, the small dragon wings and
 * the Rago shield). The red of the texture is turned into the chosen colour with its shading kept,
 * while the black and the other colours stay as they are. A texture is recoloured once, the first
 * time it is drawn in a colour; only the colour in use is kept for each texture.
 */
public final class NeonColors {
	private static final Logger LOG = LogManager.getLogger("Rago");
	/** The choices, red (the textures as drawn) first. */
	public static final String[] IDS = {"red", "orange", "yellow", "green", "cyan", "blue", "purple", "pink", "white"};
	/** The hue of each choice, in degrees; white has none. */
	private static final float[] HUES = {0, 28, 54, 125, 186, 218, 272, 325, -1};
	/** Rago's red, as the swatches show it recoloured. */
	private static final int RAGO_RED = 0xFFF2392C;

	private static final Map<ResourceLocation, Made> MADE = new HashMap<>();

	private static final class Made {
		final String id;
		final ResourceLocation texture;

		Made(String id, ResourceLocation texture) {
			this.id = id;
			this.texture = texture;
		}
	}

	private NeonColors() {
	}

	/** {@code base} in colour {@code id}; {@code base} itself for red, or when it cannot be read. */
	public static ResourceLocation texture(ResourceLocation base, String id) {
		float hue = hue(id);
		if (hue == 0) {
			return base;
		}
		Made m = MADE.get(base);
		if (m != null && m.id.equals(id)) {
			return m.texture == null ? base : m.texture;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (m != null && m.texture != null) {
			mc.getTextureManager().deleteTexture(m.texture);
		}
		ResourceLocation made = null;
		try (InputStream in = mc.getResourceManager().getResource(base).getInputStream()) {
			BufferedImage img = TextureUtil.readBufferedImage(in);
			recolor(img, hue);
			made = mc.getTextureManager().getDynamicTextureLocation("rago_neon_" + id, new DynamicTexture(img));
		} catch (IOException | RuntimeException e) {
			LOG.warn("Cannot recolour {}: {}", base, e.toString());
		}
		MADE.put(base, new Made(id, made));
		return made == null ? base : made;
	}

	/** The colour a choice shows as, for its swatch. */
	public static int swatch(String id) {
		return recolor(RAGO_RED, hue(id));
	}

	/** The hue of a choice in degrees, -1 for white, 0 for red or an unknown choice. */
	static float hue(String id) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) {
				return HUES[i];
			}
		}
		return 0;
	}

	static void recolor(BufferedImage img, float hue) {
		int w = img.getWidth();
		int h = img.getHeight();
		int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		for (int i = 0; i < px.length; i++) {
			px[i] = recolor(px[i], hue);
		}
		img.setRGB(0, 0, w, h, px, 0, w);
	}

	/**
	 * One pixel: a red one (within 30 degrees of red, clearly coloured, not black) takes the hue,
	 * shifted as much as it was from pure red, or turns white-grey; any other pixel is kept.
	 */
	static int recolor(int argb, float hue) {
		int a = argb >>> 24;
		if (a == 0 || hue == 0) {
			return argb;
		}
		float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
		float deg = hsb[0] * 360;
		float offset = deg > 180 ? deg - 360 : deg;
		if (Math.abs(offset) > 30 || hsb[1] < 0.3f || hsb[2] < 0.05f) {
			return argb;
		}
		int rgb = hue < 0
			? Color.HSBtoRGB(0, hsb[1] * 0.05f, Math.min(1, hsb[2] * 1.05f))
			: Color.HSBtoRGB(((hue + offset) / 360 + 1) % 1, hsb[1], hsb[2]);
		return a << 24 | rgb & 0xFFFFFF;
	}
}
