package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * Motion blur: each frame of the world is blended with the frames before it, so fast camera turns
 * leave a short trail. The HUD and menus are drawn afterwards and stay sharp.
 *
 * <p>The trail is kept in a floating-point image so it fades out cleanly instead of leaving a
 * faint ghost, and its length is set in time rather than in frames, so it looks the same at 60
 * and at 600 FPS. It can apply all the time, only while the camera turns, or only while moving,
 * with a sensitivity for how fast a turn reaches full strength.
 */
public final class MotionBlur extends Module {
	/** GL_RGBA16F, the same value in OpenGL 3.0 and ARB_texture_float. */
	private static final int RGBA16F = 0x881A;
	/** At 60 FPS the trail keeps at most this much of the previous frame. */
	private static final float MAX_KEEP = 0.82f;
	/** Without a floating-point image, a long trail would leave a ghost: keep less per frame. */
	private static final float MAX_KEEP_8BIT = 0.78f;

	private final IntOption strength = addOption(new IntOption("blur_strength", 35, 1, 100));
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
	private boolean floatHistory;
	private boolean fresh = true;
	private boolean doneThisFrame;
	private long lastFrame;
	private float lastYaw;
	private float lastPitch;
	private double lastX;
	private double lastY;
	private double lastZ;
	private World lastWorld;

	public MotionBlur(Zoom zoom) {
		super("motion_blur", FeatureClass.VISUAL, false);
		this.zoom = zoom;
		sensitivity.showIf(() -> !mode.is("always"));
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
			apply(mc);
		}
	}

	/** Before the HUD is drawn: the screen holds the world and the hand only. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.type == RenderGameOverlayEvent.ElementType.ALL && isEnabled() && !doneThisFrame) {
			apply(Minecraft.getMinecraft());
		}
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
		if (!ensureTargets(mc.displayWidth, mc.displayHeight)) {
			return;
		}
		float keep = keep(dt, motion);
		if (pauseOnZoom.get() && zoom != null && zoom.isZooming()) {
			keep = 0;
		}
		int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
		int[] viewport = viewport();
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
		try {
			// 1. the frame just drawn, as a texture
			GlStateManager.bindTexture(frameTexture);
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
			// 2. blend it into the trail: trail = keep * trail + (1 - keep) * frame
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, historyFbo);
			GlStateManager.viewport(0, 0, width, height);
			if (fresh || keep <= 0) {
				GlStateManager.disableBlend();
				quad(1);
				fresh = false;
			} else {
				GlStateManager.enableBlend();
				GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
				quad(1 - keep);
			}
			// 3. the trail back on screen
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFbo);
			GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
			GlStateManager.disableBlend();
			GlStateManager.bindTexture(historyTexture);
			quad(1);
		} finally {
			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFbo);
			GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
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

	/** How much of the trail stays this frame (0 none, close to 1 a long trail). */
	private float keep(float dt, float motion) {
		float max = floatHistory ? MAX_KEEP : MAX_KEEP_8BIT;
		// a curve, so the lower half of the slider gives the light trails most players want
		float s = strength.get() / 100f;
		float at60 = max * s * (float) Math.sqrt(s) * motion;
		if (at60 <= 0) {
			return 0;
		}
		if (!frameIndependent.get()) {
			return at60;
		}
		// the same fade per second whatever the frame rate
		return (float) Math.pow(at60, dt * 60);
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

	private static int[] viewport() {
		java.nio.IntBuffer b = org.lwjgl.BufferUtils.createIntBuffer(16);
		GL11.glGetInteger(GL11.GL_VIEWPORT, b);
		return new int[]{b.get(0), b.get(1), b.get(2), b.get(3)};
	}

	/** A screen-filling quad with the bound texture at {@code alpha}. */
	private static void quad(float alpha) {
		GlStateManager.color(1, 1, 1, alpha);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		w.pos(-1, -1, 0).tex(0, 0).endVertex();
		w.pos(1, -1, 0).tex(1, 0).endVertex();
		w.pos(1, 1, 0).tex(1, 1).endVertex();
		w.pos(-1, 1, 0).tex(0, 1).endVertex();
		t.draw();
	}

	private boolean ensureTargets(int w, int h) {
		if (!OpenGlHelper.isFramebufferEnabled() && !OpenGlHelper.framebufferSupported) {
			return false;
		}
		if (historyFbo >= 0 && w == width && h == height) {
			return true;
		}
		release();
		width = w;
		height = h;
		frameTexture = texture(GL11.GL_RGBA8, w, h);
		ContextCapabilities caps = GLContext.getCapabilities();
		floatHistory = caps.OpenGL30 || caps.GL_ARB_texture_float;
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
	}
}
