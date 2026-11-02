package gg.rago.asm;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.ModListHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.CRC32;

/**
 * Starts Phosphor, the rewrite of the game's light engine (Phosphor Legacy Forge, GPL-3.0, by
 * JellySquid and HowardZHY, unmodified), that ships inside Rago's jar. Forge would load a copy left
 * in the mods folder by itself, and then a Phosphor the player also installed would load twice; so
 * Rago keeps its copy in {@code rago/bundled/} in the game folder and starts it itself on every
 * launch, the way Forge starts a jar from the mods folder: its access transformer, its tweaker
 * (Mixin, which applies its changes), and the jar found as a mod. When the player has Phosphor in
 * the mods folder, that copy is used and the bundled one is not started.
 */
final class BundledPhosphor {
	private static final Logger LOG = LogManager.getLogger("Rago");
	/** Where the jar is inside Rago's jar; Forge does not extract it, unlike {@code ContainedDeps}. */
	static final String ENTRY_DIR = "META-INF/rago/bundled/";
	static final String NAME_PREFIX = "Phosphor";
	private static final String MAIN_CLASS = "me/jellysquid/mods/phosphor/core/PhosphorFMLPlugin.class";

	private BundledPhosphor() {
	}

	static void start() {
		try {
			File self = new File(RagoCorePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (!self.isFile()) {
				return; // development run from class folders
			}
			File jar;
			try (JarFile rago = new JarFile(self)) {
				JarEntry entry = bundledEntry(rago);
				if (entry == null) {
					return; // built with -PnoPhosphor
				}
				File installed = installedCopy(new File(Launch.minecraftHome, "mods"));
				if (installed != null) {
					LOG.info("Phosphor is installed separately ({}); using that copy", installed.getName());
					return;
				}
				String name = entry.getName().substring(ENTRY_DIR.length());
				jar = new File(new File(new File(Launch.minecraftHome, "rago"), "bundled"), name);
				copyIfChanged(rago, entry, jar);
			}
			try (JarFile phosphor = new JarFile(jar)) {
				Manifest manifest = phosphor.getManifest();
				Attributes main = manifest == null ? null : manifest.getMainAttributes();
				String tweaker = main == null ? null : main.getValue("TweakClass");
				if (tweaker == null) {
					LOG.warn("{} has no TweakClass; starting without Phosphor", jar.getName());
					return;
				}
				// Forge reads a mods-folder jar's access transformer (FMLAT) before starting its tweaker
				Class.forName("net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer", true, Launch.class.getClassLoader())
					.getMethod("addJar", JarFile.class).invoke(null, phosphor);
				String order = main.getValue("TweakOrder");
				Integer sorting = order == null ? 0 : Integer.valueOf(order.trim());
				Method cascade = CoreModManager.class.getDeclaredMethod("handleCascadingTweak",
					File.class, JarFile.class, String.class, net.minecraft.launchwrapper.LaunchClassLoader.class, Integer.class);
				cascade.setAccessible(true);
				cascade.invoke(null, jar, phosphor, tweaker, Launch.classLoader, sorting);
				// looked through for its @Mod like a jar Forge extracts for a mod
				ModListHelper.additionalMods.put(jar.getName(), jar);
				LOG.info("Started bundled {}", jar.getName());
			}
		} catch (Throwable e) {
			LOG.error("Could not start the bundled Phosphor; the game continues with the vanilla light engine", e);
		}
	}

	/** The Phosphor jar inside Rago's jar, or null when this build has none. */
	static JarEntry bundledEntry(JarFile rago) {
		Enumeration<JarEntry> entries = rago.entries();
		while (entries.hasMoreElements()) {
			JarEntry e = entries.nextElement();
			String n = e.getName();
			if (n.startsWith(ENTRY_DIR + NAME_PREFIX) && n.endsWith(".jar") && n.indexOf('/', ENTRY_DIR.length()) < 0) {
				return e;
			}
		}
		return null;
	}

	/** A Phosphor jar the player put in {@code mods} or {@code mods/1.8.9}, or null. */
	static File installedCopy(File mods) {
		for (File dir : new File[] {mods, new File(mods, "1.8.9")}) {
			File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
			for (File jar : jars == null ? new File[0] : jars) {
				if (hasClass(jar)) {
					return jar;
				}
			}
		}
		return null;
	}

	/** Writes the bundled jar to {@code target} unless the same bytes are already there. */
	static void copyIfChanged(JarFile rago, JarEntry entry, File target) throws IOException {
		if (target.isFile() && target.length() == entry.getSize() && crc(target) == entry.getCrc()) {
			return;
		}
		File dir = target.getParentFile();
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("Could not make " + dir);
		}
		File tmp = File.createTempFile(target.getName(), ".part", dir);
		try {
			try (InputStream in = rago.getInputStream(entry)) {
				Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmp.toPath());
		}
	}

	private static long crc(File f) throws IOException {
		CRC32 crc = new CRC32();
		byte[] buf = new byte[65536];
		try (InputStream in = Files.newInputStream(f.toPath())) {
			for (int n; (n = in.read(buf)) > 0; ) {
				crc.update(buf, 0, n);
			}
		}
		return crc.getValue();
	}

	private static boolean hasClass(File jar) {
		try (JarFile j = new JarFile(jar)) {
			return j.getEntry(MAIN_CLASS) != null;
		} catch (Exception e) {
			return false;
		}
	}
}
