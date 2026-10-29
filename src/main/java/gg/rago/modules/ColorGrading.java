package gg.rago.modules;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * Colour grading of the world: saturation, vibrance (saturates dull colours more than vivid
 * ones), contrast, brightness, gamma, white balance (temperature and tint) and sharpening. One
 * shader pass over the world image before the HUD is drawn, so the HUD and menus keep their
 * colours. Needs OpenGL 2.0; with every slider at its middle the pass is skipped.
 */
public final class ColorGrading extends Module {
	private static final String VERTEX = "#version 110\n"
		+ "void main() { gl_TexCoord[0] = gl_MultiTexCoord0; gl_Position = gl_Vertex; }\n";
	private static final String FRAGMENT = "#version 110\n"
		+ "uniform sampler2D tex;\n"
		+ "uniform vec2 texel;\n"
		+ "uniform float saturation, vibrance, contrast, brightness, gammaInv, sharpen;\n"
		+ "uniform vec3 balance;\n"
		+ "void main() {\n"
		+ "  vec2 uv = gl_TexCoord[0].xy;\n"
		+ "  vec3 c = texture2D(tex, uv).rgb;\n"
		+ "  if (sharpen > 0.0) {\n"
		+ "    vec3 around = texture2D(tex, uv + vec2(texel.x, 0.0)).rgb + texture2D(tex, uv - vec2(texel.x, 0.0)).rgb\n"
		+ "      + texture2D(tex, uv + vec2(0.0, texel.y)).rgb + texture2D(tex, uv - vec2(0.0, texel.y)).rgb;\n"
		+ "    c += (c - around * 0.25) * sharpen;\n"
		+ "  }\n"
		+ "  c = c * balance + brightness;\n"
		+ "  c = (c - 0.5) * contrast + 0.5;\n"
		+ "  float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));\n"
		+ "  float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));\n"
		+ "  c = mix(vec3(luma), c, saturation * (1.0 + vibrance * (1.0 - clamp(chroma, 0.0, 1.0))));\n"
		+ "  gl_FragColor = vec4(pow(clamp(c, 0.0, 1.0), vec3(gammaInv)), 1.0);\n"
		+ "}\n";

	private final IntOption saturation = addOption(new IntOption("grading_saturation", 115, 0, 200));
	private final IntOption vibrance = addOption(new IntOption("grading_vibrance", 0, -100, 100));
	private final IntOption contrast = addOption(new IntOption("grading_contrast", 105, 50, 150));
	private final IntOption brightness = addOption(new IntOption("grading_brightness", 0, -50, 50));
	private final IntOption gamma = addOption(new IntOption("grading_gamma", 100, 50, 150));
	private final IntOption temperature = addOption(new IntOption("grading_temperature", 0, -100, 100));
	private final IntOption tint = addOption(new IntOption("grading_tint", 0, -100, 100));
	private final IntOption sharpen = addOption(new IntOption("grading_sharpen", 0, 0, 100));

	private int program = -1;
	private boolean failed;
	private int texture = -1;
	private int width;
	private int height;
	private boolean doneThisFrame;

	public ColorGrading() {
		super("color_grading", FeatureClass.VISUAL, false);
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
			apply(mc);
		}
	}

	/** After motion blur (which runs at HIGHEST), before the HUD. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.type == RenderGameOverlayEvent.ElementType.ALL && isEnabled() && !doneThisFrame) {
			apply(Minecraft.getMinecraft());
		}
	}

	private boolean neutral() {
		return saturation.get() == 100 && vibrance.get() == 0 && contrast.get() == 100 && brightness.get() == 0
			&& gamma.get() == 100 && temperature.get() == 0 && tint.get() == 0 && sharpen.get() == 0;
	}

	private void apply(Minecraft mc) {
		doneThisFrame = true;
		if (mc.theWorld == null || neutral() || !ready(mc.displayWidth, mc.displayHeight)) {
			return;
		}
		GlStateManager.pushMatrix();
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.disableAlpha();
		GlStateManager.disableBlend();
		GlStateManager.disableLighting();
		GlStateManager.disableFog();
		GlStateManager.disableCull();
		GlStateManager.enableTexture2D();
		GlStateManager.color(1, 1, 1, 1);
		try {
			GlStateManager.bindTexture(texture);
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
			GL20.glUseProgram(program);
			GL20.glUniform1i(GL20.glGetUniformLocation(program, "tex"), 0);
			GL20.glUniform2f(GL20.glGetUniformLocation(program, "texel"), 1f / width, 1f / height);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "saturation"), saturation.get() / 100f);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "vibrance"), vibrance.get() / 100f);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "contrast"), contrast.get() / 100f);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "brightness"), brightness.get() / 200f);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "gammaInv"), 100f / gamma.get());
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "sharpen"), sharpen.get() / 50f);
			float t = temperature.get() / 100f * 0.15f;
			float g = tint.get() / 100f * 0.1f;
			GL20.glUniform3f(GL20.glGetUniformLocation(program, "balance"), 1 + t, 1 + g, 1 - t);
			Tessellator tess = Tessellator.getInstance();
			WorldRenderer w = tess.getWorldRenderer();
			w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
			w.pos(-1, -1, 0).tex(0, 0).endVertex();
			w.pos(1, -1, 0).tex(1, 0).endVertex();
			w.pos(1, 1, 0).tex(1, 1).endVertex();
			w.pos(-1, 1, 0).tex(0, 1).endVertex();
			tess.draw();
		} finally {
			GL20.glUseProgram(0);
			GlStateManager.popMatrix();
			GlStateManager.enableBlend();
			GlStateManager.enableAlpha();
			GlStateManager.enableDepth();
			GlStateManager.depthMask(true);
		}
	}

	private boolean ready(int w, int h) {
		if (failed) {
			return false;
		}
		if (program < 0) {
			if (!GLContext.getCapabilities().OpenGL20) {
				return fail("OpenGL 2.0 is not available");
			}
			program = link();
			if (program < 0) {
				return false;
			}
		}
		if (texture < 0 || w != width || h != height) {
			if (texture >= 0) {
				GlStateManager.deleteTexture(texture);
			}
			width = w;
			height = h;
			texture = GL11.glGenTextures();
			GlStateManager.bindTexture(texture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		}
		return true;
	}

	private int link() {
		int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
		int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
		if (vs < 0 || fs < 0) {
			return -1;
		}
		int p = GL20.glCreateProgram();
		GL20.glAttachShader(p, vs);
		GL20.glAttachShader(p, fs);
		GL20.glLinkProgram(p);
		GL20.glDeleteShader(vs);
		GL20.glDeleteShader(fs);
		if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			fail("shader link failed: " + GL20.glGetProgramInfoLog(p, 1024));
			GL20.glDeleteProgram(p);
			return -1;
		}
		return p;
	}

	private int compile(int type, String source) {
		int s = GL20.glCreateShader(type);
		GL20.glShaderSource(s, source);
		GL20.glCompileShader(s);
		if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			fail("shader compile failed: " + GL20.glGetShaderInfoLog(s, 1024));
			GL20.glDeleteShader(s);
			return -1;
		}
		return s;
	}

	private boolean fail(String why) {
		RagoClient.LOG.warn("Colour grading unavailable: {}", why);
		failed = true;
		return false;
	}

	private void release() {
		if (texture >= 0) {
			GlStateManager.deleteTexture(texture);
			texture = -1;
		}
		if (program >= 0) {
			GL20.glDeleteProgram(program);
			program = -1;
		}
	}
}
