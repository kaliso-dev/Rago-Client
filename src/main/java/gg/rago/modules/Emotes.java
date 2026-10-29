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
	/** Models a dance has posed, with what to put back on them (see {@link #snapshot}). */
	private final java.util.Map<ModelBiped, float[]> touched = new java.util.WeakHashMap<>();
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

	/** Whether a model still carries a dance pose to take off (see {@link #pose}). */
	public boolean posed() {
		return !touched.isEmpty();
	}

	/**
	 * ModelBiped.setRotationAngles, at its end: your player's pose while a dance plays. The models are
	 * shared by every player drawn with them, and vanilla leaves a few angles and positions alone each
	 * frame; the ones a dance moves are put back before the model draws anyone else.
	 */
	public void pose(ModelBiped m, Entity entity) {
		if (playing == null || entity != Minecraft.getMinecraft().thePlayer) {
			float[] base = touched.remove(m);
			if (base != null) {
				restore(m, base);
			}
			return;
		}
		float[] base = touched.get(m);
		if (base == null) {
			base = snapshot(m);
			touched.put(m, base);
		}
		float elapsed = (System.nanoTime() - startedNs) / 1e9f;
		float t = elapsed * speed.get() / 100f;
		// the dance eases in from the pose the player had
		float w = smooth(Math.min(1, elapsed / BLEND_IN));
		ModelRenderer[] parts = parts(m);
		float[] before = new float[parts.length * 3];
		for (int i = 0; i < parts.length; i++) {
			before[i * 3] = parts[i].rotateAngleX;
			before[i * 3 + 1] = parts[i].rotateAngleY;
			before[i * 3 + 2] = parts[i].rotateAngleZ;
			set(parts[i], 0, 0, 0);
		}
		Rig rig = new Rig();
		switch (playing) {
			case "floss":
				floss(m, rig, t);
				break;
			case "dab":
				dab(m, rig, t);
				break;
			case "wave":
				wave(m, rig, t);
				break;
			case "disco":
				disco(m, rig, t);
				break;
			case "zombie":
				zombie(m, rig, t);
				break;
			case "helicopter":
				helicopter(m, rig, t);
				break;
			case "clap":
				clap(m, rig, t);
				break;
			default:
				macarena(m, rig, t);
		}
		for (int i = 0; i < parts.length; i++) {
			parts[i].rotateAngleX = lerp(before[i * 3], parts[i].rotateAngleX, w);
			parts[i].rotateAngleY = lerp(before[i * 3 + 1], parts[i].rotateAngleY, w);
			parts[i].rotateAngleZ = lerp(before[i * 3 + 2], parts[i].rotateAngleZ, w);
		}
		apply(m, base, rig, w);
		ModelBiped.copyModelAngles(m.bipedHead, m.bipedHeadwear);
	}

	// ---- rig: the whole body moving, on top of the limbs' angles -----------------------------------

	private static final float BLEND_IN = 0.3f;

	/** Moves of the whole body: the upper body sinking (knees bending), turning and leaning. */
	private static final class Rig {
		/** Pixels the head, body and arms sink, as when the knees bend. */
		float drop;
		/** Turn of the upper body around the spine, in radians. */
		float twist;
		/** Sideways lean of the upper body, in radians (the hips go the other way). */
		float lean;
	}

	private static ModelRenderer[] parts(ModelBiped m) {
		return new ModelRenderer[] {m.bipedHead, m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg, m.bipedLeftLeg};
	}

	/** What vanilla never puts back itself: body and head positions, the arms' height, legs' x. */
	private static float[] snapshot(ModelBiped m) {
		return new float[] {m.bipedBody.rotationPointX, m.bipedBody.rotationPointY, m.bipedBody.rotationPointZ,
			m.bipedHead.rotationPointX, m.bipedHead.rotationPointZ,
			m.bipedRightArm.rotationPointY, m.bipedLeftArm.rotationPointY,
			m.bipedRightLeg.rotationPointX, m.bipedLeftLeg.rotationPointX};
	}

	private static void restore(ModelBiped m, float[] b) {
		m.bipedBody.setRotationPoint(b[0], b[1], b[2]);
		m.bipedHead.rotationPointX = b[3];
		m.bipedHead.rotationPointZ = b[4];
		m.bipedRightArm.rotationPointY = b[5];
		m.bipedLeftArm.rotationPointY = b[6];
		m.bipedRightLeg.rotationPointX = b[7];
		m.bipedLeftLeg.rotationPointX = b[8];
		// angles vanilla never sets: back to its rest
		m.bipedBody.rotateAngleZ = 0;
		m.bipedHead.rotateAngleZ = 0;
		m.bipedRightLeg.rotateAngleZ = 0;
		m.bipedLeftLeg.rotateAngleZ = 0;
		m.bipedHeadwear.rotateAngleZ = 0;
	}

	/** The rig on the parts: sinking, twisting the shoulders around the spine, leaning. */
	private static void apply(ModelBiped m, float[] base, Rig rig, float w) {
		float drop = rig.drop * w;
		float twist = rig.twist * w;
		float lean = rig.lean * w;
		// the body turns around its top, the neck: moving the top keeps the hips in place as it leans
		float shift = MathHelper.sin(lean) * 12;
		m.bipedBody.setRotationPoint(base[0] + shift, base[1] + drop, base[2]);
		m.bipedBody.rotateAngleY += twist;
		m.bipedBody.rotateAngleZ += lean;
		m.bipedHead.rotationPointX = base[3] + shift;
		m.bipedHead.rotationPointY = drop;
		m.bipedHead.rotationPointZ = base[4];
		m.bipedHead.rotateAngleY += twist * 0.6f;
		// the shoulders follow the turned and leaning chest
		float sin = MathHelper.sin(twist);
		float cos = MathHelper.cos(twist);
		float shoulders = MathHelper.sin(lean) * 10;
		m.bipedRightArm.rotationPointX = -cos * 5 + shoulders;
		m.bipedRightArm.rotationPointZ = sin * 5;
		m.bipedLeftArm.rotationPointX = cos * 5 + shoulders;
		m.bipedLeftArm.rotationPointZ = -sin * 5;
		m.bipedRightArm.rotationPointY = base[5] + drop - MathHelper.sin(lean) * 5;
		m.bipedLeftArm.rotationPointY = base[6] + drop + MathHelper.sin(lean) * 5;
		m.bipedRightArm.rotateAngleY += twist;
		m.bipedLeftArm.rotateAngleY += twist;
		m.bipedRightArm.rotateAngleZ += lean;
		m.bipedLeftArm.rotateAngleZ += lean;
		// knees bending: the legs swing a little forward as the body sinks; the hips sway against the lean
		float bend = drop * 0.07f;
		m.bipedRightLeg.rotateAngleX -= bend;
		m.bipedLeftLeg.rotateAngleX -= bend;
		m.bipedRightLeg.rotateAngleZ -= lean * 0.4f;
		m.bipedLeftLeg.rotateAngleZ -= lean * 0.4f;
		m.bipedRightLeg.rotationPointX = base[7];
		m.bipedLeftLeg.rotationPointX = base[8];
	}

	private static void set(ModelRenderer r, float x, float y, float z) {
		r.rotateAngleX = x;
		r.rotateAngleY = y;
		r.rotateAngleZ = z;
	}

	private static float lerp(float a, float b, float k) {
		return a + (b - a) * k;
	}

	/** 0 to 1 with a soft start and end. */
	private static float smooth(float x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}

	/** A wave of the beat: 0 to 1 and back, {@code beats} times a second. */
	private static float pulse(float t, float beats) {
		return (1 - MathHelper.cos(t * beats * 2 * PI)) / 2;
	}

	private static float wave(float t, float hz) {
		return MathHelper.sin(t * hz * 2 * PI);
	}

	/** Between keyframes, eased: {@code frames[i]} at step i, {@code stepTime} seconds a step, looping. */
	private static float key(float[] frames, float t, float stepTime) {
		float pos = t / stepTime;
		int i = (int) Math.floor(pos);
		float f = smooth(pos - i);
		int n = frames.length;
		float a = frames[((i % n) + n) % n];
		float b = frames[(((i + 1) % n) + n) % n];
		return lerp(a, b, f);
	}

	// ---- dances ------------------------------------------------------------------------------------

	/** Arms swinging together side to side, in front of and behind the body; hips the other way. */
	private static void floss(ModelBiped m, Rig rig, float t) {
		float hz = 1.4f;
		float side = wave(t, hz);
		float through = MathHelper.cos(t * hz * 2 * PI);
		set(m.bipedRightArm, -0.45f * through, 0, 0.15f + side * 0.6f);
		set(m.bipedLeftArm, 0.45f * through, 0, -0.15f + side * 0.6f);
		rig.lean = -side * 0.14f;
		rig.drop = pulse(t, hz * 2) * 0.6f;
		m.bipedHead.rotateAngleZ = side * 0.08f;
		m.bipedHead.rotateAngleX = 0.05f;
	}

	/** Head down into the bent left elbow, the right arm thrown up and out; again every two seconds. */
	private static void dab(ModelBiped m, Rig rig, float t) {
		float cycle = t % 2f;
		float k = cycle < 0.25f ? smooth(cycle / 0.25f) : cycle < 1.45f ? 1 : smooth(1 - (cycle - 1.45f) / 0.35f);
		// a little kick as the dab lands
		float kick = cycle < 0.45f ? MathHelper.sin(Math.min(1, cycle / 0.45f) * PI) : 0;
		set(m.bipedRightArm, -2.35f * k, 0.15f * k, 0.95f * k);
		set(m.bipedLeftArm, -2.0f * k, 0.95f * k, 0.1f * k);
		set(m.bipedHead, 0.6f * k, 0.55f * k, 0.1f * k);
		rig.twist = 0.25f * k;
		rig.lean = -0.08f * k;
		rig.drop = 1.2f * k + kick * 0.6f;
		m.bipedRightLeg.rotateAngleZ = 0.12f * k;
		m.bipedLeftLeg.rotateAngleZ = -0.05f * k;
	}

	/** A hand raised high, waving hello, the weight rocking from foot to foot. */
	private static void wave(ModelBiped m, Rig rig, float t) {
		float hand = wave(t, 2.2f);
		float sway = wave(t, 0.55f);
		set(m.bipedRightArm, -2.75f, 0.1f, 0.3f + hand * 0.35f);
		set(m.bipedLeftArm, 0.05f, 0, -0.08f - sway * 0.04f);
		m.bipedHead.rotateAngleZ = -0.1f + sway * 0.06f;
		m.bipedHead.rotateAngleY = 0.15f;
		rig.lean = sway * 0.07f;
		rig.drop = pulse(t, 1.1f) * 0.35f;
	}

	/** Pointing up across the body and down again, hips swinging, knees bouncing on the beat. */
	private static void disco(ModelBiped m, Rig rig, float t) {
		float hz = 0.9f;
		float u = pulse(t, hz);
		set(m.bipedRightArm, lerp(-0.35f, -2.7f, u), 0, lerp(0.55f, -0.35f, u));
		set(m.bipedLeftArm, lerp(-0.2f, 0.2f, u), 0, lerp(-0.15f, -0.25f, u));
		m.bipedHead.rotateAngleX = lerp(0.25f, -0.3f, u);
		m.bipedHead.rotateAngleZ = lerp(-0.1f, 0.15f, u);
		rig.lean = lerp(0.1f, -0.12f, u);
		rig.twist = lerp(-0.15f, 0.2f, u);
		rig.drop = pulse(t, hz * 2) * 1.0f;
		m.bipedRightLeg.rotateAngleX = -pulse(t, hz * 2) * 0.1f;
	}

	/** Arms stretched forward, staggering slowly, head lolling. */
	private static void zombie(ModelBiped m, Rig rig, float t) {
		float step = wave(t, 0.7f);
		set(m.bipedRightArm, -1.5f + step * 0.12f, -0.1f, 0.05f);
		set(m.bipedLeftArm, -1.5f - step * 0.12f, 0.1f, -0.05f);
		set(m.bipedHead, 0.25f + pulse(t, 0.7f) * 0.1f, step * 0.15f, step * 0.25f);
		m.bipedRightLeg.rotateAngleX = step * 0.35f;
		m.bipedLeftLeg.rotateAngleX = -step * 0.35f;
		rig.lean = step * 0.1f;
		rig.twist = step * 0.12f;
		rig.drop = pulse(t, 1.4f) * 0.5f;
	}

	/** Both arms straight up and spinning, the body turning with them and bouncing. */
	private static void helicopter(ModelBiped m, Rig rig, float t) {
		float a = t * PI * 3;
		set(m.bipedRightArm, -PI + 0.15f, a, 0);
		set(m.bipedLeftArm, -PI + 0.15f, a + PI, 0);
		m.bipedHead.rotateAngleX = -0.2f;
		rig.twist = wave(t, 0.75f) * 0.35f;
		rig.drop = pulse(t, 3) * 0.6f;
		m.bipedRightLeg.rotateAngleZ = 0.06f;
		m.bipedLeftLeg.rotateAngleZ = -0.06f;
	}

	/** Clapping in front of the chest, nodding and bouncing on each clap. */
	private static void clap(ModelBiped m, Rig rig, float t) {
		float hz = 2.4f;
		float open = pulse(t, hz);
		set(m.bipedRightArm, -1.25f, lerp(-0.12f, -0.6f, open), 0);
		set(m.bipedLeftArm, -1.25f, lerp(0.12f, 0.6f, open), 0);
		m.bipedHead.rotateAngleX = 0.05f + (1 - open) * 0.12f;
		rig.drop = (1 - open) * 0.6f;
		rig.lean = wave(t, hz / 4) * 0.05f;
	}

	private static final float[] MAC_RX = {-1.5f, -1.5f, -1.5f, -1.5f, -2.8f, -2.8f, 0.25f, 0.25f};
	private static final float[] MAC_RY = {0, 0, -0.9f, -0.9f, -0.6f, -0.6f, 0, 0};
	private static final float[] MAC_RZ = {0, 0, 0, 0, 0, 0, 0.35f, 0.35f};
	private static final float[] MAC_LX = {0, -1.5f, -1.5f, -1.5f, -1.5f, -2.8f, -2.8f, 0.25f};
	private static final float[] MAC_LY = {0, 0, 0, 0.9f, 0.9f, 0.6f, 0.6f, 0};
	private static final float[] MAC_LZ = {0, 0, 0, 0, 0, 0, 0, -0.35f};

	/** The Macarena's arm moves, one every half second and eased, with the hips swinging throughout. */
	private static void macarena(ModelBiped m, Rig rig, float t) {
		float step = 0.5f;
		set(m.bipedRightArm, key(MAC_RX, t, step), key(MAC_RY, t, step), key(MAC_RZ, t, step));
		set(m.bipedLeftArm, key(MAC_LX, t, step), key(MAC_LY, t, step), key(MAC_LZ, t, step));
		float hips = wave(t, 1);
		rig.lean = hips * 0.1f;
		rig.drop = pulse(t, 2) * 0.5f;
		m.bipedHead.rotateAngleZ = -hips * 0.06f;
		m.bipedHead.rotateAngleX = 0.05f;
	}
}
