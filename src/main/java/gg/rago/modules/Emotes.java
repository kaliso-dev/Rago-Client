package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.EmoteWheelScreen;
import gg.rago.render.BentModel;
import gg.rago.render.BentModel.Bend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

/**
 * Emotes: dances and gestures for your own player, picked on a wheel (B by default). While one
 * plays, the camera turns to face you, and moving, attacking or the key again stops it. The body
 * moves as one: elbows and knees bend ({@link BentModel}), the hips sink as the knees bend and the
 * chest bends at the waist. Only the pose of your player on your screen changes: nothing is sent,
 * other players see you standing.
 */
public final class Emotes extends Module {
	public static final String[] DANCES = {"twerk", "dab", "wave", "disco", "zombie", "griddy", "clap", "macarena", "groove"};
	private static final float PI = (float) Math.PI;

	private final BooleanOption faceCamera = addOption(new BooleanOption("emote_camera", true));
	private final IntOption speed = addOption(new IntOption("emote_speed", 100, 50, 200));
	private final KeyBinding key = new KeyBinding("key.rago.emotes", Keyboard.KEY_B, "key.categories.rago");

	private String playing;
	private long startedNs;
	/** Models a dance has posed, with what to put back on them (see {@link #snapshot}). */
	private final java.util.Map<ModelBiped, float[]> touched = new java.util.WeakHashMap<>();
	private int viewBefore = -1;
	private final Bend waist = new Bend(true);
	private final Bend rightElbow = new Bend(false);
	private final Bend leftElbow = new Bend(false);
	private final Bend rightKnee = new Bend(false);
	private final Bend leftKnee = new Bend(false);

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
		if (faceCamera.get()) {
			if (viewBefore < 0) {
				viewBefore = mc.gameSettings.thirdPersonView;
			}
			// the twerk is seen from behind, the others from the front
			mc.gameSettings.thirdPersonView = "twerk".equals(dance) ? 1 : 2;
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
				bendParts(m, false);
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
			case "twerk":
				twerk(m, rig, t);
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
			case "griddy":
				griddy(m, rig, t);
				break;
			case "clap":
				clap(m, rig, t);
				break;
			case "groove":
				groove(m, rig, t);
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
		// the hand drawn in first person stays straight; the whole player bends
		Minecraft mc = Minecraft.getMinecraft();
		bendParts(m, mc.gameSettings.thirdPersonView != 0 || mc.currentScreen != null);
	}

	/** Gives the model's arms, legs and body (and their outer skin layer) this frame's bends, or takes them off. */
	private void bendParts(ModelBiped m, boolean on) {
		BentModel.bend(m.bipedBody, on ? waist : null);
		BentModel.bend(m.bipedRightArm, on ? rightElbow : null);
		BentModel.bend(m.bipedLeftArm, on ? leftElbow : null);
		BentModel.bend(m.bipedRightLeg, on ? rightKnee : null);
		BentModel.bend(m.bipedLeftLeg, on ? leftKnee : null);
		if (m instanceof ModelPlayer) {
			ModelPlayer p = (ModelPlayer) m;
			BentModel.bend(p.bipedBodyWear, on ? waist : null);
			BentModel.bend(p.bipedRightArmwear, on ? rightElbow : null);
			BentModel.bend(p.bipedLeftArmwear, on ? leftElbow : null);
			BentModel.bend(p.bipedRightLegwear, on ? rightKnee : null);
			BentModel.bend(p.bipedLeftLegwear, on ? leftKnee : null);
		}
	}

	// ---- rig: the whole body moving, on top of the limbs' angles -----------------------------------

	private static final float BLEND_IN = 0.3f;

	/** Moves of the whole body: sinking on bent knees, turning, bending at the waist and the elbows. */
	private static final class Rig {
		/** Pixels the hips sink, the knees bending to keep the feet under them. */
		float drop;
		/** Turn of the upper body around the spine, in radians. */
		float twist;
		/** The chest bending sideways at the waist, in radians (toward +x when positive). */
		float lean;
		/** The chest bowing forward at the waist, in radians. */
		float bow;
		/** How far each elbow bends, in radians, and which way the forearm goes (0 forward, PI/2 toward +x). */
		float rightElbow;
		float leftElbow;
		float rightElbowWay;
		float leftElbowWay;
		/** Each knee's bend on top of the one sinking makes, the shin going back, in radians. */
		float rightKnee;
		float leftKnee;
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
		ModelRenderer cape = cape(m);
		if (cape != null) {
			cape.offsetX = 0;
			cape.offsetY = 0;
			cape.offsetZ = 0;
			cape.rotateAngleX = 0;
		}
		// angles vanilla never sets: back to its rest
		m.bipedBody.rotateAngleZ = 0;
		m.bipedHead.rotateAngleZ = 0;
		m.bipedRightLeg.rotateAngleZ = 0;
		m.bipedLeftLeg.rotateAngleZ = 0;
		m.bipedHeadwear.rotateAngleZ = 0;
	}

	/**
	 * The rig on the parts. The hips sink by {@code drop} on bent knees: each thigh comes forward by
	 * the angle that keeps the foot under the hip, and the knee bends twice that. The chest bends at
	 * the waist (sideways and forward) and carries the head and the shoulders; the elbows bend.
	 */
	private void apply(ModelBiped m, float[] base, Rig rig, float w) {
		float drop = Math.max(0, Math.min(6, rig.drop * w));
		float twist = rig.twist * w;
		float lean = rig.lean * w;
		float bow = rig.bow * w;
		waist.set((float) Math.hypot(lean, bow), (float) Math.atan2(lean, bow));
		m.bipedBody.setRotationPoint(base[0], base[1] + drop, base[2]);
		m.bipedBody.rotateAngleY += twist;
		float sin = MathHelper.sin(twist);
		float cos = MathHelper.cos(twist);
		// the neck and the shoulders where the bent chest puts them, turned with the body
		float[] neck = BentModel.move(m.bipedBody, waist, 0, 0, 0);
		m.bipedHead.rotationPointX = base[0] + neck[0] * cos + neck[2] * sin;
		m.bipedHead.rotationPointY = base[1] + drop + neck[1];
		m.bipedHead.rotationPointZ = base[2] - neck[0] * sin + neck[2] * cos;
		m.bipedHead.rotateAngleY += twist * 0.6f;
		m.bipedHead.rotateAngleZ += lean * 0.5f;
		m.bipedHead.rotateAngleX += bow * 0.5f;
		float[] right = BentModel.move(m.bipedBody, waist, -5, base[5] - base[1], 0);
		float[] left = BentModel.move(m.bipedBody, waist, 5, base[6] - base[1], 0);
		m.bipedRightArm.rotationPointX = base[0] + right[0] * cos + right[2] * sin;
		m.bipedRightArm.rotationPointY = base[1] + drop + right[1];
		m.bipedRightArm.rotationPointZ = base[2] - right[0] * sin + right[2] * cos;
		m.bipedLeftArm.rotationPointX = base[0] + left[0] * cos + left[2] * sin;
		m.bipedLeftArm.rotationPointY = base[1] + drop + left[1];
		m.bipedLeftArm.rotationPointZ = base[2] - left[0] * sin + left[2] * cos;
		for (ModelRenderer arm : new ModelRenderer[] {m.bipedRightArm, m.bipedLeftArm}) {
			arm.rotateAngleY += twist;
			arm.rotateAngleZ += lean;
			arm.rotateAngleX -= bow;
		}
		rightElbow.set(rig.rightElbow * w, rig.rightElbowWay);
		leftElbow.set(rig.leftElbow * w, rig.leftElbowWay);
		// knees: thighs forward, shins back under the hips; the hips sway against the lean
		float thigh = (float) Math.acos(1 - drop / 12);
		for (ModelRenderer leg : new ModelRenderer[] {m.bipedRightLeg, m.bipedLeftLeg}) {
			leg.rotateAngleX -= thigh;
			leg.rotationPointY += drop;
			leg.rotateAngleZ -= lean * 0.4f;
		}
		rightKnee.set(2 * thigh + rig.rightKnee * w, PI);
		leftKnee.set(2 * thigh + rig.leftKnee * w, PI);
		m.bipedRightLeg.rotationPointX = base[7];
		m.bipedLeftLeg.rotationPointX = base[8];
		// the cape hangs from the neck, wherever it went (its layer turns it half a turn: x and z swap sign)
		ModelRenderer cape = cape(m);
		if (cape != null) {
			cape.offsetX = -(m.bipedHead.rotationPointX - base[3]) / 16;
			cape.offsetY = m.bipedHead.rotationPointY / 16;
			cape.offsetZ = -(m.bipedHead.rotationPointZ - base[4]) / 16;
			// and lies along the back as the chest bows
			cape.rotateAngleX = CAPE_BOW * bow;
		}
	}

	/** Which way the cape turns to follow the chest bowing (its layer draws it turned half a turn). */
	private static final float CAPE_BOW = -1;

	private static java.lang.reflect.Field capeField;
	private static boolean capeBroken;

	/** The player model's cape, which vanilla keeps to itself; null for other models. */
	private static ModelRenderer cape(ModelBiped m) {
		if (!(m instanceof ModelPlayer) || capeBroken) {
			return null;
		}
		try {
			if (capeField == null) {
				capeField = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ModelPlayer.class, "bipedCape", "field_178729_w");
			}
			return (ModelRenderer) capeField.get(m);
		} catch (ReflectiveOperationException | RuntimeException e) {
			capeBroken = true;
			return null;
		}
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

	/**
	 * Twerk, seen from behind: a wide, low squat, the chest bowed forward with the hands near the
	 * knees, the hips popping up quickly on each beat and settling back, swaying from side to side
	 * every few beats.
	 */
	private static void twerk(ModelBiped m, Rig rig, float t) {
		float hz = 3.2f;
		// the beat's wave, sharpened: a quick pop and a slower settle
		float beat = pulse(t, hz);
		float pop = beat * beat;
		float sway = wave(t, hz / 8);
		rig.drop = 3.8f - pop * 1.5f;
		rig.bow = 0.8f + pop * 0.14f;
		rig.lean = sway * 0.1f;
		rig.twist = sway * 0.07f;
		// the arms hang from the bowed chest toward the knees, elbows soft
		set(m.bipedRightArm, 0.62f, 0.12f, 0.22f);
		set(m.bipedLeftArm, 0.62f, -0.12f, -0.22f);
		rig.rightElbow = 0.35f;
		rig.leftElbow = 0.35f;
		// knees wide apart
		m.bipedRightLeg.rotateAngleZ = 0.28f;
		m.bipedLeftLeg.rotateAngleZ = -0.28f;
		m.bipedRightLeg.rotateAngleY = 0.45f;
		m.bipedLeftLeg.rotateAngleY = -0.45f;
		// the head up, looking ahead over the bow, nodding with the pop
		m.bipedHead.rotateAngleX = -0.45f + pop * 0.08f;
		m.bipedHead.rotateAngleY = sway * 0.2f;
	}

	/** Head down into the bent left elbow, the right arm thrown up and out; again every two seconds. */
	private static void dab(ModelBiped m, Rig rig, float t) {
		float cycle = t % 2f;
		float k = cycle < 0.25f ? smooth(cycle / 0.25f) : cycle < 1.45f ? 1 : smooth(1 - (cycle - 1.45f) / 0.35f);
		// a little kick as the dab lands
		float kick = cycle < 0.45f ? MathHelper.sin(Math.min(1, cycle / 0.45f) * PI) : 0;
		set(m.bipedRightArm, -2.35f * k, 0.15f * k, 0.95f * k);
		set(m.bipedLeftArm, -1.6f * k, 0.9f * k, 0.15f * k);
		rig.rightElbow = 0.05f;
		rig.leftElbow = 2.1f * k;
		set(m.bipedHead, 0.55f * k, 0.5f * k, 0.1f * k);
		rig.twist = 0.25f * k;
		rig.lean = -0.1f * k;
		rig.bow = 0.2f * k;
		rig.drop = 1.8f * k + kick * 0.8f;
		m.bipedRightLeg.rotateAngleZ = 0.12f * k;
		m.bipedLeftLeg.rotateAngleZ = -0.05f * k;
	}

	/** A hand raised high, waving hello from the elbow, the weight rocking from foot to foot. */
	private static void wave(ModelBiped m, Rig rig, float t) {
		float hand = wave(t, 2.2f);
		float sway = wave(t, 0.55f);
		// the upper arm out to the side, the forearm up from the elbow and swinging
		set(m.bipedRightArm, -0.3f, 0, 1.95f);
		rig.rightElbow = 1.2f + hand * 0.4f;
		rig.rightElbowWay = -PI / 2;
		set(m.bipedLeftArm, 0.05f, 0, -0.08f - sway * 0.04f);
		rig.leftElbow = 0.1f;
		m.bipedHead.rotateAngleZ = -0.1f + sway * 0.06f;
		m.bipedHead.rotateAngleY = 0.15f;
		rig.lean = sway * 0.09f;
		rig.drop = 0.3f + pulse(t, 1.1f) * 0.5f;
		rig.rightKnee = Math.max(0, sway) * 0.35f;
		rig.leftKnee = Math.max(0, -sway) * 0.35f;
	}

	/** Pointing up across the body and down again, the other hand on the hip, knees bouncing on the beat. */
	private static void disco(ModelBiped m, Rig rig, float t) {
		float hz = 0.9f;
		float u = pulse(t, hz);
		set(m.bipedRightArm, lerp(-0.35f, -2.7f, u), 0, lerp(0.55f, -0.35f, u));
		rig.rightElbow = lerp(0.5f, 0.05f, u);
		set(m.bipedLeftArm, 0.2f, 0, -0.65f);
		rig.leftElbow = 1.7f;
		rig.leftElbowWay = -PI / 2;
		m.bipedHead.rotateAngleX = lerp(0.25f, -0.3f, u);
		m.bipedHead.rotateAngleZ = lerp(-0.1f, 0.15f, u);
		rig.lean = lerp(0.14f, -0.16f, u);
		rig.twist = lerp(-0.15f, 0.2f, u);
		rig.drop = 0.5f + pulse(t, hz * 2) * 1.8f;
		rig.rightKnee = pulse(t, hz * 2) * 0.3f;
	}

	/** Arms stretched forward, staggering slowly, knees giving way, head lolling. */
	private static void zombie(ModelBiped m, Rig rig, float t) {
		float step = wave(t, 0.7f);
		set(m.bipedRightArm, -1.45f + step * 0.12f, -0.1f, 0.05f);
		set(m.bipedLeftArm, -1.45f - step * 0.12f, 0.1f, -0.05f);
		rig.rightElbow = 0.25f - step * 0.1f;
		rig.leftElbow = 0.25f + step * 0.1f;
		set(m.bipedHead, 0.25f + pulse(t, 0.7f) * 0.1f, step * 0.15f, step * 0.25f);
		m.bipedRightLeg.rotateAngleX = step * 0.35f;
		m.bipedLeftLeg.rotateAngleX = -step * 0.35f;
		rig.rightKnee = Math.max(0, -step) * 0.6f;
		rig.leftKnee = Math.max(0, step) * 0.6f;
		rig.lean = step * 0.12f;
		rig.twist = step * 0.12f;
		rig.bow = 0.15f;
		rig.drop = 0.6f + pulse(t, 1.4f) * 0.6f;
	}

	/**
	 * The Griddy: on the balls of the feet, a heel tapped forward with one foot then the other, with
	 * a little hop on each step, the arms swinging hard against the legs; on the last of every four
	 * steps pairs the hands come up around the eyes like glasses.
	 */
	private static void griddy(ModelBiped m, Rig rig, float t) {
		float hz = 1.25f;
		// which leg is forward: 1 the right, -1 the left
		float s = wave(t, hz);
		float hop = pulse(t, hz * 2);
		float bar = (t * hz) % 4f;
		float glasses = bar < 3f ? 0 : smooth((bar - 3f) / 0.2f) * smooth((4f - bar) / 0.2f);
		// the leg in front straight, heel down; the one behind bending
		m.bipedRightLeg.rotateAngleX = -0.6f * Math.max(0, s);
		m.bipedLeftLeg.rotateAngleX = -0.6f * Math.max(0, -s);
		rig.rightKnee = 0.55f * Math.max(0, -s);
		rig.leftKnee = 0.55f * Math.max(0, s);
		rig.drop = 1.3f - hop * 0.9f;
		rig.bow = 0.16f + hop * 0.05f;
		rig.twist = -s * 0.2f * (1 - glasses);
		// arms against the legs, elbows bent, swinging hard
		float rx = lerp(-0.25f + 0.8f * s, -1.5f, glasses);
		float lx = lerp(-0.25f - 0.8f * s, -1.5f, glasses);
		set(m.bipedRightArm, rx, lerp(0, 0.62f, glasses), lerp(0.18f, 0, glasses));
		set(m.bipedLeftArm, lx, lerp(0, -0.62f, glasses), lerp(-0.18f, 0, glasses));
		rig.rightElbow = lerp(1.15f, 2.1f, glasses);
		rig.leftElbow = lerp(1.15f, 2.1f, glasses);
		m.bipedHead.rotateAngleX = 0.05f + hop * 0.07f - glasses * 0.1f;
		m.bipedHead.rotateAngleY = s * 0.08f * (1 - glasses);
	}

	/** Clapping in front of the chest, forearms up from the elbows, nodding and bouncing on each clap. */
	private static void clap(ModelBiped m, Rig rig, float t) {
		float hz = 2.4f;
		float open = pulse(t, hz);
		set(m.bipedRightArm, -0.6f, lerp(-0.2f, -0.75f, open), 0);
		set(m.bipedLeftArm, -0.6f, lerp(0.2f, 0.75f, open), 0);
		rig.rightElbow = 1.15f;
		rig.leftElbow = 1.15f;
		m.bipedHead.rotateAngleX = 0.05f + (1 - open) * 0.12f;
		rig.drop = 0.3f + (1 - open) * 0.9f;
		rig.lean = wave(t, hz / 4) * 0.06f;
		rig.bow = (1 - open) * 0.08f;
	}

	private static final float[] MAC_RX = {-1.5f, -1.5f, -1.5f, -1.5f, -2.6f, -2.6f, 0.3f, 0.3f};
	private static final float[] MAC_RY = {0, 0, -0.9f, -0.9f, -0.5f, -0.5f, 0.2f, 0.2f};
	private static final float[] MAC_RZ = {0, 0, 0, 0, 0.3f, 0.3f, 0.6f, 0.6f};
	private static final float[] MAC_RE = {0.05f, 0.05f, 0.35f, 0.35f, 2.0f, 2.0f, 1.5f, 1.5f};
	private static final float[] MAC_LX = {0.3f, -1.5f, -1.5f, -1.5f, -1.5f, -2.6f, -2.6f, 0.3f};
	private static final float[] MAC_LY = {-0.2f, 0, 0, 0.9f, 0.9f, 0.5f, 0.5f, -0.2f};
	private static final float[] MAC_LZ = {-0.6f, 0, 0, 0, 0, -0.3f, -0.3f, -0.6f};
	private static final float[] MAC_LE = {1.5f, 0.05f, 0.05f, 0.35f, 0.35f, 2.0f, 2.0f, 1.5f};
	private static final float[] MAC_RW = {0, 0, 0, 0, 0, 0, PI / 2, PI / 2};
	private static final float[] MAC_LW = {-PI / 2, 0, 0, 0, 0, 0, 0, -PI / 2};

	/** The Macarena's arm moves, one every half second and eased: hands to the head and the hips, hips swinging. */
	private static void macarena(ModelBiped m, Rig rig, float t) {
		float step = 0.5f;
		set(m.bipedRightArm, key(MAC_RX, t, step), key(MAC_RY, t, step), key(MAC_RZ, t, step));
		set(m.bipedLeftArm, key(MAC_LX, t, step), key(MAC_LY, t, step), key(MAC_LZ, t, step));
		rig.rightElbow = key(MAC_RE, t, step);
		rig.leftElbow = key(MAC_LE, t, step);
		// hands on the hips: the forearms come in toward the body; elsewhere forward
		rig.rightElbowWay = key(MAC_RW, t, step);
		rig.leftElbowWay = key(MAC_LW, t, step);
		float hips = wave(t, 1);
		rig.lean = hips * 0.12f;
		rig.drop = 0.4f + pulse(t, 2) * 0.8f;
		m.bipedHead.rotateAngleZ = -hips * 0.06f;
		m.bipedHead.rotateAngleX = 0.05f;
	}

	/**
	 * A low groove: knees bent wide and bouncing, fists pumping from the elbows one after the other,
	 * the chest swinging over the hips.
	 */
	private static void groove(ModelBiped m, Rig rig, float t) {
		float hz = 1.8f;
		float bounce = pulse(t, hz);
		float pump = wave(t, hz / 2);
		set(m.bipedRightArm, -0.35f - Math.max(0, pump) * 0.5f, 0.15f, 0.35f);
		set(m.bipedLeftArm, -0.35f - Math.max(0, -pump) * 0.5f, -0.15f, -0.35f);
		rig.rightElbow = 1.3f + Math.max(0, pump) * 0.5f;
		rig.leftElbow = 1.3f + Math.max(0, -pump) * 0.5f;
		// knees out to the sides
		m.bipedRightLeg.rotateAngleZ = 0.22f;
		m.bipedLeftLeg.rotateAngleZ = -0.22f;
		m.bipedRightLeg.rotateAngleY = 0.35f;
		m.bipedLeftLeg.rotateAngleY = -0.35f;
		rig.drop = 2.2f + bounce * 1.4f;
		rig.lean = pump * 0.14f;
		rig.bow = 0.12f + bounce * 0.08f;
		rig.twist = pump * 0.12f;
		m.bipedHead.rotateAngleX = 0.1f - bounce * 0.15f;
		m.bipedHead.rotateAngleZ = -pump * 0.08f;
	}
}
