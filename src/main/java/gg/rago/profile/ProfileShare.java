package gg.rago.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A profile as a code to send to a friend, who imports it as a new profile of their own.
 *
 * <p>The code holds only what differs from Rago's defaults (mods on or off, options, HUD positions),
 * packed with a dictionary of Rago's mod and option names, so it stays short enough for a chat
 * message. Mods that are about the player themselves stay out
 * of it and keep the importer's own settings: their cape and cosmetics (some are only theirs), nick,
 * badge, skin, Discord status, Rago Network server and the messages Rago may send for them. The
 * Hypixel key is never in a profile.
 */
public final class ProfileShare {
	public static final String PREFIX = "RAGO1:";

	/** Mods that stay as the importer set them, and are never in a code. */
	public static final Set<String> PERSONAL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"rago_cape", "cosmetics", "nick_hider", "rago_badge", "skin_changer", "discord", "rago_network",
		"auto_messages")));

	/** Profile icons an imported profile may use; anything else becomes the plain user icon. */
	static final Set<String> ICONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"user", "bed", "swords", "cloud", "sword", "shield", "trophy", "target", "flame", "zap", "star", "crown")));

	public static final int MAX_NAME = 24;
	/** A longer code is not one of ours; a code that unpacks to more is refused (no zip bombs). */
	private static final int MAX_CODE = 200_000;
	private static final int MAX_JSON = 1_000_000;

	/**
	 * Mod and option names, the dictionary codes are packed with. It must never change: a code is
	 * read with the same dictionary it was made with ({@code RAGO1}); new names only pack less tightly.
	 */
	static final String DICTIONARY = "/assets/rago/profile_code_dict.txt";
	private static byte[] dictionary;

	private static final Pattern CODE = Pattern.compile(Pattern.quote(PREFIX) + "([A-Za-z0-9_-]+)");

	private ProfileShare() {
	}

	/** A profile read from a code. */
	public static final class Shared {
		public final String name;
		public final String icon;
		/** Only what differs from the defaults, per mod. */
		public final JsonObject changes;

		Shared(String name, String icon, JsonObject changes) {
			this.name = name;
			this.icon = icon;
			this.changes = changes;
		}
	}

	/** What a profile changes from the defaults, personal mods left out. */
	public static JsonObject changes(JsonObject modules, JsonObject defaults) {
		JsonObject out = new JsonObject();
		for (Map.Entry<String, JsonElement> e : modules.entrySet()) {
			String id = e.getKey();
			JsonObject mine = object(e.getValue());
			JsonObject base = object(defaults.get(id));
			if (PERSONAL.contains(id) || mine == null || base == null) {
				continue;
			}
			JsonObject d = new JsonObject();
			JsonElement enabled = mine.get("enabled");
			if (enabled != null && !enabled.equals(base.get("enabled"))) {
				d.add("enabled", enabled);
			}
			JsonObject options = object(mine.get("options"));
			JsonObject baseOptions = object(base.get("options"));
			if (options != null && baseOptions != null) {
				JsonObject changed = new JsonObject();
				for (Map.Entry<String, JsonElement> o : options.entrySet()) {
					if (baseOptions.has(o.getKey()) && !o.getValue().equals(baseOptions.get(o.getKey()))) {
						changed.add(o.getKey(), o.getValue());
					}
				}
				if (changed.entrySet().size() > 0) {
					d.add("options", changed);
				}
			}
			JsonObject hud = object(mine.get("hud"));
			if (hud != null && !hud.equals(base.get("hud"))) {
				d.add("hud", hud);
			}
			if (d.entrySet().size() > 0) {
				out.add(id, d);
			}
		}
		return out;
	}

	/** The code for a profile: {@link #PREFIX} then the profile, packed. */
	public static String encode(String name, String icon, JsonObject changes) {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("name", cleanName(name));
		o.addProperty("icon", ICONS.contains(icon) ? icon : "user");
		o.add("modules", changes);
		return pack(o.toString().getBytes(StandardCharsets.UTF_8));
	}

	static String pack(byte[] json) {
		Deflater d = new Deflater(Deflater.BEST_COMPRESSION, true);
		try {
			d.setDictionary(dictionary());
			d.setInput(json);
			d.finish();
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			while (!d.finished()) {
				bytes.write(buf, 0, d.deflate(buf));
			}
			return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} finally {
			d.end();
		}
	}

	/**
	 * Reads a code, also from inside a longer text (a pasted chat message or the exported file);
	 * null when there is none or it is damaged.
	 */
	public static Shared decode(String text) {
		if (text == null || text.length() > MAX_CODE + 10_000) {
			return null;
		}
		Matcher m = CODE.matcher(text);
		if (!m.find() || m.group(1).length() > MAX_CODE) {
			return null;
		}
		try {
			byte[] packed = Base64.getUrlDecoder().decode(m.group(1));
			String json = unpack(packed);
			if (json == null) {
				return null;
			}
			JsonElement parsed = new JsonParser().parse(json);
			JsonObject o = object(parsed);
			if (o == null) {
				return null;
			}
			JsonObject modules = object(o.get("modules"));
			if (modules == null) {
				return null;
			}
			JsonObject changes = new JsonObject();
			for (Map.Entry<String, JsonElement> e : modules.entrySet()) {
				JsonObject mod = object(e.getValue());
				if (mod != null && !PERSONAL.contains(e.getKey())) {
					changes.add(e.getKey(), mod);
				}
			}
			String icon = string(o.get("icon"));
			return new Shared(cleanName(string(o.get("name"))), ICONS.contains(icon) ? icon : "user", changes);
		} catch (RuntimeException e) {
			// not base64, not packed or not JSON: not a code we can read
			return null;
		}
	}

	/**
	 * The full mod states of an imported profile: every mod as it starts out with the friend's changes
	 * on top, and the personal mods exactly as the importer has them.
	 */
	public static JsonObject merge(JsonObject defaults, JsonObject mine, JsonObject changes) {
		JsonObject out = new JsonObject();
		for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
			String id = e.getKey();
			JsonObject own = object(mine.get(id));
			if (PERSONAL.contains(id) && own != null) {
				out.add(id, copy(own));
				continue;
			}
			JsonObject m = copy(object(e.getValue()));
			JsonObject d = PERSONAL.contains(id) ? null : object(changes.get(id));
			if (d != null) {
				JsonElement enabled = d.get("enabled");
				if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
					m.add("enabled", enabled);
				}
				JsonObject options = object(d.get("options"));
				JsonObject into = object(m.get("options"));
				if (options != null && into != null) {
					for (Map.Entry<String, JsonElement> o : options.entrySet()) {
						if (into.has(o.getKey())) {
							into.add(o.getKey(), o.getValue());
						}
					}
				}
				JsonObject hud = object(d.get("hud"));
				if (hud != null && m.has("hud")) {
					m.add("hud", hud);
				}
			}
			out.add(id, m);
		}
		return out;
	}

	/** A name fit for the profile list: no colour codes or control characters, at most 24 characters. */
	static String cleanName(String name) {
		if (name == null) {
			return "Imported";
		}
		String s = name.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
		if (s.length() > MAX_NAME) {
			s = s.substring(0, MAX_NAME).trim();
		}
		return s.isEmpty() ? "Imported" : s;
	}

	/** A name not yet in {@code taken}: the name itself, else "name 2", "name 3"…, kept to 24 characters. */
	public static String freeName(String name, java.util.Collection<String> taken) {
		String base = cleanName(name);
		if (!taken.contains(base)) {
			return base;
		}
		for (int n = 2; ; n++) {
			String suffix = " " + n;
			String head = base.length() + suffix.length() > MAX_NAME ? base.substring(0, MAX_NAME - suffix.length()).trim() : base;
			if (!taken.contains(head + suffix)) {
				return head + suffix;
			}
		}
	}

	private static String unpack(byte[] packed) {
		Inflater in = new Inflater(true);
		try {
			in.setDictionary(dictionary());
			in.setInput(packed);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			while (!in.finished()) {
				int n = in.inflate(buf);
				if (n == 0 && (in.needsInput() || in.needsDictionary())) {
					return null;
				}
				out.write(buf, 0, n);
				if (out.size() > MAX_JSON) {
					return null;
				}
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} catch (DataFormatException e) {
			return null;
		} finally {
			in.end();
		}
	}

	private static synchronized byte[] dictionary() {
		if (dictionary == null) {
			try (InputStream in = ProfileShare.class.getResourceAsStream(DICTIONARY)) {
				if (in == null) {
					throw new IllegalStateException("missing " + DICTIONARY);
				}
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
				}
				dictionary = out.toByteArray();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		return dictionary;
	}

	private static JsonObject object(JsonElement e) {
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static String string(JsonElement e) {
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static JsonObject copy(JsonObject o) {
		return o == null ? new JsonObject() : new JsonParser().parse(o.toString()).getAsJsonObject();
	}
}
