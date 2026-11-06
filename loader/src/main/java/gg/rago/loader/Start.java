package gg.rago.loader;

import gg.rago.loader.patch.Access;
import gg.rago.loader.patch.CompatPatches;
import gg.rago.loader.patch.PackageAccess;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Starts Minecraft with Forge mods, without Forge:
 * {@code launch --game <renamed game jar> --mod <mod jar>... -- <Minecraft's arguments>}.
 *
 * <p>The libraries (LWJGL, Guava, log4j...) and this loader are on the class path; the game, the
 * mods and the compatibility API are loaded by a {@link GameClassLoader}, which changes the game's
 * classes as they load: first the hooks Forge would have added ({@link CompatPatches}), then the
 * mods' own transformers.
 */
final class Start {
	private static final String MOD = "Lnet/minecraftforge/fml/common/Mod;";

	record Mod(String className, Path source) { }

	record Args(Path game, List<Path> mods, String[] minecraft) {
		static Args parse(String[] args) {
			Path game = null;
			List<Path> mods = new ArrayList<>();
			int i = 1;
			for (; i < args.length && !args[i].equals("--"); i++) {
				switch (args[i]) {
					case "--game" -> game = Path.of(args[++i]);
					case "--mod" -> mods.add(Path.of(args[++i]));
					default -> throw new IllegalArgumentException("unknown option " + args[i]);
				}
			}
			if (game == null) {
				throw new IllegalArgumentException("--game is required");
			}
			String[] minecraft = i < args.length ? Arrays.copyOfRange(args, i + 1, args.length) : new String[0];
			return new Args(game, mods, minecraft);
		}

		String option(String name, String fallback) {
			for (int i = 0; i + 1 < minecraft.length; i++) {
				if (minecraft[i].equals(name)) {
					return minecraft[i + 1];
				}
			}
			return fallback;
		}
	}

	static void run(String[] raw) throws Throwable {
		long t0 = System.nanoTime();
		Args args = Args.parse(raw);
		List<Path> jars = new ArrayList<>(args.mods);
		jars.addAll(folderMods(Path.of(args.option("--gameDir", ".")), args.mods));
		Path gameJar = OptiFine.game(Path.of(args.option("--gameDir", ".")), args.game).orElse(args.game);
		List<URL> urls = new ArrayList<>();
		urls.add(gameJar.toUri().toURL());
		for (Path m : jars) {
			urls.add(m.toUri().toURL());
		}
		// the compatibility API is inside this jar, and must be loaded next to the game
		urls.add(Start.class.getProtectionDomain().getCodeSource().getLocation());
		// so must Realms, which extends the game's classes
		for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
			if (entry.endsWith(".jar") && dependsOnGame(Path.of(entry))) {
				urls.add(Path.of(entry).toUri().toURL());
			}
		}
		GameClassLoader game = new GameClassLoader(urls.toArray(new URL[0]), Start.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(game);

		// what LaunchWrapper would have set, before any mod code runs
		Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", true, game);
		launch.getField("minecraftHome").set(null, new File(args.option("--gameDir", ".")));
		launch.getField("assetsDir").set(null, new File(args.option("--assetsDir", "assets")));

		CompatPatches compat = new CompatPatches(game, line -> log(line));
		game.addTransformer(compat::transform);
		game.addTransformer(new PackageAccess()::transform);
		Access access = new Access();
		game.addTransformer(access::transform);

		// every mod is read before any of its code runs: what they use of the game must be known
		// before the first game class loads
		List<Mod> mods = new ArrayList<>();
		List<String> corePlugins = new ArrayList<>();
		for (Path jar : jars) {
			Found found = scan(jar, access);
			mods.addAll(found.mods);
			if (found.corePlugin != null) {
				corePlugins.add(found.corePlugin);
			}
			if (found.contained != null) {
				log(jar.getFileName() + " bundles " + found.contained + "; those need Forge and are not started");
			}
		}
		for (String plugin : corePlugins) {
			corePlugin(game, plugin);
		}
		Class.forName("gg.rago.loader.compat.Mods", true, game).getMethod("discovered", String[].class, String[].class).invoke(null,
			mods.stream().map(Mod::className).toArray(String[]::new),
			mods.stream().map(m -> m.source.toAbsolutePath().toString()).toArray(String[]::new));
		log(String.format("%d mod(s) found in %d ms: %s; starting Minecraft on Java %s", mods.size(),
			(System.nanoTime() - t0) / 1_000_000, mods.stream().map(Mod::className).toList(), Runtime.version()));

		Method main = Class.forName("net.minecraft.client.main.Main", true, game).getMethod("main", String[].class);
		try {
			main.invoke(null, (Object) args.minecraft);
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/**
	 * The Forge mods of the game folder's {@code mods} and {@code mods/1.8.9} that this loader can
	 * start: jars with an {@code @Mod} class, not already given with {@code --mod} (the Rago jar
	 * Forge loads from there too), and without what only Forge can run (a core plugin, a tweaker or
	 * Mixin configurations).
	 */
	static List<Path> folderMods(Path gameDir, List<Path> given) throws IOException {
		java.util.Set<String> known = new java.util.HashSet<>();
		java.util.Set<String> knownPlugins = new java.util.HashSet<>();
		for (Path jar : given) {
			known.addAll(modClasses(jar));
			try (JarFile file = new JarFile(jar.toFile())) {
				Manifest m = file.getManifest();
				if (m != null && m.getMainAttributes().getValue("FMLCorePlugin") != null) {
					knownPlugins.add(m.getMainAttributes().getValue("FMLCorePlugin"));
				}
			}
		}
		List<Path> out = new ArrayList<>();
		for (Path dir : List.of(gameDir.resolve("mods"), gameDir.resolve("mods").resolve("1.8.9"))) {
			if (!java.nio.file.Files.isDirectory(dir)) {
				continue;
			}
			List<Path> found;
			try (var list = java.nio.file.Files.list(dir)) {
				found = list.filter(f -> f.getFileName().toString().endsWith(".jar")).sorted().toList();
			}
			for (Path jar : found) {
				String name = jar.getFileName().toString();
				if (OptiFine.is(jar)) {
					continue;
				}
				try (JarFile file = new JarFile(jar.toFile())) {
					Manifest manifest = file.getManifest();
					Attributes a = manifest == null ? new Attributes() : manifest.getMainAttributes();
					String forgeOnly = a.getValue("FMLCorePlugin") != null ? "a core plugin"
						: a.getValue("TweakClass") != null ? "a tweaker"
						: a.getValue("MixinConfigs") != null ? "Mixin" : null;
					if (knownPlugins.contains(a.getValue("FMLCorePlugin"))) {
						log(name + " is already loaded");
						continue;
					}
					if (forgeOnly != null) {
						log(name + " needs Forge (" + forgeOnly + "); not started");
						continue;
					}
				} catch (IOException e) {
					log(name + " could not be read: " + e);
					continue;
				}
				List<String> classes = modClasses(jar);
				if (classes.isEmpty()) {
					log(name + " has no @Mod class; not started");
				} else if (classes.stream().anyMatch(known::contains)) {
					log(name + " is already loaded");
				} else {
					known.addAll(classes);
					out.add(jar);
					log("mod from the mods folder: " + name);
				}
			}
		}
		return out;
	}

	private static List<String> modClasses(Path jar) throws IOException {
		List<String> out = new ArrayList<>();
		try (JarFile file = new JarFile(jar.toFile())) {
			Enumeration<JarEntry> entries = file.entries();
			while (entries.hasMoreElements()) {
				JarEntry e = entries.nextElement();
				if (!e.getName().endsWith(".class") || e.getName().startsWith("META-INF/")) {
					continue;
				}
				try (InputStream in = file.getInputStream(e)) {
					String name = modClass(in.readAllBytes());
					if (name != null) {
						out.add(name);
					}
				}
			}
		}
		return out;
	}

	private static boolean dependsOnGame(Path jar) {
		try (JarFile file = new JarFile(jar.toFile())) {
			return file.getEntry("com/mojang/realmsclient/RealmsMainScreen.class") != null;
		} catch (IOException e) {
			return false;
		}
	}

	/** Registers the transformers of a mod's core plugin, called the way FML calls them. */
	private static void corePlugin(GameClassLoader game, String className) throws ReflectiveOperationException {
		Object plugin = Class.forName(className, true, game).getDeclaredConstructor().newInstance();
		String[] transformers = (String[]) plugin.getClass().getMethod("getASMTransformerClass").invoke(plugin);
		for (String name : transformers == null ? new String[0] : transformers) {
			Object t = Class.forName(name, true, game).getDeclaredConstructor().newInstance();
			Method transform = t.getClass().getMethod("transform", String.class, String.class, byte[].class);
			transform.setAccessible(true);
			game.addTransformer((cls, bytes) -> {
				try {
					byte[] out = (byte[]) transform.invoke(t, cls, cls, bytes);
					return out == null ? bytes : out;
				} catch (ReflectiveOperationException e) {
					log(name + " failed on " + cls + ": " + (e.getCause() == null ? e : e.getCause()));
					return bytes;
				}
			});
			log("transformer " + name + " from " + className);
		}
	}

	record Found(List<Mod> mods, String corePlugin, String contained) { }

	/** Finds the {@code @Mod} classes and the core plugin of one jar, and what its code uses of the game. */
	static Found scan(Path jar, Access access) throws IOException {
		List<Mod> mods = new ArrayList<>();
		String corePlugin = null;
		String contained = null;
		try (JarFile file = new JarFile(jar.toFile())) {
			Manifest manifest = file.getManifest();
			if (manifest != null) {
				Attributes a = manifest.getMainAttributes();
				corePlugin = a.getValue("FMLCorePlugin");
				contained = a.getValue("ContainedDeps");
			}
			Enumeration<JarEntry> entries = file.entries();
			while (entries.hasMoreElements()) {
				JarEntry e = entries.nextElement();
				if (!e.getName().endsWith(".class") || e.getName().startsWith("META-INF/")) {
					continue;
				}
				try (InputStream in = file.getInputStream(e)) {
					byte[] bytes = in.readAllBytes();
					access.scan(bytes);
					String name = modClass(bytes);
					if (name != null) {
						mods.add(new Mod(name.replace('/', '.'), jar));
					}
				}
			}
		}
		return new Found(mods, corePlugin, contained);
	}

	/** The class's name when it carries {@code @Mod}, else null. */
	static String modClass(byte[] bytes) {
		String[] hit = new String[1];
		new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
			private String name;

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				this.name = name;
			}

			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				if (descriptor.equals(MOD)) {
					hit[0] = name;
				}
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return hit[0];
	}

	static void log(String line) {
		System.out.println("[RagoLoader] " + line);
	}

	private Start() {
	}
}
