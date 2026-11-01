package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;

/**
 * Hypixel Bed Wars trap alerts: a sound and a chat line when one of your team's traps goes off,
 * and a reminder when your team has no trap while you carry a diamond, the price of the first one
 * (so none at the start of a game, when nobody can buy one). The traps are counted from the chat
 * (purchases and triggers) and the title Hypixel shows when a trap goes off; nothing is sent.
 */
public final class TrapNotifier extends Module {
	private static final int MAX_TRAPS = 3;
	/** A diamond kept a second (not one spent as soon as it is picked up) before the reminder. */
	private static final int KEPT_TICKS = 20;

	private final BooleanOption onTrigger = addOption(new BooleanOption("trap_triggered", true));
	private final BooleanOption onEmpty = addOption(new BooleanOption("trap_empty", true));
	private final IntOption repeat = addOption(new IntOption("trap_repeat", 60, 0, 300));
	private final BooleanOption sound = addOption(new BooleanOption("trap_sound", true));
	/** Also tells your party (/pc) when a trap goes off; off by default, as it sends a chat message. */
	private final BooleanOption party = addOption(new BooleanOption("trap_party", false));

	private World world;
	private int ticks;
	private boolean inGame;
	private boolean bedGone;
	private int traps;
	/** Tick since which the team has had no trap while you carry a diamond (-1: not now), and when the last reminder went. */
	private int canBuySince = -1;
	private int remindedAt = -1;
	private int lastTrigger = -100;
	private String lastTitle = "";

	public TrapNotifier() {
		super("trap_notifier", FeatureClass.INFORMATIONAL, false);
		repeat.showIf(onEmpty::get);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	/** Traps your team has queued, as counted. */
	public int traps() {
		return traps;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			inGame = false;
			bedGone = false;
			traps = 0;
			remindedAt = -1;
			canBuySince = -1;
		}
		if (!inGame && mc.thePlayer != null && BedwarsGame.playing()) {
			// the sidebar shows the teams: a game has started, whatever language Hypixel speaks
			start();
		}
		if (!inGame || mc.thePlayer == null) {
			return;
		}
		char own = BedwarsGame.ownTeam();
		if (own != 0 && Boolean.FALSE.equals(BedwarsGame.teamBeds().get(own))) {
			bedGone = true;
		}
		readTitle();
		if (ticks % 10 != 0) {
			return;
		}
		int diamonds = onEmpty.get() && !bedGone && traps == 0 ? diamonds(mc.thePlayer.inventory.mainInventory) : 0;
		if (diamonds == 0) {
			canBuySince = -1;
			return;
		}
		if (canBuySince < 0) {
			canBuySince = ticks;
		}
		boolean first = remindedAt < 0;
		boolean again = remindedAt >= 0 && repeat.get() > 0 && ticks - remindedAt >= repeat.get() * 20;
		if (ticks - canBuySince >= KEPT_TICKS && (first || again)) {
			remindedAt = ticks;
			alert(mc, I18n.format("rago.trap.none", diamonds), 1.0f);
		}
	}

	/** The diamonds among these stacks (the inventory). */
	static int diamonds(ItemStack[] stacks) {
		int n = 0;
		for (ItemStack stack : stacks) {
			if (stack != null && stack.getItem() == Items.diamond) {
				n += stack.stackSize;
			}
		}
		return n;
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		if (!isEnabled() || event.type == 2) {
			return;
		}
		String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText()).trim();
		if (text.contains(BedwarsGame.START)) {
			if (!inGame) {
				start();
			}
			return;
		}
		if (!inGame) {
			return;
		}
		if (text.startsWith("BED DESTRUCTION") && text.contains("Your Bed")) {
			bedGone = true;
			return;
		}
		if (isTrapPurchase(text)) {
			setTraps(Math.min(MAX_TRAPS, traps + 1));
			return;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		if (lower.contains("has been set off") || lower.contains("trap triggered") || lower.contains("été déclenché")) {
			triggered(text);
		}
	}

	private void start() {
		inGame = true;
		bedGone = false;
		setTraps(0);
		remindedAt = -1;
	}

	/**
	 * "Steve purchased It's a Trap!" (or "Steve a acheté Piège..."): a trap bought for the team; a
	 * player's chat line has a colon after the name and is left out.
	 */
	static boolean isTrapPurchase(String line) {
		String s = line.toLowerCase(Locale.ROOT);
		int verb = -1;
		for (String v : new String[] {" purchased ", " a acheté ", " avez acheté ", " hat gekauft", " compró ", " comprou "}) {
			int i = s.indexOf(v);
			if (i > 0) {
				verb = i;
				break;
			}
		}
		if (verb < 0 || s.substring(0, verb).contains(":")) {
			return false;
		}
		String item = s.substring(verb);
		return item.contains("trap") || item.contains("piège") || item.contains("piege") || item.contains("falle") || item.contains("trampa");
	}

	/** Hypixel shows "TRAP TRIGGERED!" as a title when a trap goes off. */
	private void readTitle() {
		String t = BedwarsGame.title();
		if (!t.equals(lastTitle)) {
			lastTitle = t;
			if (BedwarsGame.isTrapTitle(t)) {
				triggered(null);
			}
		}
	}

	private void triggered(String line) {
		// the chat line and the title come together: count the trap once
		if (ticks - lastTrigger < 40) {
			return;
		}
		lastTrigger = ticks;
		setTraps(Math.max(0, traps - 1));
		Minecraft mc = Minecraft.getMinecraft();
		if (onTrigger.get()) {
			alert(mc, I18n.format("rago.trap.triggered"), 0.5f);
		}
		if (party.get() && mc.thePlayer != null) {
			// plain ASCII, as every server takes it
			mc.thePlayer.sendChatMessage(partyLine(I18n.format("rago.trap.party")));
		}
	}

	/** The line sent to the party: "/pc " and the text without accents or colours. */
	static String partyLine(String text) {
		String line = BedwarsStats.ascii("/pc " + EnumChatFormatting.getTextWithoutFormattingCodes(text));
		return line.length() > BedwarsStats.CHAT_MAX ? line.substring(0, BedwarsStats.CHAT_MAX) : line;
	}

	private void setTraps(int n) {
		if (n > 0) {
			remindedAt = -1;
		}
		traps = n;
	}

	private void alert(Minecraft mc, String line, float pitch) {
		if (mc.thePlayer == null) {
			return;
		}
		mc.thePlayer.addChatMessage(new ChatComponentText("§c§lRago §8» §f" + line));
		if (sound.get()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), pitch));
		}
	}
}
