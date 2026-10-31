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
 * The colours of Rago's neon red cosmetics (the Rago cape, the small dragon wings and the Rago
 * shield; the Rago cap and beanie take the {@link #swatch} colour). The red of the texture is turned
 * into the chosen colour with its shading kept, while the black and the other colours stay as they
 * are; {@link #glow} makes the faint light added over them. A texture is recoloured once, the first
 * time it is drawn in a colour, and kept: other Rago players can wear the same piece in other colours
 * at the same time, so each colour seen keeps its own texture.
 */
public final class NeonColors {
	private static final Logger LOG = LogManager.getLogger("Rago");
	/** The choices, red (the textures as drawn) first. */
	public static final String[] IDS = {"red", "orange", "yellow", "green", "cyan", "blue", "purple", "pink", "white"};
	/** The hue of each choice, in degrees; white has none. */
	private static final float[] HUES = {0, 28, 54, 125, 186, 218, 272, 325, -1};
	/** Rago's red, as the swatches show it recoloured. */
	private static final int RAGO_RED = 0xFFF2392C;

	/** The textures made from each base, by colour (index in {@link #IDS}). */
	private static final Map<ResourceLocation, ResourceLocation[]> MADE = new HashMap<>();
	private static final Map<ResourceLocation, ResourceLocation[]> GLOWS = new HashMap<>();
	/** Marks a colour that could not be made, so it is not tried again every frame. */
	private static final ResourceLocation FAILED = new ResourceLocation("rago", "neon_failed");

	private NeonColors() {
	}

	/** {@code base} in colour {@code id}; {@code base} itself for red, or when it cannot be read. */
	public static ResourceLocation texture(ResourceLocation base, String id) {
		float hue = hue(id);
		if (hue == 0) {
			return base;
		}
		ResourceLocation made = make(MADE, base, id, "rago_neon_", img -> recolor(img, hue));
		return made == null ? base : made;
	}

	/**
	 * The glow of {@code base} in colour {@code id}, to be added over it: its bright neon pixels at
	 * half strength and a faint halo two pixels around them, the rest clear. Inside a wide neon area
	 * only its edge glows, so the area keeps the red of the texture (the Rago shield's rim and logo)
	 * instead of turning pale. Null when it cannot be made.
	 */
	public static ResourceLocation glow(ResourceLocation base, String id) {
		float hue = hue(id);
		return make(GLOWS, base, id, "rago_neon_glow_", img -> glow(img, hue));
	}

	/** The texture made from {@code base} for {@code id}, made the first time and kept; null if it cannot be. */
	private static ResourceLocation make(Map<ResourceLocation, ResourceLocation[]> made, ResourceLocation base, String id,
		String name, java.util.function.Consumer<BufferedImage> change) {
		ResourceLocation[] byColour = made.get(base);
		if (byColour == null) {
			byColour = new ResourceLocation[IDS.length];
			made.put(base, byColour);
		}
		int index = Math.max(0, index(id));
		ResourceLocation done = byColour[index];
		if (done != null) {
			return done == FAILED ? null : done;
		}
		Minecraft mc = Minecraft.getMinecraft();
		ResourceLocation tex = null;
		try (InputStream in = mc.getResourceManager().getResource(base).getInputStream()) {
			BufferedImage img = TextureUtil.readBufferedImage(in);
			change.accept(img);
			tex = mc.getTextureManager().getDynamicTextureLocation(name + id, new DynamicTexture(img));
		} catch (IOException | RuntimeException e) {
			LOG.warn("Cannot recolour {}: {}", base, e.toString());
		}
		byColour[index] = tex == null ? FAILED : tex;
		return tex;
	}

	/** The index of a choice in {@link #IDS}, -1 if it is none of them. */
	static int index(String id) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) {
				return i;
			}
		}
		return -1;
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

	/** Whether a pixel is a bright part of the neon (not the dark red of a wing's membrane). */
	static boolean bright(int argb) {
		if (argb >>> 24 == 0) {
			return false;
		}
		float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
		float deg = hsb[0] * 360;
		return (deg < 30 || deg > 330) && hsb[1] >= 0.4f && hsb[2] >= 0.45f;
	}

	static void glow(BufferedImage img, float hue) {
		int w = img.getWidth();
		int h = img.getHeight();
		int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		boolean[] lit = new boolean[px.length];
		for (int i = 0; i < px.length; i++) {
			lit[i] = bright(px[i]);
		}
		int halo = recolor(RAGO_RED, hue) & 0xFFFFFF;
		int[] out = new int[px.length];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				if (px[i] >>> 24 == 0) {
					continue;
				}
				if (lit[i]) {
					if (!inside(lit, w, h, x, y)) {
						out[i] = 0x80000000 | recolor(px[i], hue) & 0xFFFFFF;
					}
					continue;
				}
				// the halo: by how close the nearest bright pixel is, one or two pixels away
				int near = 3;
				for (int dy = -2; dy <= 2 && near > 1; dy++) {
					for (int dx = -2; dx <= 2; dx++) {
						int nx = x + dx;
						int ny = y + dy;
						if (nx >= 0 && ny >= 0 && nx < w && ny < h && lit[ny * w + nx]) {
							near = Math.min(near, Math.max(Math.abs(dx), Math.abs(dy)));
						}
					}
				}
				if (near == 1) {
					out[i] = 0x48000000 | halo;
				} else if (near == 2) {
					out[i] = 0x22000000 | halo;
				}
			}
		}
		img.setRGB(0, 0, w, h, out, 0, w);
	}

	/** Whether every pixel up to two away from (x, y) is bright too: (x, y) is inside a wide neon area. */
	private static boolean inside(boolean[] lit, int w, int h, int x, int y) {
		for (int dy = -2; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if (nx < 0 || ny < 0 || nx >= w || ny >= h || !lit[ny * w + nx]) {
					return false;
				}
			}
		}
		return true;
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
