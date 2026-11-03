package gg.rago.loader.remap;

import org.tukaani.xz.LZMAInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The names Rago is compiled against: Minecraft's obfuscated names ("notch", like {@code ave}) to
 * MCP class names and SRG member names ({@code net/minecraft/client/Minecraft},
 * {@code func_71410_x}), the same names Forge gives the game at runtime.
 *
 * <p>The table is read from the Forge jar the launcher downloads from Forge's maven
 * ({@code deobfuscation_data-1.8.9.lzma}); it is not part of Rago.
 */
public final class Srg {
	/** notch class to its new name */
	final Map<String, String> classes = new HashMap<>();
	/** "owner/name" (notch) to the new field name */
	final Map<String, String> fields = new HashMap<>();
	/** "owner/name desc" (notch) to the new method name */
	final Map<String, String> methods = new HashMap<>();

	public static Srg fromForgeJar(Path forgeJar) throws IOException {
		try (ZipFile zip = new ZipFile(forgeJar.toFile())) {
			ZipEntry e = zip.getEntry("deobfuscation_data-1.8.9.lzma");
			if (e == null) {
				throw new IOException("no deobfuscation_data-1.8.9.lzma in " + forgeJar);
			}
			try (InputStream in = new LZMAInputStream(zip.getInputStream(e))) {
				return read(in);
			}
		}
	}

	public static Srg read(InputStream in) throws IOException {
		Srg srg = new Srg();
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		for (String line; (line = r.readLine()) != null; ) {
			String[] p = line.trim().split(" ");
			switch (p[0]) {
				case "CL:" -> srg.classes.put(p[1], p[2]);
				case "FD:" -> srg.fields.put(p[1], simple(p[2]));
				case "MD:" -> srg.methods.put(p[1] + " " + p[2], simple(p[3]));
				default -> { }
			}
		}
		if (srg.classes.isEmpty() || srg.methods.isEmpty()) {
			throw new IOException("not an SRG table");
		}
		return srg;
	}

	/** "net/minecraft/client/Minecraft/func_71410_x" to "func_71410_x" */
	private static String simple(String full) {
		return full.substring(full.lastIndexOf('/') + 1);
	}

	public int size() {
		return classes.size() + fields.size() + methods.size();
	}
}
