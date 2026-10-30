package gg.rago.accounts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The skin locker: skins taken from other players by name, kept in {@code config/rago-skins/},
 * and the one you wear. Wearing a skin changes it in this client straight away; it can also be
 * set as your Minecraft account's skin, through Mojang's API with the session you are signed in
 * with, so that everyone sees it. A new locker starts with qRago's skin.
 */
public final class Skins {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final String UPLOAD = "https://api.minecraftservices.com/minecraft/profile/skins";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** The skin a new locker starts with. */
	private static final String STARTER = "qRago";

	private static final List<Skin> skins = new CopyOnWriteArrayList<>();
	private static volatile String worn;
	private static volatile Skin account;
	private static volatile boolean accountRequested;
	/** Whether the starter skin was put in the locker once; after that, removing it is the player's choice. */
	private static volatile boolean starterAdded;
	private static Path file;
	private static Path folder;
	private static int textureIds;

	private Skins() {
	}

	/** A skin in the locker. Its texture is made on first use, on the render thread. */
	public static final class Skin {
		public final String name;
		public final boolean slim;
		final String url;
		final File png;
		private volatile BufferedImage image;
		private ResourceLocation texture;
		private boolean failed;

		Skin(String name, boolean slim, String url, File png) {
			this.name = name;
			this.slim = slim;
			this.url = url;
			this.png = png;
		}

		/** The texture, or null while the image is still loading (or could not be read). */
		public ResourceLocation texture() {
			if (texture == null && image != null) {
				texture = Minecraft.getMinecraft().getTextureManager()
					.getDynamicTextureLocation("rago_locker_" + textureIds++, new DynamicTexture(image));
			}
			return texture;
		}

		public boolean loading() {
			return image == null && !failed;
		}

		void load() {
			try {
				BufferedImage raw = ImageIO.read(png);
				if (raw == null) {
					throw new IOException("not an image");
				}
				// the same clean-up Minecraft gives downloaded skins: 64x32 skins get their left limbs
				image = new ImageBufferDownload().parseUserSkin(raw);
			} catch (IOException | RuntimeException e) {
				failed = true;
				RagoClient.LOG.warn("Cannot read the skin {}: {}", png, e.toString());
			}
		}

		void release() {
			if (texture != null) {
				Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
				texture = null;
			}
		}
	}

	/** Reads the locker; the images load in the background. */
	public static void load(Path configDir) {
		file = configDir.resolve("rago-locker.json");
		folder = configDir.resolve("rago-skins");
		if (!Files.isRegularFile(file)) {
			addStarter();
			return;
		}
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = new JsonParser().parse(in).getAsJsonObject();
			if (root.has("skins")) {
				for (JsonElement e : root.getAsJsonArray("skins")) {
					JsonObject o = e.getAsJsonObject();
					String name = o.get("name").getAsString();
					if (NAME.matcher(name).matches() && find(name) == null) {
						skins.add(new Skin(name, o.has("slim") && o.get("slim").getAsBoolean(),
							o.has("url") ? o.get("url").getAsString() : null, png(name)));
					}
				}
			}
			String w = root.has("worn") && !root.get("worn").isJsonNull() ? root.get("worn").getAsString() : null;
			worn = w != null && find(w) != null ? find(w).name : null;
			starterAdded = root.has("starter_added") && root.get("starter_added").getAsBoolean();
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.warn("Cannot read {}: {}", file, e.toString());
		}
		List<Skin> toLoad = new ArrayList<>(skins);
		background("Rago locker", () -> {
			for (Skin s : toLoad) {
				s.load();
			}
		});
		if (!starterAdded) {
			addStarter();
		}
	}

	/** Fetches the starter skin in the background; without a connection, it is tried again next game. */
	private static void addStarter() {
		add(STARTER, s -> starterDone(), f -> {
			if (f != Failure.NETWORK) {
				starterDone();
			}
		});
	}

	private static void starterDone() {
		starterAdded = true;
		save();
	}

	private static File png(String name) {
		return folder.resolve(name.toLowerCase(Locale.ROOT) + ".png").toFile();
	}

	private static synchronized void save() {
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("worn", worn);
		root.addProperty("starter_added", starterAdded);
		JsonArray list = new JsonArray();
		for (Skin s : skins) {
			JsonObject o = new JsonObject();
			o.addProperty("name", s.name);
			o.addProperty("slim", s.slim);
			if (s.url != null) {
				o.addProperty("url", s.url);
			}
			list.add(o);
		}
		root.add("skins", list);
		try {
			Files.createDirectories(file.getParent());
			try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(root, out);
			}
		} catch (IOException e) {
			RagoClient.LOG.warn("Cannot save {}: {}", file, e.toString());
		}
	}

	public static List<Skin> all() {
		return skins;
	}

	public static Skin find(String name) {
		for (Skin s : skins) {
			if (s.name.equalsIgnoreCase(name)) {
				return s;
			}
		}
		return null;
	}

	/** The skin you wear, or null for your account's own. */
	public static Skin worn() {
		String w = worn;
		return w == null ? null : find(w);
	}

	public static void wear(Skin skin) {
		worn = skin == null ? null : skin.name;
		save();
	}

	public static void remove(Skin skin) {
		skins.remove(skin);
		if (skin.name.equals(worn)) {
			worn = null;
		}
		skin.release();
		if (!skin.png.delete() && skin.png.exists()) {
			skin.png.deleteOnExit();
		}
		save();
	}

	public static boolean validName(String name) {
		return NAME.matcher(name).matches();
	}

	/** Your account's own skin, looked up once per game; null until it arrives or when offline. */
	public static Skin account() {
		if (!accountRequested) {
			accountRequested = true;
			String name = Minecraft.getMinecraft().getSession().getUsername();
			background("Rago account skin", () -> {
				try {
					File tmp = File.createTempFile("rago-account-skin", ".png");
					tmp.deleteOnExit();
					Skin s = download(name, tmp);
					s.load();
					account = s;
				} catch (IOException | RuntimeException e) {
					RagoClient.LOG.debug("No account skin for {}: {}", name, e.toString());
				}
			});
		}
		return account;
	}

	/** The reasons adding a skin can fail, shown to the player. */
	public enum Failure { INVALID_NAME, ALREADY_THERE, NOT_FOUND, NETWORK }

	/**
	 * Fetches the skin {@code name} wears now and adds it to the locker, in the background. Exactly
	 * one of the callbacks runs, on the fetching thread.
	 */
	public static void add(String name, Consumer<Skin> done, Consumer<Failure> failed) {
		if (!validName(name)) {
			failed.accept(Failure.INVALID_NAME);
			return;
		}
		if (find(name) != null) {
			failed.accept(Failure.ALREADY_THERE);
			return;
		}
		background("Rago skin " + name, () -> {
			try {
				File tmp = File.createTempFile("rago-skin", ".png");
				Skin fetched = download(name, tmp);
				Skin s = new Skin(fetched.name, fetched.slim, fetched.url, png(fetched.name));
				if (find(s.name) != null) {
					tmp.delete();
					failed.accept(Failure.ALREADY_THERE);
					return;
				}
				Files.createDirectories(folder);
				Files.copy(tmp.toPath(), s.png.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				tmp.delete();
				s.load();
				skins.add(0, s);
				save();
				done.accept(s);
			} catch (NotFound e) {
				failed.accept(Failure.NOT_FOUND);
			} catch (IOException | RuntimeException e) {
				RagoClient.LOG.info("Cannot fetch the skin of {}: {}", name, e.toString());
				failed.accept(Failure.NETWORK);
			}
		});
	}

	private static final class NotFound extends IOException {
		NotFound() {
			super("no such player");
		}
	}

	/** Looks the player up and saves their skin to {@code to}; the name comes back as Mojang spells it. */
	private static Skin download(String name, File to) throws IOException {
		JsonObject id;
		try {
			id = SkinHeads.json("https://api.mojang.com/users/profiles/minecraft/" + name);
		} catch (IOException e) {
			if (e.getMessage() != null && (e.getMessage().contains("204") || e.getMessage().contains("404"))) {
				throw new NotFound();
			}
			throw e;
		}
		if (id == null || !id.has("id")) {
			throw new NotFound();
		}
		String realName = id.has("name") ? id.get("name").getAsString() : name;
		JsonObject profile = SkinHeads.json("https://sessionserver.mojang.com/session/minecraft/profile/" + id.get("id").getAsString());
		if (profile == null || !profile.has("properties")) {
			throw new NotFound();
		}
		String value = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
		JsonObject textures = new JsonParser().parse(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
			.getAsJsonObject().getAsJsonObject("textures");
		if (textures == null || !textures.has("SKIN")) {
			// no skin set: the player wears a default one, which there is no point in keeping
			throw new NotFound();
		}
		JsonObject skin = textures.getAsJsonObject("SKIN");
		String url = skin.get("url").getAsString();
		boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
		// the texture server answers on https too, which is safer than the http link Mojang gives
		try (InputStream in = SkinHeads.open(url.replaceFirst("^http://", "https://"))) {
			Files.copy(in, to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		return new Skin(realName, slim, url, to);
	}

	/** The reasons setting the account skin can fail. */
	public enum UploadResult { OK, SIGNED_OUT, FAILED }

	/**
	 * Sets {@code skin} as the skin of the Minecraft account in use, uploading the image with the
	 * session's access token. Blocks, so run it off the render thread.
	 */
	public static UploadResult setAccountSkin(Skin skin) {
		String token = Minecraft.getMinecraft().getSession().getToken();
		if (token == null || token.length() < 20) {
			return UploadResult.SIGNED_OUT;
		}
		try {
			byte[] image = Files.readAllBytes(skin.png.toPath());
			String boundary = "RagoSkin" + Long.toHexString(System.nanoTime());
			ByteArrayOutputStream body = new ByteArrayOutputStream();
			String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
				+ (skin.slim ? "slim" : "classic") + "\r\n--" + boundary
				+ "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n";
			body.write(head.getBytes(StandardCharsets.UTF_8));
			body.write(image);
			body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			HttpURLConnection c = (HttpURLConnection) new URL(UPLOAD).openConnection();
			c.setConnectTimeout(8000);
			c.setReadTimeout(15000);
			c.setRequestMethod("POST");
			c.setDoOutput(true);
			c.setRequestProperty("User-Agent", "Rago");
			c.setRequestProperty("Authorization", "Bearer " + token);
			c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			try (OutputStream out = c.getOutputStream()) {
				body.writeTo(out);
			}
			int code = c.getResponseCode();
			if (code == 200) {
				return UploadResult.OK;
			}
			RagoClient.LOG.info("Setting the account skin failed: HTTP {}", code);
			return code == 401 || code == 403 ? UploadResult.SIGNED_OUT : UploadResult.FAILED;
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.info("Setting the account skin failed: {}", e.toString());
			return UploadResult.FAILED;
		}
	}

	private static void background(String name, Runnable r) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		t.start();
	}
}
