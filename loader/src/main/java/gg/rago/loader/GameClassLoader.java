package gg.rago.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Manifest;

/**
 * Loads the game, Rago and the loader's Forge-compatible API, and lets transformers change the
 * game's classes before they are defined.
 *
 * <p>The game's packages are loaded here first, never by the parent (where the loader itself and
 * the libraries live): a class of the game must see the game's other classes, the mods and the
 * compatibility API, all in this one loader. So is Realms, a library built on the game's classes.
 * Everything else (Java, LWJGL, Guava, log4j, ASM) comes from the parent as usual.
 */
public final class GameClassLoader extends URLClassLoader {
	static {
		ClassLoader.registerAsParallelCapable();
	}

	/** Changes one class's bytes; {@code name} is the binary name ("net.minecraft.client.Minecraft"). */
	public interface Transformer {
		byte[] transform(String name, byte[] bytes);
	}

	private final List<Transformer> transformers = new CopyOnWriteArrayList<>();

	public GameClassLoader(URL[] urls, ClassLoader parent) {
		super("rago-game", urls, parent);
	}

	public void addTransformer(Transformer t) {
		transformers.add(t);
	}

	static boolean ours(String name) {
		return name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.") || name.startsWith("com.mojang.realmsclient.")
			|| name.startsWith("gg.rago.loader.compat.") || (name.startsWith("gg.rago.") && !name.startsWith("gg.rago.loader."));
	}

	/** Only the game's own classes and the API Forge mods compile against are changed. */
	static boolean transformed(String name) {
		return name.startsWith("net.minecraft.") && !name.startsWith("net.minecraft.launchwrapper.");
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (!ours(name)) {
			return super.loadClass(name, resolve);
		}
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);
			if (c == null) {
				c = findClass(name);
			}
			if (resolve) {
				resolveClass(c);
			}
			return c;
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		URL url = findResource(name.replace('.', '/') + ".class");
		if (url == null) {
			throw new ClassNotFoundException(name);
		}
		byte[] bytes;
		URL jar = null;
		Manifest manifest = null;
		try {
			URLConnection connection = url.openConnection();
			if (connection instanceof JarURLConnection j) {
				jar = j.getJarFileURL();
				manifest = j.getManifest();
			}
			try (InputStream in = connection.getInputStream()) {
				bytes = in.readAllBytes();
			}
		} catch (IOException e) {
			throw new ClassNotFoundException(name, e);
		}
		if (transformed(name)) {
			for (Transformer t : transformers) {
				bytes = t.transform(name, bytes);
			}
		}
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			String pkg = name.substring(0, dot);
			if (getDefinedPackage(pkg) == null) {
				try {
					if (manifest != null) {
						definePackage(pkg, manifest, jar);
					} else {
						definePackage(pkg, null, null, null, null, null, null, null);
					}
				} catch (IllegalArgumentException alreadyDefinedByAnotherThread) {
					// fine
				}
			}
		}
		return defineClass(name, bytes, 0, bytes.length, new CodeSource(jar == null ? url : jar, (CodeSigner[]) null));
	}
}
