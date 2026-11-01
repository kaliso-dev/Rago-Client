package gg.rago.hud;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Bed Wars timers in the tab list, under the server's header. */
class TabTimersTest {
	@Test
	void timersGoFourToALine() {
		assertNull(TabTimers.lines(Collections.<String>emptyList()));
		assertEquals("a", TabTimers.lines(Arrays.asList("a")));
		assertEquals("a   b   c   d\ne", TabTimers.lines(Arrays.asList("a", "b", "c", "d", "e")));
	}

	@Test
	void theHeaderIsMadeAgainOnlyWhenSomethingChanges() {
		TabTimers tab = new TabTimers();
		IChatComponent server = new ChatComponentText("Bed Wars");
		assertSame(server, tab.header(server, null, null), "no timers: the server's header as it is");
		assertNull(tab.header(null, null, null));

		String events = "Diamond II 2:15";
		String gens = "Diamond 23s";
		IChatComponent shown = tab.header(server, events, gens);
		assertEquals("Bed Wars\nDiamond II 2:15\nDiamond 23s", shown.getUnformattedText().replace("§r", ""));
		assertSame(shown, tab.header(server, events, gens), "the same frame after frame");

		IChatComponent next = tab.header(server, events, "Diamond 22s");
		assertEquals("Bed Wars\nDiamond II 2:15\nDiamond 22s", next.getUnformattedText().replace("§r", ""));
		assertEquals("Diamond II 2:15", tab.header(null, events, null).getUnformattedText(), "no header from the server");
	}
}
