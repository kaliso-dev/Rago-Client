package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * A vignette: the edges of the screen darkened (or tinted) a little, which draws the eye to the
 * centre. Colour, intensity, size, softness and shape can be set; it can also flash when you are
 * hurt and pulse when your health is low. Drawn over the world after motion blur and colour
 * grading, before the HUD, so neither the HUD nor the blur's trail is darkened.
 *
 * <p>Only the ring where it shows is drawn: a mesh kept in a display list, made again only when
 * the size, softness, shape or the window's proportions change. The fade's curve is a small
 * texture, and the colour and strength are the current colour, so changing them (or flashing)
 * costs nothing.
 */
public final class Vignette extends Module {
	/** Rings from where the fade starts to where it ends, and segments around. */
	private static final int RINGS = 8;
	private static final int SEGMENTS = 64;
	private static final int RAMP = 256;

	private final ColorOption color = addOption(new ColorOption("vignette_color", 0xFF000000).fixedAlpha(255));
	private final IntOption intensity = addOption(new IntOption("vignette_intensity", 45, 0, 100));
	private final IntOption size = addOption(new IntOption("vignette_size", 50, 0, 100));
	private final IntOption softness = addOption(new IntOption("vignette_softness", 80, 0, 100));
	private final ChoiceOption shape = addOption(new ChoiceOption("vignette_shape", "screen", "screen", "round"));
	private final BooleanOption hurt = addOption(new BooleanOption("vignette_hurt", false));
	private final BooleanOption lowHealth = addOption(new BooleanOption("vignette_low_health", false));
	private final ColorOption alertColor = addOption(new ColorOption("vignette_alert_color", 0xFFB00000).fixedAlpha(255));

	private int list = -1;
	private int ramp = -1;
	/** The settings the display list was made with. */
	private int builtSize = -1;
	private int builtSoftness = -1;
	private String builtShape;
	private float builtAspect;
	private boolean doneThisFrame;

	public Vignette() {
		super("vignette", FeatureClass.VISUAL, false);
		alertColor.showIf(() -> hurt.get() || lowHealth.get());
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
			// F1: no HUD pass this frame, so at the end of it (after the blur and the grading)
			draw(mc, event.renderTickTime);
		}
	}

	/** After motion blur (HIGHEST) and colour grading (HIGH), before the HUD. */
	@SubscribeEvent
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.type == RenderGameOverlayEvent.ElementType.ALL && isEnabled() && !doneThisFrame) {
			draw(Minecraft.getMinecraft(), event.partialTicks);
		}
	}

	private void draw(Minecraft mc, float partialTicks) {
		doneThisFrame = true;
		if (mc.theWorld == null || mc.displayHeight <= 0) {
			return;
		}
		float alert = alert(mc.thePlayer, partialTicks);
		float strength = intensity.get() / 100f;
		strength += (Math.max(strength, 0.7f) - strength) * alert;
		if (strength <= 0.004f) {
			return;
		}
		float aspect = mc.displayWidth / (float) mc.displayHeight;
		if (list < 0 || builtSize != size.get() || builtSoftness != softness.get() || !shape.get().equals(builtShape)
			|| Math.abs(builtAspect - aspect) > 1e-3f) {
			build(aspect);
		}
		int c = color.get();
		int a = alertColor.get();
		float r = mix((c >> 16 & 0xFF) / 255f, (a >> 16 & 0xFF) / 255f, alert);
		float g = mix((c >> 8 & 0xFF) / 255f, (a >> 8 & 0xFF) / 255f, alert);
		float b = mix((c & 0xFF) / 255f, (a & 0xFF) / 255f, alert);

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
		// the colours only: the screen's alpha is left as it is
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
		GlStateManager.bindTexture(ramp);
		GlStateManager.color(r, g, b, Math.min(1, strength));
		try {
			GL11.glCallList(list);
		} finally {
			GlStateManager.color(1, 1, 1, 1);
			GlStateManager.matrixMode(GL11.GL_PROJECTION);
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.popMatrix();
			GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
			GlStateManager.enableAlpha();
			GlStateManager.enableDepth();
			GlStateManager.depthMask(true);
		}
	}

	/** 0 to 1: how much the alert colour shows (a flash when hurt, a pulse at low health). */
	private float alert(EntityPlayer player, float partialTicks) {
		if (player == null) {
			return 0;
		}
		float alert = 0;
		if (hurt.get() && player.hurtTime > 0 && player.maxHurtTime > 0) {
			alert = Math.min(1, Math.max(0, player.hurtTime - partialTicks) / player.maxHurtTime);
		}
		if (lowHealth.get() && player.getHealth() > 0 && player.getHealth() <= player.getMaxHealth() * 0.3f) {
			// a heartbeat, about 70 a minute
			double t = (System.nanoTime() / 1e9) * 1.2 * Math.PI * 2;
			alert = Math.max(alert, 0.35f + 0.25f * (float) Math.sin(t));
		}
		return alert;
	}

	private static float mix(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Where the fade starts and where it reaches full strength, as distances from the centre (1 at the corners). */
	static float[] fade(int size, int softness) {
		float start = 0.9f - 0.7f * size / 100f;
		float end = start + (1.05f - start) * (0.25f + 0.75f * softness / 100f);
		return new float[] {start, end};
	}

	/** The fade's curve at {@code t} from 0 (start) to 1 (full): smooth at both ends. */
	static float curve(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	private void build(float aspect) {
		if (ramp < 0) {
			ramp = GL11.glGenTextures();
			ByteBuffer pixels = BufferUtils.createByteBuffer(RAMP * 4);
			for (int i = 0; i < RAMP; i++) {
				pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) Math.round(255 * curve(i / (RAMP - 1f))));
			}
			pixels.flip();
			GlStateManager.bindTexture(ramp);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, RAMP, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		}
		if (list < 0) {
			list = GL11.glGenLists(1);
		}
		builtSize = size.get();
		builtSoftness = softness.get();
		builtShape = shape.get();
		builtAspect = aspect;
		float[] f = fade(builtSize, builtSoftness);
		// "screen": the same share of the width and height; "round": a circle on screen
		float kx = shape.is("round") ? (float) Math.sqrt(aspect * aspect + 1) / aspect : (float) Math.sqrt(2);
		float ky = shape.is("round") ? (float) Math.sqrt(aspect * aspect + 1) : (float) Math.sqrt(2);
		float[] radius = rings(f[0], f[1]);
		GL11.glNewList(list, GL11.GL_COMPILE);
		GL11.glBegin(GL11.GL_QUADS);
		for (int k = 0; k + 1 < radius.length; k++) {
			float d0 = radius[k];
			float d1 = radius[k + 1];
			float u0 = (d0 - f[0]) / (f[1] - f[0]);
			float u1 = (d1 - f[0]) / (f[1] - f[0]);
			for (int j = 0; j < SEGMENTS; j++) {
				double a0 = Math.PI * 2 * j / SEGMENTS;
				double a1 = Math.PI * 2 * (j + 1) / SEGMENTS;
				float c0 = (float) Math.cos(a0);
				float s0 = (float) Math.sin(a0);
				float c1 = (float) Math.cos(a1);
				float s1 = (float) Math.sin(a1);
				GL11.glTexCoord2f(u0, 0.5f);
				GL11.glVertex2f(d0 * kx * c0, d0 * ky * s0);
				GL11.glTexCoord2f(u1, 0.5f);
				GL11.glVertex2f(d1 * kx * c0, d1 * ky * s0);
				GL11.glTexCoord2f(u1, 0.5f);
				GL11.glVertex2f(d1 * kx * c1, d1 * ky * s1);
				GL11.glTexCoord2f(u0, 0.5f);
				GL11.glVertex2f(d0 * kx * c1, d0 * ky * s1);
			}
		}
		GL11.glEnd();
		GL11.glEndList();
	}

	/**
	 * Ring distances: evenly from the start of the fade to its end (or the corners), then the
	 * corners when the fade ends before them. Nothing is drawn inside the first.
	 */
	static float[] rings(float start, float end) {
		float last = Math.min(end, 1);
		boolean tail = end < 1;
		float[] r = new float[RINGS + 1 + (tail ? 1 : 0)];
		for (int i = 0; i <= RINGS; i++) {
			r[i] = start + (last - start) * i / RINGS;
		}
		if (tail) {
			r[RINGS + 1] = 1;
		}
		return r;
	}

	private void release() {
		if (list >= 0) {
			GL11.glDeleteLists(list, 1);
			list = -1;
		}
		if (ramp >= 0) {
			GlStateManager.deleteTexture(ramp);
			ramp = -1;
		}
		builtShape = null;
	}
}
