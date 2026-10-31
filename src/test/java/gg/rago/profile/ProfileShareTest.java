package gg.rago.profile;

import com.google.gson.JsonObject;
import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.ModuleRegistry;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.gui.FontRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sending a profile to a friend as a code, and importing it on their side. */
class ProfileShareTest {
	private static final String DICTIONARY_SHA256 = "ac049e2859c5d9a3cd537579caba9e426b7ba653d395a4ee57eb8bc3005553d0";

	static final class Hud extends HudElement {
		final IntOption size = addOption(new IntOption("size", 5, 1, 10));

		Hud(String id, boolean on) {
			super(id, FeatureClass.INFORMATIONAL, on, Anchor.TOP_LEFT, 4, 4);
		}

		@Override
		public int width(FontRenderer text, boolean placeholder) {
			return 10;
		}

		@Override
		public int height(FontRenderer text, boolean placeholder) {
			return 10;
		}

		@Override
		public void render(FontRenderer text, boolean placeholder) {
		}
	}

	static final class Plain extends Module {
		final BooleanOption flag = addOption(new BooleanOption("flag", false));

		Plain(String id, FeatureClass c) {
			super(id, c, false);
		}
	}

	static final class Cape extends Module {
		final ChoiceOption design = addOption(new ChoiceOption("cape_design", "rago", "rago", "black_cat", "primal"));

		Cape() {
			super("rago_cape", FeatureClass.VISUAL, true);
		}
	}

	/** One player's mods: the same ones on both sides, as both run the same Rago. */
	static final class Player {
		final Hud fps = new Hud("fps", true);
		final Hud clock = new Hud("clock", false);
		final Plain shop = new Plain("fast_shop", FeatureClass.INPUT);
		final Plain auto = new Plain("auto_messages", FeatureClass.AUTO_CHAT);
		final Cape cape = new Cape();
		final ModuleRegistry registry = new ModuleRegistry();
		final ModProfiles profiles = new ModProfiles();

		Player() {
			for (Module m : Arrays.asList(fps, clock, shop, auto, cape)) {
				registry.register(m);
			}
			profiles.ensureDefaults(registry);
		}
	}

	@Test
	void aFriendGetsTheProfileButNotThePersonalMods() {
		Player friend = new Player();
		friend.profiles.switchTo("Bedwars", friend.registry);
		friend.fps.size.set(8);
		friend.fps.position().moveTo(100, 50, 10, 10, 400, 300);
		friend.shop.setEnabled(true);
		friend.auto.setEnabled(true);
		friend.cape.design.set("black_cat");
		String code = friend.profiles.exportCode("Bedwars", friend.registry);
		assertTrue(code.startsWith(ProfileShare.PREFIX), code);

		ProfileShare.Shared shared = ProfileShare.decode(code);
		assertNotNull(shared);
		assertEquals("Bedwars", shared.name);
		assertEquals("bed", shared.icon);
		assertFalse(shared.changes.has("rago_cape"), "the cape stays the friend's");
		assertFalse(shared.changes.has("auto_messages"), "messages sent for them stay theirs");
		assertFalse(shared.changes.has("clock"), "only what differs from the defaults");

		Player me = new Player();
		me.clock.setEnabled(true);
		me.fps.size.set(3);
		me.cape.design.set("primal");
		String name = me.profiles.importCode("look: " + code + " (my bedwars setup)", me.registry);
		assertEquals("Bedwars 2", name, "a free name next to my own Bedwars profile");
		assertEquals(name, me.profiles.active());
		assertEquals(8, (int) me.fps.size.get());
		assertEquals(friend.fps.position().toJson(), me.fps.position().toJson());
		assertTrue(me.shop.isEnabled());
		assertFalse(me.clock.isEnabled(), "the friend's profile, not a mix with mine");
		assertFalse(me.auto.isEnabled());
		assertEquals("primal", me.cape.design.get(), "my cape stays mine");
		assertEquals("bed", me.profiles.icon(name));

		// my own profile is kept as it was
		me.profiles.switchTo("Default", me.registry);
		assertTrue(me.clock.isEnabled());
		assertEquals(3, (int) me.fps.size.get());
	}

	@Test
	void anUntouchedProfileGivesAShortCode() {
		Player p = new Player();
		String code = p.profiles.exportCode("Default", p.registry);
		assertTrue(code.length() < 120, code);
		assertEquals(0, ProfileShare.decode(code).changes.entrySet().size());
		assertNull(p.profiles.exportCode("Nope", p.registry));
	}

	@Test
	void damagedOrForeignTextIsNotACode() throws Exception {
		Player p = new Player();
		for (String text : new String[] {null, "", "hello", "RAGO1:", "RAGO1:!!!!", "RAGO1:abcd", "RAGO2:H4sIAAAAAAAAA"}) {
			assertNull(ProfileShare.decode(text), String.valueOf(text));
			assertNull(p.profiles.importCode(text, p.registry), String.valueOf(text));
		}
		assertEquals(4, p.profiles.names().size(), "nothing added");
		// a code that unpacks to far more than any profile
		String bomb = ProfileShare.pack(new byte[4 << 20]);
		assertTrue(bomb.length() < 10_000);
		assertNull(ProfileShare.decode(bomb));
		// packed without Rago's dictionary
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
			gz.write("{\"name\":\"x\",\"modules\":{}}".getBytes(StandardCharsets.UTF_8));
		}
		assertNull(ProfileShare.decode(ProfileShare.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())));
		// valid JSON without modules
		assertNull(ProfileShare.decode(pack("{\"v\":1,\"name\":\"x\"}")));
	}

	@Test
	void namesAndIconsFromACodeAreCleaned() throws Exception {
		ProfileShare.Shared s = ProfileShare.decode(pack("{\"name\":\"§c§lMy\\u0007 very very long profile name here\",\"icon\":\"../../evil\",\"modules\":{}}"));
		assertEquals("My very very long profil", s.name);
		assertEquals(ProfileShare.MAX_NAME, s.name.length());
		assertEquals("user", s.icon);
		assertEquals("Imported", ProfileShare.decode(pack("{\"name\":\"§a\",\"modules\":{}}")).name);
	}

	@Test
	void freeNamesStayShortEnough() {
		assertEquals("Bedwars", ProfileShare.freeName("Bedwars", Arrays.asList("Default")));
		assertEquals("Bedwars 3", ProfileShare.freeName("Bedwars", Arrays.asList("Bedwars", "Bedwars 2")));
		String full = "abcdefghijklmnopqrstuvwx";
		assertEquals("abcdefghijklmnopqrstuv 2", ProfileShare.freeName(full, Arrays.asList(full)));
	}

	@Test
	void unknownModsAndOptionsFromANewerRagoAreIgnored() throws Exception {
		Player p = new Player();
		String name = p.profiles.importCode(pack("{\"name\":\"New\",\"modules\":{\"future_mod\":{\"enabled\":true},"
			+ "\"fps\":{\"options\":{\"size\":7,\"future_option\":1}},\"rago_cape\":{\"options\":{\"cape_design\":\"black_cat\"}}}}"),
			p.registry);
		assertEquals("New", name);
		assertEquals(7, (int) p.fps.size.get());
		assertEquals("rago", p.cape.design.get(), "a code cannot hand out capes");
		assertFalse(ProfileShare.merge(new JsonObject(), new JsonObject(), new JsonObject()).has("future_mod"));
	}

	@Test
	void exportedFilesCanBeImported(@TempDir Path config) throws Exception {
		Player friend = new Player();
		friend.fps.size.set(9);
		String code = friend.profiles.exportCode("Default", friend.registry);
		String written = ProfileFiles.write(config, "Default", code);
		assertEquals("config/rago-profiles/Default.txt", written);
		Path file = config.resolve("rago-profiles/Default.txt");
		assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(code));

		assertNull(ProfileFiles.newestImport(config));
		Files.copy(file, ProfileFiles.importFolder(config).resolve("message.txt"));
		Path found = ProfileFiles.newestImport(config);
		assertNotNull(found);
		Player me = new Player();
		assertEquals("Default 2", me.profiles.importCode(ProfileFiles.read(found), me.registry));
		assertEquals(9, (int) me.fps.size.get());
		ProfileFiles.markImported(found);
		assertNull(ProfileFiles.newestImport(config), "not taken twice");
		assertEquals("My_Profile__", ProfileFiles.fileName("My/Profile:*"));
	}

	@Test
	void theDictionaryNeverChanges() throws Exception {
		// codes made by any Rago since 1.8.8 are read with this exact file
		byte[] dict = Files.readAllBytes(java.nio.file.Paths.get("src/main/resources" + ProfileShare.DICTIONARY));
		byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(dict);
		StringBuilder hex = new StringBuilder();
		for (byte b : hash) {
			hex.append(String.format("%02x", b));
		}
		assertEquals(DICTIONARY_SHA256, hex.toString());
	}

	private static String pack(String json) {
		return ProfileShare.pack(json.getBytes(StandardCharsets.UTF_8));
	}
}
