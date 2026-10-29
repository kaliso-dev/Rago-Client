package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.CosmeticModel;
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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Cosmetics on your own player: a hat, wings and a shield in the left hand, each with its size (and
 * colour for the hat and wings). The Rago cap and beanie are textured boxes; the voxel hats, wings
 * and shields are {@link CosmeticModel}s built by tools/models.py (credits in
 * tools/models/CREDITS.md); the crown and halo are simple shapes. Off by default, and each piece can
 * be set to none. Only this client draws them.
 */
public final class Cosmetics extends Module {
	private static final ResourceLocation TEX_CAP = new ResourceLocation("rago", "textures/cosmetics/cap.png");
	private static final ResourceLocation TEX_BEANIE = new ResourceLocation("rago", "textures/cosmetics/beanie.png");
	private static final ResourceLocation TEX_LOGO = new ResourceLocation("rago", "textures/logo.png");
	private static final float PX = 1 / 16f;
	/** The voxel hats, by option value. */
	private static final Map<String, CosmeticModel> HATS = new HashMap<>();
	private static final CosmeticModel ANGEL = model("angel", "angel");
	private static final CosmeticModel BUTTERFLY = model("butterfly", "butterfly");
	private static final CosmeticModel DRAGON = model("dragon", "dragon");
	private static final CosmeticModel DRAGON_SMALL = model("dragon_small", "dragon_small");
	private static final String[] SHIELDS = {"none", "rago", "crimson", "gold", "frost", "void"};
	private static final Map<String, CosmeticModel> SHIELD_MODELS = new HashMap<>();

	static {
		for (String h : new String[] {"top_hat", "mushroom_red", "mushroom_green"}) {
			HATS.put(h, model(h, "hats_atlas"));
		}
		HATS.put("wizard_hat", model("wizard_hat", "wizard_hat"));
		HATS.put("cap_red", model("cap_red", "cap_red"));
		for (int i = 1; i < SHIELDS.length; i++) {
			SHIELD_MODELS.put(SHIELDS[i], model("shield_" + SHIELDS[i], "shield_" + SHIELDS[i]));
		}
	}

	private final ChoiceOption hat = addOption(new ChoiceOption("cosmetic_hat", "none", "none", "rago_cap", "rago_beanie",
		"cap_red", "top_hat", "wizard_hat", "mushroom_red", "mushroom_green", "crown", "halo"));
	private final ColorOption hatColor = addOption(new ColorOption("cosmetic_hat_color", 0xFFFFFFFF));
	private final ColorOption logoColor = addOption(new ColorOption("cosmetic_logo_color", 0xFFF2392C));
	private final IntOption hatScale = addOption(new IntOption("cosmetic_hat_scale", 100, 70, 150));
	private final ChoiceOption wings = addOption(new ChoiceOption("cosmetic_wings", "none", "none", "angel", "butterfly", "dragon", "dragon_small"));
	private final ColorOption wingsColor = addOption(new ColorOption("cosmetic_wings_color", 0xFFFFFFFF));
	private final IntOption wingsScale = addOption(new IntOption("cosmetic_wings_scale", 100, 50, 160));
	private final BooleanOption flap = addOption(new BooleanOption("cosmetic_wings_flap", true));
	private final IntOption flapSpeed = addOption(new IntOption("cosmetic_wings_flap_speed", 100, 25, 300));
	private final ChoiceOption shield = addOption(new ChoiceOption("cosmetic_shield", "none", SHIELDS));
	private final IntOption shieldScale = addOption(new IntOption("cosmetic_shield_scale", 100, 50, 150));
	/** 0: facing forward beside the arm; 90: flat against the outside of the arm. */
	private final IntOption shieldAngle = addOption(new IntOption("cosmetic_shield_angle", 60, 0, 90));
	private boolean installed;

	private static CosmeticModel model(String name, String texture) {
		return new CosmeticModel(name, new ResourceLocation("rago", "textures/cosmetics/" + texture + ".png"));
	}

	public Cosmetics() {
		super("cosmetics", FeatureClass.VISUAL, false);
		hatColor.showIf(() -> !hat.is("none"));
		logoColor.showIf(() -> hat.is("rago_cap") || hat.is("rago_beanie"));
		hatScale.showIf(() -> !hat.is("none"));
		wingsColor.showIf(() -> !wings.is("none"));
		wingsScale.showIf(() -> !wings.is("none"));
		flap.showIf(() -> !wings.is("none"));
		flapSpeed.showIf(() -> !wings.is("none") && flap.get());
		shieldScale.showIf(() -> !shield.is("none"));
		shieldAngle.showIf(() -> !shield.is("none"));
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
					float hs = hatScale.get() / 100f;
					if (hs != 1) {
						// grow from the top of the head, where the hat sits
						GlStateManager.translate(0, -0.5f, 0);
						GlStateManager.scale(hs, hs, hs);
						GlStateManager.translate(0, 0.5f, 0);
					}
					drawHat(player.getCurrentArmor(3) != null, ageInTicks);
					GlStateManager.popMatrix();
				}
				if (!wings.is("none")) {
					GlStateManager.pushMatrix();
					if (player.isSneaking()) {
						GlStateManager.translate(0, 0.2f, 0);
					}
					model.bipedBody.postRender(0.0625f);
					float beat = flap.get() ? flapAngle(player, ageInTicks * flapSpeed.get() / 100f, wings.is("butterfly")) : 0;
					if (wings.is("angel")) {
						angelWings(beat);
					} else {
						extrudedWing(-1, beat);
						extrudedWing(1, beat);
					}
					GlStateManager.popMatrix();
				}
				if (!shield.is("none")) {
					GlStateManager.pushMatrix();
					if (player.isSneaking()) {
						GlStateManager.translate(0, 0.2f, 0);
					}
					model.bipedLeftArm.postRender(0.0625f);
					drawShield();
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

	private static float flapAngle(AbstractClientPlayer player, float age, boolean butterfly) {
		boolean airborne = !player.onGround || player.capabilities.isFlying;
		float speed = airborne ? 0.45f : 0.12f;
		float amount = butterfly ? (airborne ? 40 : 14) : airborne ? 22 : 6;
		if (butterfly) {
			speed *= 1.6f;
		}
		return (float) Math.sin(age * speed) * amount;
	}

	private int color(ColorOption option, int fallback) {
		int c = option.get();
		return c == 0xFFFFFFFF ? fallback : c;
	}

	// ---- hats: head space, 1 unit = 1 block, y down, top of the head at y = -0.5 ----------------

	private void drawHat(boolean helmet, float age) {
		float base = helmet ? -0.58f : -0.535f;
		float lift = helmet ? -0.75f * PX : 0;
		switch (hat.get()) {
			case "rago_cap":
				drawCap(lift);
				break;
			case "rago_beanie":
				drawBeanie(lift);
				break;
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
			case "halo": {
				// halo: a glowing ring floating above the head
				int c = color(hatColor, 0xFFFFE27A);
				float y = base - 0.22f + (float) Math.sin(age * 0.08f) * 0.02f;
				float lx = OpenGlHelper.lastBrightnessX;
				float ly = OpenGlHelper.lastBrightnessY;
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
				ring(0, y, 0, 0.22f, 0.04f, 0.025f, c);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
				break;
			}
			default: {
				CosmeticModel m = HATS.get(hat.get());
				if (m != null) {
					GlStateManager.pushMatrix();
					GlStateManager.translate(0, lift, 0);
					GlStateManager.disableBlend();
					m.render("hat", hatColor.get());
					GlStateManager.popMatrix();
					untextured();
				}
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

	// ---- textured hats: head space in pixels (1/16 block), y down, the face looking to -z --------

	private void drawCap(float lift) {
		int c = color(hatColor, 0xFF232328);
		textured(TEX_CAP);
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, lift, 0);
		texBox(-4.5f, -8.75f, -4.5f, 4.5f, -4.75f, 4.5f, 0, 0, 9, 4, 9, 64, 32, c);
		// the brim, tilted down a little at the front
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, -4.9f * PX, -4.5f * PX);
		GlStateManager.rotate(8, 1, 0, 0);
		texBox(-4.5f, -0.5f, -5, 4.5f, 0.25f, 0, 0, 14, 9, 1, 5, 64, 32, c);
		GlStateManager.popMatrix();
		texBox(-0.5f, -9.25f, -0.5f, 0.5f, -8.75f, 0.5f, 40, 0, 1, 1, 1, 64, 32, c);
		logo(-1.6f, -8.3f, 3.2f, -4.53f);
		GlStateManager.popMatrix();
		untextured();
	}

	private void drawBeanie(float lift) {
		int c = color(hatColor, 0xFF232328);
		textured(TEX_BEANIE);
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, lift, 0);
		texBox(-4.5f, -9.25f, -4.5f, 4.5f, -5.5f, 4.5f, 0, 0, 9, 4, 9, 64, 32, c);
		texBox(-4.8f, -5.75f, -4.8f, 4.8f, -3.9f, 4.8f, 0, 14, 9, 2, 9, 64, 32, c);
		int pom = logoColor.get();
		texBox(-1.5f, -11.6f, -1.5f, 1.5f, -9.1f, 1.5f, 40, 0, 3, 3, 3, 64, 32, pom);
		logo(-0.9f, -5.65f, 1.8f, -4.83f);
		GlStateManager.popMatrix();
		untextured();
	}

	/** Rago's logo on the front of a hat: a square {@code size} pixels wide at depth {@code z}. */
	private void logo(float x, float y, float size, float z) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_LOGO);
		int c = logoColor.get();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		float x0 = x * PX;
		float x1 = (x + size) * PX;
		float y0 = y * PX;
		float y1 = (y + size) * PX;
		float zz = z * PX;
		// The face looks toward -z, so the viewer's right is +x.
		texVertex(w, x1, y0, zz, 1, 0, c, 1);
		texVertex(w, x0, y0, zz, 0, 0, c, 1);
		texVertex(w, x0, y1, zz, 0, 1, c, 1);
		texVertex(w, x1, y1, zz, 1, 1, c, 1);
		t.draw();
	}

	private static void textured(ResourceLocation tex) {
		GlStateManager.enableTexture2D();
		Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
		GlStateManager.enableAlpha();
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
		GlStateManager.disableBlend();
	}

	private static void untextured() {
		GlStateManager.disableTexture2D();
	}

	/**
	 * A box in pixels from (x0, y0, z0) to (x1, y1, z1), textured from the box layout at (u, v) of a
	 * w x h x d box (see tools/cosmetics.py), tinted and shaded per face.
	 */
	private static void texBox(float x0, float y0, float z0, float x1, float y1, float z1, int u, int v, int w, int h, int d,
		float tw, float th, int argb) {
		float ax = x0 * PX;
		float ay = y0 * PX;
		float az = z0 * PX;
		float bx = x1 * PX;
		float by = y1 * PX;
		float bz = z1 * PX;
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		// top (y0): u+d .. u+d+w across x, v .. v+d from back to front
		texQuad(wr, argb, 1.0f, u + d, v, w, d, tw, th, ax, ay, bz, bx, ay, bz, bx, ay, az, ax, ay, az);
		// bottom (y1)
		texQuad(wr, argb, 0.5f, u + d + w, v, w, d, tw, th, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz);
		// front (z0)
		texQuad(wr, argb, 0.82f, u + d, v + d, w, h, tw, th, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
		// back (z1)
		texQuad(wr, argb, 0.82f, u + 2 * d + w, v + d, w, h, tw, th, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
		// sides (x0 and x1)
		texQuad(wr, argb, 0.68f, u, v + d, d, h, tw, th, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
		texQuad(wr, argb, 0.68f, u + d + w, v + d, d, h, tw, th, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
		t.draw();
	}

	/** One face: corners top-left, top-right, bottom-right, bottom-left of the texture rectangle. */
	private static void texQuad(WorldRenderer w, int argb, float shade, int u, int v, int du, int dv, float tw, float th,
		float... p) {
		float u0 = u / tw;
		float v0 = v / th;
		float u1 = (u + du) / tw;
		float v1 = (v + dv) / th;
		texVertex(w, p[0], p[1], p[2], u0, v0, argb, shade);
		texVertex(w, p[3], p[4], p[5], u1, v0, argb, shade);
		texVertex(w, p[6], p[7], p[8], u1, v1, argb, shade);
		texVertex(w, p[9], p[10], p[11], u0, v1, argb, shade);
	}

	private static void texVertex(WorldRenderer w, float x, float y, float z, float u, float v, int argb, float shade) {
		w.pos(x, y, z).tex(u, v).color(Math.round((argb >> 16 & 0xFF) * shade), Math.round((argb >> 8 & 0xFF) * shade),
			Math.round((argb & 0xFF) * shade), argb >>> 24).endVertex();
	}

	// ---- wings: body space, the back of the body at z = 0.125 --------------------------------------

	/**
	 * The voxel angel wings: both wings in one model, each turning around its root near the spine. The
	 * model's texture is grey so the chosen colour tints it.
	 */
	private void angelWings(float beat) {
		int c = wingsColor.get();
		float span = wingsScale.get() / 100f;
		GlStateManager.disableBlend();
		GlStateManager.pushMatrix();
		// higher than the source model, so the wings start at the top of the shoulder blades
		GlStateManager.translate(0, -0.16f, 0);
		for (int side = -1; side <= 1; side += 2) {
			String group = side > 0 ? "left" : "right";
			float[] p = ANGEL.pivot(group);
			GlStateManager.pushMatrix();
			GlStateManager.translate(p[0], p[1], p[2]);
			// beating sweeps the tips back and forth
			GlStateManager.rotate(-side * beat, 0, 1, 0);
			GlStateManager.scale(span, span, span);
			GlStateManager.translate(-p[0], -p[1], -p[2]);
			ANGEL.render(group, c);
			GlStateManager.popMatrix();
		}
		GlStateManager.popMatrix();
		untextured();
	}

	/**
	 * One butterfly or dragon wing: Rago's pixel-art wing extruded into voxels, its root on the back.
	 * The other side is the same model mirrored.
	 */
	private void extrudedWing(int side, float beat) {
		boolean butterfly = wings.is("butterfly");
		boolean small = wings.is("dragon_small");
		int c = color(wingsColor, butterfly ? 0xFF4FA3FF : small ? 0xFF3FB950 : 0xFF5A3E78);
		// the small dragon wings are compact, like Lunar Client's
		float span = (butterfly ? 0.9f : small ? 0.7f : 1.15f) * wingsScale.get() / 100f;
		GlStateManager.pushMatrix();
		// the root high on the back, between the shoulder blades
		GlStateManager.translate(side * 0.05f, butterfly ? 0.14f : 0.04f, 0.16f);
		if (butterfly) {
			// butterflies open and close around the spine
			GlStateManager.rotate(side * (-20 + beat), 0, 1, 0);
		} else {
			GlStateManager.rotate(side * (-30 + beat), 0, 1, 0);
			// raised, the tips above the shoulders
			GlStateManager.rotate(side * -22, 0, 0, 1);
		}
		GlStateManager.scale(side * span, span, span);
		GlStateManager.disableBlend();
		(butterfly ? BUTTERFLY : small ? DRAGON_SMALL : DRAGON).render("wing", c);
		GlStateManager.popMatrix();
		untextured();
	}

	// ---- shield: left arm space, the arm from x -1 to 3 pixels, the shoulder at y = -2 -----------

	/**
	 * A Rago shield held in the left hand, as Badlion shows one: upright beside the forearm,
	 * its face forward and turned a little outward. Only seen in third person, like the rest of the
	 * player.
	 */
	/** The direction a positive shield angle turns it in, so its face turns outward. */
	private static final int SHIELD_TURN = 1;

	private void drawShield() {
		CosmeticModel m = SHIELD_MODELS.get(shield.get());
		if (m == null) {
			return;
		}
		// the model is one unit tall (48 texture pixels, 40 wide): 16 pixels on the player at 100%
		float size = shieldScale.get() / 100f;
		float width = 16 * size * 40 / 48;
		float angle = shieldAngle.get();
		GlStateManager.pushMatrix();
		// hinged on the outer side of the forearm (x = 3), turned from facing forward (0) to lying
		// flat against the arm (90); its inner edge stays on the arm
		GlStateManager.translate(3.5f * PX, 7 * PX, 0);
		GlStateManager.rotate(SHIELD_TURN * angle, 0, 1, 0);
		GlStateManager.translate(width / 2 * (1 - angle / 90f) * PX, 0, 0);
		GlStateManager.scale(size, size, size);
		GlStateManager.disableBlend();
		m.render("shield", 0xFFFFFFFF);
		GlStateManager.popMatrix();
		untextured();
	}
}
