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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;

/**
 * Hypixel Bed Wars trap alerts: a sound and a chat line when one of your team's traps goes off,
 * and a reminder when your team has had no trap for a while. The traps are counted from the chat
 * (purchases and triggers) and the title Hypixel shows when a trap goes off; nothing is sent.
 */
public final class TrapNotifier extends Module {
	private static final int MAX_TRAPS = 3;

	private final BooleanOption onTrigger = addOption(new BooleanOption("trap_triggered", true));
	private final BooleanOption onEmpty = addOption(new BooleanOption("trap_empty", true));
	private final IntOption emptyAfter = addOption(new IntOption("trap_empty_after", 30, 5, 180));
	private final IntOption repeat = addOption(new IntOption("trap_repeat", 60, 0, 300));
	private final BooleanOption sound = addOption(new BooleanOption("trap_sound", true));

	private World world;
	private int ticks;
	private boolean inGame;
	private boolean bedGone;
	private int traps;
	/** Tick since which the team has had no trap, and when the last reminder went. */
	private int emptySince;
	private int remindedAt = -1;
	private int lastTrigger = -100;
	private String lastTitle = "";

	public TrapNotifier() {
		super("trap_notifier", FeatureClass.INFORMATIONAL, false);
		emptyAfter.showIf(onEmpty::get);
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
		if (!onEmpty.get() || bedGone || traps > 0 || ticks % 10 != 0) {
			return;
		}
		int since = ticks - emptySince;
		boolean first = remindedAt < 0 && since >= emptyAfter.get() * 20;
		boolean again = remindedAt >= 0 && repeat.get() > 0 && ticks - remindedAt >= repeat.get() * 20;
		if (first || again) {
			remindedAt = ticks;
			alert(mc, I18n.format("rago.trap.none"), 1.0f);
		}
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
		if (onTrigger.get()) {
			alert(Minecraft.getMinecraft(), I18n.format("rago.trap.triggered"), 0.5f);
		}
	}

	private void setTraps(int n) {
		if (n == 0 && traps > 0 || n == 0 && remindedAt < 0) {
			emptySince = ticks;
		}
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
