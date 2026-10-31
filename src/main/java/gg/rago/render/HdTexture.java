package gg.rago.render;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A big picture shown smooth: filtered, with mipmaps, where Minecraft draws its textures as sharp
 * pixels and without mipmaps (a 2048 px cape would shimmer when small). The picture is read and its
 * mipmaps made on a thread of its own, so a cape seen for the first time does not stall the game; it
 * goes to the graphics card on the game's thread, the next time it is asked for.
 */
public final class HdTexture extends AbstractTexture {
	private static final Map<ResourceLocation, HdTexture> ALL = new HashMap<>();
	private static final ExecutorService READER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Rago textures");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private final ResourceLocation location;
	/** The picture and its mipmaps once read, until they are on the graphics card. */
	private volatile int[][] read;
	private volatile boolean failed;
	private int width;
	private int height;
	private boolean uploaded;

	private HdTexture(ResourceLocation location) {
		this.location = location;
	}

	/**
	 * {@code location}, once its picture is ready to draw; null while it is being read, or if it cannot
	 * be. The first call starts reading it. Game thread only.
	 */
	public static ResourceLocation ready(ResourceLocation location) {
		HdTexture t = ALL.get(location);
		if (t == null) {
			t = new HdTexture(location);
			ALL.put(location, t);
			t.start(Minecraft.getMinecraft().getResourceManager());
		}
		return t.poll() ? location : null;
	}

	private void start(IResourceManager manager) {
		READER.execute(() -> {
			try (InputStream in = manager.getResource(location).getInputStream()) {
				BufferedImage img = TextureUtil.readBufferedImage(in);
				int w = img.getWidth();
				int h = img.getHeight();
				int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
				width = w;
				height = h;
				read = mipmaps(pixels, w, h, levels(w, h));
			} catch (IOException | RuntimeException e) {
				RagoClient.LOG.warn("Cannot read {}: {}", location, e.toString());
				failed = true;
			}
		});
	}

	/** Whether the picture is on the graphics card; puts it there when it has just been read. */
	private boolean poll() {
		if (uploaded) {
			return true;
		}
		if (failed || read == null) {
			return false;
		}
		// the texture manager keeps it from now on (and binds it by its location)
		uploaded = Minecraft.getMinecraft().getTextureManager().loadTexture(location, this);
		read = null;
		if (!uploaded) {
			failed = true;
		}
		return uploaded;
	}

	@Override
	public void loadTexture(IResourceManager manager) throws IOException {
		int[][] mips = read;
		if (mips == null) {
			// a resource reload: the picture is already on the graphics card
			if (!uploaded) {
				throw new IOException("not read yet");
			}
			return;
		}
		TextureUtil.allocateTextureImpl(getGlTextureId(), mips.length - 1, width, height);
		TextureUtil.uploadTextureMipmap(mips, width, height, 0, 0, true, false);
	}

	/**
	 * How many mipmaps to make below the picture: halving down to the size of Minecraft's own capes (64
	 * px wide), not further, where the parts of the cape would run into one another.
	 */
	static int levels(int w, int h) {
		int levels = 0;
		while ((w >> (levels + 1)) >= 64 && (h >> (levels + 1)) >= 1 && ((w >> levels) & 1) == 0 && ((h >> levels) & 1) == 0) {
			levels++;
		}
		return levels;
	}

	/**
	 * The picture and its halvings, each pixel of a level the average of four of the level above (the
	 * see-through ones left out of the colour, so edges do not turn dark).
	 */
	static int[][] mipmaps(int[] pixels, int w, int h, int levels) {
		int[][] out = new int[levels + 1][];
		out[0] = pixels;
		for (int l = 1; l <= levels; l++) {
			int[] up = out[l - 1];
			int uw = w >> (l - 1);
			int lw = w >> l;
			int lh = h >> l;
			int[] level = new int[lw * lh];
			for (int y = 0; y < lh; y++) {
				for (int x = 0; x < lw; x++) {
					int i = 2 * y * uw + 2 * x;
					level[y * lw + x] = average(up[i], up[i + 1], up[i + uw], up[i + uw + 1]);
				}
			}
			out[l] = level;
		}
		return out;
	}

	static int average(int a, int b, int c, int d) {
		int aa = a >>> 24;
		int ab = b >>> 24;
		int ac = c >>> 24;
		int ad = d >>> 24;
		int alpha = aa + ab + ac + ad;
		if (alpha == 0) {
			return 0;
		}
		int r = ((a >> 16 & 0xFF) * aa + (b >> 16 & 0xFF) * ab + (c >> 16 & 0xFF) * ac + (d >> 16 & 0xFF) * ad + alpha / 2) / alpha;
		int g = ((a >> 8 & 0xFF) * aa + (b >> 8 & 0xFF) * ab + (c >> 8 & 0xFF) * ac + (d >> 8 & 0xFF) * ad + alpha / 2) / alpha;
		int bl = ((a & 0xFF) * aa + (b & 0xFF) * ab + (c & 0xFF) * ac + (d & 0xFF) * ad + alpha / 2) / alpha;
		return (alpha + 2) / 4 << 24 | r << 16 | g << 8 | bl;
	}
}
