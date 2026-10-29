package gg.rago.asm;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * In-Game Account Switcher (LGPL-3.0, by The_Fireplace, The_BrainStone and VidTu) ships unmodified
 * inside Rago's jar; Forge extracts it to {@code mods/1.8.9/} and loads it like any mod. If the
 * player also has their own copy in the mods folder, theirs is used and the bundled one is skipped,
 * since Forge refuses to start with the same mod twice.
 */
final class BundledAccountSwitcher {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final String MAIN_CLASS = "the_fireplace/ias/IAS.class";

	private BundledAccountSwitcher() {
	}

	static void start() {
		try {
			File self = new File(RagoCorePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (!self.isFile()) {
				return;
			}
			String bundled = containedDep(self);
			if (bundled == null) {
				return;
			}
			File mods = new File(Launch.minecraftHome, "mods");
			for (File dir : new File[] {mods, new File(mods, "1.8.9")}) {
				File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar") && !name.equals(bundled));
				if (jars == null) {
					continue;
				}
				for (File jar : jars) {
					if (!jar.equals(self) && hasClass(jar)) {
						CoreModManager.getIgnoredMods().add(bundled);
						LOG.info("In-Game Account Switcher is installed separately ({}); using that copy", jar.getName());
						return;
					}
				}
			}
		} catch (Throwable e) {
			LOG.warn("Could not check for a separate In-Game Account Switcher", e);
		}
	}

	private static boolean hasClass(File jar) {
		try (JarFile j = new JarFile(jar)) {
			return j.getEntry(MAIN_CLASS) != null;
		} catch (Exception e) {
			return false;
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
				if (dep.startsWith("InGameAccountSwitcher")) {
					return dep;
				}
			}
			return null;
		}
	}
}
