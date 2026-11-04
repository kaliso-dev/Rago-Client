package gg.rago.loader;

import gg.rago.loader.remap.JarRemapper;
import gg.rago.loader.remap.Srg;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Runs the game with the OptiFine jar of the mods folder, which Forge starts with a tweaker this
 * loader has not: OptiFine's own patcher makes its classes from the vanilla jar (the way it does
 * when installed), they replace the vanilla ones, and that jar is renamed like the game. Done once
 * per OptiFine jar and kept next to the renamed game.
 */
final class OptiFine {
	private OptiFine() {
	}

	static boolean is(Path jar) {
		try (JarFile file = new JarFile(jar.toFile())) {
			Manifest m = file.getManifest();
			String tweaker = m == null ? null : m.getMainAttributes().getValue("TweakClass");
			return tweaker != null && tweaker.startsWith("optifine.");
		} catch (IOException e) {
			return false;
		}
	}

	static Optional<Path> find(Path gameDir) throws IOException {
		for (Path dir : List.of(gameDir.resolve("mods"), gameDir.resolve("mods").resolve("1.8.9"))) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> list = Files.list(dir)) {
				Optional<Path> found = list.filter(f -> f.getFileName().toString().endsWith(".jar")).sorted().filter(OptiFine::is).findFirst();
				if (found.isPresent()) {
					return found;
				}
			}
		}
		return Optional.empty();
	}

	/** The renamed game with OptiFine in it, made the first time; empty when it cannot be made. */
	static Optional<Path> game(Path gameDir, Path renamedGame) {
		try {
			Optional<Path> optifine = find(gameDir);
			if (optifine.isEmpty()) {
				return Optional.empty();
			}
			Path of = optifine.get();
			String name = of.getFileName().toString().replaceAll("\\.jar$", "");
			Path out = renamedGame.resolveSibling("1.8.9-" + name + "-" + Files.size(of) + "-renamed.jar");
			if (Files.isRegularFile(out)) {
				Start.log("OptiFine: " + of.getFileName());
				return Optional.of(out);
			}
			Path vanilla = gameDir.resolve("versions").resolve("1.8.9").resolve("1.8.9.jar");
			Path forge = forgeJar(gameDir);
			if (!Files.isRegularFile(vanilla) || forge == null) {
				Start.log(of.getFileName() + ": needs versions/1.8.9/1.8.9.jar and Forge 1.8.9 in libraries; OptiFine not started");
				return Optional.empty();
			}
			long t = System.nanoTime();
			Path work = Files.createTempDirectory("rago-optifine");
			Path patched = work.resolve("optifine-classes.jar");
			java.io.PrintStream console = System.out;
			// the patcher prints a line per file
			System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
			try (URLClassLoader patcher = new URLClassLoader(new URL[] {of.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
				Class.forName("optifine.Patcher", true, patcher).getMethod("main", String[].class)
					.invoke(null, (Object) new String[] {vanilla.toString(), of.toString(), patched.toString()});
			} finally {
				System.setOut(console);
			}
			Path merged = work.resolve("merged.jar");
			merge(patched, vanilla, merged);
			Path tmp = work.resolve("renamed.jar");
			new JarRemapper(Srg.fromForgeJar(forge)).remap(merged, tmp);
			Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
			Files.deleteIfExists(patched);
			Files.deleteIfExists(merged);
			Files.deleteIfExists(work);
			Start.log(String.format("OptiFine: %s, prepared in %d ms", of.getFileName(), (System.nanoTime() - t) / 1_000_000));
			return Optional.of(out);
		} catch (Throwable e) {
			Start.log("OptiFine could not be prepared, the game starts without it: " + e);
			return Optional.empty();
		}
	}

	private static Path forgeJar(Path gameDir) throws IOException {
		Path dir = gameDir.resolve("libraries").resolve("net").resolve("minecraftforge").resolve("forge");
		if (!Files.isDirectory(dir)) {
			return null;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			return walk.filter(p -> {
				String n = p.getFileName().toString();
				return n.startsWith("forge-1.8.9-") && n.endsWith(".jar");
			}).sorted().findFirst().orElse(null);
		}
	}

	/** OptiFine's classes and files, then every vanilla entry OptiFine does not replace. */
	static void merge(Path optifine, Path vanilla, Path out) throws IOException {
		Set<String> seen = new HashSet<>();
		try (JarOutputStream o = new JarOutputStream(Files.newOutputStream(out))) {
			for (Path in : List.of(optifine, vanilla)) {
				try (JarFile file = new JarFile(in.toFile())) {
					Enumeration<JarEntry> entries = file.entries();
					while (entries.hasMoreElements()) {
						JarEntry e = entries.nextElement();
						if (e.isDirectory() || e.getName().startsWith("META-INF/") || !seen.add(e.getName())) {
							continue;
						}
						o.putNextEntry(new JarEntry(e.getName()));
						try (InputStream s = file.getInputStream(e)) {
							s.transferTo(o);
						}
						o.closeEntry();
					}
				}
			}
		}
	}
}
