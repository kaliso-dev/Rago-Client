package gg.rago.modules;

import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
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
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Inventory Blur: behind the inventory, chests and every other container window (Hypixel's menus
 * and shops too) the world is blurred, fading in as the window opens, and the dark shade over it is
 * lighter so the blur shows. The HUD stays sharp: the blur is done before it is drawn.
 *
 * <p>The blur is a true Gaussian, worked out by the graphics card in two passes (across, then
 * down) on a copy of the world half the width and height, each pass reading its neighbours in
 * pairs through the card's own filtering (half the reads). Its radius is given in pixels of a
 * 1080p screen and follows the window's size. Without OpenGL 2.0 a few fixed-function passes on a
 * quarter-size copy stand in.
 *
 * <p>Made to cost as little as possible, though it can still cost a few frames per second on a
 * weak graphics card while a window is open: nothing at all while none is; the half-size copy is
 * made by the card in one step from the game's framebuffer or the screen (OpenGL 3.0 blit, else
 * read straight from the game's framebuffer, a copy of the screen only with OptiFine's Fast
 * Render); the blurred picture is remade 60 times per second by default, or 30 if chosen, and
 * the frames between only lay it over the screen. The "light" quality blurs a copy a quarter of the
 * width and height instead (a sixteenth of the pixels, and fewer taps for the same radius): a
 * little less smooth, much less work for the card. The world behind always keeps moving.
 */
public final class InventoryBlur extends Module {
	/** Blending by a constant (OpenGL 1.4): GL_CONSTANT_ALPHA and GL_ONE_MINUS_CONSTANT_ALPHA. */
	private static final int CONSTANT_ALPHA = 0x8003;
	private static final int ONE_MINUS_CONSTANT_ALPHA = 0x8004;
	/** Pairs of taps the shader reads at most on each side, plus the centre: up to 61 pixels across. */
	static final int MAX_TAPS = 16;
	/** Fixed-function stand-in: each pass's tap distance in blurred pixels (plus half a pixel). */
	private static final float[] KAWASE = {1, 2, 2, 3, 4};
	/** A window drawn again within this time is the same opening: no new fade (Hypixel's menus open one window after another). */
	private static final long SAME_OPENING_NS = 250_000_000L;
	/** The two colours of vanilla's dark gradient behind menus in a world. */
	static final int VANILLA_TOP = 0xC0101010;
	static final int VANILLA_BOTTOM = 0xD0101010;

	private static final String VERTEX = "#version 110\n"
		+ "void main() { gl_TexCoord[0] = gl_MultiTexCoord0; gl_Position = gl_Vertex; }\n";
	/** One direction of the Gaussian: the centre, then pairs of taps on both sides along {@code dir}. */
	private static final String FRAGMENT = "#version 110\n"
		+ "uniform sampler2D tex;\n"
		+ "uniform vec2 dir;\n"
		+ "uniform float offsets[" + MAX_TAPS + "];\n"
		+ "uniform float weights[" + MAX_TAPS + "];\n"
		+ "uniform int taps;\n"
		+ "void main() {\n"
		+ "  vec2 uv = gl_TexCoord[0].xy;\n"
		+ "  vec3 c = texture2D(tex, uv).rgb * weights[0];\n"
		+ "  for (int i = 1; i < " + MAX_TAPS + "; i++) {\n"
		+ "    if (i >= taps) break;\n"
		+ "    vec2 o = dir * offsets[i];\n"
		+ "    c += (texture2D(tex, uv + o).rgb + texture2D(tex, uv - o).rgb) * weights[i];\n"
		+ "  }\n"
		+ "  gl_FragColor = vec4(c, 1.0);\n"
		+ "}\n";

	private final IntOption radius = addOption(new IntOption("inv_blur_radius", 20, 2, 50));
	private final ChoiceOption quality = addOption(new ChoiceOption("inv_blur_quality", "high", "high", "light"));
	private final ChoiceOption refresh = addOption(new ChoiceOption("inv_blur_refresh", "60", "30", "60"));
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

	private int program = -1;
	private boolean shaderFailed;
	private int uTex;
	private int uDir;
	private int uOffsets;
	private int uWeights;
	private int uTaps;
	private float kernelSigma = -1;
	private float[][] kernel;
	private final FloatBuffer offsetBuffer = BufferUtils.createFloatBuffer(MAX_TAPS);
	private final FloatBuffer weightBuffer = BufferUtils.createFloatBuffer(MAX_TAPS);

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

	/**
	 * A Gaussian of standard deviation {@code sigma} texels, read in bilinear pairs: {offsets,
	 * weights}, the centre first (offset 0), then each pair of neighbours as one tap on each side at
	 * the offset where linear filtering gives both their weights. The weights add up to 1, counting
	 * every tap but the centre twice. At most {@link #MAX_TAPS} taps (a radius of 30 texels).
	 */
	static float[][] kernel(float sigma) {
		float s = Math.max(0.2f, sigma);
		int r = Math.max(1, Math.min(2 * (MAX_TAPS - 1), (int) Math.ceil(s * 3)));
		double[] w = new double[r + 2];
		double sum = 0;
		for (int i = 0; i <= r; i++) {
			w[i] = Math.exp(-i * (double) i / (2.0 * s * s));
			sum += i == 0 ? w[i] : 2 * w[i];
		}
		int taps = 1 + (r + 1) / 2;
		float[] offsets = new float[taps];
		float[] weights = new float[taps];
		weights[0] = (float) (w[0] / sum);
		for (int t = 1, i = 1; t < taps; t++, i += 2) {
			double a = w[i];
			double b = w[i + 1];
			weights[t] = (float) ((a + b) / sum);
			offsets[t] = (float) ((i * a + (i + 1) * b) / (a + b));
		}
		return new float[][] {offsets, weights};
	}

	/** Whether the blurred picture is remade, {@code sinceMade} ns after the last time, at this refresh choice. */
	static boolean remakeDue(long sinceMade, String refresh) {
		return sinceMade >= 1_000_000_000L / ("30".equals(refresh) ? 30 : 60);
	}

	/**
	 * How many times smaller than the screen the blurred copy is: half the width and height, a
	 * quarter for the light quality, very wide blurs and the fixed-function stand-in.
	 */
	static int downscale(boolean gaussian, boolean light, float sigma) {
		return gaussian && !light && sigma <= 16 ? 2 : 4;
	}

	/** The blur's standard deviation in screen pixels: the radius (three deviations) at 1080p, scaled to the window. */
	static float sigmaPixels(int radius1080, int screenHeight) {
		return radius1080 / 3f * screenHeight / 1080f;
	}

	private static boolean canDraw(Minecraft mc) {
		return OpenGlHelper.framebufferSupported && mc.displayWidth >= 8 && mc.displayHeight >= 8;
	}

	/** Blurs the screen and lays the blur over it at {@code alpha}; false when this card cannot. */
	private boolean draw(Minecraft mc, long now, float alpha) {
		if (!canDraw(mc)) {
			return false;
		}
		int dw = mc.displayWidth;
		int dh = mc.displayHeight;
		boolean gaussian = shaderReady();
		float sigma = sigmaPixels(radius.get(), dh);
		boolean light = quality.is("light");
		int down = downscale(gaussian, light, sigma);
		int bw = dw / down;
		int bh = dh / down;
		int target = GameFramebuffer.bound();
		if (fbo[0] < 0 || bw != blurW || bh != blurH) {
			targets(bw, bh, target);
		}
		int setting = radius.get() * 4 + (gaussian ? 1 : 0) + (light ? 2 : 0);
		boolean remake = fresh || setting != madeWith || remakeDue(now - madeAt, refresh.get());
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
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, 1);
		try {
			if (remake) {
				// 1. a smaller copy of the world
				shrink(mc, target, dw, dh, down, bw, bh);
				// 2. the blur
				if (gaussian) {
					gaussian(sigma / down, bw, bh);
					result = 0;
				} else {
					int passes = Math.max(1, Math.min(KAWASE.length, (radius.get() + 8) / 8));
					int src = 0;
					for (int i = 0; i < passes; i++) {
						pass(tex[src], bw, bh, 1 - src, KAWASE[i]);
						src = 1 - src;
					}
					result = src;
				}
				madeWith = setting;
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
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
			GlStateManager.enableAlpha();
			GlStateManager.enableDepth();
			GlStateManager.depthMask(true);
			GlStateManager.color(1, 1, 1, 1);
		}
		return true;
	}

	/** The Gaussian of {@code sigma} texels on texture 0 ({@code w} x {@code h}): across into 1, then down back into 0. */
	private void gaussian(float sigma, int w, int h) {
		if (kernel == null || sigma != kernelSigma) {
			kernel = kernel(sigma);
			kernelSigma = sigma;
		}
		offsetBuffer.clear();
		weightBuffer.clear();
		for (int i = 0; i < MAX_TAPS; i++) {
			offsetBuffer.put(i < kernel[0].length ? kernel[0][i] : 0);
			weightBuffer.put(i < kernel[1].length ? kernel[1][i] : 0);
		}
		offsetBuffer.flip();
		weightBuffer.flip();
		GlStateManager.disableBlend();
		GL20.glUseProgram(program);
		try {
			GL20.glUniform1i(uTex, 0);
			GL20.glUniform1(uOffsets, offsetBuffer);
			GL20.glUniform1(uWeights, weightBuffer);
			GL20.glUniform1i(uTaps, kernel[0].length);
			into(1);
			GlStateManager.bindTexture(tex[0]);
			GL20.glUniform2f(uDir, 1f / w, 0);
			quad(0, 0);
			into(0);
			GlStateManager.bindTexture(tex[1]);
			GL20.glUniform2f(uDir, 0, 1f / h);
			quad(0, 0);
		} finally {
			GL20.glUseProgram(0);
			GlStateManager.enableBlend();
		}
	}

	/** Whether the Gaussian's shader is ready, made on first use; false (once and for all) without OpenGL 2.0. */
	private boolean shaderReady() {
		if (program >= 0) {
			return true;
		}
		if (shaderFailed) {
			return false;
		}
		if (!GLContext.getCapabilities().OpenGL20) {
			return shaderFailed("OpenGL 2.0 is not available");
		}
		int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
		int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
		if (vs < 0 || fs < 0) {
			return false;
		}
		int p = GL20.glCreateProgram();
		GL20.glAttachShader(p, vs);
		GL20.glAttachShader(p, fs);
		GL20.glLinkProgram(p);
		GL20.glDeleteShader(vs);
		GL20.glDeleteShader(fs);
		if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			String log = GL20.glGetProgramInfoLog(p, 1024);
			GL20.glDeleteProgram(p);
			return shaderFailed("shader link failed: " + log);
		}
		program = p;
		uTex = GL20.glGetUniformLocation(p, "tex");
		uDir = GL20.glGetUniformLocation(p, "dir");
		uOffsets = GL20.glGetUniformLocation(p, "offsets");
		uWeights = GL20.glGetUniformLocation(p, "weights");
		uTaps = GL20.glGetUniformLocation(p, "taps");
		return true;
	}

	private int compile(int type, String source) {
		int s = GL20.glCreateShader(type);
		GL20.glShaderSource(s, source);
		GL20.glCompileShader(s);
		if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			String log = GL20.glGetShaderInfoLog(s, 1024);
			GL20.glDeleteShader(s);
			shaderFailed("shader compile failed: " + log);
			return -1;
		}
		return s;
	}

	private boolean shaderFailed(String why) {
		RagoClient.LOG.warn("Inventory Blur: {}; using the simpler blur", why);
		shaderFailed = true;
		return false;
	}

	/**
	 * A copy of the world {@code down} times smaller in small texture 0, each pixel the mean of the
	 * block it covers. At half size, one blit by the card from where the game draws (a linear 2:1
	 * blit is the mean of each 2x2 block), with no full-size copy even with Fast Render. Otherwise
	 * one bilinear tap per 2x2 block, four per 4x4, read straight from the game's framebuffer when
	 * it draws into one, from a copy of the screen when it does not.
	 */
	private void shrink(Minecraft mc, int target, int dw, int dh, int down, int bw, int bh) {
		if (down == 2 && blit(target, bw, bh)) {
			return;
		}
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
		if (down == 2) {
			into(0);
			GlStateManager.disableBlend();
			GlStateManager.bindTexture(source);
			quad(0, 0);
			GlStateManager.enableBlend();
		} else {
			pass(source, dw, dh, 0, 0.5f);
		}
		if (direct) {
			GlStateManager.bindTexture(source);
			filter(main.framebufferFilter);
		}
	}

	/**
	 * Half-size copy of what is drawn in {@code from} into small texture 0 with one blit, when the
	 * card has OpenGL 3.0; false otherwise.
	 */
	private boolean blit(int from, int bw, int bh) {
		if (!GLContext.getCapabilities().OpenGL30) {
			return false;
		}
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo[0]);
		GL30.glBlitFramebuffer(0, 0, bw * 2, bh * 2, 0, 0, bw, bh, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo[0]);
		return true;
	}

	/** Draws into small texture {@code i}. */
	private void into(int i) {
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo[i]);
		GlStateManager.viewport(0, 0, blurW, blurH);
	}

	/**
	 * Fixed-function stand-in: one four-tap pass from texture {@code source} ({@code sw} x
	 * {@code sh}) into small texture {@code into}, the mean of four bilinear taps {@code offset} +
	 * 0.5 texels away diagonally. Each tap is blended with a constant weight (1, 1/2, 1/3, 1/4),
	 * which gives the exact mean without depending on the alpha stored in the source.
	 */
	private void pass(int source, int sw, int sh, int into, float offset) {
		into(into);
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
		if (program >= 0) {
			GL20.glDeleteProgram(program);
			program = -1;
		}
		fresh = true;
	}
}
