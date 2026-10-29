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
		j.addProperty("ofAnimatedExplosion", true);
		j.addProperty("ofPotionParticles", true);
		j.addProperty("ofClouds", 0);
		j.addProperty("ofTrees", 2);
		j.addProperty("ofFastRender", true);
		return j;
	}

	@Test
	void applyThenRestoreGivesBackTheOriginal() {
		JsonObject original = extras();
		JsonObject target = extras();
		JsonObject old = ProfileBackup.apply(target, CompetitivePreset.OPTIFINE);
		assertFalse(target.get("ofAnimatedExplosion").getAsBoolean());
		assertEquals(3, target.get("ofClouds").getAsInt());
		assertEquals(1, target.get("ofTrees").getAsInt());
		assertTrue(target.get("ofFastRender").getAsBoolean(), "keys outside the preset are untouched");
		ProfileBackup.restore(target, old);
		assertEquals(original, target);
	}

	@Test
	void keysThePlayerNeverHadAreRemovedOnRestore() {
		JsonObject target = new JsonObject();
		JsonObject old = ProfileBackup.apply(target, CompetitivePreset.OPTIFINE);
		ProfileBackup.restore(target, old);
		assertEquals(0, target.entrySet().size());
	}

	@Test
	void presetKeepsPotionParticles() {
		assertFalse(CompetitivePreset.OPTIFINE.containsKey("ofPotionParticles"));
	}

	@Test
	void backupSurvivesAFileRoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sub").resolve("backup.json");
		JsonObject backup = new JsonObject();
		backup.add("extras", ProfileBackup.apply(new JsonObject(), CompetitivePreset.OPTIFINE));
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
