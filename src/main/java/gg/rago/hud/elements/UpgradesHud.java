package gg.rago.hud.elements;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.bedwars.GameEvents;
import gg.rago.bedwars.TeamUpgrades;
import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import gg.rago.render.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Locale;

/**
 * Your team's Bed Wars upgrades: Sharpness, Protection, Haste, Feather Falling, the heal pool,
 * the forge and the traps waiting at your base, next one first. Enchantments are read from your
 * gear twice a second; purchases and traps going off come from the chat and titles.
 */
public final class UpgradesHud extends TextHudElement {
	/** A trap going off shows as a title and a chat line: the second one within this many ticks is the same trap. */
	private static final int SAME_TRIGGER_TICKS = 40;

	private final BooleanOption sharpness = addOption(new BooleanOption("upgrades_sharpness", true));
	private final BooleanOption protection = addOption(new BooleanOption("upgrades_protection", true));
	private final BooleanOption haste = addOption(new BooleanOption("upgrades_haste", true));
	private final BooleanOption feather = addOption(new BooleanOption("upgrades_feather", true));
	private final BooleanOption healPool = addOption(new BooleanOption("upgrades_heal_pool", true));
	private final BooleanOption forge = addOption(new BooleanOption("upgrades_forge", true));
	private final BooleanOption traps = addOption(new BooleanOption("upgrades_traps", true));
	private final BooleanOption shortNames = addOption(new BooleanOption("upgrades_short", false));
	private final BooleanOption hideMissing = addOption(new BooleanOption("upgrades_hide_missing", false));

	private final TeamUpgrades team = new TeamUpgrades();
	private final TeamUpgrades sample = new TeamUpgrades();
	private World world;
	private int ticks;
	private boolean inGame;
	private int lastTrigger = -SAME_TRIGGER_TICKS;
	private String lastTitle = "";
	private String text = "";

	public UpgradesHud() {
		super("upgrades_hud", FeatureClass.INFORMATIONAL, false, Anchor.MIDDLE_LEFT, 4, 60);
		sample.seen(1, 2, 0, 1);
		sample.forge = 2;
		sample.chat("Steve purchased It's a Trap!");
		sample.chat("Steve purchased Alarm Trap");
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	public boolean hasContent() {
		return inGame && !text.isEmpty();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			inGame = false;
			team.reset();
			text = "";
		}
		if (mc.thePlayer == null || ticks % 5 != 0) {
			return;
		}
		readTitle();
		if (ticks % 10 != 0) {
			return;
		}
		inGame = BedwarsGame.playing();
		if (!inGame) {
			return;
		}
		readGear(mc.thePlayer);
		String t = build(team);
		if (!t.equals(text)) {
			text = t;
		}
	}

	private void readGear(EntityPlayerSP p) {
		int sharp = 0;
		for (ItemStack s : p.inventory.mainInventory) {
			if (s != null && (s.getItem() instanceof ItemSword || s.getItem() instanceof ItemAxe)) {
				sharp = Math.max(sharp, EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, s));
			}
		}
		int prot = 0;
		for (ItemStack s : p.inventory.armorInventory) {
			if (s != null) {
				prot = Math.max(prot, EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, s));
			}
		}
		ItemStack boots = p.inventory.armorInventory[0];
		int fall = boots == null ? 0 : EnchantmentHelper.getEnchantmentLevel(Enchantment.featherFalling.effectId, boots);
		PotionEffect dig = p.getActivePotionEffect(Potion.digSpeed);
		team.seen(sharp, prot, fall, dig == null ? 0 : dig.getAmplifier() + 1);
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		if (!isEnabled() || event.type == 2 || !inGame) {
			return;
		}
		String line = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText()).trim();
		if (team.chat(line)) {
			text = build(team);
			return;
		}
		String lower = line.toLowerCase(Locale.ROOT);
		if (lower.contains("has been set off") || lower.contains("trap triggered") || lower.contains("été déclenché")) {
			triggered(line);
		}
	}

	/** Hypixel shows "TRAP TRIGGERED!" as a title when one of your traps goes off. */
	private void readTitle() {
		String t = BedwarsGame.title();
		if (!t.equals(lastTitle)) {
			lastTitle = t;
			if (inGame && BedwarsGame.isTrapTitle(t)) {
				triggered(null);
			}
		}
	}

	private void triggered(String line) {
		if (ticks - lastTrigger < SAME_TRIGGER_TICKS) {
			return;
		}
		lastTrigger = ticks;
		team.triggered(line);
		text = build(team);
	}

	private String build(TeamUpgrades u) {
		StringBuilder b = new StringBuilder();
		if (sharpness.get()) {
			level(b, "sharpness", u.sharpness);
		}
		if (protection.get()) {
			level(b, "protection", u.protection);
		}
		if (haste.get()) {
			level(b, "haste", u.haste);
		}
		if (feather.get()) {
			level(b, "feather", u.featherFalling);
		}
		if (healPool.get()) {
			row(b, "heal_pool", u.healPool ? "§a" + yes() : null);
		}
		if (forge.get()) {
			row(b, "forge", u.forge > 0 ? "§a" + I18n.format("rago.upgrades.forge." + u.forge) : null);
		}
		if (traps.get()) {
			List<TeamUpgrades.Trap> queue = u.traps();
			StringBuilder q = new StringBuilder();
			for (TeamUpgrades.Trap trap : queue) {
				q.append(q.length() > 0 ? "§7, §a" : "§a").append(I18n.format("rago.upgrades.trap." + trap.name().toLowerCase(Locale.ROOT)));
			}
			row(b, "traps", queue.isEmpty() ? null : q.toString());
		}
		return b.toString();
	}

	private void level(StringBuilder b, String key, int level) {
		row(b, key, level > 0 ? "§a" + GameEvents.roman(level) : null);
	}

	/** One line: the upgrade's name, then its state, or a red cross when the team lacks it. */
	private void row(StringBuilder b, String key, String state) {
		if (state == null && hideMissing.get()) {
			return;
		}
		if (b.length() > 0) {
			b.append('\n');
		}
		String name = I18n.format("rago.upgrades." + key + (shortNames.get() ? ".short" : ""));
		b.append(name).append(' ').append(state == null ? "§c×" : state);
	}

	/** A tick in Minecraft's font; the other fonts have no tick, so a word instead. */
	private String yes() {
		return font.is(HudText.MINECRAFT) ? "✔" : I18n.format("rago.upgrades.yes");
	}

	@Override
	protected String value(boolean placeholder) {
		return placeholder ? build(sample) : text;
	}
}
