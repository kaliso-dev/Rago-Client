package gg.rago.accounts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skin faces for the account list, looked up from Mojang's public profile API by name and kept in
 * {@code rago/skins/} for a day. Until a skin arrives, or if there is none, Steve is shown.
 */
public final class SkinHeads {
	private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
	private static final Map<String, BufferedImage> loaded = new ConcurrentHashMap<>();
	private static final Map<String, ResourceLocation> textures = new HashMap<>();
	private static final Map<String, Boolean> requested = new ConcurrentHashMap<>();

	private SkinHeads() {
	}

	/** The skin texture for {@code name} (64 wide; 32 or 64 high), starting a download if needed. */
	public static ResourceLocation skin(String name) {
		String key = name.toLowerCase(java.util.Locale.ROOT);
		ResourceLocation tex = textures.get(key);
		if (tex != null) {
			return tex;
		}
		BufferedImage img = loaded.get(key);
		if (img != null) {
			tex = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("rago_head_" + key.replaceAll("[^a-z0-9_]", "_"), new DynamicTexture(img));
			textures.put(key, tex);
			return tex;
		}
		if (requested.putIfAbsent(key, true) == null) {
			Thread t = new Thread(() -> fetch(name, key), "Rago skin " + name);
			t.setDaemon(true);
			t.start();
		}
		return DefaultPlayerSkin.getDefaultSkinLegacy();
	}

	/** Height of the skin image, so the face can be cut from 64x32 and 64x64 skins alike. */
	public static int skinHeight(String name) {
		BufferedImage img = loaded.get(name.toLowerCase(java.util.Locale.ROOT));
		return img == null ? 64 : img.getHeight();
	}

	private static void fetch(String name, String key) {
		File file = new File(Minecraft.getMinecraft().mcDataDir, "rago/skins/" + key.replaceAll("[^a-z0-9_]", "_") + ".png");
		try {
			if (file.isFile() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
				loaded.put(key, ImageIO.read(file));
				return;
			}
			JsonObject id = json("https://api.mojang.com/users/profiles/minecraft/" + name);
			if (id == null || !id.has("id")) {
				return;
			}
			JsonObject profile = json("https://sessionserver.mojang.com/session/minecraft/profile/" + id.get("id").getAsString());
			if (profile == null || !profile.has("properties")) {
				return;
			}
			String value = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
			JsonObject textures = new JsonParser().parse(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
				.getAsJsonObject().getAsJsonObject("textures");
			if (textures == null || !textures.has("SKIN")) {
				return;
			}
			String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
			BufferedImage img;
			try (InputStream in = open(url)) {
				img = ImageIO.read(in);
			}
			if (img != null && img.getWidth() == 64) {
				file.getParentFile().mkdirs();
				ImageIO.write(img, "png", file);
				loaded.put(key, img);
			}
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.debug("No skin for {}: {}", name, e.toString());
			if (file.isFile()) {
				try {
					loaded.put(key, ImageIO.read(file));
				} catch (IOException ignored) {
					// keep Steve
				}
			}
		}
	}

	static InputStream open(String url) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(5000);
		c.setReadTimeout(5000);
		c.setRequestProperty("User-Agent", "Rago");
		if (c.getResponseCode() != 200) {
			throw new IOException("HTTP " + c.getResponseCode());
		}
		return c.getInputStream();
	}

	static JsonObject json(String url) throws IOException {
		try (InputStream in = open(url)) {
			return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
