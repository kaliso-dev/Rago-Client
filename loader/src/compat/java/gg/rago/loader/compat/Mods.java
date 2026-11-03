package gg.rago.loader.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Starts the {@code @Mod} classes the loader found, the way FML does for a client: construct,
 * pre-init before the game loads its resources, then init, post-init and load complete before
 * the game shows its first screen.
 */
public final class Mods {
	private static final Logger LOG = LogManager.getLogger("RagoLoader");

	private record Running(Object instance, String modid, String name, String version, File source, List<Method> handlers)
		implements ModContainer {
		@Override
		public String getModId() {
			return modid;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getVersion() {
			return version;
		}
	}

	private static final List<String[]> found = new ArrayList<>();
	private static final List<Running> running = new ArrayList<>();

	/** Called by the loader before the game starts: each entry is {class name, jar path}. */
	public static synchronized void discovered(String[] classes, String[] sources) {
		for (int i = 0; i < classes.length; i++) {
			found.add(new String[] {classes[i], sources[i]});
		}
	}

	static synchronized void preInit(Minecraft mc) {
		Loader loader = Loader.instance();
		loader.rago$state(LoaderState.CONSTRUCTING);
		for (String[] f : found) {
			Running mod = construct(f[0], new File(f[1]));
			running.add(mod);
			loader.rago$add(mod);
		}
		resources(mc);
		File config = new File(mc.field_71412_D, "config");
		if (!config.isDirectory() && !config.mkdirs()) {
			LOG.warn("Could not create {}", config);
		}
		loader.rago$state(LoaderState.PREINITIALIZATION);
		for (Running mod : running) {
			fire(mod, new FMLPreInitializationEvent(config, mod.source, mod.modid));
		}
		LOG.info("{} mod(s) pre-initialized: {}", running.size(), running.stream().map(r -> r.modid + " " + r.version).toList());
	}

	static synchronized void init(Minecraft mc) {
		Loader loader = Loader.instance();
		loader.rago$state(LoaderState.INITIALIZATION);
		running.forEach(m -> fire(m, new FMLInitializationEvent()));
		loader.rago$state(LoaderState.POSTINITIALIZATION);
		running.forEach(m -> fire(m, new FMLPostInitializationEvent()));
		running.forEach(m -> fire(m, new FMLLoadCompleteEvent()));
		loader.rago$state(LoaderState.AVAILABLE);
		// mods add their keys during init: read the player's saved keys for them, as FML does
		mc.field_71474_y.func_74300_a();
		KeyBinding.func_74508_b();
		LOG.info("{} mod(s) running", running.size());
	}

	private static Running construct(String className, File source) {
		try {
			Class<?> cls = Class.forName(className, true, Mods.class.getClassLoader());
			Mod mod = cls.getAnnotation(Mod.class);
			if (mod == null) {
				throw new IllegalStateException(className + " has no @Mod");
			}
			var ctor = cls.getDeclaredConstructor();
			ctor.setAccessible(true);
			Object instance = ctor.newInstance();
			for (Field f : cls.getDeclaredFields()) {
				Mod.Instance in = f.getAnnotation(Mod.Instance.class);
				if (in != null && (in.value().isEmpty() || in.value().equals(mod.modid())) && f.getType().isInstance(instance)) {
					f.setAccessible(true);
					f.set(Modifier.isStatic(f.getModifiers()) ? null : instance, instance);
				}
			}
			List<Method> handlers = new ArrayList<>();
			for (Method m : cls.getDeclaredMethods()) {
				if (m.isAnnotationPresent(Mod.EventHandler.class) && m.getParameterCount() == 1
					&& FMLEvent.class.isAssignableFrom(m.getParameterTypes()[0])) {
					m.setAccessible(true);
					handlers.add(m);
				}
			}
			String name = mod.name().isEmpty() ? mod.modid() : mod.name();
			String version = mod.version();
			if (mod.useMetadata() || version.isEmpty()) {
				JsonObject meta = metadata(source, mod.modid());
				if (meta != null) {
					name = text(meta, "name", name);
					version = text(meta, "version", version);
				}
			}
			return new Running(instance, mod.modid(), name, version, source, handlers);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("Mod " + className + " failed to start", e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Mod " + className + " could not be started", e);
		}
	}

	/** Each mod jar is a resource pack under the game's own, so its textures, sounds and lang files load, as with Forge. */
	@SuppressWarnings("unchecked")
	private static void resources(Minecraft mc) {
		Map<File, String> jars = new LinkedHashMap<>();
		running.forEach(m -> jars.putIfAbsent(m.source, m.name));
		try {
			Field f = Minecraft.class.getDeclaredField("field_110449_ao");
			f.setAccessible(true);
			List<IResourcePack> packs = (List<IResourcePack>) f.get(mc);
			jars.forEach((jar, name) -> packs.add(new ModResourcePack(jar, name)));
		} catch (ReflectiveOperationException e) {
			LOG.error("Mods' resources are not loaded", e);
		}
	}

	/** The mod's entry in its jar's mcmod.info, a list or {"modList": [...]}; null when there is none. */
	private static JsonObject metadata(File jar, String modid) {
		try (ZipFile zip = new ZipFile(jar)) {
			ZipEntry entry = zip.getEntry("mcmod.info");
			if (entry == null) {
				return null;
			}
			try (Reader in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
				JsonElement root = new JsonParser().parse(in);
				JsonArray list = root.isJsonArray() ? root.getAsJsonArray()
					: root.isJsonObject() && root.getAsJsonObject().has("modList") ? root.getAsJsonObject().getAsJsonArray("modList") : null;
				if (list == null) {
					return null;
				}
				for (JsonElement e : list) {
					if (e.isJsonObject() && modid.equals(text(e.getAsJsonObject(), "modid", null))) {
						return e.getAsJsonObject();
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			LOG.warn("Could not read mcmod.info of {}", jar, e);
		}
		return null;
	}

	private static String text(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	/** A mod that fails while loading stops the game, as with Forge: half a mod is worse than none. */
	private static void fire(Running mod, FMLEvent event) {
		for (Method m : mod.handlers) {
			if (!m.getParameterTypes()[0].isInstance(event)) {
				continue;
			}
			try {
				m.invoke(Modifier.isStatic(m.getModifiers()) ? null : mod.instance, event);
			} catch (InvocationTargetException e) {
				throw new IllegalStateException(mod.modid + " failed during " + event.getClass().getSimpleName(), e.getCause());
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(mod.modid + ": " + m + " cannot be called", e);
			}
		}
	}

	private Mods() {
	}
}
