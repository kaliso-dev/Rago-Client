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
				exchange.getResponseHeaders().add("RateLimit-Reset", "299");
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
}
