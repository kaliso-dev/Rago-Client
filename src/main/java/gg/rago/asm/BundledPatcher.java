package gg.rago.asm;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Starts the PolyPatcher jar that ships inside Rago's jar (CC BY-NC-SA 4.0, by Sk1er LLC and
 * Polyfrost, unmodified). Like OptiFine it hooks into the game through a tweaker, which Forge 1.8.9
 * does not run for a jar it extracts from {@code ContainedDeps}; this runs it the way Forge would
 * for a jar in the mods folder. When the player has Patcher or PolyPatcher in their own mods
 * folder, that copy is used and the bundled one is skipped, since two copies cannot run together.
 */
final class BundledPatcher {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final String MAIN_CLASS = "club/sk1er/patcher/Patcher.class";

	private BundledPatcher() {
	}

	@SuppressWarnings("unchecked")
	static void start() {
		try {
			File self = new File(RagoCorePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (!self.isFile()) {
				return;
			}
			String bundled = BundledOptiFine.containedDep(self, "PolyPatcher");
			if (bundled == null) {
				return;
			}
			File mods = new File(Launch.minecraftHome, "mods");
			File jar = new File(new File(mods, "1.8.9"), bundled);
			for (File dir : new File[] {mods, new File(mods, "1.8.9")}) {
				File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar") && !name.equals(bundled));
				for (File other : jars == null ? new File[0] : jars) {
					if (!other.equals(self) && hasClass(other)) {
						CoreModManager.getIgnoredMods().add(bundled);
						LOG.info("Patcher is installed separately ({}); using that copy", other.getName());
						return;
					}
				}
			}
			if (!jar.isFile()) {
				LOG.warn("PolyPatcher is bundled but {} was not extracted; starting without it", jar);
				return;
			}
			try (JarFile patcher = new JarFile(jar)) {
				Manifest manifest = patcher.getManifest();
				Attributes main = manifest == null ? null : manifest.getMainAttributes();
				String tweaker = main == null ? null : main.getValue("TweakClass");
				if (tweaker == null) {
					LOG.warn("{} has no TweakClass; starting without PolyPatcher", jar.getName());
					return;
				}
				disableForgeSplash();
				List<String> pending = (List<String>) Launch.blackboard.get("TweakClasses");
				if (pending != null && pending.contains(tweaker)) {
					// Forge found the extracted jar itself (every launch after the first)
					return;
				}
				// Forge reads a mods-folder jar's access transformer (FMLAT) before starting its
				// tweaker; Patcher needs it, e.g. to reach GlStateManager's texture state
				Class.forName("net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer", true, Launch.class.getClassLoader())
					.getMethod("addJar", JarFile.class).invoke(null, patcher);
				String order = main.getValue("TweakOrder");
				Integer sorting = order == null ? 0 : Integer.valueOf(order.trim());
				Method cascade = CoreModManager.class.getDeclaredMethod("handleCascadingTweak",
					File.class, JarFile.class, String.class, net.minecraft.launchwrapper.LaunchClassLoader.class, Integer.class);
				cascade.setAccessible(true);
				cascade.invoke(null, jar, patcher, tweaker, Launch.classLoader, sorting);
				CoreModManager.getIgnoredMods().add(jar.getName());
				LOG.info("Started bundled {}", jar.getName());
			}
		} catch (Throwable e) {
			LOG.error("Could not start the bundled PolyPatcher; the game continues without it", e);
		}
	}

	/**
	 * Forge's loading screen draws text on its own thread while Patcher's libraries are still
	 * adding class transformers, which can crash the launch; Forge switches the screen off itself
	 * after such a crash, this does it beforehand.
	 */
	private static void disableForgeSplash() {
		File file = new File(new File(Launch.minecraftHome, "config"), "splash.properties");
		Properties props = new Properties();
		if (file.isFile()) {
			try (InputStream in = new FileInputStream(file)) {
				props.load(in);
			} catch (IOException e) {
				LOG.warn("Could not read {}", file, e);
				return;
			}
		}
		if ("false".equals(props.getProperty("enabled"))) {
			return;
		}
		props.setProperty("enabled", "false");
		file.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(file)) {
			props.store(out, "Splash screen properties");
			LOG.info("Turned off Forge's loading screen, which can crash with Patcher");
		} catch (IOException e) {
			LOG.warn("Could not write {}", file, e);
		}
	}

	private static boolean hasClass(File jar) {
		try (JarFile j = new JarFile(jar)) {
			return j.getEntry(MAIN_CLASS) != null;
		} catch (Exception e) {
			return false;
		}
	}
}
