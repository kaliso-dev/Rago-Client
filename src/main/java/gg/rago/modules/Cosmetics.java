package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * Cosmetics on your own player: a hat (top hat, crown or halo) and wings (angel or dragon), each
 * with its colour. All of them are Rago's own models, drawn from simple shapes. Off by default,
 * and each piece can be set to none. Only this client draws them.
 */
public final class Cosmetics extends Module {
	private final ChoiceOption hat = addOption(new ChoiceOption("cosmetic_hat", "none", "none", "top_hat", "crown", "halo"));
	private final ColorOption hatColor = addOption(new ColorOption("cosmetic_hat_color", 0xFFFFFFFF));
	private final ChoiceOption wings = addOption(new ChoiceOption("cosmetic_wings", "none", "none", "angel", "dragon"));
	private final ColorOption wingsColor = addOption(new ColorOption("cosmetic_wings_color", 0xFFFFFFFF));
	private final BooleanOption flap = addOption(new BooleanOption("cosmetic_wings_flap", true));
	private boolean installed;

	/** Wing outlines in wing space: x out from the back, y down; the root is at (0, 0). */
	private static final float[][] ANGEL = {
		{0.00f, -0.05f}, {0.20f, -0.22f}, {0.45f, -0.32f}, {0.72f, -0.34f}, {0.95f, -0.22f}, {1.00f, -0.08f},
		{0.88f, 0.10f}, {0.74f, 0.32f}, {0.58f, 0.52f}, {0.42f, 0.64f}, {0.28f, 0.62f}, {0.16f, 0.44f}, {0.06f, 0.20f}
	};
	private static final float[][] DRAGON = {
		{0.00f, -0.06f}, {0.30f, -0.30f}, {0.62f, -0.40f}, {1.00f, -0.30f}, {0.86f, -0.05f}, {0.92f, 0.30f},
		{0.70f, 0.18f}, {0.66f, 0.56f}, {0.46f, 0.38f}, {0.36f, 0.70f}, {0.22f, 0.44f}, {0.08f, 0.40f}
	};
	/** Bone tips of the dragon wing (indices into DRAGON). */
	private static final int[] DRAGON_BONES = {3, 5, 7, 9};

	public Cosmetics() {
		super("cosmetics", FeatureClass.VISUAL, false);
		hatColor.showIf(() -> !hat.is("none"));
		wingsColor.showIf(() -> !wings.is("none"));
		flap.showIf(() -> !wings.is("none"));
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (installed || event.phase != TickEvent.Phase.START || Minecraft.getMinecraft().getRenderManager() == null) {
			return;
		}
		installed = true;
		for (RenderPlayer render : Minecraft.getMinecraft().getRenderManager().getSkinMap().values()) {
			render.addLayer(new Layer(render));
		}
	}

	private final class Layer implements LayerRenderer<AbstractClientPlayer> {
		private final RenderPlayer render;

		Layer(RenderPlayer render) {
			this.render = render;
		}

		@Override
		public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks,
			float ageInTicks, float netHeadYaw, float headPitch, float scale) {
			Minecraft mc = Minecraft.getMinecraft();
			if (!isEnabled() || player.isInvisible()
				|| player != mc.thePlayer && !player.getUniqueID().equals(mc.getSession().getProfile().getId())) {
				return;
			}
			ModelPlayer model = render.getMainModel();
			GlStateManager.disableTexture2D();
			GlStateManager.disableLighting();
			GlStateManager.disableCull();
			try {
				if (!hat.is("none")) {
					GlStateManager.pushMatrix();
					if (player.isSneaking()) {
						GlStateManager.translate(0, 0.2f, 0);
					}
					model.bipedHead.postRender(0.0625f);
					drawHat(player.getCurrentArmor(3) != null, ageInTicks);
					GlStateManager.popMatrix();
				}
				if (!wings.is("none")) {
					GlStateManager.pushMatrix();
					if (player.isSneaking()) {
						GlStateManager.translate(0, 0.2f, 0);
					}
					model.bipedBody.postRender(0.0625f);
					float beat = flap.get() ? flapAngle(player, ageInTicks) : 0;
					drawWing(-1, beat);
					drawWing(1, beat);
					GlStateManager.popMatrix();
				}
			} finally {
				GlStateManager.enableCull();
				GlStateManager.enableLighting();
				GlStateManager.enableTexture2D();
				GlStateManager.color(1, 1, 1, 1);
			}
		}

		@Override
		public boolean shouldCombineTextures() {
			return false;
		}
	}

	private static float flapAngle(AbstractClientPlayer player, float age) {
		boolean airborne = !player.onGround || player.capabilities.isFlying;
		float speed = airborne ? 0.45f : 0.12f;
		float amount = airborne ? 22 : 6;
		return (float) Math.sin(age * speed) * amount;
	}

	private int color(ColorOption option, int fallback) {
		int c = option.get();
		return c == 0xFFFFFFFF ? fallback : c;
	}

	// ---- hats: head space, 1 unit = 1 block, y down, top of the head at y = -0.5 ----------------

	private void drawHat(boolean helmet, float age) {
		float base = helmet ? -0.58f : -0.535f;
		switch (hat.get()) {
			case "top_hat": {
				int c = color(hatColor, 0xFF26262B);
				box(-0.36f, base - 0.03f, -0.36f, 0.36f, base, 0.36f, c);
				box(-0.24f, base - 0.42f, -0.24f, 0.24f, base - 0.03f, 0.24f, c);
				box(-0.245f, base - 0.10f, -0.245f, 0.245f, base - 0.04f, 0.245f, 0xFFC0392B);
				break;
			}
			case "crown": {
				int c = color(hatColor, 0xFFF2C14E);
				float r = 0.27f;
				float t = 0.03f;
				float top = base - 0.16f;
				box(-r, top, -r, r, base, -r + t, c);
				box(-r, top, r - t, r, base, r, c);
				box(-r, top, -r, -r + t, base, r, c);
				box(r - t, top, -r, r, base, r, c);
				for (float p : new float[] {-r, 0, r - 0.06f}) {
					box(p, top - 0.09f, -r, p + 0.06f, top, -r + t, c);
					box(p, top - 0.09f, r - t, p + 0.06f, top, r, c);
				}
				box(-0.04f, base - 0.11f, -r - 0.01f, 0.04f, base - 0.04f, -r + t, 0xFFE74C3C);
				box(-0.04f, base - 0.11f, r - t, 0.04f, base - 0.04f, r + 0.01f, 0xFF3498DB);
				break;
			}
			default: {
				// halo: a glowing ring floating above the head
				int c = color(hatColor, 0xFFFFE27A);
				float y = base - 0.22f + (float) Math.sin(age * 0.08f) * 0.02f;
				float lx = OpenGlHelper.lastBrightnessX;
				float ly = OpenGlHelper.lastBrightnessY;
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
				ring(0, y, 0, 0.22f, 0.04f, 0.025f, c);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
			}
		}
	}

	/** A box with flat shading: top brightest, then the sides, bottom darkest. */
	private static void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		face(w, argb, 1.0f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		face(w, argb, 0.55f, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		face(w, argb, 0.8f, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		face(w, argb, 0.8f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		face(w, argb, 0.68f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		face(w, argb, 0.68f, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
		t.draw();
	}

	private static void face(WorldRenderer w, int argb, float shade, float... p) {
		int r = Math.round((argb >> 16 & 0xFF) * shade);
		int g = Math.round((argb >> 8 & 0xFF) * shade);
		int b = Math.round((argb & 0xFF) * shade);
		int a = argb >>> 24;
		for (int i = 0; i < p.length; i += 3) {
			w.pos(p[i], p[i + 1], p[i + 2]).color(r, g, b, a).endVertex();
		}
	}

	private static void ring(float cx, float cy, float cz, float radius, float width, float height, int argb) {
		int seg = 32;
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		float in = radius - width / 2;
		float out = radius + width / 2;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg;
			double a1 = Math.PI * 2 * (i + 1) / seg;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			float top = cy - height / 2;
			float bottom = cy + height / 2;
			face(w, argb, 1.0f, cx + c0 * in, top, cz + s0 * in, cx + c1 * in, top, cz + s1 * in,
				cx + c1 * out, top, cz + s1 * out, cx + c0 * out, top, cz + s0 * out);
			face(w, argb, 0.85f, cx + c0 * in, bottom, cz + s0 * in, cx + c0 * out, bottom, cz + s0 * out,
				cx + c1 * out, bottom, cz + s1 * out, cx + c1 * in, bottom, cz + s1 * in);
			face(w, argb, 0.92f, cx + c0 * out, top, cz + s0 * out, cx + c1 * out, top, cz + s1 * out,
				cx + c1 * out, bottom, cz + s1 * out, cx + c0 * out, bottom, cz + s0 * out);
			face(w, argb, 0.92f, cx + c0 * in, top, cz + s0 * in, cx + c0 * in, bottom, cz + s0 * in,
				cx + c1 * in, bottom, cz + s1 * in, cx + c1 * in, top, cz + s1 * in);
		}
		t.draw();
	}

	// ---- wings: body space, the back of the body at z = 0.125 --------------------------------------

	private void drawWing(int side, float beat) {
		boolean dragon = wings.is("dragon");
		int c = color(wingsColor, dragon ? 0xFF3B2A4D : 0xFFF4F4F6);
		float span = dragon ? 1.05f : 0.95f;
		GlStateManager.pushMatrix();
		GlStateManager.translate(side * 0.06f, 0.18f, 0.14f);
		// folded back about 30 degrees, opening with the beat
		GlStateManager.rotate(side * (-30 + beat), 0, 1, 0);
		GlStateManager.rotate(side * -12, 0, 0, 1);
		GlStateManager.scale(side * span, span, span);
		float[][] shape = dragon ? DRAGON : ANGEL;
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
		int root = shade(c, dragon ? 0.6f : 0.82f);
		for (int i = 0; i < shape.length; i++) {
			float[] a = shape[i];
			float[] b = shape[(i + 1) % shape.length];
			vertex(w, 0.04f, 0.12f, root);
			vertex(w, a[0], a[1], shade(c, 0.85f + 0.15f * a[0]));
			vertex(w, b[0], b[1], shade(c, 0.85f + 0.15f * b[0]));
		}
		t.draw();
		// feather rows or bones, slightly darker and just behind the surface
		GL11.glLineWidth(2f);
		w.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		int line = shade(c, dragon ? 0.45f : 0.7f);
		if (dragon) {
			for (int tip : DRAGON_BONES) {
				line(w, 0, 0, shape[tip][0], shape[tip][1], line);
			}
			line(w, 0, 0, shape[2][0], shape[2][1], line);
			line(w, shape[2][0], shape[2][1], shape[3][0], shape[3][1], line);
		} else {
			for (int i = 6; i < shape.length; i++) {
				line(w, 0.05f, 0.1f, shape[i][0] * 0.98f, shape[i][1] * 0.98f, line);
			}
			for (int i = 1; i < 6; i++) {
				line(w, shape[i][0] * 0.55f, shape[i][1] * 0.55f + 0.1f, shape[i][0] * 0.95f, shape[i][1] * 0.95f + 0.05f, line);
			}
		}
		t.draw();
		GL11.glLineWidth(1f);
		GlStateManager.popMatrix();
	}

	/** Wing space to model space: x along the span, y down, a slight sweep back. */
	private static void vertex(WorldRenderer w, float x, float y, int argb) {
		w.pos(x, y, x * 0.25f).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24).endVertex();
	}

	private static void line(WorldRenderer w, float x0, float y0, float x1, float y1, int argb) {
		w.pos(x0, y0, x0 * 0.25f + 0.002f).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, 255).endVertex();
		w.pos(x1, y1, x1 * 0.25f + 0.002f).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, 255).endVertex();
	}

	private static int shade(int argb, float f) {
		f = Math.min(1, f);
		return argb & 0xFF000000 | Math.round((argb >> 16 & 0xFF) * f) << 16 | Math.round((argb >> 8 & 0xFF) * f) << 8
			| Math.round((argb & 0xFF) * f);
	}
}
