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
