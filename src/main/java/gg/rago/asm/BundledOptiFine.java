package gg.rago.asm;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Starts the OptiFine jar that ships inside Rago's jar. Forge extracts a jar listed under
 * {@code ContainedDeps} to {@code mods/1.8.9/}, but in 1.8.9 it only puts it on the classpath and
 * never runs its tweaker, which is how OptiFine hooks into the game. This does what Forge does for
 * an OptiFine jar found directly in the mods folder, and does nothing when the player installed
 * OptiFine themselves or the build has no OptiFine inside.
 */
final class BundledOptiFine {
	private static final Logger LOG = LogManager.getLogger("Rago");

	private BundledOptiFine() {
	}

	@SuppressWarnings("unchecked")
	static void start() {
		try {
			File self = new File(RagoCorePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (!self.isFile()) {
				return; // development run from class folders
			}
			String bundled = containedDep(self);
			if (bundled == null) {
				return;
			}
			File jar = new File(new File(Launch.minecraftHome, "mods"), "1.8.9" + File.separator + bundled);
			if (!jar.isFile()) {
				LOG.warn("OptiFine is bundled but {} was not extracted; starting without it", jar);
				return;
			}
			try (JarFile optifine = new JarFile(jar)) {
				Manifest manifest = optifine.getManifest();
				Attributes main = manifest == null ? null : manifest.getMainAttributes();
				String tweaker = main == null ? null : main.getValue("TweakClass");
				if (tweaker == null) {
					LOG.warn("{} has no TweakClass; starting without OptiFine", jar.getName());
					return;
				}
				List<String> pending = (List<String>) Launch.blackboard.get("TweakClasses");
				if (pending != null && pending.contains(tweaker)) {
					LOG.info("OptiFine is installed separately in the mods folder; using that copy");
					CoreModManager.getIgnoredMods().add(jar.getName());
					return;
				}
				String order = main.getValue("TweakOrder");
				Integer sorting = order == null ? 0 : Integer.valueOf(order.trim());
				Method cascade = CoreModManager.class.getDeclaredMethod("handleCascadingTweak",
					File.class, JarFile.class, String.class, net.minecraft.launchwrapper.LaunchClassLoader.class, Integer.class);
				cascade.setAccessible(true);
				cascade.invoke(null, jar, optifine, tweaker, Launch.classLoader, sorting);
				// the mod scanner would otherwise add it to the classpath a second time
				CoreModManager.getIgnoredMods().add(jar.getName());
				LOG.info("Started bundled {}", jar.getName());
			}
		} catch (Throwable e) {
			LOG.error("Could not start the bundled OptiFine; the game continues without it", e);
		}
	}

	private static String containedDep(File self) throws Exception {
		try (JarFile jar = new JarFile(self)) {
			Manifest manifest = jar.getManifest();
			String deps = manifest == null ? null : manifest.getMainAttributes().getValue("ContainedDeps");
			if (deps == null) {
				return null;
			}
			for (String dep : deps.trim().split(" ")) {
				if (dep.startsWith("OptiFine")) {
					return dep;
				}
			}
			return null;
		}
	}
}
