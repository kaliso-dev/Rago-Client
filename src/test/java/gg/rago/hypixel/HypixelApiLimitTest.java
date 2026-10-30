package gg.rago.hypixel;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Against a local stand-in for Hypixel that first says "too many requests". */
class HypixelApiLimitTest {
	private static final String KEY = "12345678-1234-1234-1234-123456789abc";
	private HttpServer server;

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private String serve(AtomicInteger calls, int limited) throws Exception {
		return serve(calls, limited, 299);
	}

	private String serve(AtomicInteger calls, int limited, int window) throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/player", exchange -> {
			int n = calls.incrementAndGet();
			byte[] body;
			if (n <= limited) {
				body = "{\"success\":false,\"cause\":\"Key throttle\",\"throttle\":true}".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("RateLimit-Limit", "300");
				exchange.getResponseHeaders().add("RateLimit-Remaining", "0");
				exchange.getResponseHeaders().add("RateLimit-Reset", "1");
				exchange.sendResponseHeaders(429, body.length);
			} else {
				body = "{\"success\":true,\"player\":{\"networkExp\":22500,\"achievements\":{\"bedwars_level\":312}}}".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("RateLimit-Limit", "300");
				exchange.getResponseHeaders().add("RateLimit-Remaining", "299");
				exchange.getResponseHeaders().add("RateLimit-Reset", String.valueOf(window));
				exchange.sendResponseHeaders(200, body.length);
			}
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/player?uuid=";
	}

	@Test
	void waitsOutTooManyRequestsAndKeepsThePlayerFirst(@TempDir Path dir) throws Exception {
		AtomicInteger calls = new AtomicInteger();
		HypixelApi api = new HypixelApi(dir, serve(calls, 1));
		UUID me = UUID.randomUUID();
		api.setKey(KEY, me);
		long until = System.currentTimeMillis() + 3000;
		while (calls.get() < 1 && System.currentTimeMillis() < until) {
			Thread.sleep(20);
		}
		Thread.sleep(100);
		// "too many requests" still means the key is good: no endless "checking"
		assertEquals(HypixelApi.Status.OK, api.status());
		assertTrue(api.pausedSeconds() > 0);
		assertNull(api.get(me));
		until = System.currentTimeMillis() + 5000;
		HypixelApi.Stats s = null;
		while (s == null && System.currentTimeMillis() < until) {
			Thread.sleep(50);
			s = api.get(me);
		}
		assertNotNull(s, "asked again once Hypixel's window reset");
		assertEquals(312, s.star);
		assertEquals(2, calls.get());
		assertEquals(0, api.pausedSeconds());
	}

	@Test
	void sparePlayersWaitWhileTheKeyIsLow(@TempDir Path dir) throws Exception {
		AtomicInteger calls = new AtomicInteger();
		HypixelApi api = new HypixelApi(dir, serve(calls, 1));
		api.setKey(KEY, UUID.randomUUID());
		long until = System.currentTimeMillis() + 3000;
		while (calls.get() < 1 && System.currentTimeMillis() < until) {
			Thread.sleep(20);
		}
		Thread.sleep(100);
		UUID lobby = UUID.randomUUID();
		api.get(lobby, true);
		assertTrue(api.debugState().contains("waiting=1"), api.debugState()); // only the first player, not the spare one
	}

	private static void waitFor(AtomicInteger calls, int n) throws InterruptedException {
		long until = System.currentTimeMillis() + 3000;
		while (calls.get() < n && System.currentTimeMillis() < until) {
			Thread.sleep(20);
		}
		Thread.sleep(150);
	}

	@Test
	void aDailyQuotaIsKeptForThePlayersThatMatter(@TempDir Path dir) throws Exception {
		AtomicInteger calls = new AtomicInteger();
		HypixelApi api = new HypixelApi(dir, serve(calls, 0, 66_850));
		api.setKey(KEY, UUID.randomUUID());
		waitFor(calls, 1);
		api.get(UUID.randomUUID(), true); // someone in a big lobby
		Thread.sleep(300);
		assertEquals(1, calls.get(), "no request for a lobby player on a daily quota");
		api.get(UUID.randomUUID(), false); // a player of your game
		waitFor(calls, 2);
		assertEquals(2, calls.get());
	}

	@Test
	void statsSurviveARestartWithoutQuests(@TempDir Path dir) throws Exception {
		AtomicInteger calls = new AtomicInteger();
		String url = serve(calls, 0);
		UUID player = UUID.randomUUID();
		HypixelApi api = new HypixelApi(dir, url);
		api.setKey(KEY, player);
		waitFor(calls, 1);
		assertNotNull(api.get(player));
		HypixelApi again = new HypixelApi(dir, url);
		HypixelApi.Stats s = again.get(player);
		assertNotNull(s, "read back from the disk");
		assertEquals(312, s.star);
		assertTrue(s.saved());
		Thread.sleep(200);
		assertEquals(1, calls.get(), "no new request after the restart");
	}
}
