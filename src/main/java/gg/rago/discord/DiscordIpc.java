package gg.rago.discord;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Discord's local connection (the one Rich Presence uses): frames of an opcode and a length, both
 * little-endian 32-bit numbers, then that many bytes of JSON. On Windows the Discord app listens on
 * the named pipes {@code \\?\pipe\discord-ipc-0} to {@code -9}, which Java opens like a file. Other
 * systems use a Unix socket, which the Java 8 Minecraft runs on cannot open: there it is not found.
 */
public final class DiscordIpc implements AutoCloseable {
	public static final int HANDSHAKE = 0;
	public static final int FRAME = 1;
	public static final int CLOSE = 2;
	public static final int PING = 3;
	public static final int PONG = 4;
	/** A frame bigger than this is not Discord's: the connection is dropped rather than read. */
	static final int MAX_FRAME = 64 * 1024;

	private final RandomAccessFile pipe;

	private DiscordIpc(RandomAccessFile pipe) {
		this.pipe = pipe;
	}

	/** The first Discord pipe that opens, or null when Discord is not running (or not on Windows). */
	public static DiscordIpc open() {
		if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
			return null;
		}
		for (int i = 0; i < 10; i++) {
			try {
				return new DiscordIpc(new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw"));
			} catch (IOException | SecurityException e) {
				// not this one: Discord takes the first free number, other apps may hold some
			}
		}
		return null;
	}

	public void send(int opcode, String json) throws IOException {
		pipe.write(frame(opcode, json));
	}

	/** Reads one frame: returns its JSON and puts its opcode in {@code opcode[0]}. */
	public String read(int[] opcode) throws IOException {
		byte[] header = new byte[8];
		pipe.readFully(header);
		opcode[0] = littleEndian(header, 0);
		int length = littleEndian(header, 4);
		if (length < 0 || length > MAX_FRAME) {
			throw new EOFException("not a Discord frame (" + length + " bytes)");
		}
		byte[] body = new byte[length];
		pipe.readFully(body);
		return new String(body, StandardCharsets.UTF_8);
	}

	@Override
	public void close() {
		try {
			pipe.close();
		} catch (IOException e) {
			// closing anyway
		}
	}

	/** One frame as it is written to the pipe. */
	static byte[] frame(int opcode, String json) {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream out = new ByteArrayOutputStream(8 + body.length);
		writeLittleEndian(out, opcode);
		writeLittleEndian(out, body.length);
		out.write(body, 0, body.length);
		return out.toByteArray();
	}

	static int littleEndian(byte[] b, int at) {
		return b[at] & 0xFF | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24;
	}

	private static void writeLittleEndian(ByteArrayOutputStream out, int v) {
		out.write(v);
		out.write(v >>> 8);
		out.write(v >>> 16);
		out.write(v >>> 24);
	}
}
