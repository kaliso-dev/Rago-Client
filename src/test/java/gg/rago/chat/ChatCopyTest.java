package gg.rago.chat;

import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCopyTest {
	/** Lines are split on "|" here, as the game splits a long message on the chat's width. */
	private static final ToIntFunction<IChatComponent> LINES = c -> c.getUnformattedText().split("\\|", -1).length;

	/** Adds a message the way GuiNewChat.setChatLine does: whole at the front, its lines at the front, newest first. */
	private static void add(List<ChatLine> full, List<ChatLine> drawn, String text, int tick, int id) {
		for (String line : text.split("\\|", -1)) {
			drawn.add(0, new ChatLine(tick, new ChatComponentText(line), id));
		}
		full.add(0, new ChatLine(tick, new ChatComponentText(text), id));
	}

	@Test
	void clicksFindTheLineTheGameFinds() {
		// GUI scale 2, chat scale 100 %, 320 wide, 10 lines shown of 30, 9 high lines, not scrolled
		assertEquals(0, ChatCopy.lineAt(20, 2 * 27, 2, 1f, 320, 10, 30, 9, 0));
		assertEquals(0, ChatCopy.lineAt(20, 2 * (27 + 8), 2, 1f, 320, 10, 30, 9, 0));
		assertEquals(1, ChatCopy.lineAt(20, 2 * (27 + 9), 2, 1f, 320, 10, 30, 9, 0));
		assertEquals(4, ChatCopy.lineAt(20, 2 * (27 + 9), 2, 1f, 320, 10, 30, 9, 3), "scrolled up by 3");
		assertEquals(-1, ChatCopy.lineAt(20, 2 * 20, 2, 1f, 320, 10, 30, 9, 0), "on the field you type in");
		assertEquals(-1, ChatCopy.lineAt(2 * 400, 2 * 30, 2, 1f, 320, 10, 30, 9, 0), "right of the chat");
		assertEquals(-1, ChatCopy.lineAt(20, 2 * (27 + 9 * 10 + 10), 2, 1f, 320, 10, 30, 9, 0), "above the lines shown");
		assertEquals(-1, ChatCopy.lineAt(20, 2 * (27 + 9 * 2 + 2), 2, 1f, 320, 10, 2, 9, 0), "above the only two lines");
		// chat scale 50 %: lines are half as high on screen
		assertEquals(2, ChatCopy.lineAt(20, 2 * 27 + 2 * 10, 2, 0.5f, 320, 10, 30, 9, 0));
	}

	@Test
	void aWrappedMessageIsCopiedWhole() {
		List<ChatLine> full = new ArrayList<>();
		List<ChatLine> drawn = new ArrayList<>();
		add(full, drawn, "first message", 10, 0);
		add(full, drawn, "a long message| that wraps| on three lines", 11, 0);
		add(full, drawn, "last", 12, 0);
		// drawn, newest first: last, " on three lines", " that wraps", "a long message", "first message"
		for (int i = 1; i <= 3; i++) {
			assertEquals("a long message| that wraps| on three lines", ChatCopy.message(full, drawn, i, LINES));
		}
		assertEquals("first message", ChatCopy.message(full, drawn, 4, LINES));
		assertEquals("last", ChatCopy.message(full, drawn, 0, LINES));
	}

	@Test
	void messagesOfTheSameTickAreTold() {
		List<ChatLine> full = new ArrayList<>();
		List<ChatLine> drawn = new ArrayList<>();
		add(full, drawn, "Bed Wars", 5, 0);
		add(full, drawn, "", 5, 0);
		add(full, drawn, "Protect your bed", 5, 0);
		assertEquals("Protect your bed", ChatCopy.message(full, drawn, 0, LINES));
		assertEquals("Bed Wars", ChatCopy.message(full, drawn, 2, LINES));
		assertEquals("", ChatCopy.message(full, drawn, 1, LINES), "a blank line copies nothing");
	}

	@Test
	void outOfStepListsStillFindTheMessage() {
		List<ChatLine> full = new ArrayList<>();
		List<ChatLine> drawn = new ArrayList<>();
		add(full, drawn, "kept", 1, 0);
		add(full, drawn, "stacked| twice", 2, 0x52A60001);
		full.remove(0); // the game drops messages with an id from its history when the chat is refreshed
		add(full, drawn, "newest", 3, 0);
		assertEquals("kept", ChatCopy.message(full, drawn, 3, LINES));
		assertEquals("twice", ChatCopy.message(full, drawn, 1, LINES), "not in the history: the line itself");
		assertEquals("newest", ChatCopy.message(full, drawn, 0, LINES));
	}

	@Test
	void colourCodesStampsAndCountersAreLeftOut() {
		List<ChatLine> full = new ArrayList<>();
		List<ChatLine> drawn = Arrays.asList(new ChatLine(1, new ChatComponentText("§8[13:05] §aGG §7(x3)"), 0));
		full.add(new ChatLine(1, new ChatComponentText("§8[13:05] §aGG §7(x3)"), 0));
		assertEquals("GG", ChatCopy.message(full, drawn, 0, LINES));
		assertEquals("gl hf", ChatCopy.clean("[1:05 PM] gl hf"));
		assertEquals("[VIP] Steve: hi (x2) there", ChatCopy.clean("[VIP] Steve: hi (x2) there"));
	}

	@Test
	void aPastedMessageIsCutOnlyWhenTheFieldIsFull() {
		assertTrue(ChatCopy.fits(57, 0, 100));
		assertTrue(ChatCopy.fits(100, 0, 100));
		assertFalse(ChatCopy.fits(126, 0, 100), "longer than the game's field");
		assertTrue(ChatCopy.fits(126, 0, 256), "a field made longer by another mod");
		assertFalse(ChatCopy.fits(60, 50, 100), "after what was already typed");
	}
}
