package gg.rago.loader;

import gg.rago.loader.remap.JarRemapper;
import gg.rago.loader.remap.Srg;
import gg.rago.loader.tools.ForgeOnly;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Rago's loader. {@code remap <forge jar> <client jar> <out>} renames the game once; {@code forge-only <game> <mod>} lists what a mod needs from Forge. */
public final class Main {
	public static void main(String[] args) throws Exception {
		if (args.length == 4 && args[0].equals("remap")) {
			long t = System.nanoTime();
			Srg srg = Srg.fromForgeJar(Path.of(args[1]));
			new JarRemapper(srg).remap(Path.of(args[2]), Path.of(args[3]));
			System.out.printf("renamed with %d names in %d ms%n", srg.size(), (System.nanoTime() - t) / 1_000_000);
		} else if (args.length == 3 && args[0].equals("forge-only")) {
			Map<String, Set<String>> missing = ForgeOnly.scan(Path.of(args[1]), Path.of(args[2]));
			missing.forEach((cls, refs) -> {
				System.out.println(cls);
				refs.forEach(r -> System.out.println("    " + r));
			});
		} else {
			System.err.println("usage: remap <forge jar> <client jar> <out> | forge-only <game jar> <mod jar>");
			System.exit(2);
		}
	}

	private Main() {
	}
}
