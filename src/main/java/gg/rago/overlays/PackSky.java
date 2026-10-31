package gg.rago.overlays;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Custom skies from resource packs, in the format OptiFine and MCPatcher read
 * ({@code mcpatcher/sky/world0/sky1.properties} and its picture, or the same under
 * {@code optifine/sky/}), drawn by Rago when OptiFine is not installed, so a sky overlay works in
 * any launcher. Read the way OptiFine reads them: layers from 1 up to the first missing number, each
 * with its fade times, blend, rotation with the time of day and weather; the highest pack that has a
 * file wins. Drawn over the game's sky and under the sun and moon, where OptiFine draws them.
 */
public final class PackSky {
	static final int ALPHA = 0;
	static final int ADD = 1;
	static final int SUBTRACT = 2;
	static final int MULTIPLY = 3;
	static final int DODGE = 4;
	static final int BURN = 5;
	static final int SCREEN = 6;
	static final int OVERLAY = 7;
	static final int REPLACE = 8;
	private static final String[] BLENDS = {"alpha", "add", "subtract", "multiply", "dodge", "burn", "screen", "overlay", "replace"};

	/** One layer: a picture and when and how it shows. */
	static final class Layer {
		ResourceLocation source;
		/** Times in ticks of the day (0 is 6:00); -1 when not given. */
		int startFadeIn = -1;
		int endFadeIn = -1;
		int startFadeOut = -1;
		int endFadeOut = -1;
		/** No fade times at all: always shown (newer OptiFine; OptiFine 1.8.9 required them). */
		boolean always;
		int blend = ADD;
		boolean rotate = true;
		float speed = 1;
		/** The axis the layer turns around, in the sun's turned frame (OptiFine stores z, y, -x). */
		float[] axis = {1, 0, 0};
		boolean clear = true;
		boolean rain;
		boolean thunder;

		/** Brightness from the fade times at a time of day (0 to 23999). */
		float fade(int time) {
			if (always) {
				return 1;
			}
			if (between(time, startFadeIn, endFadeIn)) {
				return (float) normalize(time - startFadeIn) / Math.max(1, normalize(endFadeIn - startFadeIn));
			}
			if (between(time, endFadeIn, startFadeOut)) {
				return 1;
			}
			if (between(time, startFadeOut, endFadeOut)) {
				return 1 - (float) normalize(time - startFadeOut) / Math.max(1, normalize(endFadeOut - startFadeOut));
			}
			return 0;
		}

		/** Brightness from the weather: clear, rain and thunder each count when the layer lists them. */
		float weather(float rainStrength, float thunderStrength) {
			float w = 0;
			if (clear) {
				w += 1 - rainStrength;
			}
			if (rain) {
				w += rainStrength - thunderStrength;
			}
			if (thunder) {
				w += thunderStrength;
			}
			return Math.max(0, Math.min(1, w));
		}
	}

	private final List<Layer> layers;
	/** The pack folder the layers came from ("mcpatcher" or "optifine"), for the menu. */
	private final String from;

	private PackSky(List<Layer> layers, String from) {
		this.layers = layers;
		this.from = from;
	}

	/** No sky from packs. */
	public static final PackSky NONE = new PackSky(Collections.emptyList(), null);

	public boolean isEmpty() {
		return layers.isEmpty();
	}

	public int layerCount() {
		return layers.size();
	}

	/** The packs' folder the sky came from: "mcpatcher" or "optifine" (null when none). */
	public String folder() {
		return from;
	}

	/** Reads the overworld's sky layers from the packs in use; {@link #NONE} when there are none. */
	public static PackSky load(IResourceManager resources) {
		for (String root : new String[] {"mcpatcher", "optifine"}) {
			List<Layer> found = new ArrayList<>();
			String dir = root + "/sky/world0/";
			for (int i = 0; i < 1000; i++) {
				Properties props = read(resources, dir + "sky" + i + ".properties");
				if (props == null) {
					if (i == 0) {
						continue; // OptiFine 1.8.9 starts at 1, newer packs at 0
					}
					break;
				}
				Layer l = parse(props, dir, "sky" + i + ".png", root);
				if (l == null) {
					RagoClient.LOG.info("Overlays: {}sky{}.properties has no valid fade times, skipped as OptiFine would", dir, i);
					continue;
				}
				if (!exists(resources, l.source)) {
					RagoClient.LOG.info("Overlays: sky picture {} not found", l.source);
					continue;
				}
				found.add(l);
			}
			if (!found.isEmpty()) {
				RagoClient.LOG.info("Overlays: {} sky layer(s) from packs in {}", found.size(), dir);
				return new PackSky(found, root);
			}
		}
		return NONE;
	}

	private static Properties read(IResourceManager resources, String path) {
		try {
			IResource r = resources.getResource(new ResourceLocation("minecraft", path));
			try (InputStream in = r.getInputStream()) {
				Properties p = new Properties();
				p.load(in);
				return p;
			}
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.warn("Overlays: could not read {}", path, e);
			return null;
		}
	}

	private static boolean exists(IResourceManager resources, ResourceLocation rl) {
		try {
			resources.getResource(rl).getInputStream().close();
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** One layer from its properties, or null when OptiFine would refuse it. */
	static Layer parse(Properties p, String dir, String defaultSource, String root) {
		Layer l = new Layer();
		l.source = source(p.getProperty("source", dir + defaultSource), dir, root);
		l.startFadeIn = time(p.getProperty("startFadeIn"));
		l.endFadeIn = time(p.getProperty("endFadeIn"));
		l.startFadeOut = time(p.getProperty("startFadeOut"));
		l.endFadeOut = time(p.getProperty("endFadeOut"));
		if (p.getProperty("startFadeIn") == null && p.getProperty("endFadeIn") == null
			&& p.getProperty("startFadeOut") == null && p.getProperty("endFadeOut") == null) {
			l.always = true;
		} else if (!fadeTimesValid(l)) {
			return null;
		}
		String blend = p.getProperty("blend");
		if (blend != null) {
			int b = java.util.Arrays.asList(BLENDS).indexOf(blend.trim().toLowerCase(Locale.ROOT));
			l.blend = b < 0 ? ADD : b;
		}
		l.rotate = !"false".equalsIgnoreCase(trim(p.getProperty("rotate")));
		l.speed = number(p.getProperty("speed"), 1);
		if (l.speed < 0) {
			return null;
		}
		String axis = p.getProperty("axis");
		if (axis != null) {
			String[] parts = axis.trim().split("\\s+");
			if (parts.length == 3) {
				float ax = number(parts[0], 0);
				float ay = number(parts[1], 0);
				float az = number(parts[2], 0);
				if (ax * ax + ay * ay + az * az >= 1e-5f) {
					l.axis = new float[] {az, ay, -ax};
				}
			}
		}
		String weather = p.getProperty("weather");
		if (weather != null) {
			List<String> w = java.util.Arrays.asList(weather.trim().toLowerCase(Locale.ROOT).split("[\\s|,]+"));
			l.clear = w.contains("clear");
			l.rain = w.contains("rain");
			l.thunder = w.contains("thunder");
		}
		return l;
	}

	/** OptiFine 1.8.9's rule: start and end of fade-in and end of fade-out given, the four parts making a day. */
	private static boolean fadeTimesValid(Layer l) {
		if (l.startFadeIn < 0 || l.endFadeIn < 0 || l.endFadeOut < 0) {
			return false;
		}
		int fadeIn = normalize(l.endFadeIn - l.startFadeIn);
		if (l.startFadeOut < 0) {
			l.startFadeOut = normalize(l.endFadeOut - fadeIn);
			if (between(l.startFadeOut, l.startFadeIn, l.endFadeIn)) {
				l.startFadeOut = l.endFadeIn;
			}
		}
		int on = normalize(l.startFadeOut - l.endFadeIn);
		int fadeOut = normalize(l.endFadeOut - l.startFadeOut);
		int off = normalize(l.startFadeIn - l.endFadeOut);
		return fadeIn + on + fadeOut + off == 24000;
	}

	/** A picture's location: "./x" next to the properties, "~/x" in the pack's mcpatcher folder, "ns:path", or from assets/minecraft. */
	static ResourceLocation source(String path, String dir, String root) {
		String s = path.trim();
		if (s.startsWith("./")) {
			s = dir + s.substring(2);
		} else if (s.startsWith("~/")) {
			s = root + "/" + s.substring(2);
		} else if (s.startsWith("/")) {
			s = s.substring(1);
		}
		int colon = s.indexOf(':');
		if (colon > 0) {
			return new ResourceLocation(s.substring(0, colon), s.substring(colon + 1));
		}
		return new ResourceLocation("minecraft", s);
	}

	/** "hh:mm" as ticks of the day (6:00 is 0), or -1. */
	static int time(String s) {
		if (s == null) {
			return -1;
		}
		String[] parts = s.trim().split(":");
		if (parts.length != 2) {
			return -1;
		}
		try {
			int h = Integer.parseInt(parts[0].trim());
			int m = Integer.parseInt(parts[1].trim());
			if (h < 0 || h > 23 || m < 0 || m > 59) {
				return -1;
			}
			h -= 6;
			if (h < 0) {
				h += 24;
			}
			return h * 1000 + (int) (m / 60.0 * 1000);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static float number(String s, float fallback) {
		if (s == null) {
			return fallback;
		}
		try {
			return Float.parseFloat(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String trim(String s) {
		return s == null ? null : s.trim();
	}

	static int normalize(int t) {
		return ((t % 24000) + 24000) % 24000;
	}

	static boolean between(int t, int start, int end) {
		return start <= end ? t >= start && t <= end : t >= start || t <= end;
	}

	/**
	 * Draws the layers; called inside the game's sky drawing, right after it turns for the sun, with
	 * the state it has there (texture on, blending on), which is put back after.
	 */
	public void render(WorldClient world, float partialTicks) {
		if (layers.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		int time = (int) (world.getWorldTime() % 24000L);
		float celestial = world.getCelestialAngle(partialTicks);
		float rain = world.getRainStrength(partialTicks);
		float thunder = world.getThunderStrength(partialTicks);
		if (rain > 0) {
			thunder /= rain;
		}
		for (Layer l : layers) {
			float b = l.weather(rain, thunder) * l.fade(time);
			if (b < 1e-4f) {
				continue;
			}
			mc.getTextureManager().bindTexture(l.source);
			blend(l.blend, Math.min(1, b));
			GlStateManager.pushMatrix();
			if (l.rotate) {
				float dayStart = 0;
				if (l.speed != Math.round(l.speed)) {
					long day = (world.getWorldTime() + 18000L) / 24000L;
					dayStart = (float) ((day * (double) (l.speed % 1)) % 1.0);
				}
				GlStateManager.rotate(360 * (dayStart + celestial * l.speed), l.axis[0], l.axis[1], l.axis[2]);
			}
			SkyBox.draw(0, 0);
			GlStateManager.popMatrix();
		}
		// as the game had it, for the sun and moon drawn next
		GlStateManager.disableAlpha();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.color(1, 1, 1, 1 - rain);
		GlStateManager.enableTexture2D();
	}

	/** How a layer mixes with what is under it, as OptiFine sets each blend mode. */
	private static void blend(int mode, float b) {
		if (mode == REPLACE) {
			GlStateManager.enableAlpha();
			GlStateManager.disableBlend();
			GlStateManager.color(1, 1, 1, b);
			return;
		}
		GlStateManager.disableAlpha();
		GlStateManager.enableBlend();
		switch (mode) {
			case ALPHA:
				GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GlStateManager.color(1, 1, 1, b);
				break;
			case SUBTRACT:
				GlStateManager.blendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ZERO);
				GlStateManager.color(b, b, b, 1);
				break;
			case MULTIPLY:
				GlStateManager.blendFunc(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GlStateManager.color(b, b, b, b);
				break;
			case DODGE:
				GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
				GlStateManager.color(b, b, b, 1);
				break;
			case BURN:
				GlStateManager.blendFunc(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);
				GlStateManager.color(b, b, b, 1);
				break;
			case SCREEN:
				GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR);
				GlStateManager.color(b, b, b, 1);
				break;
			case OVERLAY:
				GlStateManager.blendFunc(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR);
				GlStateManager.color(b, b, b, 1);
				break;
			default: // add
				GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				GlStateManager.color(1, 1, 1, b);
		}
	}
}
