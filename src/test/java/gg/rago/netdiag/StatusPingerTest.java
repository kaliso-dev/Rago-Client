package gg.rago.netdiag;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusPingerTest {
	@Test
	void varIntRoundTrips() throws IOException {
		for (int v : new int[] {0, 1, 127, 128, 255, 25565, 2097151, Integer.MAX_VALUE, -1}) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			StatusPinger.writeVarInt(new DataOutputStream(bytes), v);
			assertEquals(v, StatusPinger.readVarInt(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
		}
	}

	/** A local server that answers the way a 1.8 server answers the server list. */
	@Test
	void measuresAgainstAStatusServer() throws Exception {
		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = new Thread(() -> {
				try (Socket s = server.accept()) {
					DataInputStream in = new DataInputStream(s.getInputStream());
					DataOutputStream out = new DataOutputStream(s.getOutputStream());
					in.readFully(new byte[StatusPinger.readVarInt(in)]); // handshake
					in.readFully(new byte[StatusPinger.readVarInt(in)]); // status request
					byte[] json = "{\"description\":\"test\"}".getBytes(StandardCharsets.UTF_8);
					ByteArrayOutputStream body = new ByteArrayOutputStream();
					DataOutputStream b = new DataOutputStream(body);
					StatusPinger.writeVarInt(b, 0x00);
					StatusPinger.writeVarInt(b, json.length);
					b.write(json);
					StatusPinger.writeVarInt(out, body.size());
					out.write(body.toByteArray());
					out.flush();
					int length = StatusPinger.readVarInt(in);
					byte[] ping = new byte[length];
					in.readFully(ping);
					StatusPinger.writeVarInt(out, length);
					out.write(ping);
					out.flush();
				} catch (IOException ignored) {
				}
			});
			t.start();
			int ms = StatusPinger.measure(new InetSocketAddress("127.0.0.1", server.getLocalPort()), "localhost");
			t.join(5000);
			assertTrue(ms >= 0 && ms < 2000, "round trip " + ms);
		}
	}
}
