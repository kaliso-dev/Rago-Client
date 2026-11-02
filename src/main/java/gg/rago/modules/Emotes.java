package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.EmoteWheelScreen;
import gg.rago.render.BentModel;
import gg.rago.render.BentModel.Bend;
import gg.rago.hooks.Hooks;
import gg.rago.network.NetworkClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
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
 * chest bends at the waist. Through Rago Network the other Rago players see your emote (only which
 * one and since when is sent), and you see theirs ("See other players' emotes"); a player who walks
 * stops dancing on your screen at once. Players without Rago see you standing.
 */
public final class Emotes extends Module {
	/** The dances, as Rago Network's catalogue names them ("dance:..."): the clap is everyone's, the others are won. */
	public static final String[] DANCES = {"clap", "wave", "twerk", "griddy", "moonwalk"};
	private static final float PI = (float) Math.PI;

	private final BooleanOption faceCamera = addOption(new BooleanOption("emote_camera", true));
	private final IntOption speed = addOption(new IntOption("emote_speed", 100, 50, 200));
	private final BooleanOption others = addOption(new BooleanOption("emote_others", true));
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
		RagoNetwork network = Hooks.network;
		if (network != null) {
			network.shareEmote(dance, speed.get());
		}
		if (faceCamera.get()) {
			if (viewBefore < 0) {
				viewBefore = mc.gameSettings.thirdPersonView;
			}
			// the twerk is seen from behind, the others from the front
			mc.gameSettings.thirdPersonView = "twerk".equals(dance) ? 1 : 2;
		}
	}

	public void stop() {
		if (playing != null) {
			RagoNetwork network = Hooks.network;
			if (network != null) {
				network.shareEmote(null, speed.get());
			}
		}
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

	/** Whether {@link #pose} has anything to do: your dance, a Rago player's around, or a pose to take off. */
	public boolean active() {
		if (playing != null || !touched.isEmpty()) {
			return true;
		}
		RagoNetwork network = Hooks.network;
		return others.get() && isEnabled() && network != null && network.anyEmote();
	}

	/** The emote another Rago player plays now, if they stand still; null for none. */
	private NetworkClient.Emote remote(Entity entity) {
		if (!(entity instanceof EntityOtherPlayerMP) || !others.get() || !isEnabled()) {
			return null;
		}
		RagoNetwork network = Hooks.network;
		NetworkClient.Emote e = network == null ? null : network.emoteOf(entity.getUniqueID());
		if (e == null || !known(e.dance)) {
			return null;
		}
		// walking ends it there: no sliding dancer while the stop is on its way
		double dx = entity.posX - entity.prevPosX;
		double dz = entity.posZ - entity.prevPosZ;
		return dx * dx + dz * dz > 0.0025 ? null : e;
	}

	static boolean known(String dance) {
		for (String d : DANCES) {
			if (d.equals(dance)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * ModelBiped.setRotationAngles, at its end: your player's pose while a dance plays. The models are
	 * shared by every player drawn with them, and vanilla leaves a few angles and positions alone each
	 * frame; the ones a dance moves are put back before the model draws anyone else.
	 */
	public void pose(ModelBiped m, Entity entity) {
		boolean own = entity == Minecraft.getMinecraft().thePlayer;
		String dance;
		float elapsed;
		int danceSpeed;
		if (own) {
			dance = playing;
			elapsed = (System.nanoTime() - startedNs) / 1e9f;
			danceSpeed = speed.get();
		} else {
			NetworkClient.Emote e = remote(entity);
			dance = e == null ? null : e.dance;
			elapsed = e == null ? 0 : Math.max(0, System.currentTimeMillis() - e.startedAt) / 1000f;
			danceSpeed = e == null ? 100 : e.speed;
		}
		if (dance == null) {
			float[] base = touched.remove(m);
			if (base != null) {
				restore(m, base);
				bendParts(m, false);
				Glutes.show(m, false, 0, 0);
			}
			return;
		}
		float[] base = touched.get(m);
		if (base == null) {
			base = snapshot(m);
			touched.put(m, base);
		}
		float t = elapsed * danceSpeed / 100f;
		// the dance eases in from the pose the player had
		float w = smooth(Math.min(1, elapsed / BLEND_IN));
		ModelRenderer[] parts = parts(m);
		float[] before = this.before;
		for (int i = 0; i < parts.length; i++) {
			before[i * 3] = parts[i].rotateAngleX;
			before[i * 3 + 1] = parts[i].rotateAngleY;
			before[i * 3 + 2] = parts[i].rotateAngleZ;
			set(parts[i], 0, 0, 0);
		}
		Rig rig = this.rig;
		rig.reset();
		switch (dance) {
			case "twerk":
				twerk(m, rig, t);
				break;
			case "wave":
				wave(m, rig, t);
				break;
			case "griddy":
				griddy(m, rig, t);
				break;
			case "moonwalk":
				moonwalk(m, rig, t);
				break;
			default:
				clap(m, rig, t);
		}
		Glutes.show(m, "twerk".equals(dance), rig.jiggle, rig.sway);
		for (int i = 0; i < parts.length; i++) {
			parts[i].rotateAngleX = lerp(before[i * 3], parts[i].rotateAngleX, w);
			parts[i].rotateAngleY = lerp(before[i * 3 + 1], parts[i].rotateAngleY, w);
			parts[i].rotateAngleZ = lerp(before[i * 3 + 2], parts[i].rotateAngleZ, w);
		}
		apply(m, base, rig, w);
		ModelBiped.copyModelAngles(m.bipedHead, m.bipedHeadwear);
		// the hand drawn in first person stays straight; the whole player bends
		Minecraft mc = Minecraft.getMinecraft();
		bendParts(m, !own || mc.gameSettings.thirdPersonView != 0 || mc.currentScreen != null);
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
		/** Pixels the whole player has gone back (toward +z), for a dance that travels. */
		float back;
		/** A turn of the whole player around the spine, legs and head included, in radians. */
		float spin;
		/** The twerk's glutes: how high they bounce (0 to 1) and their sway (-1 to 1). */
		float jiggle;
		float sway;

		void reset() {
			drop = twist = lean = bow = 0;
			rightElbow = leftElbow = rightElbowWay = leftElbowWay = 0;
			rightKnee = leftKnee = 0;
			back = spin = jiggle = sway = 0;
		}
	}

	// reused for every dancer, one after another on the render thread: no garbage per frame
	private final Rig rig = new Rig();
	private final ModelRenderer[] parts = new ModelRenderer[6];
	private final float[] before = new float[18];
	private final float[] neck = new float[3];
	private final float[] rightShoulder = new float[3];
	private final float[] leftShoulder = new float[3];

	private ModelRenderer[] parts(ModelBiped m) {
		ModelRenderer[] p = parts;
		p[0] = m.bipedHead;
		p[1] = m.bipedBody;
		p[2] = m.bipedRightArm;
		p[3] = m.bipedLeftArm;
		p[4] = m.bipedRightLeg;
		p[5] = m.bipedLeftLeg;
		return p;
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
		float spin = rig.spin * w;
		float twist = rig.twist * w + spin;
		float lean = rig.lean * w;
		float bow = rig.bow * w;
		float back = rig.back * w;
		waist.set((float) Math.hypot(lean, bow), (float) Math.atan2(lean, bow));
		m.bipedBody.setRotationPoint(base[0], base[1] + drop, base[2] + back);
		m.bipedBody.rotateAngleY += twist;
		float sin = MathHelper.sin(twist);
		float cos = MathHelper.cos(twist);
		// the neck and the shoulders where the bent chest puts them, turned with the body
		float[] neck = BentModel.move(m.bipedBody, waist, 0, 0, 0, this.neck);
		m.bipedHead.rotationPointX = base[0] + neck[0] * cos + neck[2] * sin;
		m.bipedHead.rotationPointY = base[1] + drop + neck[1];
		m.bipedHead.rotationPointZ = base[2] + back - neck[0] * sin + neck[2] * cos;
		m.bipedHead.rotateAngleY += (twist - spin) * 0.6f + spin;
		m.bipedHead.rotateAngleZ += lean * 0.5f;
		m.bipedHead.rotateAngleX += bow * 0.5f;
		float[] right = BentModel.move(m.bipedBody, waist, -5, base[5] - base[1], 0, rightShoulder);
		float[] left = BentModel.move(m.bipedBody, waist, 5, base[6] - base[1], 0, leftShoulder);
		m.bipedRightArm.rotationPointX = base[0] + right[0] * cos + right[2] * sin;
		m.bipedRightArm.rotationPointY = base[1] + drop + right[1];
		m.bipedRightArm.rotationPointZ = base[2] + back - right[0] * sin + right[2] * cos;
		m.bipedLeftArm.rotationPointX = base[0] + left[0] * cos + left[2] * sin;
		m.bipedLeftArm.rotationPointY = base[1] + drop + left[1];
		m.bipedLeftArm.rotationPointZ = base[2] + back - left[0] * sin + left[2] * cos;
		turnArm(m.bipedRightArm, twist, lean, bow);
		turnArm(m.bipedLeftArm, twist, lean, bow);
		rightElbow.set(rig.rightElbow * w, rig.rightElbowWay);
		leftElbow.set(rig.leftElbow * w, rig.leftElbowWay);
		// knees: thighs forward, shins back under the hips; the hips sway against the lean
		float thigh = (float) Math.acos(1 - drop / 12);
		sinkLeg(m.bipedRightLeg, thigh, drop, lean);
		sinkLeg(m.bipedLeftLeg, thigh, drop, lean);
		rightKnee.set(2 * thigh + rig.rightKnee * w, PI);
		leftKnee.set(2 * thigh + rig.leftKnee * w, PI);
		m.bipedRightLeg.rotationPointX = base[7];
		m.bipedLeftLeg.rotationPointX = base[8];
		// vanilla sets the legs' z every frame
		m.bipedRightLeg.rotationPointZ += back;
		m.bipedLeftLeg.rotationPointZ += back;
		if (spin != 0) {
			// the hips turn with the player: each leg goes round the spine and turns with it
			spinLeg(m.bipedRightLeg, spin, base[0], base[2] + back);
			spinLeg(m.bipedLeftLeg, spin, base[0], base[2] + back);
		}
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

	private static void spinLeg(ModelRenderer leg, float spin, float cx, float cz) {
		float ss = MathHelper.sin(spin);
		float cs = MathHelper.cos(spin);
		float x = leg.rotationPointX - cx;
		float z = leg.rotationPointZ - cz;
		leg.rotationPointX = cx + x * cs + z * ss;
		leg.rotationPointZ = cz - x * ss + z * cs;
		leg.rotateAngleY += spin;
	}

	private static void turnArm(ModelRenderer arm, float twist, float lean, float bow) {
		arm.rotateAngleY += twist;
		arm.rotateAngleZ += lean;
		arm.rotateAngleX -= bow;
	}

	private static void sinkLeg(ModelRenderer leg, float thigh, float drop, float lean) {
		leg.rotateAngleX -= thigh;
		leg.rotationPointY += drop;
		leg.rotateAngleZ -= lean * 0.4f;
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
	 * Twerk, seen from behind: a wide, low squat, the chest bowed forward with the hands on the knees,
	 * the hips popping up quickly on each beat and settling back (the glutes bouncing with them),
	 * swaying from side to side every few beats.
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
		rig.jiggle = pop;
		rig.sway = sway;
		// the hands on the knees, elbows soft
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

	/**
	 * The Griddy: hunched over, head down watching the feet, the dancer skips from foot to foot,
	 * tapping the heel of the front foot ahead (that leg straight) while the back knee bends, with a
	 * little hop on each tap; the arms, bent hard at the elbows, swing big and fast against the legs
	 * like an exaggerated jog. On the last two taps of every eight the hands come up around the eyes
	 * like goggles and the head comes up.
	 */
	private static void griddy(ModelBiped m, Rig rig, float t) {
		// a tap on each beat, about 104 a minute: a full right-left cycle in 1.15 seconds
		float hz = 0.87f;
		float phase = t * hz;
		// which leg is ahead: 1 the right, -1 the left, snapping quickly from one to the other
		float raw = wave(t, hz);
		float s = Math.signum(raw) * (float) Math.pow(Math.abs(raw), 0.45);
		// the hop: up as the feet change, down on the tap
		float hop = pulse(t, hz * 2);
		float bar = phase % 4f;
		float goggles = bar < 3f ? 0 : smooth((bar - 3f) / 0.15f) * smooth((4f - bar) / 0.15f);
		float step = 1 - goggles * 0.6f;
		// the front leg straight out, heel tapping; the one behind bending under the body
		m.bipedRightLeg.rotateAngleX = (-0.62f * Math.max(0, s) + 0.18f * Math.max(0, -s)) * step;
		m.bipedLeftLeg.rotateAngleX = (-0.62f * Math.max(0, -s) + 0.18f * Math.max(0, s)) * step;
		rig.rightKnee = 0.7f * Math.max(0, -s) * step;
		rig.leftKnee = 0.7f * Math.max(0, s) * step;
		rig.drop = 2.2f - hop * 1.1f;
		// hunched forward over the feet
		rig.bow = lerp(0.42f, 0.2f, goggles) + hop * 0.04f;
		rig.twist = s * 0.22f * (1 - goggles);
		// the arms swing against the legs, far forward and far back, elbows bent hard
		float rx = lerp(1.05f * s - 0.1f, -2.0f, goggles);
		float lx = lerp(-1.05f * s - 0.1f, -2.0f, goggles);
		set(m.bipedRightArm, rx, lerp(0, 0.55f, goggles), lerp(0.22f, 0, goggles));
		set(m.bipedLeftArm, lx, lerp(0, -0.55f, goggles), lerp(-0.22f, 0, goggles));
		rig.rightElbow = lerp(1.45f, 2.25f, goggles);
		rig.leftElbow = lerp(1.45f, 2.25f, goggles);
		// head down, eyes on the feet, bobbing with the hop; up behind the goggles
		m.bipedHead.rotateAngleX = lerp(0.62f + hop * 0.1f, -0.05f, goggles);
		m.bipedHead.rotateAngleY = s * 0.1f * (1 - goggles);
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

	// ---- the moonwalk ------------------------------------------------------------------------------

	/** Seconds per moonwalk step (one slide and one switch of the heels). */
	private static final float MOON_STEP = 0.6f;
	/** Steps back before the spin. */
	private static final int MOON_STEPS = 6;
	/** Pixels the player goes back on each step: how far the flat foot slides past the other. */
	private static final float MOON_STRIDE = 2.6f;
	private static final float MOON_SPIN = 0.55f;
	private static final float MOON_TOE_STAND = 1.1f;
	private static final float MOON_LOOP = MOON_STEPS * MOON_STEP + MOON_SPIN + MOON_TOE_STAND;

	/**
	 * The moonwalk: one foot flat, the other up on its toes. The flat foot slides straight back past
	 * the other, then in one motion its heel rises and the other heel drops, and that one slides back:
	 * never both feet flat, never both up. The foot on its toes stays where it is on the ground while
	 * the player glides back, so the walk looks like walking forward. The knee over the raised heel is
	 * bent, the leg sliding is straight; the head is left behind as the foot slides and catches up on
	 * the switch; the shoulders are a little hunched, the arms loose, swinging with the steps. After
	 * six steps, a spin on the spot and a toe stand, one hand up at the brim of the hat, while the
	 * player glides back to where they started.
	 */
	private static void moonwalk(ModelBiped m, Rig rig, float t) {
		float loopT = t % MOON_LOOP;
		float walk = MOON_STEPS * MOON_STEP;
		float travelled = MOON_STEPS * MOON_STRIDE;
		if (loopT < walk) {
			int n = (int) (loopT / MOON_STEP);
			float u = (loopT - n * MOON_STEP) / MOON_STEP;
			// the slide takes most of the step, the switch of the heels the end of it
			float slide = smooth(Math.min(1, u / 0.78f));
			float sw = smooth(Math.max(0, (u - 0.78f) / 0.22f));
			boolean rightSlides = n % 2 == 0;
			// the body glides back steadily
			rig.back = (n + u) * MOON_STRIDE;
			// relative to the hips: the flat foot goes from half a stride in front to half behind; the
			// planted foot, still on the ground, from half behind to half in front
			float half = MOON_STRIDE / 2;
			float flatZ = lerp(-half, half, slide);
			float toeZ = lerp(half, -half, u);
			// the planted foot is up on its toes until the switch, then flat; the sliding one the other way
			float flatHeel = sw;
			float toeHeel = 1 - sw;
			rig.drop = 0.6f;
			leg(m, rig, true, rightSlides ? flatZ : toeZ, rightSlides ? flatHeel : toeHeel);
			leg(m, rig, false, rightSlides ? toeZ : flatZ, rightSlides ? toeHeel : flatHeel);
			float side = rightSlides ? 1 : -1;
			// the weight over the planted foot, the hips leaning to it
			rig.lean = -side * 0.05f;
			rig.bow = 0.12f;
			// the head left behind as the foot slides, catching up on the switch
			m.bipedHead.rotateAngleX = 0.12f - slide * 0.1f + sw * 0.1f;
			rig.twist = side * 0.06f * (slide - sw);
			m.bipedHead.rotateAngleY = -rig.twist * 0.8f;
			// arms loose, bent a little, swinging with the steps
			float swing = side * (slide - 0.5f) * 0.5f;
			set(m.bipedRightArm, -0.15f - swing, 0, 0.1f);
			set(m.bipedLeftArm, -0.15f + swing, 0, -0.1f);
			rig.rightElbow = 0.75f;
			rig.leftElbow = 0.75f;
			return;
		}
		float after = loopT - walk;
		// back to the start during the spin and the toe stand
		float home = smooth(after / (MOON_SPIN + MOON_TOE_STAND));
		rig.back = travelled * (1 - home);
		if (after < MOON_SPIN) {
			// a full turn on the spot, arms in close
			float k = after / MOON_SPIN;
			rig.spin = smooth(k) * 2 * PI;
			set(m.bipedRightArm, -0.3f, 0, 0.35f);
			set(m.bipedLeftArm, -0.3f, 0, -0.35f);
			rig.rightElbow = 1.4f;
			rig.leftElbow = 1.4f;
			rig.drop = 0.8f - MathHelper.sin(k * PI) * 0.4f;
			// on the ball of the right foot, the left tucked in
			leg(m, rig, true, 0, 1);
			leg(m, rig, false, -0.5f, 0);
			return;
		}
		// the toe stand: up on both toes, knees together, the right hand at the brim of the hat
		float k = smooth(Math.min(1, (after - MOON_SPIN) / 0.2f)) * smooth(Math.min(1, (MOON_LOOP - loopT) / 0.25f));
		leg(m, rig, true, 0, k);
		leg(m, rig, false, 0, k);
		m.bipedRightLeg.rotateAngleZ = -0.06f * k;
		m.bipedLeftLeg.rotateAngleZ = 0.06f * k;
		set(m.bipedRightArm, -2.5f * k, -0.35f * k, 0.25f * k);
		rig.rightElbow = 1.9f * k;
		set(m.bipedLeftArm, 0.1f * k, 0, -0.3f * k);
		rig.leftElbow = 0.4f * k;
		m.bipedHead.rotateAngleX = 0.18f * k;
		m.bipedHead.rotateAngleY = 0.3f * k;
		rig.lean = 0.04f * k;
	}

	/**
	 * Puts one leg's foot {@code z} pixels behind the hip (in front when negative), on the ground,
	 * its heel up by {@code heel} (0 flat, 1 up on the toes: the knee bends to lift it). Works out the
	 * thigh's angle and the knee's bend for a leg of two six pixel halves under hips {@code rig.drop}
	 * lower, as {@link #apply} will sink them.
	 */
	private static void leg(ModelBiped m, Rig rig, boolean right, float z, float heel) {
		float hip = 12 - Math.max(0, Math.min(6, rig.drop));
		// up on the toes: the ankle (the bottom of the model's leg) rises about a pixel and a half
		float y = hip - heel * 1.5f;
		float d = Math.min(11.98f, (float) Math.hypot(z, y));
		float knee = 2 * (float) Math.acos(d / 12);
		float thigh = (float) Math.atan2(z, y) - knee / 2;
		// apply() bends the knees and brings the thighs forward for the drop: take that off again
		float sink = (float) Math.acos(1 - Math.max(0, Math.min(6, rig.drop)) / 12);
		ModelRenderer leg = right ? m.bipedRightLeg : m.bipedLeftLeg;
		leg.rotateAngleX = thigh + sink;
		if (right) {
			rig.rightKnee = knee - 2 * sink;
		} else {
			rig.leftKnee = knee - 2 * sink;
		}
	}

	/**
	 * The twerk's glutes: two rounded pads on the back of the hips, on the player model's legs (so
	 * they take the skin of the trousers there), shown only while the twerk plays and bouncing with
	 * it. Cartoonish, like the rest of the blocky player.
	 */
	static final class Glutes {
		private static final java.util.Map<ModelBiped, ModelRenderer[]> MADE = new java.util.WeakHashMap<>();

		private Glutes() {
		}

		static void show(ModelBiped m, boolean on, float jiggle, float sway) {
			if (!(m instanceof ModelPlayer)) {
				return;
			}
			ModelRenderer[] g = MADE.get(m);
			if (g == null) {
				if (!on) {
					return;
				}
				g = new ModelRenderer[] {pad(m, m.bipedRightLeg, 4, 18, 7, 20), pad(m, m.bipedLeftLeg, 20, 50, 23, 52)};
				MADE.put(m, g);
			}
			for (int i = 0; i < 2; i++) {
				g[i].showModel = on;
				float side = i == 0 ? 1 : -1;
				// up and back on the pop, one side then the other with the sway
				g[i].rotateAngleX = -0.28f * jiggle - 0.06f * side * sway;
				g[i].rotateAngleZ = 0.05f * side * sway;
			}
		}

		/**
		 * One glute on a leg: a box behind the top of the thigh and a smaller one further back, so it
		 * looks round; textured from the back of the leg ({@code u, v}: the boxes' texture corners).
		 */
		private static ModelRenderer pad(ModelBiped m, ModelRenderer leg, int u, int v, int u2, int v2) {
			ModelRenderer pad = new ModelRenderer(m, u, v);
			pad.setTextureSize(m.textureWidth, m.textureHeight);
			pad.setRotationPoint(0, 0.5f, 1.6f);
			pad.addBox(-2, 0, -0.2f, 4, 5, 2, 0.2f);
			ModelRenderer round = new ModelRenderer(m, u2, v2);
			round.setTextureSize(m.textureWidth, m.textureHeight);
			round.addBox(-1.5f, 0.8f, 1.3f, 3, 3, 1, 0.15f);
			pad.addChild(round);
			pad.showModel = false;
			leg.addChild(pad);
			return pad;
		}
	}
}
