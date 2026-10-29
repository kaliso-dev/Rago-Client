package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.EmoteWheelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

/**
 * Emotes: dances and gestures for your own player, picked on a wheel (B by default). While one
 * plays, the camera turns to face you, and moving, attacking or the key again stops it. Only the
 * pose of your player on your screen changes: nothing is sent, other players see you standing.
 */
public final class Emotes extends Module {
	public static final String[] DANCES = {"floss", "dab", "wave", "disco", "zombie", "helicopter", "clap", "macarena"};
	private static final float PI = (float) Math.PI;

	private final BooleanOption faceCamera = addOption(new BooleanOption("emote_camera", true));
	private final IntOption speed = addOption(new IntOption("emote_speed", 100, 50, 200));
	private final KeyBinding key = new KeyBinding("key.rago.emotes", Keyboard.KEY_B, "key.categories.rago");

	private String playing;
	private long startedNs;
	private int viewBefore = -1;

	public Emotes() {
		super("emotes", FeatureClass.VISUAL, true);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	public KeyBinding key() {
		return key;
	}

	public String playing() {
		return playing;
	}

	@Override
	protected void onDisable() {
		stop();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		while (key.isPressed()) {
			if (playing != null) {
				stop();
			} else if (mc.currentScreen == null && mc.thePlayer != null) {
				mc.displayGuiScreen(new EmoteWheelScreen(this));
			}
		}
		if (playing == null) {
			return;
		}
		if (mc.thePlayer == null) {
			playing = null;
			viewBefore = -1;
			return;
		}
		// moving or fighting ends the dance
		if (mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown()
			|| mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown()
			|| mc.gameSettings.keyBindJump.isKeyDown() || mc.gameSettings.keyBindAttack.isKeyDown()
			|| mc.gameSettings.keyBindUseItem.isKeyDown() || mc.thePlayer.hurtTime > 0) {
			stop();
		}
	}

	public void play(String dance) {
		Minecraft mc = Minecraft.getMinecraft();
		playing = dance;
		startedNs = System.nanoTime();
		if (faceCamera.get() && viewBefore < 0) {
			viewBefore = mc.gameSettings.thirdPersonView;
			mc.gameSettings.thirdPersonView = 2;
		}
	}

	public void stop() {
		playing = null;
		if (viewBefore >= 0) {
			Minecraft.getMinecraft().gameSettings.thirdPersonView = viewBefore;
			viewBefore = -1;
		}
	}

	/** ModelBiped.setRotationAngles, at its end: your player's pose while a dance plays. */
	public void pose(ModelBiped m, Entity entity) {
		if (playing == null || entity != Minecraft.getMinecraft().thePlayer) {
			return;
		}
		float t = (System.nanoTime() - startedNs) / 1e9f * speed.get() / 100f;
		reset(m);
		switch (playing) {
			case "floss":
				floss(m, t);
				break;
			case "dab":
				dab(m, t);
				break;
			case "wave":
				wave(m, t);
				break;
			case "disco":
				disco(m, t);
				break;
			case "zombie":
				zombie(m, t);
				break;
			case "helicopter":
				helicopter(m, t);
				break;
			case "clap":
				clap(m, t);
				break;
			default:
				macarena(m, t);
		}
		ModelBiped.copyModelAngles(m.bipedHead, m.bipedHeadwear);
	}

	private static void reset(ModelBiped m) {
		for (ModelRenderer r : new ModelRenderer[] {m.bipedHead, m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg, m.bipedLeftLeg}) {
			r.rotateAngleX = 0;
			r.rotateAngleY = 0;
			r.rotateAngleZ = 0;
		}
	}

	private static void set(ModelRenderer r, float x, float y, float z) {
		r.rotateAngleX = x;
		r.rotateAngleY = y;
		r.rotateAngleZ = z;
	}

	/** Arms swung side to side, one in front of the body and one behind, hips the other way. */
	private static void floss(ModelBiped m, float t) {
		float s = MathHelper.sin(t * PI * 3);
		boolean front = MathHelper.sin(t * PI * 1.5f) > 0;
		float side = s * 0.55f;
		set(m.bipedRightArm, front ? -0.35f : 0.35f, 0, side);
		set(m.bipedLeftArm, front ? 0.35f : -0.35f, 0, side);
		m.bipedBody.rotateAngleZ = -s * 0.12f;
		m.bipedRightLeg.rotateAngleZ = -s * 0.12f;
		m.bipedLeftLeg.rotateAngleZ = -s * 0.12f;
		m.bipedHead.rotateAngleZ = s * 0.1f;
	}

	/** Head down into the bent left elbow, the right arm thrown up and out; hit every second. */
	private static void dab(ModelBiped m, float t) {
		float k = Math.min(1, (t % 1.6f) * 6);
		set(m.bipedRightArm, -2.4f * k, 0, 0.9f * k);
		set(m.bipedLeftArm, -2.0f * k, 0.9f * k, 0);
		set(m.bipedHead, 0.55f * k, 0.6f * k, 0);
	}

	/** A hand raised and waving hello. */
	private static void wave(ModelBiped m, float t) {
		set(m.bipedRightArm, -2.7f, 0, 0.35f + MathHelper.sin(t * PI * 4) * 0.35f);
		m.bipedHead.rotateAngleZ = MathHelper.sin(t * PI * 2) * 0.08f;
	}

	/** Pointing up and down across the body, knees bouncing. */
	private static void disco(ModelBiped m, float t) {
		boolean up = MathHelper.sin(t * PI * 2) > 0;
		float bounce = Math.abs(MathHelper.sin(t * PI * 4)) * 0.25f;
		set(m.bipedRightArm, up ? -2.6f : -0.3f, 0, up ? -0.4f : 0.6f);
		set(m.bipedLeftArm, up ? -0.3f : -2.6f, 0, up ? -0.6f : 0.4f);
		m.bipedRightLeg.rotateAngleX = -bounce;
		m.bipedLeftLeg.rotateAngleX = -bounce;
		m.bipedHead.rotateAngleZ = up ? 0.15f : -0.15f;
	}

	/** Arms stretched forward, head lolling. */
	private static void zombie(ModelBiped m, float t) {
		float sway = MathHelper.sin(t * PI * 1.5f);
		set(m.bipedRightArm, -1.5f + sway * 0.1f, -0.1f, 0);
		set(m.bipedLeftArm, -1.5f - sway * 0.1f, 0.1f, 0);
		set(m.bipedHead, 0.2f, 0, sway * 0.25f);
		m.bipedRightLeg.rotateAngleX = sway * 0.3f;
		m.bipedLeftLeg.rotateAngleX = -sway * 0.3f;
	}

	/** Both arms stretched out to the sides, spinning around above the head. */
	private static void helicopter(ModelBiped m, float t) {
		float a = t * PI * 4;
		set(m.bipedRightArm, -PI, a, 0);
		set(m.bipedLeftArm, -PI, a + PI, 0);
		m.bipedHead.rotateAngleY = MathHelper.sin(a) * 0.2f;
	}

	/** Clapping in front of the chest. */
	private static void clap(ModelBiped m, float t) {
		float open = (MathHelper.sin(t * PI * 6) + 1) / 2;
		set(m.bipedRightArm, -1.3f, -0.6f + open * 0.5f, 0);
		set(m.bipedLeftArm, -1.3f, 0.6f - open * 0.5f, 0);
		m.bipedHead.rotateAngleX = 0.1f;
	}

	/** The arm moves of the Macarena, one every half second, and a hip swing at the end. */
	private static void macarena(ModelBiped m, float t) {
		int step = (int) (t * 2) % 8;
		float hips = MathHelper.sin(t * PI * 4) * 0.15f;
		// arms out, palms down; then hands to the other shoulder; then behind the head; then hips
		float[][] right = {{-1.5f, 0, 0}, {-1.5f, 0, 0}, {-1.5f, -0.9f, 0}, {-1.5f, -0.9f, 0}, {-2.8f, -0.6f, 0}, {-2.8f, -0.6f, 0}, {0.2f, 0, 0.3f}, {0.2f, 0, 0.3f}};
		float[][] left = {{0, 0, 0}, {-1.5f, 0, 0}, {-1.5f, 0, 0}, {-1.5f, 0.9f, 0}, {-1.5f, 0.9f, 0}, {-2.8f, 0.6f, 0}, {-2.8f, 0.6f, 0}, {0.2f, 0, -0.3f}};
		set(m.bipedRightArm, right[step][0], right[step][1], right[step][2]);
		set(m.bipedLeftArm, left[step][0], left[step][1], left[step][2]);
		if (step >= 6) {
			m.bipedBody.rotateAngleZ = hips;
			m.bipedRightLeg.rotateAngleZ = hips;
			m.bipedLeftLeg.rotateAngleZ = hips;
		}
	}
}
