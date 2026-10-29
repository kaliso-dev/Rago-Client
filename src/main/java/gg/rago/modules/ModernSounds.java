package gg.rago.modules;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The combat sounds of recent Minecraft versions (critical, strong, sweep, knockback and weak
 * attacks, and the amethyst chime), read from your own Minecraft install: the launcher keeps the
 * sounds of every version you have played in {@code assets/objects}, listed by
 * {@code assets/indexes}. Rago ships none of these files; when no recent version was ever
 * launched, these sounds are simply not offered. They reach the game as the sounds
 * {@code ragomodern:<id>}, through this built-in resource pack.
 */
public final class ModernSounds implements IResourcePack {
	public static final String DOMAIN = "ragomodern";
	/** Sound id, and the path in recent versions' assets the numbered files start with. */
	static final String[][] SOUNDS = {
		{"crit", "minecraft/sounds/entity/player/attack/crit"},
		{"strong", "minecraft/sounds/entity/player/attack/strong"},
		{"knockback", "minecraft/sounds/entity/player/attack/knockback"},
		{"sweep", "minecraft/sounds/entity/player/attack/sweep"},
		{"weak", "minecraft/sounds/entity/player/attack/weak"},
		{"amethyst", "minecraft/sounds/block/amethyst/hit"},
	};
	private static ModernSounds installed;

	/** Resource path ("sounds/crit1.ogg") to the file in the launcher's objects folder. */
	private final Map<String, File> files = new LinkedHashMap<>();
	/** Sound id to its numbered file names ("crit1", "crit2"...). */
	private final Map<String, List<String>> sounds = new LinkedHashMap<>();
	private byte[] soundsJson;

	private ModernSounds() {
	}

	/** Whether the sound {@code id} was found in the install. */
	public static boolean available(String id) {
		return installed != null && installed.sounds.containsKey(id);
	}

	/**
	 * Looks for the sounds and, when some are there, adds the pack under every other one. Call it
	 * before the game first loads its resources (pre-initialisation), so no reload is needed.
	 */
	public static void install() {
		Minecraft mc = Minecraft.getMinecraft();
		try {
			ModernSounds pack = new ModernSounds();
			List<File> roots = new ArrayList<>();
			File assets = ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, mc, "fileAssets", "field_110446_Y");
			roots.add(assets);
			String home = System.getProperty("user.home");
			String appData = System.getenv("APPDATA");
			if (appData != null) {
				roots.add(new File(appData, ".minecraft/assets"));
			}
			roots.add(new File(home, ".minecraft/assets"));
			roots.add(new File(home, "Library/Application Support/minecraft/assets"));
			for (File root : roots) {
				if (root != null && pack.scan(root)) {
					break;
				}
			}
			if (pack.sounds.isEmpty()) {
				RagoClient.LOG.info("Action Sounds: no recent Minecraft version's sounds found, only Rago's own are offered");
				return;
			}
			pack.soundsJson = pack.json();
			List<net.minecraft.client.resources.IResourcePack> packs =
				ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, mc, "defaultResourcePacks", "field_110449_ao");
			packs.add(pack);
			installed = pack;
			RagoClient.LOG.info("Action Sounds: recent sounds found: {}", pack.sounds.keySet());
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("Action Sounds: could not read the recent sounds", e);
		}
	}

	/** Reads the newest index file that has the attack sounds; returns whether it found any. */
	private boolean scan(File assets) {
		File[] indexes = new File(assets, "indexes").listFiles((d, n) -> n.endsWith(".json"));
		File objects = new File(assets, "objects");
		if (indexes == null || !objects.isDirectory()) {
			return false;
		}
		Arrays.sort(indexes, Comparator.comparingLong(File::lastModified).reversed());
		for (File index : indexes) {
			if (index.length() > 32L << 20) {
				continue;
			}
			try (Reader in = Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8)) {
				JsonElement root = new JsonParser().parse(in);
				if (!root.isJsonObject() || !root.getAsJsonObject().has("objects")) {
					continue;
				}
				JsonObject all = root.getAsJsonObject().getAsJsonObject("objects");
				for (String[] s : SOUNDS) {
					for (int i = 1; i <= 16; i++) {
						JsonElement e = all.get(s[1] + i + ".ogg");
						if (e == null || !e.isJsonObject() || !e.getAsJsonObject().has("hash")) {
							continue;
						}
						String hash = e.getAsJsonObject().get("hash").getAsString().toLowerCase(Locale.ROOT);
						File f = new File(objects, hash.substring(0, 2) + "/" + hash);
						if (f.isFile()) {
							String name = s[0] + i;
							files.put("sounds/" + name + ".ogg", f);
							sounds.computeIfAbsent(s[0], k -> new ArrayList<>()).add(name);
						}
					}
				}
				if (!sounds.isEmpty()) {
					return true;
				}
			} catch (IOException | RuntimeException e) {
				RagoClient.LOG.debug("Action Sounds: skipping {}: {}", index, e.toString());
			}
		}
		return false;
	}

	private byte[] json() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<String>> e : sounds.entrySet()) {
			JsonObject event = new JsonObject();
			event.addProperty("category", "player");
			JsonArray list = new JsonArray();
			for (String name : e.getValue()) {
				JsonObject s = new JsonObject();
				s.addProperty("name", name);
				s.addProperty("stream", false);
				list.add(s);
			}
			event.add("sounds", list);
			root.add(e.getKey(), event);
		}
		return root.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public InputStream getInputStream(ResourceLocation rl) throws IOException {
		if (DOMAIN.equals(rl.getResourceDomain())) {
			if ("sounds.json".equals(rl.getResourcePath())) {
				return new ByteArrayInputStream(soundsJson);
			}
			File f = files.get(rl.getResourcePath());
			if (f != null) {
				return new FileInputStream(f);
			}
		}
		throw new FileNotFoundException(rl.toString());
	}

	@Override
	public boolean resourceExists(ResourceLocation rl) {
		return DOMAIN.equals(rl.getResourceDomain())
			&& ("sounds.json".equals(rl.getResourcePath()) || files.containsKey(rl.getResourcePath()));
	}

	@Override
	public Set<String> getResourceDomains() {
		return ImmutableSet.of(DOMAIN);
	}

	@Override
	public <T extends IMetadataSection> T getPackMetadata(IMetadataSerializer serializer, String section) {
		return null;
	}

	@Override
	public BufferedImage getPackImage() throws IOException {
		throw new FileNotFoundException("pack.png");
	}

	@Override
	public String getPackName() {
		return "Rago recent sounds";
	}
}
