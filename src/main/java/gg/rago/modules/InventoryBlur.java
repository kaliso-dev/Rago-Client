package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.GameFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;

/**
 * Inventory Blur: behind the inventory, chests and every other container window (Hypixel's menus
 * and shops too) the world is blurred, fading in as the window opens, and the dark shade over it is
 * lighter so the blur shows. The HUD stays sharp: the blur is done before it is drawn.
 *
 * <p>Made to cost as little as possible: nothing at all while no such window is open; the world is
 * read straight from the game's framebuffer (a copy of the screen only with OptiFine's Fast
 * Render), shrunk to a quarter of the width and height on the way (a sixteenth of the pixels) and
 * blurred there with a few four-tap passes, no shaders; the blurred picture is remade at most 60
 * times per second and frames in between only lay it over the screen again.
 */
public final class InventoryBlur extends Module {
	/** Blending by a constant (OpenGL 1.4): GL_CONSTANT_ALPHA and GL_ONE_MINUS_CONSTANT_ALPHA. */
	private static final int CONSTANT_ALPHA = 0x8003;
	private static final int ONE_MINUS_CONSTANT_ALPHA = 0x8004;
	private static final int DOWNSCALE = 4;
	/**
	 * Each pass's tap distance in blurred pixels (plus half a pixel, so each bilinear tap is the mean
	 * of a 2x2 block: no grid pattern), one more pass per strength level, as in Kawase's blur.
	 */
	private static final float[] OFFSETS = {1, 2, 2, 3, 4};
	/** The blurred picture is remade at most this often (60 times per second). */
	private static final long REFRESH_NS = 1_000_000_000L / 60;
	/** A window drawn again within this time is the same opening: no new fade (Hypixel's menus open one window after another). */
	private static final long SAME_OPENING_NS = 250_000_000L;
	/** The two colours of vanilla's dark gradient behind menus in a world. */
	static final int VANILLA_TOP = 0xC0101010;
	static final int VANILLA_BOTTOM = 0xD0101010;

	private final IntOption strength = addOption(new IntOption("inv_blur_strength", 4, 1, OFFSETS.length));
	private final IntOption fade = addOption(new IntOption("inv_blur_fade", 150, 0, 500));
	private final IntOption shade = addOption(new IntOption("inv_blur_shade", 45, 0, 100));

	private int frameTexture = -1;
	private int frameW;
	private int frameH;
	private final int[] tex = {-1, -1};
	private final int[] fbo = {-1, -1};
	private int blurW;
	private int blurH;
	/** Which of the two textures holds the blurred picture. */
	private int result;
	private boolean fresh = true;
	private int madeWith;
	private long madeAt;
	private long openedAt;
	private long lastDrawn;
	private int frame;
	private int drawnFrame = -1;
	/** The frame whose window background is being drawn with the lighter shade. */
	private int shadeFrame = -1;

	public InventoryBlur() {
		super("inventory_blur", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	@Override
	protected void onDisable() {
		release();
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			frame++;
		}
	}

	/** Before the HUD is drawn (after motion blur, colour grading and the vignette): the screen holds the world only. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL || !isEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (!(mc.currentScreen instanceof GuiContainer) || mc.theWorld == null) {
			return;
		}
		long now = System.nanoTime();
		if (now - lastDrawn > SAME_OPENING_NS) {
			openedAt = now;
			fresh = true;
		}
		lastDrawn = now;
		float alpha = fadeIn(now - openedAt, fade.get());
		// the very first frame shows no blur yet, but already the lighter shade
		if (alpha > 0 ? draw(mc, now, alpha) : canDraw(mc)) {
			drawnFrame = frame;
		}
	}

	/** How much of the blur shows {@code sinceOpen} ns after the window opened, with a fade of {@code fadeMs}. */
	static float fadeIn(long sinceOpen, int fadeMs) {
		if (fadeMs <= 0) {
			return 1;
		}
		float t = Math.min(1, Math.max(0, sinceOpen / 1e6f / fadeMs));
		// eased: quick at first, gentle at the end
		return 1 - (1 - t) * (1 - t);
	}

	/**
	 * The colour of the dark gradient behind menus: lighter (the chosen share of vanilla's opacity)
	 * when the world behind was blurred this frame, otherwise vanilla's.
	 */
	public int shade(int vanilla) {
		if (!isEnabled() || drawnFrame != frame) {
			return vanilla;
		}
		shadeFrame = frame;
		return shaded(vanilla, shade.get());
	}

	/**
	 * A colour of a gradient about to be drawn. Right after {@link #shade} (the window's world
	 * background, filling the screen), it is set again from vanilla's colours: another mod may have
	 * changed them in between (PolyPatcher's container background opacity) and the shade chosen
	 * here is kept. Any other gradient (tooltips, buttons) is left alone.
	 */
	public int gradient(int left, int top, int right, int bottom, int color, boolean start) {
		if (shadeFrame != frame || left != 0 || top != 0) {
			return color;
		}
		net.minecraft.client.gui.GuiScreen screen = Minecraft.getMinecraft().currentScreen;
		if (screen == null || right != screen.width || bottom != screen.height) {
			return color;
		}
		if (!start) {
			shadeFrame = -1;
		}
		return shaded(start ? VANILLA_TOP : VANILLA_BOTTOM, shade.get());
	}

	static int shaded(int argb, int percent) {
		int alpha = Math.round((argb >>> 24) * percent / 100f);
		return alpha << 24 | argb & 0xFFFFFF;
	}

	private static boolean canDraw(Minecraft mc) {
		return OpenGlHelper.framebufferSupported && mc.displayWidth >= DOWNSCALE * 2 && mc.displayHeight >= DOWNSCALE * 2;
	}

	/** Blurs the screen and lays the blur over it at {@code alpha}; false when this card cannot. */
	private boolean draw(Minecraft mc, long now, float alpha) {
		if (!canDraw(mc)) {
			return false;
		}
		int dw = mc.displayWidth;
		int dh = mc.displayHeight;
		int bw = dw / DOWNSCALE;
		int bh = dh / DOWNSCALE;
		int target = GameFramebuffer.bound();
		if (fbo[0] < 0 || bw != blurW || bh != blurH) {
			targets(bw, bh, target);
		}
		int passes = strength.get();
		boolean remake = fresh || passes != madeWith || now - madeAt >= REFRESH_NS;
		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.disableAlpha();
		GlStateManager.disableLighting();
		GlStateManager.disableFog();
		GlStateManager.disableCull();
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, 1);
		try {
			if (remake) {
				// 1. a quarter-size copy of the world: each pixel the mean of a 4x4 block (four bilinear
				// taps one texel from its centre), read straight from the game's framebuffer when it
				// draws into one
				Framebuffer main = mc.getFramebuffer();
				boolean direct = OpenGlHelper.isFramebufferEnabled() && main != null && main.framebufferObject == target
					&& main.framebufferTextureWidth == dw && main.framebufferTextureHeight == dh;
				int source;
				if (direct) {
					source = main.framebufferTexture;
					GlStateManager.bindTexture(source);
					filter(GL11.GL_LINEAR);
				} else {
					if (frameTexture < 0 || frameW != dw || frameH != dh) {
						if (frameTexture >= 0) {
							GlStateManager.deleteTexture(frameTexture);
						}
						frameTexture = texture(dw, dh);
						frameW = dw;
						frameH = dh;
					}
					source = frameTexture;
					GlStateManager.bindTexture(source);
					GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dw, dh);
				}
				pass(source, dw, dh, 0, 0.5f);
				if (direct) {
					GlStateManager.bindTexture(source);
					filter(main.framebufferFilter);
				}
				// 2. the blur, ping-ponging between the two small textures
				int src = 0;
				for (int i = 0; i < passes; i++) {
					pass(tex[src], bw, bh, 1 - src, OFFSETS[i]);
					src = 1 - src;
				}
				result = src;
				madeWith = passes;
				madeAt = now;
				fresh = false;
				OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
				GlStateManager.viewport(0, 0, dw, dh);
			}
			// 3. the blur over the screen; the screen's alpha is left as it is
			blend(alpha);
			GlStateManager.bindTexture(tex[result]);
			quad(0, 0);
		} finally {
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
			GlStateManager.viewport(0, 0, dw, dh);
			GL14.glBlendColor(0, 0, 0, 0);
			GlStateManager.matrixMode(GL11.GL_PROJECTION);
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.popMatrix();
			GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
			GlStateManager.enableAlpha();
			GlStateManager.enableDepth();
			GlStateManager.depthMask(true);
			GlStateManager.color(1, 1, 1, 1);
		}
		return true;
	}

	/**
	 * One four-tap pass from texture {@code source} ({@code sw} x {@code sh}) into small target
	 * {@code into}: the mean of four bilinear taps {@code offset} + 0.5 texels away diagonally.
	 * Each tap is blended with a constant weight (1, 1/2, 1/3, 1/4), which gives the exact mean
	 * without depending on the alpha stored in the source.
	 */
	private void pass(int source, int sw, int sh, int into, float offset) {
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo[into]);
		GlStateManager.viewport(0, 0, blurW, blurH);
		GlStateManager.bindTexture(source);
		float dx = (offset + 0.5f) / sw;
		float dy = (offset + 0.5f) / sh;
		blend(1);
		quad(-dx, -dy);
		blend(1 / 2f);
		quad(dx, dy);
		blend(1 / 3f);
		quad(-dx, dy);
		blend(1 / 4f);
		quad(dx, -dy);
	}

	/** Blends what is drawn next over the colours under it at {@code alpha}, leaving their alpha. */
	private static void blend(float alpha) {
		GL14.glBlendColor(0, 0, 0, alpha);
		GlStateManager.tryBlendFuncSeparate(CONSTANT_ALPHA, ONE_MINUS_CONSTANT_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
	}

	/** A quad filling the bound target with the bound texture, shifted by ({@code u}, {@code v}). */
	private static void quad(float u, float v) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		w.pos(-1, -1, 0).tex(u, v).endVertex();
		w.pos(1, -1, 0).tex(1 + u, v).endVertex();
		w.pos(1, 1, 0).tex(1 + u, 1 + v).endVertex();
		w.pos(-1, 1, 0).tex(u, 1 + v).endVertex();
		t.draw();
	}

	private static void filter(int filter) {
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
	}

	private static int texture(int w, int h) {
		int id = GL11.glGenTextures();
		GlStateManager.bindTexture(id);
		filter(GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		return id;
	}

	/** The two small textures and their framebuffers, at this size. */
	private void targets(int w, int h, int bound) {
		releaseTargets();
		blurW = w;
		blurH = h;
		for (int i = 0; i < 2; i++) {
			tex[i] = texture(w, h);
			fbo[i] = OpenGlHelper.glGenFramebuffers();
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo[i]);
			OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex[i], 0);
		}
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, bound);
		fresh = true;
	}

	private void releaseTargets() {
		for (int i = 0; i < 2; i++) {
			if (fbo[i] >= 0) {
				OpenGlHelper.glDeleteFramebuffers(fbo[i]);
			}
			if (tex[i] >= 0) {
				GlStateManager.deleteTexture(tex[i]);
			}
			fbo[i] = -1;
			tex[i] = -1;
		}
		blurW = 0;
		blurH = 0;
	}

	private void release() {
		releaseTargets();
		if (frameTexture >= 0) {
			GlStateManager.deleteTexture(frameTexture);
		}
		frameTexture = -1;
		frameW = 0;
		frameH = 0;
		fresh = true;
	}
}
