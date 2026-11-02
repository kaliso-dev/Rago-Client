package gg.rago.profile;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The profiles that come with Rago: in everyone's list, never switched to on their own. */
class BuiltInProfileTest {
	private static final String NAME = "qRago Official";
	private static final String ICON = "official:qrago";

	private static String creatorsCode() {
		ProfileShareTest.Player creator = new ProfileShareTest.Player();
		creator.fps.size.set(8);
		creator.shop.setEnabled(true);
		creator.cape.design.set("black_cat");
		return creator.profiles.exportCode("Default", creator.registry);
	}

	@Test
	void itIsOfferedOnceWithoutBeingSwitchedTo() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.clock.setEnabled(true);
		me.cape.design.set("primal");
		assertTrue(me.profiles.offer(me.registry, NAME, ICON, creatorsCode()));
		assertEquals(5, me.profiles.names().size());
		assertEquals(NAME, me.profiles.names().get(4), "after the player's own profiles");
		assertEquals("Default", me.profiles.active(), "offered, not switched to");
		assertTrue(me.clock.isEnabled(), "my settings untouched");
		assertEquals(5, (int) me.fps.size.get());
		assertEquals(ICON, me.profiles.icon(NAME));

		me.profiles.switchTo(NAME, me.registry);
		assertEquals(8, (int) me.fps.size.get());
		assertTrue(me.shop.isEnabled());
		assertFalse(me.clock.isEnabled());
		assertEquals("primal", me.cape.design.get(), "the creator's cape is not handed out");

		assertTrue(me.profiles.offer(me.registry, NAME, ICON, creatorsCode()));
		assertEquals(5, me.profiles.names().size(), "not added twice");
	}

	@Test
	void aDeletedOneDoesNotComeBack() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.profiles.offer(me.registry, NAME, ICON, creatorsCode());
		assertTrue(me.profiles.delete(NAME, me.registry));
		JsonObject saved = me.profiles.toJson();

		ModProfiles restarted = new ModProfiles();
		restarted.fromJson(saved);
		restarted.ensureDefaults(me.registry);
		assertTrue(restarted.offer(me.registry, NAME, ICON, creatorsCode()));
		assertFalse(restarted.names().contains(NAME));
	}

	@Test
	void aMyOwnProfileWithTheSameNameIsKept() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.profiles.switchTo("Bedwars", me.registry);
		assertTrue(me.profiles.rename("Bedwars", NAME));
		me.profiles.offer(me.registry, NAME, ICON, creatorsCode());
		assertEquals("bed", me.profiles.icon(NAME));
	}

	@Test
	void anUnreadableCodeIsOfferedAgainLater() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		assertFalse(me.profiles.offer(me.registry, NAME, ICON, null));
		assertFalse(me.profiles.offer(me.registry, NAME, ICON, "RAGO1:abcd"));
		assertFalse(me.profiles.names().contains(NAME));
		assertTrue(me.profiles.offer(me.registry, NAME, ICON, creatorsCode()));
		assertTrue(me.profiles.names().contains(NAME));
	}

	@Test
	void onlyRagosOwnProfilesShowAPicture() {
		assertTrue(ModProfiles.isPicture(ICON));
		assertFalse(ModProfiles.isPicture("official:../../evil"));
		assertFalse(ModProfiles.isPicture("user"));
		assertFalse(ModProfiles.isPicture(null));
		// a code cannot bring the picture with it
		String code = ProfileShare.encode(NAME, ICON, new JsonObject());
		assertEquals("user", ProfileShare.decode(code).icon);
	}

	/** An update to the built-in profile: the clock on and the FPS counter at size 9. */
	private static String update() {
		JsonObject clock = new JsonObject();
		clock.addProperty("enabled", true);
		JsonObject size = new JsonObject();
		size.addProperty("size", 9);
		JsonObject fps = new JsonObject();
		fps.add("options", size);
		JsonObject changes = new JsonObject();
		changes.add("clock", clock);
		changes.add("fps", fps);
		return ProfileShare.encode("Update", "user", changes);
	}

	/** The profiles as Rago 1.10.6 and earlier saved them: offered, without revisions. */
	private static ModProfiles savedByAnOlderRago(ProfileShareTest.Player me) {
		JsonObject saved = me.profiles.toJson();
		saved.remove("revisions");
		ModProfiles older = new ModProfiles();
		older.fromJson(saved);
		older.ensureDefaults(me.registry);
		return older;
	}

	@Test
	void anUpdateReachesACopyFromAnOlderRagoOnceAndKeepsTheRest() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.profiles.offer(me.registry, NAME, ICON, creatorsCode());
		me.profiles.switchTo(NAME, me.registry);
		me.shop.setEnabled(false); // the player's own change to the profile
		me.profiles.switchTo("Default", me.registry);

		ModProfiles profiles = savedByAnOlderRago(me);
		assertTrue(profiles.update(me.registry, NAME, 2, update()));
		assertEquals("Default", profiles.active(), "not switched to");
		assertFalse(me.clock.isEnabled(), "the active profile is not touched");
		profiles.switchTo(NAME, me.registry);
		assertTrue(me.clock.isEnabled(), "turned on by the update");
		assertEquals(9, (int) me.fps.size.get(), "set by the update");
		assertFalse(me.shop.isEnabled(), "the player's own change is kept");

		me.clock.setEnabled(false);
		profiles.switchTo("Default", me.registry);
		ModProfiles restarted = new ModProfiles();
		restarted.fromJson(profiles.toJson());
		restarted.ensureDefaults(me.registry);
		assertTrue(restarted.update(me.registry, NAME, 2, update()));
		restarted.switchTo(NAME, me.registry);
		assertFalse(me.clock.isEnabled(), "an update is applied once");
	}

	@Test
	void anUpdateReachesTheActiveProfileAtOnce() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.profiles.offer(me.registry, NAME, ICON, creatorsCode());
		me.profiles.switchTo(NAME, me.registry);

		ModProfiles profiles = savedByAnOlderRago(me);
		assertTrue(profiles.update(me.registry, NAME, 2, update()));
		assertTrue(me.clock.isEnabled());
		assertEquals(9, (int) me.fps.size.get());
		assertTrue(me.shop.isEnabled(), "the rest of the profile as it was");
	}

	@Test
	void aNewPlayerGetsTheFullProfileWithoutTheUpdateOnTop() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.profiles.offer(me.registry, NAME, ICON, creatorsCode());
		assertTrue(me.profiles.update(me.registry, NAME, ModProfiles.latestRevision(NAME), update()));
		me.profiles.switchTo(NAME, me.registry);
		assertFalse(me.clock.isEnabled(), "the full code already holds every update");
		assertEquals(8, (int) me.fps.size.get());
	}

	@Test
	void anUpdateBringsBackNoDeletedProfileAndLeavesMyOwnAlone() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.profiles.offer(me.registry, NAME, ICON, creatorsCode());
		me.profiles.delete(NAME, me.registry);
		ModProfiles profiles = savedByAnOlderRago(me);
		assertTrue(profiles.update(me.registry, NAME, 2, update()));
		assertFalse(profiles.names().contains(NAME));

		ProfileShareTest.Player you = new ProfileShareTest.Player();
		you.profiles.switchTo("Bedwars", you.registry);
		you.profiles.rename("Bedwars", NAME);
		you.profiles.offer(you.registry, NAME, ICON, creatorsCode());
		ModProfiles yours = savedByAnOlderRago(you);
		assertTrue(yours.update(you.registry, NAME, 2, update()));
		assertEquals(NAME, yours.active());
		assertFalse(you.clock.isEnabled(), "a profile of my own with that name is not changed");
	}

	@Test
	void anUnreadableUpdateIsTriedAgainLater() {
		ProfileShareTest.Player me = new ProfileShareTest.Player();
		me.profiles.offer(me.registry, NAME, ICON, creatorsCode());
		ModProfiles profiles = savedByAnOlderRago(me);
		assertFalse(profiles.update(me.registry, NAME, 2, null));
		assertFalse(profiles.update(me.registry, NAME, 2, "RAGO1:abcd"));
		assertTrue(profiles.update(me.registry, NAME, 2, update()));
		profiles.switchTo(NAME, me.registry);
		assertTrue(me.clock.isEnabled());
	}

	@Test
	void theOfficialProfileShowsGeneratorTimersInTheTabList() {
		JsonObject full = ProfileShare.decode(ModProfiles.resource(ModProfiles.BUILT_IN[0][2])).changes;
		JsonObject gen = full.getAsJsonObject("gen_timers");
		assertTrue(gen.get("enabled").getAsBoolean());
		assertEquals("tab", gen.getAsJsonObject("options").get("timers_display").getAsString());

		ProfileShare.Shared r2 = ProfileShare.decode(ModProfiles.resource(ModProfiles.UPDATES[0][2]));
		assertNotNull(r2);
		assertEquals(NAME, ModProfiles.UPDATES[0][0]);
		assertEquals(1, r2.changes.entrySet().size(), "the update changes Generator Timers only");
		assertEquals(gen, r2.changes.getAsJsonObject("gen_timers"));
		assertEquals(2, ModProfiles.latestRevision(NAME));
	}

	@Test
	void everyUpdateIsInTheFullCode() {
		for (String[] u : ModProfiles.UPDATES) {
			JsonObject changes = ProfileShare.decode(ModProfiles.resource(u[2])).changes;
			String full = null;
			for (String[] b : ModProfiles.BUILT_IN) {
				if (b[0].equals(u[0])) {
					full = b[2];
				}
			}
			assertNotNull(full, u[0] + " is a built-in profile");
			JsonObject all = ProfileShare.decode(ModProfiles.resource(full)).changes;
			for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : changes.entrySet()) {
				JsonObject in = all.getAsJsonObject(e.getKey());
				assertNotNull(in, e.getKey());
				for (java.util.Map.Entry<String, com.google.gson.JsonElement> part : e.getValue().getAsJsonObject().entrySet()) {
					if (part.getValue().isJsonObject()) {
						for (java.util.Map.Entry<String, com.google.gson.JsonElement> o : part.getValue().getAsJsonObject().entrySet()) {
							assertEquals(o.getValue(), in.getAsJsonObject(part.getKey()).get(o.getKey()), e.getKey() + " " + o.getKey());
						}
					} else {
						assertEquals(part.getValue(), in.get(part.getKey()), e.getKey() + " " + part.getKey());
					}
				}
			}
		}
	}

	@Test
	void everyBuiltInProfileCanBeReadAndHasItsPicture() {
		for (String[] b : ModProfiles.BUILT_IN) {
			assertNotNull(ProfileShare.decode(ModProfiles.resource(b[2])), b[2]);
			String picture = "src/main/resources/assets/rago/textures/profiles/"
				+ b[1].substring("official:".length()) + "_official.png";
			assertTrue(Files.isRegularFile(Paths.get(picture)), picture);
		}
	}
}
