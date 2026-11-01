package gg.rago.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link NetworkClient} against a stand-in for the Rago Network server, with requests run at once. */
class NetworkClientTest {
	private static final UUID SELF = UUID.randomUUID();
	private static final UUID RAGO_PLAYER = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();
	/** Version 2, as Hypixel's NPCs. */
	private static final UUID NPC = UUID.fromString("0f0e0d0c-0b0a-2908-8706-050403020100");
	private static final String CHALLENGE = "3f1c9a0e5b7d2c4a6e8f0b1d3c5a7e9f2b4d6a8c";

	private HttpServer server;
	private String url;
	private final AtomicLong now = new AtomicLong(1_000_000);
	private final List<String> calls = new ArrayList<>();
	private final List<JsonObject> presences = new ArrayList<>();
	private final List<JsonArray> lookups = new ArrayList<>();
	private final List<JsonArray> emotePolls = new ArrayList<>();
	private int startStatus = 200;
	private int presenceStatus = 200;
	private String joined;

	private final NetworkClient.Account account = new NetworkClient.Account() {
		@Override
		public String name() {
			return "qRago";
		}

		@Override
		public UUID id() {
			return SELF;
		}

		@Override
		public void joinServer(String serverId) {
			joined = serverId;
		}
	};

	private final NetworkClient client = new NetworkClient(Runnable::run, now::get, () -> "1.6.2", s -> { });

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/auth/start", x -> {
			JsonObject body = body(x);
			calls.add("start " + body.get("name").getAsString());
			answer(x, startStatus, startStatus == 200 ? "{\"serverId\": \"" + CHALLENGE + "\", \"expiresIn\": 60}" : "{\"error\": \"busy\"}");
		});
		server.createContext("/v1/auth/finish", x -> {
			JsonObject body = body(x);
			calls.add("finish " + body.get("serverId").getAsString());
			boolean ok = NetworkClient.joinId(CHALLENGE).equals(joined) && body.get("serverId").getAsString().equals(CHALLENGE);
			answer(x, ok ? 200 : 401, ok ? "{\"token\": \"t1\", \"uuid\": \"" + SELF.toString().replace("-", "")
				+ "\", \"name\": \"qRago\", \"role\": \"creator\", \"expiresIn\": 86400}" : "{\"error\": \"not_verified\"}");
		});
		server.createContext("/v1/presence", x -> {
			JsonObject body = body(x);
			calls.add("presence " + x.getRequestHeaders().getFirst("Authorization"));
			presences.add(body);
			answer(x, presenceStatus, presenceStatus == 200 ? "{\"online\": 3, \"cosmetics\": " + body.get("cosmetics") + "}"
				: "{\"error\": \"x\"}");
		});
		server.createContext("/v1/lookup", x -> {
			JsonArray ids = body(x).getAsJsonArray("uuids");
			calls.add("lookup " + ids.size());
			lookups.add(ids);
			answer(x, 200, "{\"players\": {\"" + RAGO_PLAYER + "\": {\"role\": \"partner\", \"cosmetics\":"
				+ " {\"cape\": \"rago\", \"cape_neon\": \"blue\", \"hat\": \"crown\"}}}}");
		});
		server.createContext("/v1/emote", x -> {
			JsonObject body = body(x);
			calls.add("emote " + body.get("emote").getAsString() + " " + body.get("speed").getAsInt());
			answer(x, 200, "{\"ok\": true}");
		});
		server.createContext("/v1/emotes", x -> {
			emotePolls.add(body(x).getAsJsonArray("uuids"));
			answer(x, 200, "{\"emotes\": {\"" + RAGO_PLAYER + "\": {\"emote\": \"wave\", \"speed\": 120, \"elapsed\": 2000}}}");
		});
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static JsonObject body(HttpExchange x) throws IOException {
		try (InputStream in = x.getRequestBody(); Scanner s = new Scanner(in, "UTF-8")) {
			s.useDelimiter("\\A");
			JsonElement json = new JsonParser().parse(s.hasNext() ? s.next() : "{}");
			return json.getAsJsonObject();
		}
	}

	private static void answer(HttpExchange x, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		x.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = x.getResponseBody()) {
			out.write(bytes);
		}
	}

	private void tick(int signature, Collection<UUID> around) {
		Loadout own = new Loadout("rago", "red", false, "none", "red", Loadout.NO_TINT, 100, "none", "red", Loadout.NO_TINT, 100,
			true, 100, "none", "red", 100, true);
		client.tick(url, account, true, true, signature, () -> own, () -> around);
	}

	private void tick() {
		tick(1, Arrays.asList(SELF, RAGO_PLAYER, OTHER, NPC));
	}

	@Test
	void signsInThenSendsPresenceThenAsksAboutThePlayersAround() {
		tick();
		assertEquals(Arrays.asList("start qRago", "finish " + CHALLENGE), calls);
		assertEquals(NetworkClient.joinId(CHALLENGE), joined, "Mojang is told the hash of the challenge, not the challenge");
		assertTrue(client.signedIn());
		assertEquals("creator", client.role());

		tick();
		assertEquals("presence Bearer t1", calls.get(2));
		JsonObject p = presences.get(0);
		assertTrue(p.get("visible").getAsBoolean());
		assertEquals("1.6.2", p.get("version").getAsString());
		assertEquals("rago", p.getAsJsonObject("cosmetics").get("cape").getAsString());
		assertEquals(3, client.online());

		tick();
		assertEquals("lookup 2", calls.get(3));
		List<String> asked = new ArrayList<>();
		for (JsonElement e : lookups.get(0)) {
			asked.add(e.getAsString());
		}
		assertEquals(Arrays.asList(RAGO_PLAYER.toString(), OTHER.toString()), asked, "not you, not an NPC");
		NetworkClient.Player rago = client.player(RAGO_PLAYER);
		assertEquals("partner", rago.role);
		assertEquals("blue", rago.loadout.capeNeon);
		assertEquals("crown", rago.loadout.hat);
		assertNull(client.player(OTHER));
		assertNull(client.player(SELF));

		// nothing more to ask for half a minute; then the player who is not on Rago, in case they just signed in
		for (int s = 1; s <= 25; s++) {
			now.addAndGet(1000);
			tick();
		}
		assertEquals(1, lookups.size());
		now.addAndGet(6_000);
		tick();
		tick();
		assertEquals(2, lookups.size());
		assertEquals(1, lookups.get(1).size());
	}

	@Test
	void yourEmoteIsSentAndTheRagoPlayersAroundAreAskedForTheirs() {
		tick();
		tick();
		tick();
		assertEquals("partner", client.player(RAGO_PLAYER).role);
		// a second later: only the Rago player, not you, not the others
		now.addAndGet(1000);
		tick();
		assertEquals(1, emotePolls.size());
		assertEquals(1, emotePolls.get(0).size());
		assertEquals(RAGO_PLAYER.toString(), emotePolls.get(0).get(0).getAsString());
		NetworkClient.Emote e = client.emoteOf(RAGO_PLAYER);
		assertEquals("wave", e.dance);
		assertEquals(120, e.speed);
		assertEquals(now.get() - 2000, e.startedAt);
		assertNull(client.emoteOf(OTHER));

		client.emote("dab", 150);
		tick();
		assertEquals("emote dab 150", calls.get(calls.size() - 1));
		client.emote(null, 150);
		tick();
		assertEquals("emote none 150", calls.get(calls.size() - 1));

		// asked again a second later, not before
		tick();
		assertEquals(1, emotePolls.size());
		now.addAndGet(1000);
		tick();
		assertEquals(2, emotePolls.size());
		assertEquals(now.get() - 1000 - 2000, client.emoteOf(RAGO_PLAYER).startedAt, "the same dance keeps its start");
	}

	@Test
	void anEndedSessionSignsInAgain() {
		tick();
		presenceStatus = 401;
		tick();
		assertFalse(client.signedIn());
		presenceStatus = 200;
		tick();
		assertEquals(Arrays.asList("start qRago", "finish " + CHALLENGE, "presence Bearer t1", "start qRago", "finish " + CHALLENGE), calls);
		assertTrue(client.signedIn());
	}

	@Test
	void aFailedSignInWaitsLongerEachTime() {
		startStatus = 503;
		tick();
		now.addAndGet(NetworkClient.FIRST_RETRY - 1);
		tick();
		assertEquals(1, calls.size());
		now.addAndGet(1);
		tick();
		assertEquals(2, calls.size());
		now.addAndGet(2 * NetworkClient.FIRST_RETRY - 1);
		tick();
		assertEquals(2, calls.size());
		now.addAndGet(1);
		tick();
		assertEquals(3, calls.size());
		assertFalse(client.signedIn());
	}

	@Test
	void aChangedLoadoutIsSentWithinTenSeconds() {
		tick();
		tick();
		assertEquals(1, presences.size());
		now.addAndGet(NetworkClient.PRESENCE_SOONEST - 1);
		tick(2, Collections.emptyList());
		assertEquals(1, presences.size());
		now.addAndGet(1);
		tick(2, Collections.emptyList());
		assertEquals(2, presences.size());
	}

	@Test
	void aHundredPlayersAtMostPerLookup() {
		List<UUID> crowd = new ArrayList<>();
		for (int i = 0; i < 250; i++) {
			crowd.add(UUID.randomUUID());
		}
		tick(1, crowd);
		tick(1, crowd);
		tick(1, crowd);
		assertEquals(NetworkClient.BATCH, lookups.get(0).size());
		now.addAndGet(NetworkClient.LOOKUP_GAP);
		tick(1, crowd);
		now.addAndGet(NetworkClient.LOOKUP_GAP);
		tick(1, crowd);
		assertEquals(Arrays.asList(100, 100, 50), Arrays.asList(lookups.get(0).size(), lookups.get(1).size(), lookups.get(2).size()));
	}

	@Test
	void someoneNotOnRagoYetIsAskedAboutAgainSooner() {
		tick();
		tick();
		tick();
		assertEquals(1, lookups.size());
		now.addAndGet(NetworkClient.UNKNOWN_FOR);
		tick();
		assertEquals(2, lookups.size(), "the player who was not on Rago, half a minute later");
		assertEquals(1, lookups.get(1).size());
		assertEquals(OTHER.toString(), lookups.get(1).get(0).getAsString());
		now.addAndGet(NetworkClient.KNOWN_FOR - NetworkClient.UNKNOWN_FOR);
		tick();
		tick();
		assertEquals(3, lookups.size(), "the Rago player again after a minute, for a changed cape");
	}

	@Test
	void onlyHttpsOrThisComputer() {
		assertEquals("https://network.example.com", NetworkClient.normalize(" https://network.example.com/ "));
		assertEquals("http://localhost:8080", NetworkClient.normalize("http://localhost:8080"));
		assertEquals("http://127.0.0.1:8080", NetworkClient.normalize("http://127.0.0.1:8080/"));
		assertEquals("", NetworkClient.normalize("http://network.example.com"));
		assertEquals("", NetworkClient.normalize("https://network.example.com/?a=b"));
		assertEquals("", NetworkClient.normalize("https://user:pw@network.example.com"));
		assertEquals("", NetworkClient.normalize("ftp://network.example.com"));
		assertEquals("", NetworkClient.normalize("network.example.com"));
		assertEquals("", NetworkClient.normalize(""));
	}

	@Test
	void turningItOffForgetsEverything() {
		tick();
		tick();
		tick();
		assertTrue(client.player(RAGO_PLAYER) != null);
		client.tick("", account, true, true, 1, () -> Loadout.NONE, Collections::emptyList);
		assertNull(client.player(RAGO_PLAYER));
		assertFalse(client.signedIn());
		assertEquals(-1, client.online());
	}

	@Test
	void uuidsWithOrWithoutDashes() {
		assertEquals(SELF, NetworkClient.uuid(SELF.toString()));
		assertEquals(SELF, NetworkClient.uuid(SELF.toString().replace("-", "")));
		assertNull(NetworkClient.uuid("not-a-uuid"));
	}

	/** In Minecraft's own form for server ids (two's complement hex); the server's tests check the same values. */
	@Test
	void theJoinIdIsTheChallengesHash() {
		assertEquals("3eb134e9faffe05e70d4f1174927717bece96767", NetworkClient.joinId("abc"));
		assertEquals("-1ed171fca8556eb96ab06194d04b3938f1367609", NetworkClient.joinId(CHALLENGE));
	}
}
