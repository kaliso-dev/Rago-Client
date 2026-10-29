package gg.rago.netdiag;

import gg.rago.core.RagoClient;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Measures the real round trip to the server the player is on, the same way the multiplayer
 * server list does: a separate short connection asks for the server status and times a ping and
 * its pong. Nothing is sent on the game connection. On large networks the ping shown in the tab
 * list is worked out by the server and refreshed only now and then, so it can sit well above
 * the actual network latency; this number is a fresh measurement.
 */
public final class StatusPinger {
	private static final int PROTOCOL_1_8 = 47;
	private static final int TIMEOUT_MS = 3000;

	private final AtomicBoolean running = new AtomicBoolean();
	private final LatencySampler samples = new LatencySampler();
	private volatile InetSocketAddress target;
	private long lastStartNs;

	public LatencySampler samples() {
		return samples;
	}

	/**
	 * Starts a measurement on a background thread when {@code intervalSeconds} have passed since
	 * the last one. {@code address} is the game connection's remote address, or null when not on
	 * a server (which clears the samples).
	 */
	public void tick(InetSocketAddress address, String hostName, int intervalSeconds) {
		if (address == null) {
			if (target != null) {
				target = null;
				samples.reset();
			}
			return;
		}
		if (!address.equals(target)) {
			target = address;
			samples.reset();
			lastStartNs = 0;
		}
		long now = System.nanoTime();
		if (lastStartNs != 0 && now - lastStartNs < intervalSeconds * 1_000_000_000L || !running.compareAndSet(false, true)) {
			return;
		}
		lastStartNs = now;
		Thread t = new Thread(() -> {
			try {
				int ms = measure(address, hostName);
				if (address.equals(target)) {
					samples.add(ms);
				}
			} catch (IOException e) {
				RagoClient.LOG.debug("Status ping to {} failed: {}", address, e.toString());
			} finally {
				running.set(false);
			}
		}, "Rago status ping");
		t.setDaemon(true);
		t.start();
	}

	/** One status exchange; returns the ping/pong round trip in milliseconds. */
	static int measure(InetSocketAddress address, String hostName) throws IOException {
		try (Socket socket = new Socket()) {
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(TIMEOUT_MS);
			socket.connect(address, TIMEOUT_MS);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			DataInputStream in = new DataInputStream(socket.getInputStream());

			ByteArrayOutputStream handshake = new ByteArrayOutputStream();
			DataOutputStream h = new DataOutputStream(handshake);
			writeVarInt(h, 0x00);
			writeVarInt(h, PROTOCOL_1_8);
			byte[] host = hostName.getBytes(StandardCharsets.UTF_8);
			writeVarInt(h, host.length);
			h.write(host);
			h.writeShort(address.getPort());
			writeVarInt(h, 1); // next state: status
			frame(out, handshake.toByteArray());
			frame(out, new byte[] {0x00}); // status request
			out.flush();

			skipPacket(in); // status response (server description), not needed

			ByteArrayOutputStream ping = new ByteArrayOutputStream();
			DataOutputStream p = new DataOutputStream(ping);
			writeVarInt(p, 0x01);
			long payload = System.nanoTime();
			p.writeLong(payload);
			long sent = System.nanoTime();
			frame(out, ping.toByteArray());
			out.flush();
			int length = readVarInt(in);
			int id = readVarInt(in);
			long echoed = in.readLong();
			long received = System.nanoTime();
			if (id != 0x01 || echoed != payload || length != 9) {
				throw new IOException("unexpected pong");
			}
			return (int) Math.max(0, (received - sent) / 1_000_000L);
		}
	}

	private static void frame(OutputStream out, byte[] body) throws IOException {
		DataOutputStream d = new DataOutputStream(out);
		writeVarInt(d, body.length);
		d.write(body);
	}

	private static void skipPacket(DataInputStream in) throws IOException {
		int length = readVarInt(in);
		if (length < 0 || length > 1 << 21) {
			throw new IOException("bad status length " + length);
		}
		in.readFully(new byte[length]);
	}

	static void writeVarInt(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte(value & 0x7F | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;
		for (int i = 0; i < 5; i++) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << (7 * i);
			if ((b & 0x80) == 0) {
				return value;
			}
		}
		throw new IOException("VarInt too long");
	}
}
