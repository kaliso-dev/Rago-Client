package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.hooks.Hooks;
import gg.rago.network.Loadout;
import gg.rago.network.NetworkClient;
import gg.rago.render.BatWing;
import gg.rago.render.CosmeticModel;
import gg.rago.render.NeonColors;
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
 * tools/models/CREDITS.md); the Dragon wings ({@link BatWing}), the crown and the halo are shapes drawn in code. The pieces in Rago's neon red (the
 * logo of the Rago cap and beanie, the small dragon wings, the Rago shield) come in the
 * {@link NeonColors}, with a faint glow that can be turned off. Off by default, and each piece can be
 * set to none. Everything is drawn from a {@link Loadout}: yours from these options, another Rago
 * player's from {@link RagoNetwork}, which also sends yours to the others.
 */
public final class Cosmetics extends Module {
	private static final ResourceLocation TEX_CAP = new ResourceLocation("rago", "textures/cosmetics/cap.png");
	private static final ResourceLocation TEX_BEANIE = new ResourceLocation("rago", "textures/cosmetics/beanie.png");
	private static final ResourceLocation TEX_LOGO = new ResourceLocation("rago", "textures/logo.png");
	private static final ResourceLocation TEX_LOGO_GLOW = new ResourceLocation("rago", "textures/logo_glow.png");
	private static final float PX = 1 / 16f;
	/** The voxel hats, by option value. */
	private static final Map<String, CosmeticModel> HATS = new HashMap<>();
	private static final CosmeticModel DRAGON_SMALL = model("dragon_small", "dragon_small");
	private static final String[] SHIELDS = Loadout.SHIELDS;
	private static final Map<String, CosmeticModel> SHIELD_MODELS = new HashMap<>();

	static {
		HATS.put("top_hat", model("top_hat", "hats_atlas"));
		HATS.put("wizard_hat", model("wizard_hat", "wizard_hat"));
		for (int i = 1; i < SHIELDS.length; i++) {
			SHIELD_MODELS.put(SHIELDS[i], model("shield_" + SHIELDS[i], "shield_" + SHIELDS[i]));
		}
	}

	private final ChoiceOption hat = addOption(new ChoiceOption("cosmetic_hat", "none", Loadout.HATS));
	/** The colour of the Rago cap's and beanie's logo, the cap's strap and the beanie's pompom. */
	private final ChoiceOption hatNeon = addOption(new ChoiceOption("cosmetic_hat_neon", "red", NeonColors.IDS));
	private final ColorOption hatColor = addOption(new ColorOption("cosmetic_hat_color", 0xFFFFFFFF));
	private final IntOption hatScale = addOption(new IntOption("cosmetic_hat_scale", 100, Loadout.HAT_SCALE_MIN,
		Loadout.HAT_SCALE_MAX));
	private final ChoiceOption wings = addOption(new ChoiceOption("cosmetic_wings", "none", Loadout.WINGS));
	private final ChoiceOption wingsNeon = addOption(new ChoiceOption("cosmetic_wings_neon", "red", NeonColors.IDS));
	private final ColorOption wingsColor = addOption(new ColorOption("cosmetic_wings_color", 0xFFFFFFFF));
	private final IntOption wingsScale = addOption(new IntOption("cosmetic_wings_scale", 100, Loadout.WINGS_SCALE_MIN,
		Loadout.WINGS_SCALE_MAX));
	private final BooleanOption flap = addOption(new BooleanOption("cosmetic_wings_flap", true));
	private final IntOption flapSpeed = addOption(new IntOption("cosmetic_wings_flap_speed", 100, Loadout.FLAP_SPEED_MIN,
		Loadout.FLAP_SPEED_MAX));
	private final ChoiceOption shield = addOption(new ChoiceOption("cosmetic_shield", "none", SHIELDS));
	private final ChoiceOption shieldNeon = addOption(new ChoiceOption("cosmetic_shield_neon", "red", NeonColors.IDS));
	private final IntOption shieldScale = addOption(new IntOption("cosmetic_shield_scale", 100, Loadout.SHIELD_SCALE_MIN,
		Loadout.SHIELD_SCALE_MAX));
	/** The faint glow of the neon pieces: one more draw of each, added at full brightness. */
	private final BooleanOption neonGlow = addOption(new BooleanOption("cosmetic_neon_glow", true));
	private boolean installed;
	/** Your loadout as drawn on you, made again when an option changes ({@link #ownSignature}). */
	private Loadout own;
	private int ownSignature;

	private static CosmeticModel model(String name, String texture) {
		return new CosmeticModel(name, new ResourceLocation("rago", "textures/cosmetics/" + texture + ".png"));
	}

	public Cosmetics() {
		super("cosmetics", FeatureClass.VISUAL, false);
		hatColor.showIf(() -> !hat.is("none"));
		hatNeon.showIf(this::ragoHat);
		wingsNeon.showIf(() -> wings.is("dragon_small"));
		shieldNeon.showIf(() -> shield.is("rago"));
		neonGlow.showIf(() -> ragoHat() || wings.is("dragon_small") || shield.is("rago"));
		hatScale.showIf(() -> !hat.is("none"));
		wingsColor.showIf(() -> !wings.is("none"));
		wingsScale.showIf(() -> !wings.is("none"));
		flap.showIf(() -> !wings.is("none"));
		flapSpeed.showIf(() -> !wings.is("none") && flap.get());
		shieldScale.showIf(() -> !shield.is("none"));
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	private boolean ragoHat() {
		return hat.is("rago_cap") || hat.is("rago_beanie");
	}

	/** Your loadout as Rago Network sends it: nothing on you while this or the cape is off. */
	public Loadout shared(RagoCape cape) {
		return loadout(isEnabled(), cape);
	}

	/** Changes whenever {@link #shared} would, without making anything. */
	public int sharedSignature(RagoCape cape) {
		return signature(isEnabled(), cape);
	}

	private Loadout loadout(boolean on, RagoCape cape) {
		boolean capeOn = cape != null && cape.isEnabled();
		return new Loadout(capeOn ? cape.design() : "none", cape != null ? cape.neon() : "red", cape != null && cape.overOwn(),
			on ? hat.get() : "none", hatNeon.get(), hatColor.get(), hatScale.get(),
			on ? wings.get() : "none", wingsNeon.get(), wingsColor.get(), wingsScale.get(), flap.get(), flapSpeed.get(),
			on ? shield.get() : "none", shieldNeon.get(), shieldScale.get(), neonGlow.get());
	}

	private int signature(boolean on, RagoCape cape) {
		int h = on ? 1 : 0;
		h = 31 * h + hat.get().hashCode();
		h = 31 * h + hatNeon.get().hashCode();
		h = 31 * h + hatColor.get();
		h = 31 * h + hatScale.get();
		h = 31 * h + wings.get().hashCode();
		h = 31 * h + wingsNeon.get().hashCode();
		h = 31 * h + wingsColor.get();
		h = 31 * h + wingsScale.get();
		h = 31 * h + (flap.get() ? 1 : 0);
		h = 31 * h + flapSpeed.get();
		h = 31 * h + shield.get().hashCode();
		h = 31 * h + shieldNeon.get().hashCode();
		h = 31 * h + shieldScale.get();
		h = 31 * h + (neonGlow.get() ? 1 : 0);
		if (cape != null) {
			h = 31 * h + (cape.isEnabled() ? 1 : 0);
			h = 31 * h + cape.design().hashCode();
			h = 31 * h + cape.neon().hashCode();
			h = 31 * h + (cape.overOwn() ? 1 : 0);
		}
		return h;
	}

	/** Your loadout as drawn on you (the cape is drawn by {@link RagoCape}). */
	private Loadout ownLoadout() {
		int sig = signature(true, null);
		if (own == null || sig != ownSignature) {
			own = loadout(true, null);
			ownSignature = sig;
		}
		return own;
	}

	/**
	 * Draws {@code pass} once more over what is there, added at full brightness whatever the light:
	 * the faint glow of the neon pieces. Nothing when the loadout's glow is turned off.
	 */
	private static void glow(Loadout l, Runnable pass) {
		if (!l.neonGlow) {
			return;
		}
		float lx = OpenGlHelper.lastBrightnessX;
		float ly = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GlStateManager.depthMask(false);
		pass.run();
		GlStateManager.depthMask(true);
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.disableBlend();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
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
			if (player.isInvisible()) {
				return;
			}
			Loadout l;
			if (RagoClient.isOwn(player)) {
				if (!isEnabled()) {
					return;
				}
				l = ownLoadout();
			} else {
				RagoNetwork network = Hooks.network;
				NetworkClient.Player p = network != null ? network.player(player.getUniqueID()) : null;
				if (p == null) {
					return;
				}
				l = p.loadout;
			}
			if (l.hat.equals("none") && l.wings.equals("none") && l.shield.equals("none")) {
				return;
			}
			ModelPlayer model = render.getMainModel();
			GlStateManager.disableTexture2D();
			GlStateManager.disableLighting();
			GlStateManager.disableCull();
			try {
				if (!l.hat.equals("none")) {
					GlStateManager.pushMatrix();
					if (player.isSneaking()) {
						GlStateManager.translate(0, 0.2f, 0);
					}
					model.bipedHead.postRender(0.0625f);
					// the hats that go around the head are always a little bigger, to clear the skin's hat layer
					float hs = l.hatScale / 100f * skinLayerFit(l.hat);
					if (hs != 1) {
						// grow from the top of the head, where the hat sits
						GlStateManager.translate(0, -0.5f, 0);
						GlStateManager.scale(hs, hs, hs);
						GlStateManager.translate(0, 0.5f, 0);
					}
					drawHat(l, player.getCurrentArmor(3) != null, ageInTicks);
					GlStateManager.popMatrix();
				}
				if (!l.wings.equals("none")) {
					GlStateManager.pushMatrix();
					if (player.isSneaking()) {
						GlStateManager.translate(0, 0.2f, 0);
					}
					model.bipedBody.postRender(0.0625f);
					float beat = l.wingsFlap ? flapAngle(player, ageInTicks * l.wingsFlapSpeed / 100f) : 0;
					if (l.wings.equals("dragon")) {
						BatWing.draw(tint(l.wingsColor, BatWing.MEMBRANE), l.wingsScale / 100f, -1, beat);
						BatWing.draw(tint(l.wingsColor, BatWing.MEMBRANE), l.wingsScale / 100f, 1, beat);
					} else {
						extrudedWing(l, -1, beat);
						extrudedWing(l, 1, beat);
					}
					GlStateManager.popMatrix();
				}
				if (!l.shield.equals("none")) {
					GlStateManager.pushMatrix();
					if (player.isSneaking()) {
						GlStateManager.translate(0, 0.2f, 0);
					}
					model.bipedLeftArm.postRender(0.0625f);
					drawShield(l);
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

	/** A piece's colour: {@code c}, or {@code fallback} while it is left at white. */
	private static int tint(int c, int fallback) {
		return c == Loadout.NO_TINT ? fallback : c;
	}

	/** Half the width of the skin's second layer on the head (8 pixels and half a pixel each side). */
	private static final float SKIN_LAYER_HALF = 4.5f;
	/** How far outside that layer a hat's sides stay, in pixels, so the two never fight for the same place. */
	private static final float LAYER_CLEARANCE = 0.4f;

	/**
	 * How much bigger a hat is drawn so its sides clear the skin's hat layer instead of cutting
	 * through it: the hats that go around the head, by the half width of their sides in pixels.
	 */
	static float skinLayerFit(String hat) {
		float half;
		switch (hat) {
			case "rago_cap":
			case "rago_beanie":
				half = 4.5f;
				break;
			case "top_hat":
			case "wizard_hat":
				half = 4.1f;
				break;
			default:
				return 1;
		}
		return Math.max(1, (SKIN_LAYER_HALF + LAYER_CLEARANCE) / half);
	}

	// ---- hats: head space, 1 unit = 1 block, y down, top of the head at y = -0.5 ----------------

	private static void drawHat(Loadout l, boolean helmet, float age) {
		float base = helmet ? -0.58f : -0.535f;
		float lift = helmet ? -0.75f * PX : 0;
		switch (l.hat) {
			case "rago_cap":
				drawCap(l, lift);
				break;
			case "rago_beanie":
				drawBeanie(l, lift);
				break;
			case "crown": {
				int c = tint(l.hatColor, 0xFFF2C14E);
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
				int c = tint(l.hatColor, 0xFFFFE27A);
				float y = base - 0.22f + (float) Math.sin(age * 0.08f) * 0.02f;
				float lx = OpenGlHelper.lastBrightnessX;
				float ly = OpenGlHelper.lastBrightnessY;
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
				ring(0, y, 0, 0.22f, 0.04f, 0.025f, c);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
				break;
			}
			default: {
				CosmeticModel m = HATS.get(l.hat);
				if (m != null) {
					GlStateManager.pushMatrix();
					GlStateManager.translate(0, lift, 0);
					GlStateManager.disableBlend();
					m.render("hat", l.hatColor);
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

	private static void drawCap(Loadout l, float lift) {
		int c = tint(l.hatColor, 0xFF232328);
		int neon = NeonColors.swatch(l.hatNeon);
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
		logo(-1.6f, -8.3f, 3.2f, -4.53f, neon);
		// the back, as on a snapback: the opening, the strap under it in the logo's colour, its buckle
		untextured();
		box(-1.7f * PX, -7.1f * PX, 4.5f * PX, 1.7f * PX, -5.7f * PX, 4.55f * PX, 0xFF0B0B0E);
		box(-2.8f * PX, -5.7f * PX, 4.5f * PX, 2.8f * PX, -4.95f * PX, 4.7f * PX, neon);
		box(1.3f * PX, -5.85f * PX, 4.7f * PX, 2.2f * PX, -4.8f * PX, 4.8f * PX, 0xFFC9C9D0);
		glow(l, () -> {
			logoGlow(-1.6f, -8.3f, 3.2f, -4.56f, neon);
			untextured();
			box(-2.9f * PX, -5.8f * PX, 4.5f * PX, 2.9f * PX, -4.85f * PX, 4.74f * PX, 0x55000000 | neon & 0xFFFFFF);
		});
		GlStateManager.popMatrix();
		untextured();
	}

	private static void drawBeanie(Loadout l, float lift) {
		int c = tint(l.hatColor, 0xFF232328);
		int neon = NeonColors.swatch(l.hatNeon);
		textured(TEX_BEANIE);
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, lift, 0);
		texBox(-4.5f, -9.25f, -4.5f, 4.5f, -5.5f, 4.5f, 0, 0, 9, 4, 9, 64, 32, c);
		texBox(-4.8f, -5.75f, -4.8f, 4.8f, -3.9f, 4.8f, 0, 14, 9, 2, 9, 64, 32, c);
		texBox(-1.5f, -11.6f, -1.5f, 1.5f, -9.1f, 1.5f, 40, 0, 3, 3, 3, 64, 32, neon);
		logo(-0.9f, -5.65f, 1.8f, -4.83f, neon);
		glow(l, () -> logoGlow(-0.9f, -5.65f, 1.8f, -4.86f, neon));
		GlStateManager.popMatrix();
		untextured();
	}

	/** Rago's logo on the front of a hat: a square {@code size} pixels wide at depth {@code z}. */
	private static void logo(float x, float y, float size, float z, int argb) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_LOGO);
		flatQuad(x, y, size, z, argb);
	}

	/** The soft light around the logo, twice its size, for {@link #glow}. */
	private static void logoGlow(float x, float y, float size, float z, int argb) {
		GlStateManager.enableTexture2D();
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_LOGO_GLOW);
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.004f);
		flatQuad(x - size / 2, y - size / 2, size * 2, z, 0x70000000 | argb & 0xFFFFFF);
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
	}

	private static void flatQuad(float x, float y, float size, float z, int argb) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		float x0 = x * PX;
		float x1 = (x + size) * PX;
		float y0 = y * PX;
		float y1 = (y + size) * PX;
		float zz = z * PX;
		// The face looks toward -z, so the viewer's right is +x.
		texVertex(w, x1, y0, zz, 1, 0, argb, 1);
		texVertex(w, x0, y0, zz, 0, 0, argb, 1);
		texVertex(w, x0, y1, zz, 0, 1, argb, 1);
		texVertex(w, x1, y1, zz, 1, 1, argb, 1);
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
	 * One small dragon wing: Rago's pixel-art wing extruded into voxels, its root on the back. The
	 * other side is the same model mirrored.
	 */
	private static void extrudedWing(Loadout l, int side, float beat) {
		// coloured in their texture (Rago's red and black): untinted by default
		int c = tint(l.wingsColor, 0xFFFFFFFF);
		// compact
		float span = 0.7f * l.wingsScale / 100f;
		GlStateManager.pushMatrix();
		// the root high on the back, between the shoulder blades
		GlStateManager.translate(side * 0.05f, 0.04f, 0.16f);
		GlStateManager.rotate(side * (-30 + beat), 0, 1, 0);
		// raised, the tips above the shoulders
		GlStateManager.rotate(side * -22, 0, 0, 1);
		GlStateManager.scale(side * span, span, span);
		GlStateManager.disableBlend();
		DRAGON_SMALL.render("wing", c, NeonColors.texture(DRAGON_SMALL.texture(), l.wingsNeon));
		ResourceLocation lit = l.neonGlow ? NeonColors.glow(DRAGON_SMALL.texture(), l.wingsNeon) : null;
		if (lit != null) {
			glow(l, () -> DRAGON_SMALL.render("wing", c, lit));
		}
		GlStateManager.popMatrix();
		untextured();
	}

	// ---- shield: left arm space, the arm from x -1 to 3 pixels, the shoulder at y = -2 -----------

	/**
	 * A Rago shield on the left arm, as Badlion shows one: flat against the outer side of the
	 * forearm, its face outward. Only seen in third person, like the rest of the player.
	 */
	private static void drawShield(Loadout l) {
		CosmeticModel m = SHIELD_MODELS.get(l.shield);
		if (m == null) {
			return;
		}
		// the model is one unit tall (and 5/6 of that wide): 16 pixels on the player at 100%
		float size = l.shieldScale / 100f;
		GlStateManager.pushMatrix();
		// against the outer side of the forearm (x = 3), turned a quarter so the face looks outward
		GlStateManager.translate(3.5f * PX, 7 * PX, 0);
		GlStateManager.rotate(-90, 0, 1, 0);
		GlStateManager.scale(size, size, size);
		GlStateManager.disableBlend();
		if (l.shield.equals("rago")) {
			m.render("shield", 0xFFFFFFFF, NeonColors.texture(m.texture(), l.shieldNeon));
			ResourceLocation lit = l.neonGlow ? NeonColors.glow(m.texture(), l.shieldNeon) : null;
			if (lit != null) {
				glow(l, () -> m.render("shield", 0xFFFFFFFF, lit));
			}
		} else {
			m.render("shield", 0xFFFFFFFF);
		}
		GlStateManager.popMatrix();
		untextured();
	}
}
