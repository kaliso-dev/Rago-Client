package gg.rago.modules;

import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/**
 * Changes three sounds: hitting a player, getting hit, and breaking a block. Each can be the game's
 * own, one of Rago's (pops, clicks, a bell, a punch, a crunch, made by tools/sounds.py), a
 * satisfying sound already in 1.8 (the arrow-hit ding, the experience orb, the item pop), or the
 * attack sounds of recent Minecraft versions when they are on your computer ({@link ModernSounds}).
 * A hit counts once the server confirms it, by the hurt sound it sends for the player you hit.
 */
public final class ActionSounds extends Module {
	static final String[] CHOICES = {"vanilla", "pop", "click", "tick", "bubble", "ding", "thud", "crunch",
		"arrow_ding", "orb", "item_pop", "modern_crit", "modern_strong", "modern_knockback", "modern_sweep", "modern_weak",
		"modern_amethyst"};
	/** How long after you attack a player their hurt sound counts as your hit. */
	private static final long HIT_WINDOW_MS = 700;

	private final ChoiceOption hit = addOption(new ChoiceOption("sound_hit", "ding", CHOICES));
	private final IntOption hitVolume = addOption(new IntOption("sound_hit_volume", 70, 0, 100));
	private final IntOption hitPitch = addOption(new IntOption("sound_hit_pitch", 100, 50, 200));
	private final ChoiceOption hurt = addOption(new ChoiceOption("sound_hurt", "vanilla", CHOICES));
	private final IntOption hurtVolume = addOption(new IntOption("sound_hurt_volume", 80, 0, 100));
	private final IntOption hurtPitch = addOption(new IntOption("sound_hurt_pitch", 100, 50, 200));
	private final ChoiceOption breaking = addOption(new ChoiceOption("sound_break", "pop", CHOICES));
	private final IntOption breakVolume = addOption(new IntOption("sound_break_volume", 70, 0, 100));
	private final IntOption breakPitch = addOption(new IntOption("sound_break_pitch", 100, 50, 200));

	private final Random random = new Random();
	private int targetId = -1;
	private long attackedAt;
	private long lastHurt;

	public ActionSounds() {
		super("action_sounds", FeatureClass.VISUAL, false);
		hitVolume.showIf(() -> !hit.is("vanilla"));
		hitPitch.showIf(() -> !hit.is("vanilla"));
		hurtVolume.showIf(() -> !hurt.is("vanilla"));
		hurtPitch.showIf(() -> !hurt.is("vanilla"));
		breakVolume.showIf(() -> !breaking.is("vanilla"));
		breakPitch.showIf(() -> !breaking.is("vanilla"));
		// preview a sound when it is picked in the menu
		hit.onSelect(id -> preview(id, hitVolume, hitPitch));
		hurt.onSelect(id -> preview(id, hurtVolume, hurtPitch));
		breaking.onSelect(id -> preview(id, breakVolume, breakPitch));
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	/** The sound event a choice plays, or null for the game's own (or a recent sound that is missing). */
	static ResourceLocation sound(String choice) {
		switch (choice) {
			case "vanilla":
				return null;
			case "arrow_ding":
				return new ResourceLocation("random.successful_hit");
			case "orb":
				return new ResourceLocation("random.orb");
			case "item_pop":
				return new ResourceLocation("random.pop");
			default:
				if (choice.startsWith("modern_")) {
					String id = choice.substring("modern_".length());
					return ModernSounds.available(id) ? new ResourceLocation(ModernSounds.DOMAIN, id) : null;
				}
				return new ResourceLocation("rago", "action." + choice);
		}
	}

	/** Whether a choice can be played here (recent sounds need a recent version installed). */
	public static boolean playable(String choice) {
		return !choice.startsWith("modern_") || sound(choice) != null;
	}

	private ISound make(String choice, IntOption volume, IntOption pitch, float x, float y, float z, boolean positioned) {
		ResourceLocation res = sound(choice);
		if (res == null) {
			return null;
		}
		float p = pitch.get() / 100f * (0.96f + random.nextFloat() * 0.08f);
		float v = volume.get() / 100f;
		return new Sound(res, v, p, x, y, z, positioned ? ISound.AttenuationType.LINEAR : ISound.AttenuationType.NONE);
	}

	/** A one-shot sound with every field chosen (the game's own constructor for that is private). */
	private static final class Sound extends PositionedSound {
		Sound(ResourceLocation res, float volume, float pitch, float x, float y, float z, ISound.AttenuationType attenuation) {
			super(res);
			this.volume = volume;
			this.pitch = pitch;
			this.xPosF = x;
			this.yPosF = y;
			this.zPosF = z;
			this.attenuationType = attenuation;
		}
	}

	private void preview(String choice, IntOption volume, IntOption pitch) {
		ISound s = make(choice, volume, pitch, 0, 0, 0, false);
		if (s != null) {
			Minecraft.getMinecraft().getSoundHandler().playSound(s);
		}
	}

	@SubscribeEvent
	public void onAttack(AttackEntityEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.entityPlayer == mc.thePlayer && event.target instanceof EntityPlayer) {
			targetId = event.target.getEntityId();
			attackedAt = System.currentTimeMillis();
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public void onSound(PlaySoundEvent event) {
		if (!isEnabled() || event.result == null || event.name == null) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.theWorld == null) {
			return;
		}
		ISound s = event.sound;
		float x = s.getXPosF();
		float y = s.getYPosF();
		float z = s.getZPosF();
		if ("game.player.hurt".equals(event.name)) {
			if (near(mc.thePlayer, x, y, z, 1.0)) {
				if (!hurt.is("vanilla")) {
					// the hurt sound comes twice (the game's own and the server's): play ours once
					long now = System.currentTimeMillis();
					event.result = now - lastHurt < 150 ? null : make(hurt.get(), hurtVolume, hurtPitch, x, y, z, false);
					lastHurt = now;
				}
				return;
			}
			if (!hit.is("vanilla") && System.currentTimeMillis() - attackedAt < HIT_WINDOW_MS) {
				Entity target = mc.theWorld.getEntityByID(targetId);
				if (target != null && near(target, x, y, z, 2.5)) {
					ISound mine = make(hit.get(), hitVolume, hitPitch, x, y, z, false);
					if (mine != null) {
						event.result = mine;
						attackedAt = 0;
					}
				}
			}
			return;
		}
		if (event.name.startsWith("dig.") && !breaking.is("vanilla")) {
			MovingObjectPosition over = mc.objectMouseOver;
			if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
				BlockPos pos = over.getBlockPos();
				if (Math.abs(pos.getX() + 0.5f - x) < 0.01f && Math.abs(pos.getY() + 0.5f - y) < 0.01f && Math.abs(pos.getZ() + 0.5f - z) < 0.01f) {
					ISound mine = make(breaking.get(), breakVolume, breakPitch, x, y, z, true);
					if (mine != null) {
						event.result = mine;
					}
				}
			}
		}
	}

	private static boolean near(Entity e, float x, float y, float z, double within) {
		double dx = e.posX - x;
		double dy = e.posY - y;
		double dz = e.posZ - z;
		return dx * dx + dy * dy + dz * dz < within * within;
	}
}
