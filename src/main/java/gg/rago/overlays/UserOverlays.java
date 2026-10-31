package gg.rago.overlays;

import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The overlays in the player's resourcepacks folder: small packs that change only a few things
 * (wool, clay, the sky, the sword), told apart from full packs by what their files are. Turning one
 * on puts it above the packs in use, as the game's pack screen would; the change is applied in one
 * resource reload.
 */
public final class UserOverlays {
	/** What an overlay changes. */
	public enum Part {
		WOOL, CLAY, SKY, SWORD, GLASS
	}

	/** Files besides those parts that a pack may have and still count as an overlay. */
	static final int MAX_OTHER = 40;

	/** One overlay found in the folder. */
	public static final class Found {
		public final ResourcePackRepository.Entry entry;
		public final EnumSet<Part> parts;

		Found(ResourcePackRepository.Entry entry, EnumSet<Part> parts) {
			this.entry = entry;
			this.parts = parts;
		}

		public String name() {
			return entry.getResourcePackName();
		}
	}

	private static final class Scan {
		final long modified;
		final long size;
		final EnumSet<Part> parts;
		final boolean overlay;

		Scan(long modified, long size, EnumSet<Part> parts, boolean overlay) {
			this.modified = modified;
			this.size = size;
			this.parts = parts;
			this.overlay = overlay;
		}
	}

	private static final Map<String, Scan> CACHE = new HashMap<>();
	private static Field packFile;

	private UserOverlays() {
	}

	/** One thread for the overlays' slow work (listing packs, painting previews), away from the game's. */
	public static Executor worker() {
		return WORKER;
	}

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Rago overlays");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/**
	 * The overlays among all packs in the folder, by name. The folder is read on the game's thread
	 * (the pack list is the game's); the packs' files are listed on the worker, once per pack version.
	 */
	public static CompletableFuture<List<Found>> scan() {
		ResourcePackRepository repo = Minecraft.getMinecraft().getResourcePackRepository();
		repo.updateRepositoryEntriesAll();
		List<ResourcePackRepository.Entry> entries = new ArrayList<>(repo.getRepositoryEntriesAll());
		List<File> files = new ArrayList<>();
		for (ResourcePackRepository.Entry e : entries) {
			files.add(file(e.getResourcePack()));
		}
		return CompletableFuture.supplyAsync(() -> {
			List<Found> out = new ArrayList<>();
			for (int i = 0; i < entries.size(); i++) {
				File f = files.get(i);
				Scan s = f == null ? null : scan(f);
				if (s != null && s.overlay) {
					out.add(new Found(entries.get(i), s.parts));
				}
			}
			out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
			return out;
		}, WORKER);
	}

	/** Whether the pack is in use. */
	public static boolean active(ResourcePackRepository.Entry e) {
		return Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries().contains(e);
	}

	/**
	 * Turns overlays on (above every pack in use, the last turned on highest) or off, then reloads the
	 * resources once, as the game's pack screen does when it closes.
	 */
	public static void apply(Map<ResourcePackRepository.Entry, Boolean> changes) {
		if (changes.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		ResourcePackRepository repo = mc.getResourcePackRepository();
		// the game's order: the last entry is drawn over the others
		List<ResourcePackRepository.Entry> list = new ArrayList<>(repo.getRepositoryEntries());
		for (Map.Entry<ResourcePackRepository.Entry, Boolean> c : changes.entrySet()) {
			list.remove(c.getKey());
			if (c.getValue()) {
				list.add(c.getKey());
			}
		}
		repo.setRepositories(list);
		mc.gameSettings.resourcePacks.clear();
		mc.gameSettings.incompatibleResourcePacks.clear();
		for (ResourcePackRepository.Entry e : list) {
			mc.gameSettings.resourcePacks.add(e.getResourcePackName());
			if (e.func_183027_f() != 1) {
				mc.gameSettings.incompatibleResourcePacks.add(e.getResourcePackName());
			}
		}
		mc.gameSettings.saveOptions();
		mc.refreshResources();
	}

	private static Scan scan(File f) {
		String key = f.getAbsolutePath();
		long modified = f.lastModified();
		long size = f.isFile() ? f.length() : -1;
		Scan cached;
		synchronized (CACHE) {
			cached = CACHE.get(key);
		}
		if (cached != null && cached.modified == modified && cached.size == size) {
			return cached;
		}
		List<String> paths = new ArrayList<>();
		try {
			if (f.isFile()) {
				try (ZipFile zip = new ZipFile(f)) {
					Enumeration<? extends ZipEntry> en = zip.entries();
					while (en.hasMoreElements() && paths.size() < 5000) {
						ZipEntry z = en.nextElement();
						if (!z.isDirectory()) {
							paths.add(z.getName());
						}
					}
				}
			} else if (f.isDirectory()) {
				Path root = f.toPath();
				try (Stream<Path> walk = Files.walk(root, 12)) {
					walk.filter(Files::isRegularFile).limit(5000)
						.forEach(p -> paths.add(root.relativize(p).toString().replace(File.separatorChar, '/')));
				}
			}
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.debug("Overlays: could not list {}", f, e);
			return null;
		}
		EnumSet<Part> parts = EnumSet.noneOf(Part.class);
		int other = classify(paths, parts);
		Scan s = new Scan(modified, size, parts, !parts.isEmpty() && other <= MAX_OTHER);
		synchronized (CACHE) {
			CACHE.put(key, s);
		}
		return s;
	}

	/** Adds to {@code parts} what the files change; returns how many files are about something else. */
	static int classify(Collection<String> paths, EnumSet<Part> parts) {
		int other = 0;
		for (String raw : paths) {
			String p = raw.toLowerCase(Locale.ROOT);
			if (!p.startsWith("assets/")) {
				continue; // pack.mcmeta, pack.png, readme files
			}
			Part part = part(p);
			if (part != null) {
				parts.add(part);
			} else if (!p.endsWith(".mcmeta") && !p.endsWith("/")) {
				other++;
			}
		}
		return other;
	}

	private static Part part(String p) {
		if (p.startsWith("assets/minecraft/mcpatcher/sky/") || p.startsWith("assets/minecraft/optifine/sky/")
			|| p.equals("assets/minecraft/textures/environment/sun.png") || p.equals("assets/minecraft/textures/environment/moon_phases.png")) {
			return Part.SKY;
		}
		if (p.startsWith("assets/minecraft/textures/blocks/wool_colored_")) {
			return Part.WOOL;
		}
		if (p.startsWith("assets/minecraft/textures/blocks/hardened_clay")) {
			return Part.CLAY;
		}
		if (p.startsWith("assets/minecraft/textures/blocks/glass")) {
			return Part.GLASS;
		}
		if ((p.startsWith("assets/minecraft/models/item/") || p.startsWith("assets/minecraft/textures/items/")) && p.contains("sword")) {
			return Part.SWORD;
		}
		return null;
	}

	private static File file(IResourcePack pack) {
		if (!(pack instanceof AbstractResourcePack)) {
			return null;
		}
		try {
			if (packFile == null) {
				packFile = ReflectionHelper.findField(AbstractResourcePack.class, "field_110597_b", "resourcePackFile");
			}
			return (File) packFile.get(pack);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
