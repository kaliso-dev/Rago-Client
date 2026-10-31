package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.debug.FrameProfile;
import gg.rago.render.GameFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * Motion blur: each frame of the world is blended with the frames before it, so fast camera turns
 * leave a short trail. The HUD and menus are drawn afterwards and stay sharp.
 *
 * <p>The trail is updated 60 times per second whatever the frame rate and laid over the sharp
 * current frame. Updating it on every frame at 1000+ FPS would add only a few percent of each new
 * frame, too little for the image's precision to ever fade small differences out: those stay on
 * screen as pixel trails, most visibly in the sky. It can apply all the time, only while the camera turns, or only while moving,
 * with a sensitivity for how fast a turn reaches full strength.
 *
 * <p>The clean blur is updated every frame by a fixed share, enough for an 8-bit image: half the
 * memory traffic of the finer one the adjustable blur needs. The blending weights are constants
 * given to the graphics card, not the images' alpha, and the framebuffer the game draws into is
 * known rather than asked of the driver each frame ({@link GameFramebuffer}).
 */
public final class MotionBlur extends Module {
	/** GL_RGBA16F, the same value in OpenGL 3.0 and ARB_texture_float. */
	private static final int RGBA16F = 0x881A;
	/** Blending by a constant (OpenGL 1.4): GL_CONSTANT_ALPHA and GL_ONE_MINUS_CONSTANT_ALPHA. */
	private static final int CONSTANT_ALPHA = 0x8003;
	private static final int ONE_MINUS_CONSTANT_ALPHA = 0x8004;
	/** At 60 FPS the trail keeps at most this much of the previous frame. */
	private static final float MAX_KEEP = 0.72f;
	/** Without a floating-point image, a long trail would leave a ghost: keep less per frame. */
	private static final float MAX_KEEP_8BIT = 0.66f;
	/** The trail is updated at most this often. */
	private static final float STEP = 1 / 60f;

	/** Clean: the trail keeps this much of itself each frame... */
	private static final float CLEAN_TRAIL = 0.7f;
	/** ...and is laid over the sharp frame at this opacity (times the share of the newest frame in it, as before). */
	private static final float CLEAN_MIX = 0.8f;

	/** A ready-made blur: every frame, always on, fixed amounts; the other settings are hidden. */
	private final BooleanOption clean = addOption(new BooleanOption("blur_clean", false));
	private final IntOption strength = addOption(new IntOption("blur_strength", 30, 1, 100));
	private final ChoiceOption mode = addOption(new ChoiceOption("blur_mode", "always", "always", "turning", "moving"));
	private final IntOption sensitivity = addOption(new IntOption("blur_sensitivity", 50, 1, 100));
	private final BooleanOption frameIndependent = addOption(new BooleanOption("blur_frame_independent", true));
	private final BooleanOption pauseOnZoom = addOption(new BooleanOption("blur_pause_zoom", true));
	private final Zoom zoom;

	private int width;
	private int height;
	private int frameTexture = -1;
	private int historyTexture = -1;
	private int historyFbo = -1;
	/** Whether the trail is a floating-point image, and whether this card can draw into one. */
	private boolean floatHistory;
	private boolean floatOk = true;
	/** How much the trail is laid over the frame for each unit of opacity: the share of the newest frame in it. */
	private float shown = 1;
	private boolean fresh = true;
	private boolean doneThisFrame;
	private long lastFrame;
	private float sinceStep;
	private float lastYaw;
	private float lastPitch;
	private double lastX;
	private double lastY;
	private double lastZ;
	private World lastWorld;

	public MotionBlur(Zoom zoom) {
		super("motion_blur", FeatureClass.VISUAL, false);
		this.zoom = zoom;
		sensitivity.showIf(() -> !clean.get() && !mode.is("always"));
		strength.showIf(() -> !clean.get());
		mode.showIf(() -> !clean.get());
		frameIndependent.showIf(() -> !clean.get());
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
		Minecraft mc = Minecraft.getMinecraft();
		if (event.phase == TickEvent.Phase.START) {
			doneThisFrame = false;
		} else if (isEnabled() && !doneThisFrame && mc.gameSettings.hideGUI && mc.currentScreen == null && mc.theWorld != null) {
			// F1: no HUD pass this frame, so blend at the end of it (the screen is only the world)
			timedApply(mc);
		}
	}

	/** Before the HUD is drawn: the screen holds the world and the hand only. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.type == RenderGameOverlayEvent.ElementType.ALL && isEnabled() && !doneThisFrame) {
			timedApply(Minecraft.getMinecraft());
		}
	}

	/** Developer timing ({@code -Drago.bench.frame=true -Drago.bench.blur=true}): the blur's own work, the card waited for before and after. */
	private static final boolean BENCH = FrameProfile.ON && Boolean.getBoolean("rago.bench.blur");

	private void timedApply(Minecraft mc) {
		if (!BENCH) {
			apply(mc);
			return;
		}
		GL11.glFinish();
		long t = System.nanoTime();
		apply(mc);
		GL11.glFinish();
		FrameProfile.element("blur", System.nanoTime() - t);
	}

	private void apply(Minecraft mc) {
		doneThisFrame = true;
		if (mc.theWorld == null || mc.getRenderViewEntity() == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 1 / 60f : Math.min(0.25f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		float motion = motion(mc.getRenderViewEntity(), dt);
		if (mc.theWorld != lastWorld || mc.isGamePaused()) {
			fresh = true;
			lastWorld = mc.theWorld;
		}
		boolean cleanBlur = clean.get();
		float keep = cleanBlur ? CLEAN_TRAIL : keep(motion);
		if (pauseOnZoom.get() && zoom != null && zoom.isZooming()) {
			keep = 0;
		}
		if (keep <= 0) {
			// no trail to draw (standing still in the "turning" or "moving" mode, or zooming): no
			// work at all this frame, and the trail starts from the frame it is next needed on
			fresh = true;
			sinceStep = 0;
			return;
		}
		if (!ensureTargets(mc.displayWidth, mc.displayHeight, !cleanBlur && floatOk)) {
			return;
		}
		sinceStep += dt;
		boolean step = cleanBlur || !frameIndependent.get() || sinceStep >= STEP || fresh;
		GlStateManager.pushMatrix();
		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.loadIdentity();
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.disableAlpha();
		GlStateManager.disableLighting();
		GlStateManager.disableFog();
		GlStateManager.disableCull();
		GlStateManager.enableTexture2D();
		GlStateManager.color(1, 1, 1, 1);
		int target = -1;
		try {
			if (step) {
				target = GameFramebuffer.bound();
				// 1. the frame just drawn, as a texture: the game's own framebuffer when it draws into
				// one (read straight from it), otherwise a copy of the screen
				Framebuffer main = mc.getFramebuffer();
				if (OpenGlHelper.isFramebufferEnabled() && main != null && main.framebufferObject == target
					&& main.framebufferTextureWidth == width && main.framebufferTextureHeight == height) {
					GlStateManager.bindTexture(main.framebufferTexture);
				} else {
					if (frameTexture < 0) {
						frameTexture = texture(GL11.GL_RGBA8, width, height);
					}
					GlStateManager.bindTexture(frameTexture);
					GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
				}
				// 2. blend it into the trail: trail = keep * trail + (1 - keep) * frame, with keep
				// scaled to the time since the last update (one 60 FPS frame or more)
				OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, historyFbo);
				GlStateManager.viewport(0, 0, width, height);
				float stepKeep = !cleanBlur && frameIndependent.get() ? (float) Math.pow(keep, Math.min(sinceStep, 0.25f) / STEP) : keep;
				sinceStep = 0;
				if (fresh) {
					GlStateManager.disableBlend();
					quad();
					shown = 1;
					fresh = false;
				} else {
					blend(1 - stepKeep);
					quad();
					shown = 1 - stepKeep;
				}
				OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
				GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
			}
			// 3. the trail over the sharp current frame; the screen's alpha is left as it is
			blend(shown * (cleanBlur ? CLEAN_MIX : keep));
			GlStateManager.bindTexture(historyTexture);
			quad();
		} finally {
			if (target >= 0) {
				OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
				GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
			}
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
	}

	/** Blends what is drawn next over the colours under it at {@code alpha}, leaving their alpha. */
	private static void blend(float alpha) {
		GlStateManager.enableBlend();
		GL14.glBlendColor(0, 0, 0, alpha);
		GlStateManager.tryBlendFuncSeparate(CONSTANT_ALPHA, ONE_MINUS_CONSTANT_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
	}

	/** How much of the trail stays this frame (0 none, close to 1 a long trail). */
	private float keep(float motion) {
		float max = floatOk ? MAX_KEEP : MAX_KEEP_8BIT;
		// a curve, so the lower half of the slider gives the light trails most players want
		float s = strength.get() / 100f;
		return Math.max(0, max * s * (float) Math.sqrt(s) * motion);
	}

	/** 0 to 1: how much the current movement asks for blur in the chosen mode. */
	private float motion(Entity camera, float dt) {
		float yaw = camera.rotationYaw;
		float pitch = camera.rotationPitch;
		double x = camera.posX;
		double y = camera.posY;
		double z = camera.posZ;
		float turn = (Math.abs(yaw - lastYaw) + Math.abs(pitch - lastPitch)) / dt;
		double move = Math.sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ)) / dt;
		lastYaw = yaw;
		lastPitch = pitch;
		lastX = x;
		lastY = y;
		lastZ = z;
		// sensitivity 100: full blur from 30 degrees/s (or 1 block/s); sensitivity 1: from 600 (or 20)
		float full = 30 + (100 - sensitivity.get()) * 5.7f;
		switch (mode.get()) {
			case "turning":
				return Math.min(1, turn / full);
			case "moving":
				return (float) Math.min(1, move / (full / 30));
			default:
				return 1;
		}
	}

	/** A screen-filling quad with the bound texture. */
	private static void quad() {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		w.pos(-1, -1, 0).tex(0, 0).endVertex();
		w.pos(1, -1, 0).tex(1, 0).endVertex();
		w.pos(1, 1, 0).tex(1, 1).endVertex();
		w.pos(-1, 1, 0).tex(0, 1).endVertex();
		t.draw();
	}

	/** The trail's image and framebuffer at this size, floating-point or 8-bit; false when it cannot be made. */
	private boolean ensureTargets(int w, int h, boolean wantFloat) {
		if (!OpenGlHelper.isFramebufferEnabled() && !OpenGlHelper.framebufferSupported) {
			return false;
		}
		if (historyFbo >= 0 && w == width && h == height && wantFloat == floatHistory) {
			return true;
		}
		release();
		width = w;
		height = h;
		ContextCapabilities caps = GLContext.getCapabilities();
		floatHistory = wantFloat && (caps.OpenGL30 || caps.GL_ARB_texture_float);
		historyTexture = texture(floatHistory ? RGBA16F : GL11.GL_RGBA8, w, h);
		historyFbo = OpenGlHelper.glGenFramebuffers();
		OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, historyFbo);
		OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, historyTexture, 0);
		int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
		if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE && floatHistory) {
			// no float render target on this card: an 8-bit trail, kept shorter
			floatHistory = false;
			GlStateManager.deleteTexture(historyTexture);
			historyTexture = texture(GL11.GL_RGBA8, w, h);
			OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, historyTexture, 0);
			status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
		}
		if (wantFloat && !floatHistory) {
			floatOk = false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (OpenGlHelper.isFramebufferEnabled()) {
			mc.getFramebuffer().bindFramebuffer(true);
		} else {
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
		}
		if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
			RagoClient.LOG.warn("Motion blur unavailable: framebuffer status {}", status);
			release();
			setEnabled(false);
			return false;
		}
		fresh = true;
		return true;
	}

	private static int texture(int format, int w, int h) {
		int id = GL11.glGenTextures();
		GlStateManager.bindTexture(id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, GL11.GL_RGBA, format == RGBA16F ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		return id;
	}

	private void release() {
		if (historyFbo >= 0) {
			OpenGlHelper.glDeleteFramebuffers(historyFbo);
		}
		if (frameTexture >= 0) {
			GlStateManager.deleteTexture(frameTexture);
		}
		if (historyTexture >= 0) {
			GlStateManager.deleteTexture(historyTexture);
		}
		historyFbo = -1;
		frameTexture = -1;
		historyTexture = -1;
		width = 0;
		height = 0;
		lastFrame = 0;
		sinceStep = 0;
	}
}
