package gg.rago.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Shift + click on a chat message, with the chat open, copies the whole message (all the lines it
 * wraps to) as plain text: no colour codes, no time stamp and no (x2) counter from Rago's chat. The
 * message also goes in the chat field where the cursor is, as Ctrl + V would put it; nothing is sent
 * until you press Enter. The line under the mouse is found as the game itself finds it for clicks on
 * links.
 */
public final class ChatCopy {
	private static final Logger LOG = LogManager.getLogger("Rago");
	/** Rago's time stamp in front of a message: [13:05] or [1:05 PM]. */
	private static final Pattern STAMP = Pattern.compile("^\\[\\d{1,2}:\\d{2}(?: ?[^\\]]{1,5})?\\] ");
	/** Rago's counter after a repeated message: (x2). */
	private static final Pattern COUNTER = Pattern.compile(" \\(x\\d+\\)$");
	private static final long SHOWN_MS = 1200;

	private final BooleanSupplier on;
	private Field chatLines;
	private Field drawnChatLines;
	private Field scrollPos;
	private Field inputField;
	private boolean unavailable;
	private long copiedAt;
	private boolean cut;
	private int fieldLength;

	public ChatCopy(BooleanSupplier on) {
		this.on = on;
	}

	@SubscribeEvent
	public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
		if (!(event.gui instanceof GuiChat) || unavailable || !on.getAsBoolean()
			|| Mouse.getEventButton() != 0 || !Mouse.getEventButtonState() || !GuiScreen.isShiftKeyDown()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		GuiNewChat chat = mc.ingameGUI == null ? null : mc.ingameGUI.getChatGUI();
		if (chat == null || !fields()) {
			return;
		}
		String text;
		try {
			text = messageAt(mc, chat, Mouse.getEventX(), Mouse.getEventY());
		} catch (ReflectiveOperationException | RuntimeException e) {
			unavailable = true;
			LOG.warn("Copying chat messages is off: the chat could not be read", e);
			return;
		}
		if (text == null || text.isEmpty()) {
			return; // not on a message: the click goes on as usual
		}
		GuiScreen.setClipboardString(text);
		cut = !paste((GuiChat) event.gui, text);
		mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		copiedAt = System.currentTimeMillis();
		event.setCanceled(true);
	}

	/**
	 * Puts the message in the chat field at the cursor (over the selected text, if any), as Ctrl + V
	 * does. The field takes 100 characters in the base game (some mods allow more); false when the
	 * message did not fit whole.
	 */
	private boolean paste(GuiChat gui, String text) {
		try {
			if (inputField == null) {
				inputField = ReflectionHelper.findField(GuiChat.class, "field_146415_a", "inputField");
			}
			GuiTextField field = (GuiTextField) inputField.get(gui);
			String typed = ChatAllowedCharacters.filterAllowedCharacters(text);
			fieldLength = field.getMaxStringLength();
			boolean whole = fits(typed.length(), field.getText().length() - field.getSelectedText().length(), fieldLength);
			field.writeText(typed);
			return whole;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("The copied message could not be put in the chat field", e);
			return true;
		}
	}

	/** Whether {@code pasted} characters fit a field of {@code max} that keeps {@code kept} of its text. */
	static boolean fits(int pasted, int kept, int max) {
		return pasted <= max - kept;
	}

	/**
	 * "Copied and pasted", or that the message was cut, for a moment at the right just above the chat
	 * field, so the end of the pasted text stays in sight.
	 */
	@SubscribeEvent
	public void onDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
		if (copiedAt == 0 || !(event.gui instanceof GuiChat)) {
			return;
		}
		if (System.currentTimeMillis() - copiedAt > SHOWN_MS) {
			copiedAt = 0;
			return;
		}
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		String label = cut ? I18n.format("rago.chat.copied_cut", fieldLength) : I18n.format("rago.chat.copied");
		int w = font.getStringWidth(label);
		int x = event.gui.width - w - 6;
		int y = event.gui.height - 27;
		Gui.drawRect(x - 4, y - 2, x + w + 3, y + 10, 0xE0101010);
		font.drawStringWithShadow(label, x, y, cut ? 0xFFFFAA00 : 0xFF55FF55);
	}

	private boolean fields() {
		if (scrollPos != null) {
			return true;
		}
		try {
			chatLines = ReflectionHelper.findField(GuiNewChat.class, "field_146252_h", "chatLines");
			drawnChatLines = ReflectionHelper.findField(GuiNewChat.class, "field_146253_i", "drawnChatLines");
			scrollPos = ReflectionHelper.findField(GuiNewChat.class, "field_146250_j", "scrollPos");
			return true;
		} catch (RuntimeException e) {
			unavailable = true;
			LOG.warn("Copying chat messages is off: the chat's fields were not found", e);
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private String messageAt(Minecraft mc, GuiNewChat chat, int mouseX, int mouseY) throws ReflectiveOperationException {
		List<ChatLine> drawn = (List<ChatLine>) drawnChatLines.get(chat);
		List<ChatLine> full = (List<ChatLine>) chatLines.get(chat);
		FontRenderer font = mc.fontRendererObj;
		int index = lineAt(mouseX, mouseY, new ScaledResolution(mc).getScaleFactor(), chat.getChatScale(), chat.getChatWidth(),
			chat.getLineCount(), drawn.size(), font.FONT_HEIGHT, scrollPos.getInt(chat));
		if (index < 0) {
			return null;
		}
		int width = (int) Math.floor(chat.getChatWidth() / chat.getChatScale());
		return message(full, drawn, index, c -> GuiUtilRenderComponents.splitText(c, width, font, false, false).size());
	}

	/**
	 * Which drawn line (0 is the newest, at the bottom) the mouse is on, by the same sums as
	 * GuiNewChat.getChatComponent; -1 when it is not on one. Mouse positions are window pixels from
	 * the bottom left.
	 */
	static int lineAt(int mouseX, int mouseY, int scaleFactor, float chatScale, int chatWidth, int lineCount, int drawn,
		int fontHeight, int scroll) {
		int x = (int) Math.floor((mouseX / scaleFactor - 3) / chatScale);
		int y = (int) Math.floor((mouseY / scaleFactor - 27) / chatScale);
		if (x < 0 || y < 0) {
			return -1;
		}
		int shown = Math.min(lineCount, drawn);
		if (x > (int) Math.floor(chatWidth / chatScale) || y >= fontHeight * shown + shown) {
			return -1;
		}
		int index = y / fontHeight + scroll;
		return index < drawn ? index : -1;
	}

	/**
	 * The plain text of the message that drawn line {@code index} belongs to. The game keeps each
	 * message whole (newest first) and its drawn lines apart (newest first too), {@code lines} of
	 * them per message, so counting lines finds the message. When another mod or a chat refresh has
	 * put the two lists out of step, the message of the same tick that contains the line is used,
	 * and failing that the line itself.
	 */
	static String message(List<ChatLine> full, List<ChatLine> drawn, int index, ToIntFunction<IChatComponent> lines) {
		ChatLine line = drawn.get(index);
		String part = plain(line.getChatComponent()).trim();
		int seen = 0;
		for (ChatLine m : full) {
			seen += Math.max(1, lines.applyAsInt(m.getChatComponent()));
			if (index < seen) {
				if (sameTick(m, line) && plain(m.getChatComponent()).contains(part)) {
					return clean(plain(m.getChatComponent()));
				}
				break;
			}
		}
		for (ChatLine m : full) {
			if (sameTick(m, line) && !part.isEmpty() && plain(m.getChatComponent()).contains(part)) {
				return clean(plain(m.getChatComponent()));
			}
		}
		return clean(part);
	}

	private static boolean sameTick(ChatLine a, ChatLine b) {
		return a.getUpdatedCounter() == b.getUpdatedCounter() && a.getChatLineID() == b.getChatLineID();
	}

	private static String plain(IChatComponent c) {
		String s = EnumChatFormatting.getTextWithoutFormattingCodes(c.getUnformattedText());
		return s == null ? "" : s;
	}

	/** Without Rago's time stamp and repeat counter, and without blank space around it. */
	static String clean(String text) {
		String s = STAMP.matcher(text).replaceFirst("");
		s = COUNTER.matcher(s).replaceFirst("");
		return s.trim();
	}
}
