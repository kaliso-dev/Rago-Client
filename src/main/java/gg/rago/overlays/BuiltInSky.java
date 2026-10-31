package gg.rago.overlays;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/**
 * One of Rago's skies in the world, in place of the game's: the sky box painted by {@link SkyArt},
 * then its stars, sun or moon as sprites so they stay sharp. Painted on a background thread when
 * chosen (about a second), then drawn from display lists: the cost per frame is a handful of quads.
 * The sky does not follow the time of day; rain dims it a little.
 */
public final class BuiltInSky extends IRenderHandler {
	/** Pixels per side of the sky box: smooth things only, so this is plenty. */
	static final int FACE = 256;
	private static final int STAR_PX = 32;
	private static final int DISC_PX = 64;

	public final SkyArt.Kind kind;
	public final int horizon;
	private final CompletableFuture<Painted> painting;
	private int boxTexture = -1;
	private int starTexture = -1;
	private int discTexture = -1;
	private int boxList = -1;
	private int starList = -1;
	private int discList = -1;
	private boolean failed;

	private static final class Painted {
		int[] box;
		double[][] bodies;
		int[] star;
		int[] discs;
	}

	public BuiltInSky(SkyArt.Kind kind) {
		this.kind = kind;
		this.horizon = SkyArt.horizon(kind);
		this.painting = CompletableFuture.supplyAsync(() -> {
			Painted p = new Painted();
			p.box = SkyArt.box(kind, FACE);
			p.bodies = SkyArt.bodies(kind);
			p.star = SkyArt.starSprite(STAR_PX);
			// the sun and the moon side by side in one picture
			int[] sun = SkyArt.disc(false, sunColor(p.bodies), DISC_PX);
			int[] moon = SkyArt.disc(true, 0xEEF2FF, DISC_PX);
			p.discs = new int[DISC_PX * 2 * DISC_PX];
			for (int y = 0; y < DISC_PX; y++) {
				System.arraycopy(sun, y * DISC_PX, p.discs, y * DISC_PX * 2, DISC_PX);
				System.arraycopy(moon, y * DISC_PX, p.discs, y * DISC_PX * 2 + DISC_PX, DISC_PX);
			}
			return p;
		});
	}

	private static int sunColor(double[][] bodies) {
		for (double[] b : bodies) {
			if (b[6] == 1) {
				return (int) b[4];
			}
		}
		return 0xFFFFFF;
	}

	/** Whether the sky is painted and can be drawn (it shows from the next frame). */
	public boolean ready() {
		return !failed && painting.isDone() && !painting.isCompletedExceptionally();
	}

	@Override
	public void render(float partialTicks, WorldClient world, Minecraft mc) {
		if (!upload()) {
			return;
		}
		float rain = 1 - world.getRainStrength(partialTicks) * 0.45f;
		GlStateManager.disableFog();
		GlStateManager.disableAlpha();
		GlStateManager.depthMask(false);
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.color(rain, rain, rain, 1);
		GlStateManager.pushMatrix();
		// the turn every custom sky is drawn with (the sun's), which SkyArt painted for
		GlStateManager.rotate(-90, 0, 1, 0);
		GlStateManager.bindTexture(boxTexture);
		GlStateManager.callList(boxList);
		GlStateManager.popMatrix();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.color(1, 1, 1, rain * rain);
		GlStateManager.bindTexture(starTexture);
		GlStateManager.callList(starList);
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.color(1, 1, 1, rain);
		GlStateManager.bindTexture(discTexture);
		GlStateManager.callList(discList);
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.disableBlend();
		GlStateManager.enableAlpha();
		GlStateManager.enableFog();
		GlStateManager.depthMask(true);
	}

	/** Makes the textures and display lists once the painting is done; false until then. */
	private boolean upload() {
		if (boxList >= 0) {
			return true;
		}
		if (!ready()) {
			return false;
		}
		try {
			Painted p = painting.join();
			boxTexture = texture(p.box, FACE * 3, FACE * 2, true);
			starTexture = texture(p.star, STAR_PX, STAR_PX, true);
			discTexture = texture(p.discs, DISC_PX * 2, DISC_PX, true);
			boxList = GLAllocation.generateDisplayLists(3);
			starList = boxList + 1;
			discList = boxList + 2;
			GL11.glNewList(boxList, GL11.GL_COMPILE);
			// half a pixel in from each cell's edge: smooth filtering stays inside the side
			SkyBox.draw(0.5 / (FACE * 3), 0.5 / (FACE * 2));
			GL11.glEndList();
			GL11.glNewList(starList, GL11.GL_COMPILE);
			sprites(p.bodies, 0);
			GL11.glEndList();
			GL11.glNewList(discList, GL11.GL_COMPILE);
			sprites(p.bodies, 1);
			GL11.glEndList();
			return true;
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("Overlays: could not make the {} sky", kind, e);
			failed = true;
			return false;
		}
	}

	/** A GL texture of the picture, smoothed and clamped at its edges. */
	private static int texture(int[] argb, int w, int h, boolean smooth) {
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, w, h, argb, 0, w);
		int id = TextureUtil.glGenTextures();
		TextureUtil.uploadTextureImageAllocate(id, image, smooth, true);
		return id;
	}

	/** Quads facing the camera for the stars ({@code kind} 0) or the sun and moon (1). */
	private static void sprites(double[][] bodies, int kind) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		double r = SkyBox.SIZE * 0.9;
		for (double[] b : bodies) {
			boolean star = b[6] == 0;
			if (star != (kind == 0)) {
				continue;
			}
			double x = b[0];
			double y = b[1];
			double z = b[2];
			// two directions across the sprite
			double ux = 0;
			double uy = 1;
			double uz = 0;
			if (Math.abs(y) > 0.95) {
				ux = 1;
				uy = 0;
			}
			double rx = y * uz - z * uy;
			double ry = z * ux - x * uz;
			double rz = x * uy - y * ux;
			double rn = Math.sqrt(rx * rx + ry * ry + rz * rz);
			rx /= rn;
			ry /= rn;
			rz /= rn;
			double vx = ry * z - rz * y;
			double vy = rz * x - rx * z;
			double vz = rx * y - ry * x;
			// a disc fills 70 % of its picture: grow the quad so the disc has the size asked for
			double half = r * Math.tan(Math.toRadians(b[3])) * (star ? 1 : 1 / 0.7);
			int rgb = (int) b[4];
			int cr = rgb >> 16 & 0xFF;
			int cg = rgb >> 8 & 0xFF;
			int cb = rgb & 0xFF;
			int ca = (int) Math.round(Math.min(1, b[5]) * 255);
			double u0 = star ? 0 : b[6] == 1 ? 0 : 0.5;
			double u1 = star ? 1 : u0 + 0.5;
			double cx = x * r;
			double cy = y * r;
			double cz = z * r;
			wr.pos(cx - rx * half - vx * half, cy - ry * half - vy * half, cz - rz * half - vz * half).tex(u0, 1).color(cr, cg, cb, ca).endVertex();
			wr.pos(cx + rx * half - vx * half, cy + ry * half - vy * half, cz + rz * half - vz * half).tex(u1, 1).color(cr, cg, cb, ca).endVertex();
			wr.pos(cx + rx * half + vx * half, cy + ry * half + vy * half, cz + rz * half + vz * half).tex(u1, 0).color(cr, cg, cb, ca).endVertex();
			wr.pos(cx - rx * half + vx * half, cy - ry * half + vy * half, cz - rz * half + vz * half).tex(u0, 0).color(cr, cg, cb, ca).endVertex();
		}
		t.draw();
	}

	/** Frees the textures and lists (the sky was changed or turned off). */
	public void delete() {
		if (boxList >= 0) {
			GLAllocation.deleteDisplayLists(boxList, 3);
			boxList = -1;
		}
		for (int id : new int[] {boxTexture, starTexture, discTexture}) {
			if (id >= 0) {
				TextureUtil.deleteTexture(id);
			}
		}
		boxTexture = starTexture = discTexture = -1;
	}
}
