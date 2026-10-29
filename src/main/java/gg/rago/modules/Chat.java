package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Chat improvements: a timestamp in front of each message, repeated messages stacked into one
 * line with a counter, and a longer history to scroll back through. Only received messages are
 * changed; nothing is sent.
 */
public final class Chat extends Module {
	/** Chat line ids Rago uses for stacked messages; vanilla and servers use 0. */
	private static final int ID_BASE = 0x52A60000;
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a");

	private final BooleanOption timestamps = addOption(new BooleanOption("timestamps", true));
	private final IntOption timeFormat = addOption(new IntOption("time_format", 0, 0, 1));
	private final BooleanOption compact = addOption(new BooleanOption("compact_chat", true));
	private final IntOption history = addOption(new IntOption("chat_history", 500, 100, 1000));

	private String lastText;
	private int lastId;
	private int repeats;
	private int nextId;

	public Chat() {
		super("chat", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	public int historyLines() {
		return history.get();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onChat(ClientChatReceivedEvent event) {
		if (!isEnabled() || event.isCanceled() || event.type == 2 || event.message == null) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		GuiNewChat chat = mc.ingameGUI == null ? null : mc.ingameGUI.getChatGUI();
		if (chat == null) {
			return;
		}
		IChatComponent message = event.message;
		String text = message.getUnformattedText();
		boolean stackable = compact.get() && hasWords(text);
		if (!stackable && !timestamps.get()) {
			lastText = null;
			return;
		}
		event.setCanceled(true);
		int id;
		if (stackable && text.equals(lastText)) {
			repeats++;
			id = lastId;
			message = message.createCopy().appendSibling(counter(repeats));
		} else {
			repeats = 1;
			lastText = stackable ? text : null;
			nextId = (nextId + 1) & 0xFFFF;
			id = stackable ? ID_BASE + nextId : 0;
			lastId = id;
		}
		if (timestamps.get()) {
			message = new ChatComponentText("").appendSibling(stamp()).appendSibling(message);
		}
		chat.printChatMessageWithOptionalDeletion(message, id);
	}

	/** Lines without letters or digits (Hypixel's separators, blank lines) are never stacked. */
	static boolean hasWords(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.isLetterOrDigit(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	private static IChatComponent counter(int n) {
		ChatComponentText c = new ChatComponentText(" (x" + n + ")");
		c.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
		return c;
	}

	private IChatComponent stamp() {
		String time = LocalTime.now().format(timeFormat.get() == 1 ? H12 : H24);
		ChatComponentText c = new ChatComponentText("[" + time + "] ");
		c.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY));
		return c;
	}
}
