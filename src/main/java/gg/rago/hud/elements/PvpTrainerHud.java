package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import gg.rago.render.Paint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/**
 * PvP Trainer: how well you jump reset and sprint reset, told after the fact.
 *
 * <p>Jump reset: when you take knockback on the ground, jumping on the very tick it arrives cuts it
 * down. The knockback's tick is the one whose start finds your motion changed since the end of the
 * last tick (only a packet does that), with your hurt animation starting on it or the next one and
 * someone within 6 blocks (so your own fireball jumps do not count); a jump is the tick that ends with
 * the game's jump cooldown just set (10, set by the jump itself). Each knockback is scored three ticks later: perfect (same tick), early
 * (you were already in the air), late (you pressed jump once in the air) or no jump, drawn as a bar.
 *
 * <p>Sprint reset: a hit landed while sprinting knocks back further, and sprinting stops with it, so
 * each hit counts with or without sprint (W-tap, S-tap). Only hits that land count: the target was not
 * still hurt, and its hurt animation starts within half a second.
 *
 * <p>It only shows what already happened: it never tells you when to jump and never acts for you. A
 * few field reads a tick and one batch of shapes a frame.
 */
public final class PvpTrainerHud extends HudElement {
	/** Results kept: the graph shows the last {@link #BARS}, the percentages count them all. */
	private static final int KEPT = 20;
	private static final int BARS = 12;
	/** Ticks around the knockback that still count as early or late. */
	private static final int WINDOW = 3;
	static final int NO_JUMP = 99;
	private static final long CONFIRM_NS = 500_000_000L;
	/** EntityLivingBase.jumpTicks: 10 at the end of the tick the player jumped (null: not found). */
	private static final Field JUMP_TICKS = jumpTicks();

	private static final int GREEN = 0xFF4ADE80;
	private static final int YELLOW = 0xFFFACC15;
	private static final int ORANGE = 0xFFFB923C;
	private static final int RED = 0xFFF87171;
	private static final int EMPTY = 0x30FFFFFF;
	private static final int PANEL = 0x90000000;

	private static final int PAD = 4;
	private static final int SLOT = 12;
	private static final int BAR_W = 9;
	private static final int GRAPH_H = 18;
	private static final int DOT = 5;
	private static final int W = 2 * PAD + BARS * SLOT - (SLOT - BAR_W);

	private static final int[] SAMPLE_JUMPS = {0, -1, NO_JUMP, 0, 0, 1, 0, -2, 0, 0, NO_JUMP, 0};
	private static final boolean[] SAMPLE_SPRINTS = {true, true, false, true, true, true, false, true, true, false, true, true};

	private final BooleanOption showJump = addOption(new BooleanOption("trainer_jump", true));
	private final BooleanOption showSprint = addOption(new BooleanOption("trainer_sprint", true));

	private final int[] jumps = new int[KEPT];
	private int jumpCount;
	private final boolean[] sprints = new boolean[KEPT];
	private int sprintCount;

	/** The player the tick state belongs to (a new one after a respawn or a new world). */
	private EntityPlayerSP player;
	private long tick;
	private double mx, my, mz;
	private boolean grounded;
	private long lastJump = Long.MIN_VALUE / 2;
	private long lastPress = Long.MIN_VALUE / 2;
	private boolean jumpHeld;
	/** The last tick whose start found the motion changed (a velocity packet), and whether you were on the ground or just jumped then. */
	private long velocityTick = -10;
	private boolean velocityGrounded;
	/** The last tick whose start found your hurt animation starting. */
	private long hurtTick = -10;
	/** The tick of a knockback waiting to be scored, or -1; the last one counted. */
	private long hitTick = -1;
	private long counted = -10;

	private EntityLivingBase target;
	private long targetAt;
	private boolean targetSprint;

	public PvpTrainerHud() {
		super("pvp_trainer", FeatureClass.INFORMATIONAL, false, Anchor.TOP_RIGHT, -4, 112, 0xFFFFFFFF, true);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@Override
	protected void onDisable() {
		player = null;
		hitTick = -1;
		target = null;
	}

	// --- what happened ---

	@SubscribeEvent
	public void onTickStart(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.START || !isEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayerSP p = mc.thePlayer;
		if (p == null || p != player) {
			player = p;
			hitTick = -1;
			velocityTick = hurtTick = counted = -10;
			target = null;
			if (p != null) {
				remember(p);
			}
			return;
		}
		tick++;
		if (!showJump.get()) {
			return;
		}
		if (Math.abs(p.motionX - mx) + Math.abs(p.motionY - my) + Math.abs(p.motionZ - mz) > 0.01) {
			velocityTick = tick;
			velocityGrounded = grounded || tick - lastJump <= WINDOW;
		}
		if (p.hurtTime > 0 && p.hurtTime >= p.maxHurtTime - 1) {
			hurtTick = tick;
		}
		// the velocity and the hurt animation arrive on the same tick, or one tick apart; the server's
		// corrections in the next ticks are the same hit (no new hit lands for 10 ticks)
		if (hitTick < 0 && velocityTick >= tick - 1 && hurtTick >= tick - 1 && velocityTick - counted > 8
			&& velocityGrounded && nearby(mc, p)) {
			hitTick = counted = velocityTick;
		}
	}

	/**
	 * Someone (a player or a mob) within 6 blocks: a hit, not the explosion of your own fireball jump.
	 * Only looked up when knockback arrives.
	 */
	private static boolean nearby(Minecraft mc, EntityPlayerSP p) {
		for (Object o : mc.theWorld.getEntitiesWithinAABBExcludingEntity(p, p.getEntityBoundingBox().expand(6, 4, 6))) {
			if (o instanceof EntityLivingBase && !(o instanceof EntityArmorStand)) {
				return true;
			}
		}
		return false;
	}

	private void remember(EntityPlayerSP p) {
		mx = p.motionX;
		my = p.motionY;
		mz = p.motionZ;
		grounded = p.onGround;
	}

	private static Field jumpTicks() {
		try {
			return ReflectionHelper.findField(EntityLivingBase.class, "jumpTicks", "field_70773_bE");
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Whether the player jumped during this tick (read at its end). */
	private boolean jumped(EntityPlayerSP p, boolean held) {
		if (JUMP_TICKS != null) {
			try {
				return JUMP_TICKS.getInt(p) == 10;
			} catch (IllegalAccessException e) {
				// fall back below
			}
		}
		// jump held, on the ground at the start of the tick and off it now, going up
		return held && grounded && !p.onGround && p.motionY > 0;
	}

	@SubscribeEvent
	public void onAttack(AttackEntityEvent event) {
		EntityPlayerSP p = player;
		if (!isEnabled() || !showSprint.get() || p == null || event.entityPlayer != p
			|| !(event.target instanceof EntityLivingBase)) {
			return;
		}
		EntityLivingBase t = (EntityLivingBase) event.target;
		if (t.hurtTime > 0) {
			// still hurt from the last hit: this one cannot knock back
			return;
		}
		target = t;
		targetAt = System.nanoTime();
		// read before the hit stops sprinting
		targetSprint = p.isSprinting();
	}

	/** End of the tick: scores a knockback once its window is over, confirms a hit, keeps the motion. */
	@Override
	public void tick() {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		if (p == null || p != player) {
			return;
		}
		// the keys as the player's movement read them this tick
		boolean held = Minecraft.getMinecraft().gameSettings.keyBindJump.isKeyDown();
		if (held && !jumpHeld) {
			lastPress = tick;
		}
		jumpHeld = held;
		if (jumped(p, held)) {
			lastJump = tick;
		}
		if (hitTick >= 0 && tick - hitTick >= WINDOW) {
			jumps[jumpCount++ % KEPT] = score(hitTick, lastJump, lastPress);
			hitTick = -1;
		}
		EntityLivingBase t = target;
		if (t != null) {
			if (t.hurtTime > 0 && t.hurtTime >= t.maxHurtTime - 1) {
				sprints[sprintCount++ % KEPT] = targetSprint;
				target = null;
			} else if (System.nanoTime() - targetAt > CONFIRM_NS || t.isDead) {
				target = null;
			}
		}
		remember(p);
	}

	/**
	 * A knockback's score: 0 for a jump on its tick, minus the ticks for a jump before it (you were in
	 * the air), plus the ticks for a press after it (too late to jump), {@link #NO_JUMP} otherwise.
	 */
	static int score(long hit, long jump, long press) {
		long d = jump - hit;
		if (Math.abs(d) <= WINDOW) {
			return (int) d;
		}
		long late = press - hit;
		return late > 0 && late <= WINDOW ? (int) late : NO_JUMP;
	}

	// --- drawing ---

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		return W;
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		int h = 2 * PAD;
		if (showJump.get()) {
			h += 9 + 3 + GRAPH_H + 3 + 9;
		}
		if (showSprint.get()) {
			h += (showJump.get() ? 7 : 0) + 9 + 3 + DOT;
		}
		return h;
	}

	@Override
	public boolean hasContent() {
		return showJump.get() || showSprint.get();
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		boolean sample = placeholder && jumpCount == 0 && sprintCount == 0;
		int[] jumpsShown = sample ? SAMPLE_JUMPS : last(jumps, jumpCount, BARS);
		boolean[] sprintsShown = sample ? SAMPLE_SPRINTS : lastSprints(BARS);
		int h = height(text, placeholder);

		WorldRenderer shapes = Paint.beginShapes();
		if (background.get()) {
			Paint.roundInto(shapes, 0, 0, W, h, 4, PANEL);
		}
		int y = PAD;
		int graphTop = y + 12;
		int dotsTop = 0;
		if (showJump.get()) {
			for (int i = 0; i < BARS; i++) {
				int x = PAD + i * SLOT;
				int k = i - (BARS - jumpsShown.length);
				if (k < 0) {
					Paint.roundInto(shapes, x, graphTop + GRAPH_H - 2, BAR_W, 2, 1, EMPTY);
				} else {
					int bar = barHeight(jumpsShown[k]);
					Paint.roundInto(shapes, x, graphTop + GRAPH_H - bar, BAR_W, bar, 1, color(jumpsShown[k]));
				}
			}
			y = graphTop + GRAPH_H + 3 + 9 + 7;
		}
		if (showSprint.get()) {
			dotsTop = y + 12;
			for (int i = 0; i < BARS; i++) {
				int x = PAD + i * SLOT + (BAR_W - DOT) / 2;
				int k = i - (BARS - sprintsShown.length);
				int c = k < 0 ? EMPTY : sprintsShown[k] ? GREEN : RED;
				Paint.roundInto(shapes, x, dotsTop, DOT, DOT, DOT / 2f, c);
			}
		}
		Paint.endShapes();

		int color = textColor.get();
		if (showJump.get()) {
			drawRow(text, I18n.format("rago.trainer.jump"), percent(jumpsShown, sample), PAD, color);
			String last;
			int lastColor;
			if (jumpsShown.length == 0) {
				last = I18n.format("rago.trainer.waiting");
				lastColor = 0xFFAAAAAA;
			} else {
				int r = jumpsShown[jumpsShown.length - 1];
				last = label(r);
				lastColor = color(r);
			}
			drawText(text, last, PAD, graphTop + GRAPH_H + 3, lastColor);
		}
		if (showSprint.get()) {
			int count = sample ? sprintsShown.length : Math.min(sprintCount, KEPT);
			int good = 0;
			for (int i = 0; i < count; i++) {
				good += (sample ? sprintsShown[i] : sprints[i]) ? 1 : 0;
			}
			drawRow(text, I18n.format("rago.trainer.sprint"), count == 0 ? "-" : Math.round(good * 100f / count) + "%", dotsTop - 12, color);
		}
	}

	private void drawRow(FontRenderer text, String label, String value, int y, int color) {
		drawText(text, label, PAD, y, color);
		drawText(text, value, W - PAD - textWidth(value), y, color);
	}

	/** Perfect jump resets among the kept knockbacks, as "72%", or "-" before the first. */
	private String percent(int[] shown, boolean sample) {
		int[] all = sample ? shown : last(jumps, jumpCount, KEPT);
		if (all.length == 0) {
			return "-";
		}
		int good = 0;
		for (int r : all) {
			good += r == 0 ? 1 : 0;
		}
		return Math.round(good * 100f / all.length) + "%";
	}

	private static int[] last(int[] ring, int count, int n) {
		int k = Math.min(Math.min(count, KEPT), n);
		int[] out = new int[k];
		for (int i = 0; i < k; i++) {
			out[i] = ring[(count - k + i) % KEPT];
		}
		return out;
	}

	private boolean[] lastSprints(int n) {
		int k = Math.min(Math.min(sprintCount, KEPT), n);
		boolean[] out = new boolean[k];
		for (int i = 0; i < k; i++) {
			out[i] = sprints[(sprintCount - k + i) % KEPT];
		}
		return out;
	}

	static int barHeight(int r) {
		if (r == 0) {
			return GRAPH_H;
		}
		if (r == NO_JUMP) {
			return 3;
		}
		return Math.abs(r) == 1 ? 12 : 7;
	}

	static int color(int r) {
		if (r == 0) {
			return GREEN;
		}
		if (r == NO_JUMP) {
			return RED;
		}
		return Math.abs(r) == 1 ? YELLOW : ORANGE;
	}

	private static String label(int r) {
		if (r == 0) {
			return I18n.format("rago.trainer.perfect");
		}
		if (r == NO_JUMP) {
			return I18n.format("rago.trainer.no_jump");
		}
		int n = Math.abs(r);
		return I18n.format((r < 0 ? "rago.trainer.early" : "rago.trainer.late") + (n == 1 ? ".one" : ".many"), n);
	}
}
