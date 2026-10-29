package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.config.StringOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * Messages sent on their own, on Hypixel only: GG (and a message of yours) when a game ends, a
 * message when you kill someone, and /tip all now and then. Off by default, as the player asked
 * for these knowingly: each message is an ordinary chat line, and kill messages wait a minimum
 * time between them so the chat is not flooded.
 */
public final class AutoMessages extends Module {
	/** A death line: a name, then words, ending with a full stop or a final kill in capitals; no colon. */
	private static final Pattern DEATH = Pattern.compile("^[A-Za-z0-9_]{1,16} [\\p{Ll}\\p{L} '.,!]+");
	/** Messages wait this long after the last one, and at least this long after the event. */
	private static final int SPACING_TICKS = 25;

	private final BooleanOption gg = addOption(new BooleanOption("auto_gg", true));
	private final StringOption ggText = addOption(new StringOption("auto_gg_text", "gg", 100));
	private final StringOption endText = addOption(new StringOption("auto_end_text", "", 100));
	private final BooleanOption kill = addOption(new BooleanOption("auto_kill", false));
	private final StringOption killText = addOption(new StringOption("auto_kill_text", "", 100));
	private final BooleanOption finalOnly = addOption(new BooleanOption("auto_kill_final_only", true));
	private final IntOption killGap = addOption(new IntOption("auto_kill_gap", 15, 5, 120));
	private final BooleanOption tip = addOption(new BooleanOption("auto_tip", false));
	private final IntOption tipEvery = addOption(new IntOption("auto_tip_every", 15, 5, 60));

	private final Deque<String> queue = new ArrayDeque<>();
	private World world;
	private boolean inGame;
	private boolean ended;
	private String lastTitle = "";
	private int ticks;
	private int nextSend;
	private int lastKill = -100000;
	private int nextTip = 20 * 30;

	public AutoMessages() {
		super("auto_messages", FeatureClass.AUTO_CHAT, false);
		ggText.showIf(gg::get);
		endText.showIf(gg::get);
		killText.showIf(kill::get);
		finalOnly.showIf(kill::get);
		killGap.showIf(kill::get);
		tipEvery.showIf(tip::get);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			inGame = false;
			ended = false;
			queue.clear();
		}
		if (mc.thePlayer == null || !Hypixel.connected()) {
			return;
		}
		if (!inGame && BedwarsGame.playing()) {
			inGame = true;
			ended = false;
		}
		String title = BedwarsGame.title();
		if (!title.equals(lastTitle)) {
			lastTitle = title;
			if (inGame && !ended && (BedwarsGame.isVictory(title) || BedwarsGame.isGameOver(title))) {
				ended = true;
				if (gg.get()) {
					enqueue(ggText.get());
					enqueue(endText.get());
				}
			}
		}
		if (tip.get() && ticks >= nextTip) {
			nextTip = ticks + tipEvery.get() * 60 * 20;
			enqueue("/tip all");
		}
		if (!queue.isEmpty() && ticks >= nextSend && mc.currentScreen == null) {
			nextSend = ticks + SPACING_TICKS;
			mc.thePlayer.sendChatMessage(queue.poll());
		}
	}

	private void enqueue(String text) {
		String t = text == null ? "" : text.trim();
		if (!t.isEmpty() && queue.size() < 4) {
			if (queue.isEmpty()) {
				nextSend = Math.max(nextSend, ticks + SPACING_TICKS);
			}
			queue.add(t);
		}
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || !kill.get() || event.type == 2 || mc.thePlayer == null || !inGame || ended) {
			return;
		}
		String line = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText()).trim();
		String name = mc.thePlayer.getName();
		boolean finalKill = BedwarsGame.isOwnFinalKill(line, name);
		boolean anyKill = finalKill || isOwnKill(line, name);
		if ((finalOnly.get() ? finalKill : anyKill) && ticks - lastKill >= killGap.get() * 20) {
			lastKill = ticks;
			enqueue(killText.get());
		}
	}

	/** "Steve was killed by You.": someone else's death that names you, not a player's chat line. */
	static boolean isOwnKill(String line, String name) {
		if (line.contains(":") || line.startsWith(name + " ") || !DEATH.matcher(line).find() || !line.endsWith(".")) {
			return false;
		}
		int space = line.indexOf(' ');
		return BedwarsGame.mentionsName(line.substring(space + 1), name);
	}
}
