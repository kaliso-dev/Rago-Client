package gg.rago.discord;

import com.google.gson.JsonObject;
import gg.rago.modules.DiscordPresenceAccess;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordClientTest {
	@Test
	void framesAreOpcodeLengthThenJson() {
		byte[] f = DiscordIpc.frame(DiscordIpc.HANDSHAKE, "{\"v\":1}");
		assertEquals(8 + 7, f.length);
		assertArrayEquals(new byte[] {0, 0, 0, 0, 7, 0, 0, 0}, java.util.Arrays.copyOf(f, 8));
		assertEquals("{\"v\":1}", new String(f, 8, 7, StandardCharsets.UTF_8));
		byte[] g = DiscordIpc.frame(DiscordIpc.FRAME, "é");
		assertEquals(1, DiscordIpc.littleEndian(g, 0));
		assertEquals(2, DiscordIpc.littleEndian(g, 4), "the length counts bytes, not characters");
		assertEquals(0x12345678, DiscordIpc.littleEndian(new byte[] {0x78, 0x56, 0x34, 0x12}, 0));
	}

	@Test
	void theActivityCommand() {
		DiscordClient.Activity a = new DiscordClient.Activity("Hypixel · Bed Wars", "In a game", 1_761_900_000L, "Rago Client 1.7.0");
		JsonObject cmd = DiscordClient.parse(DiscordClient.setActivity("4242", a, "n1"));
		assertEquals("SET_ACTIVITY", cmd.get("cmd").getAsString());
		assertEquals("n1", cmd.get("nonce").getAsString());
		JsonObject args = cmd.getAsJsonObject("args");
		assertEquals(4242, args.get("pid").getAsInt());
		JsonObject act = args.getAsJsonObject("activity");
		assertEquals("Hypixel · Bed Wars", act.get("details").getAsString());
		assertEquals("In a game", act.get("state").getAsString());
		assertEquals(1_761_900_000L, act.getAsJsonObject("timestamps").get("start").getAsLong());
		assertEquals("rago", act.getAsJsonObject("assets").get("large_image").getAsString());
		assertEquals("Rago Client 1.7.0", act.getAsJsonObject("assets").get("large_text").getAsString());
	}

	@Test
	void emptyPartsAreLeftOutAndNullClears() {
		JsonObject act = DiscordClient.parse(DiscordClient.setActivity("1", new DiscordClient.Activity("In the menus", null, 0, null), "n"))
			.getAsJsonObject("args").getAsJsonObject("activity");
		assertFalse(act.has("state"));
		assertFalse(act.has("timestamps"));
		assertTrue(DiscordClient.parse(DiscordClient.setActivity("1", null, "n")).getAsJsonObject("args").get("activity").isJsonNull());
	}

	@Test
	void linesFitDiscordsLimits() {
		assertEquals("Ab", DiscordClient.limit("Ab"));
		assertEquals("A ", DiscordClient.limit("A"), "at least two characters");
		String longLine = new String(new char[300]).replace('\0', 'x');
		assertEquals(128, DiscordClient.limit(longLine).length());
	}

	@Test
	void anApplicationIdIsNeeded() {
		assertFalse(new DiscordClient("").configured());
		assertFalse(new DiscordClient("Rago").configured());
		assertTrue(new DiscordClient("1300000000000000001").configured());
	}

	@Test
	void bigNetworksByNameOtherServersStayPrivate() {
		assertEquals("Hypixel", DiscordPresenceAccess.server("mc.hypixel.net", false));
		assertEquals("Hypixel", DiscordPresenceAccess.server("Hypixel.net:25565", false));
		assertEquals("Minemen Club", DiscordPresenceAccess.server("eu.minemen.club", false));
		assertNull(DiscordPresenceAccess.server("my-friends-smp.net", false), "shown as Multiplayer");
		assertEquals("my-friends-smp.net", DiscordPresenceAccess.server("my-friends-smp.net:25565", true));
		assertNull(DiscordPresenceAccess.server("nothypixel.net", false), "only the network itself");
		assertNull(DiscordPresenceAccess.server(null, true));
	}
}
