package gg.rago.profile;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileBackupTest {
	private static JsonObject extras() {
		JsonObject j = new JsonObject();
		j.addProperty("explosion_particles", true);
		j.addProperty("spell_particles", true);
		j.addProperty("entity_shadow_distance", 16);
		j.addProperty("leaf_quality", "FANCY");
		j.addProperty("fxaa", true);
		return j;
	}

	@Test
	void applyThenRestoreGivesBackTheOriginal() {
		JsonObject original = extras();
		JsonObject target = extras();
		JsonObject old = ProfileBackup.apply(target, CompetitivePreset.EXTRAS);
		assertFalse(target.get("explosion_particles").getAsBoolean());
		assertEquals(0, target.get("entity_shadow_distance").getAsInt());
		assertEquals("FAST", target.get("leaf_quality").getAsString());
		assertTrue(target.get("fxaa").getAsBoolean(), "keys outside the preset are untouched");
		ProfileBackup.restore(target, old);
		assertEquals(original, target);
	}

	@Test
	void keysThePlayerNeverHadAreRemovedOnRestore() {
		JsonObject target = new JsonObject();
		JsonObject old = ProfileBackup.apply(target, CompetitivePreset.EXTRAS);
		ProfileBackup.restore(target, old);
		assertEquals(0, target.size());
	}

	@Test
	void presetKeepsPotionParticles() {
		assertFalse(CompetitivePreset.EXTRAS.containsKey("spell_particles"));
	}

	@Test
	void backupSurvivesAFileRoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sub").resolve("backup.json");
		JsonObject backup = new JsonObject();
		backup.add("extras", ProfileBackup.apply(new JsonObject(), CompetitivePreset.EXTRAS));
		backup.add("vanilla", ProfileBackup.apply(extras(), CompetitivePreset.VANILLA));
		ProfileBackup.write(file, backup);
		assertEquals(backup, ProfileBackup.read(file));
		assertNull(ProfileBackup.read(dir.resolve("missing.json")));
	}

	@Test
	void camelCaseMatchesArgentumFieldNames() {
		assertEquals("entityShadowDistance", ProfileBackup.camelCase("entity_shadow_distance"));
		assertEquals("smokeAndFlameParticles", ProfileBackup.camelCase("smoke_and_flame_particles"));
		assertEquals("stars", ProfileBackup.camelCase("stars"));
	}
}
