package gg.rago.rewards;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.hooks.Hooks;
import gg.rago.modules.RagoNetwork;
import net.minecraft.client.resources.I18n;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The daily chest, Rago Points, the collection and the shop, as Rago Network last told them. The
 * server draws and keeps everything; this only asks and remembers the answers for the screens.
 * Every field is replaced whole when an answer comes, so a screen can read them at any time.
 */
public final class Rewards {
	public static final String[] RARITIES = {"common", "uncommon", "rare", "epic", "legendary", "mythic"};
	/** The rarities' colours, as Minecraft colours its items' rarities (and the collector card). */
	private static final int[] COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF6A6AFF, 0xFFC04BFF, 0xFFFFAA00, 0xFFFF55FF};
	public static final String[] TYPES = {"cape", "wings", "hat", "shield", "dance", "name", "icon", "title", "card", "badge"};

	/** A cosmetic of the catalogue. */
	public static final class Item {
		public final String id;
		public final String type;
		public final String name;
		public final String rarity;
		public final List<String> sources;

		Item(String id, String rarity, List<String> sources) {
			this.id = id;
			int colon = id.indexOf(':');
			this.type = id.substring(0, colon);
			this.name = id.substring(colon + 1);
			this.rarity = rarity;
			this.sources = sources;
		}

		public boolean from(String source) {
			return sources.contains(source);
		}
	}

	/** One card of a chest. */
	public static final class Card {
		/** points, item, duplicate or fragment */
		public final String kind;
		public final String rarity;
		public final String item;
		public final int points;
		public final int count;
		public final int have;
		public final int need;
		public final boolean completed;

		Card(JsonObject o) {
			kind = str(o, "kind", "points");
			rarity = str(o, "rarity", "common");
			item = str(o, "item", null);
			points = num(o, "points", 0);
			count = num(o, "count", 0);
			have = num(o, "have", 0);
			need = num(o, "need", 0);
			completed = o.has("completed") && o.get("completed").getAsBoolean();
		}
	}

	/** A day's chest once opened. */
	public static final class Chest {
		public final List<Card> cards;
		public final List<String> gifts;
		public final int streak;
		public final int won;

		Chest(JsonObject o) {
			List<Card> c = new ArrayList<>();
			for (JsonElement e : array(o, "cards")) {
				if (e.isJsonObject()) {
					c.add(new Card(e.getAsJsonObject()));
				}
			}
			cards = Collections.unmodifiableList(c);
			gifts = strings(o, "gifts");
			streak = num(o, "streak", 0);
			won = num(o, "won", 0);
		}
	}

	/** The chest and points as of the last answer. */
	public static final class Status {
		public final int points;
		public final int streak;
		/** The streak today's chest gives (shown before it is opened: a broken streak shows here). */
		public final int todayStreak;
		public final boolean opened;
		public final Chest chest;
		/** When the next chest comes (System.currentTimeMillis()). */
		public final long resetAt;
		public final int epicIn;
		public final int legendaryIn;

		Status(JsonObject o, long now) {
			points = num(o, "points", 0);
			streak = num(o, "streak", 0);
			todayStreak = num(o, "todayStreak", streak);
			opened = o.has("opened") ? o.get("opened").getAsBoolean() : o.has("chest");
			chest = o.has("chest") && o.get("chest").isJsonObject() ? new Chest(o.getAsJsonObject("chest")) : null;
			resetAt = now + num(o, "nextReset", 0) * 1000L;
			epicIn = num(o, "epicIn", 0);
			legendaryIn = num(o, "legendaryIn", 0);
		}
	}

	/** Today's shop. */
	public static final class Shop {
		public final List<String> daily;
		public final List<String> bundle;
		public final List<String> permanent;
		public final Map<String, Integer> prices;
		public final int bundlePrice;
		public final long resetAt;

		Shop(JsonObject o, long now) {
			daily = strings(o, "daily");
			bundle = strings(o, "bundle");
			permanent = strings(o, "permanent");
			Map<String, Integer> p = new HashMap<>();
			if (o.has("prices") && o.get("prices").isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("prices").entrySet()) {
					p.put(e.getKey(), e.getValue().getAsInt());
				}
			}
			prices = Collections.unmodifiableMap(p);
			bundlePrice = num(o, "bundlePrice", 0);
			resetAt = now + num(o, "nextReset", 0) * 1000L;
		}
	}

	private static volatile Map<String, Item> catalogue = builtIn();
	private static volatile Map<String, Integer> odds = Collections.emptyMap();
	private static volatile boolean catalogueFetched;
	private static volatile Status status;
	private static volatile Set<String> owned = Collections.emptySet();
	/** Whether {@link #owned} is the account's (asked since it signed in), not just empty. */
	private static volatile boolean ownedKnown;
	/** Goes up whenever what the player owns or the catalogue changes, so what is worn can be kept. */
	private static volatile int ownedVersion;
	private static volatile Map<String, Integer> fragments = Collections.emptyMap();
	private static volatile Shop shop;
	private static volatile String error;
	private static volatile boolean busy;
	/** The owner of what is known: another account signed in forgets it. */
	private static volatile UUID owner;

	private Rewards() {
	}

	// ---- reading ------------------------------------------------------------------------------

	public static Item item(String id) {
		return catalogue.get(id);
	}

	/** The catalogue, in its order (by type, then rarity). */
	public static List<Item> items() {
		List<Item> out = new ArrayList<>(catalogue.values());
		out.sort((a, b) -> {
			int t = index(TYPES, a.type) - index(TYPES, b.type);
			return t != 0 ? t : rank(a.rarity) - rank(b.rarity);
		});
		return out;
	}

	public static Status status() {
		return status;
	}

	public static Shop shop() {
		return shop;
	}

	/** The last thing that went wrong ("offline", "sign_in", "not_enough_points"...), or null. */
	public static String error() {
		return error;
	}

	public static boolean busy() {
		return busy;
	}

	/** Whether you have {@code id}: won, bought, or in the starter pack every player has. */
	public static boolean owns(String id) {
		Item it = catalogue.get(id);
		// the creator's capes are for the accounts the cosmetics whitelist names, as on the server
		return it == null || it.from("starter") || it.from("creator") || owned.contains(id);
	}

	/**
	 * Whether the player can wear {@code id} of {@code type} now: one they own, a free one, or anything
	 * while their collection is not known yet (offline). Rago Network shows others the same.
	 */
	public static boolean wearable(String type, String id) {
		return !ownedKnown || owns(type, id);
	}

	/** Changes whenever {@link #wearable} may answer differently. */
	public static int ownedVersion() {
		return ownedVersion;
	}

	public static int fragments(String id) {
		Integer n = fragments.get(id);
		return n == null ? 0 : n;
	}

	public static Map<String, Integer> allFragments() {
		return fragments;
	}

	/** The published chance of a rarity for one card, out of 10 000; 0 when not known yet. */
	public static int odds(String rarity) {
		Integer n = odds.get(rarity);
		return n == null ? 0 : n;
	}

	/** Whether today's chest waits to be opened (false while not known). */
	public static boolean chestWaiting() {
		Status s = status;
		return s != null && (!s.opened || System.currentTimeMillis() >= s.resetAt && s.resetAt > 0);
	}

	public static int rank(String rarity) {
		return Math.max(0, index(RARITIES, rarity));
	}

	public static int color(String rarity) {
		return COLORS[rank(rarity)];
	}

	public static String rarityName(String rarity) {
		return I18n.format("rago.rarity." + rarity);
	}

	/** A cosmetic's name in the player's language. */
	public static String name(String id) {
		int colon = id.indexOf(':');
		String type = colon < 0 ? "" : id.substring(0, colon);
		String n = id.substring(colon + 1);
		String[] keys = {
			"rago.item." + type + "." + n,
			legacyKey(type, n),
			"rago.choice." + n,
		};
		for (String k : keys) {
			if (k != null) {
				String v = I18n.format(k);
				if (!v.equals(k)) {
					return v;
				}
			}
		}
		String words = n.replace('_', ' ');
		return words.isEmpty() ? id : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	private static String legacyKey(String type, String n) {
		switch (type) {
			case "cape":
				return "rago.option.cape_design." + n;
			case "hat":
				return "rago.option.cosmetic_hat." + n;
			case "wings":
				return "rago.option.cosmetic_wings." + n;
			case "shield":
				return "rago.option.cosmetic_shield." + n;
			case "dance":
				return "rago.emote." + n;
			default:
				return null;
		}
	}

	// ---- asking -------------------------------------------------------------------------------

	private static long homeAsked;

	/**
	 * On the home screen, once signed in: asks for the chest's state now and then (every five
	 * minutes, and right after the next chest comes) so the dock can show that one waits.
	 */
	public static void tickHome() {
		RagoNetwork net = Hooks.network;
		if (net == null || !net.signedIn()) {
			return;
		}
		inventory();
		long now = System.currentTimeMillis();
		Status s = status;
		boolean due = s == null ? now - homeAsked > 15_000 : now - homeAsked > 300_000 || s.opened && now >= s.resetAt && now - homeAsked > 15_000;
		if (due) {
			homeAsked = now;
			forgetIfAnotherAccount();
			net.request("/v1/rewards", new JsonObject(), (a, e) -> {
				if (e == null) {
					status = new Status(a, System.currentTimeMillis());
				}
			});
		}
	}

	/** The play pass: a season of tiers won by playing, one an hour. */
	public static final class Pass {
		public final String season;
		public final int minutesPerTier;
		public final int dailyCap;
		public final int minutes;
		public final int tier;
		public final int today;
		public final long resetAt;
		/** Each tier's reward: an item id, or null for points. */
		public final List<String> items;
		public final List<Integer> points;

		Pass(JsonObject o, long now) {
			season = str(o, "season", "");
			minutesPerTier = Math.max(1, num(o, "minutesPerTier", 60));
			dailyCap = num(o, "dailyCap", 0);
			minutes = num(o, "minutes", 0);
			tier = num(o, "tier", 0);
			today = num(o, "today", 0);
			resetAt = now + num(o, "nextReset", 0) * 1000L;
			List<String> it = new ArrayList<>();
			List<Integer> pts = new ArrayList<>();
			for (JsonElement e : array(o, "tiers")) {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject t = e.getAsJsonObject();
				it.add(t.has("item") ? t.get("item").getAsString() : null);
				pts.add(num(t, "points", 0));
			}
			items = Collections.unmodifiableList(it);
			points = Collections.unmodifiableList(pts);
		}

		Pass(Pass old, int minutes, int tier) {
			season = old.season;
			minutesPerTier = old.minutesPerTier;
			dailyCap = old.dailyCap;
			this.minutes = minutes;
			this.tier = tier;
			today = old.today + Math.max(0, minutes - old.minutes);
			resetAt = old.resetAt;
			items = old.items;
			points = old.points;
		}
	}

	private static volatile Pass pass;

	public static Pass pass() {
		return pass;
	}

	/** Asks for the play pass. */
	public static void refreshPass() {
		RagoNetwork net = Hooks.network;
		if (net == null) {
			return;
		}
		net.request("/v1/pass", new JsonObject(), (a, e) -> {
			if (e == null) {
				pass = new Pass(a, System.currentTimeMillis());
			}
		});
	}

	/**
	 * A presence counted a minute of play (on the network thread): the pass moves on, and each tier
	 * reached is told in the chat with what it gave.
	 */
	public static void passProgress(JsonObject p) {
		Pass old = pass;
		if (old != null) {
			pass = new Pass(old, num(p, "minutes", old.minutes), num(p, "tier", old.tier));
		}
		JsonArray given = array(p, "given");
		if (given.size() == 0) {
			return;
		}
		List<String> lines = new ArrayList<>();
		for (JsonElement e : given) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject g = e.getAsJsonObject();
			int tier = num(g, "tier", 0);
			String item = g.has("item") ? g.get("item").getAsString() : null;
			int points = num(g, "points", 0);
			if (item != null && points == 0) {
				lines.add(I18n.format("rago.pass.reached_item", tier, name(item)));
			} else if (item != null) {
				lines.add(I18n.format("rago.pass.reached_duplicate", tier, name(item), points));
			} else {
				lines.add(I18n.format("rago.pass.reached_points", tier, points));
			}
		}
		inventoryAsked = 0;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
		mc.addScheduledTask(() -> {
			inventory();
			if (mc.thePlayer != null) {
				for (String line : lines) {
					mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(line));
				}
			}
		});
	}

	private static long inventoryAsked;

	/**
	 * Asks for the collection again (the locker and the emote wheel lock what is not won yet), at most
	 * every half minute.
	 */
	public static void inventory() {
		RagoNetwork net = Hooks.network;
		long now = System.currentTimeMillis();
		if (net == null || !net.signedIn() || now - inventoryAsked < 30_000) {
			return;
		}
		inventoryAsked = now;
		forgetIfAnotherAccount();
		net.request("/v1/inventory", new JsonObject(), (a, e) -> {
			if (e == null) {
				readInventory(a);
			}
		});
	}

	/** Whether the cosmetic {@code type:id} is the player's ("none" always is). */
	public static boolean owns(String type, String id) {
		return "none".equals(id) || owns(type + ":" + id);
	}

	/** Asks for the chest's state, the collection, the shop and (once) the catalogue. */
	public static void refresh() {
		RagoNetwork net = Hooks.network;
		if (net == null) {
			error = "offline";
			return;
		}
		forgetIfAnotherAccount();
		if (!catalogueFetched) {
			net.request("/v1/catalogue", null, (a, e) -> {
				if (e == null) {
					readCatalogue(a);
					catalogueFetched = true;
				}
			});
		}
		net.request("/v1/rewards", new JsonObject(), (a, e) -> {
			if (e == null) {
				status = new Status(a, System.currentTimeMillis());
			}
			error = e;
		});
		net.request("/v1/inventory", new JsonObject(), (a, e) -> {
			if (e == null) {
				readInventory(a);
			}
		});
		net.request("/v1/shop", new JsonObject(), (a, e) -> {
			if (e == null) {
				shop = new Shop(a, System.currentTimeMillis());
			}
		});
	}

	/** Opens today's chest; {@code then} runs (on the network thread) with the chest, or null. */
	public static void open(java.util.function.Consumer<Chest> then) {
		RagoNetwork net = Hooks.network;
		if (net == null || busy) {
			return;
		}
		busy = true;
		net.request("/v1/rewards/open", new JsonObject(), (a, e) -> {
			busy = false;
			error = e;
			Chest chest = null;
			if (e == null && a.has("chest") && a.get("chest").isJsonObject()) {
				chest = new Chest(a.getAsJsonObject("chest"));
				JsonObject s = new JsonObject();
				s.addProperty("points", num(a, "points", 0));
				s.addProperty("streak", num(a, "streak", 0));
				s.addProperty("todayStreak", num(a, "streak", 0));
				s.addProperty("opened", true);
				s.add("chest", a.get("chest"));
				s.addProperty("nextReset", num(a, "nextReset", 0));
				Status old = status;
				s.addProperty("epicIn", old == null ? 0 : old.epicIn);
				s.addProperty("legendaryIn", old == null ? 0 : old.legendaryIn);
				status = new Status(s, System.currentTimeMillis());
				net.request("/v1/inventory", new JsonObject(), (inv, err) -> {
					if (err == null) {
						readInventory(inv);
					}
				});
				// the pity counters changed too
				net.request("/v1/rewards", new JsonObject(), (r, err) -> {
					if (err == null) {
						status = new Status(r, System.currentTimeMillis());
					}
				});
			}
			then.accept(chest);
		});
	}

	/** Buys {@code item} (or the week's bundle when null); {@code then} gets the error, null when bought. */
	public static void buy(String item, java.util.function.Consumer<String> then) {
		spend("/v1/shop/buy", item, then);
	}

	/** Finishes a started cosmetic with points. */
	public static void forge(String item, java.util.function.Consumer<String> then) {
		spend("/v1/forge", item, then);
	}

	private static void spend(String path, String item, java.util.function.Consumer<String> then) {
		RagoNetwork net = Hooks.network;
		if (net == null || busy) {
			return;
		}
		busy = true;
		JsonObject body = new JsonObject();
		if (item == null) {
			body.addProperty("bundle", true);
		} else {
			body.addProperty("item", item);
		}
		body.addProperty("key", UUID.randomUUID().toString().replace("-", ""));
		net.request(path, body, (a, e) -> {
			busy = false;
			if (e == null) {
				Status s = status;
				if (s != null && a.has("points")) {
					JsonObject copy = new JsonObject();
					copy.addProperty("points", a.get("points").getAsInt());
					copy.addProperty("streak", s.streak);
					copy.addProperty("todayStreak", s.todayStreak);
					copy.addProperty("opened", s.opened);
					copy.addProperty("nextReset", Math.max(0, (s.resetAt - System.currentTimeMillis()) / 1000));
					copy.addProperty("epicIn", s.epicIn);
					copy.addProperty("legendaryIn", s.legendaryIn);
					status = new Status(copy, System.currentTimeMillis());
				}
				net.request("/v1/inventory", new JsonObject(), (inv, err) -> {
					if (err == null) {
						readInventory(inv);
					}
				});
			}
			then.accept(e);
		});
	}

	// ---- answers ------------------------------------------------------------------------------

	static void readCatalogue(JsonObject a) {
		if (!a.has("items") || !a.get("items").isJsonObject()) {
			return;
		}
		Map<String, Item> items = parseItems(a.getAsJsonObject("items"));
		if (!items.isEmpty()) {
			catalogue = items;
			ownedVersion++;
		}
		Map<String, Integer> o = new HashMap<>();
		if (a.has("odds") && a.get("odds").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("odds").entrySet()) {
				o.put(e.getKey(), e.getValue().getAsInt());
			}
		}
		odds = o;
	}

	static void readInventory(JsonObject a) {
		owned = Collections.unmodifiableSet(new HashSet<>(strings(a, "owned")));
		Map<String, Integer> f = new HashMap<>();
		if (a.has("fragments") && a.get("fragments").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("fragments").entrySet()) {
				f.put(e.getKey(), e.getValue().getAsInt());
			}
		}
		fragments = Collections.unmodifiableMap(f);
		ownedKnown = true;
		ownedVersion++;
	}

	static Map<String, Item> parseItems(JsonObject items) {
		Map<String, Item> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : items.entrySet()) {
			String id = e.getKey();
			if (!e.getValue().isJsonObject() || id.indexOf(':') <= 0) {
				continue;
			}
			JsonObject spec = e.getValue().getAsJsonObject();
			String rarity = str(spec, "rarity", "common");
			if (index(RARITIES, rarity) < 0) {
				continue;
			}
			out.put(id, new Item(id, rarity, strings(spec, "sources")));
		}
		return out;
	}

	/** The catalogue shipped in the jar (a copy of the server's), until the server's comes. */
	private static Map<String, Item> builtIn() {
		try (InputStream in = Rewards.class.getResourceAsStream("/assets/rago/catalogue.json")) {
			if (in == null) {
				return Collections.emptyMap();
			}
			JsonObject o = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return parseItems(o.getAsJsonObject("items"));
		} catch (Exception e) {
			return Collections.emptyMap();
		}
	}

	private static void forgetIfAnotherAccount() {
		UUID id = gg.rago.core.RagoClient.ownId();
		if (id != null && !id.equals(owner)) {
			owner = id;
			status = null;
			owned = Collections.emptySet();
			ownedKnown = false;
			ownedVersion++;
			fragments = Collections.emptyMap();
			shop = null;
			pass = null;
		}
	}

	// ---- json ---------------------------------------------------------------------------------

	private static int index(String[] values, String v) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(v)) {
				return i;
			}
		}
		return -1;
	}

	static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString().toLowerCase(Locale.ROOT) : fallback;
	}

	static int num(JsonObject o, String key, int fallback) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	static JsonArray array(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : array(o, key)) {
			if (e.isJsonPrimitive()) {
				out.add(e.getAsString());
			}
		}
		return Collections.unmodifiableList(out);
	}
}
