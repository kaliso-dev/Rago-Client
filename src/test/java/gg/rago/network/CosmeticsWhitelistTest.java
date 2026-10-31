package gg.rago.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The server's server/cosmetics.json and the client's {@link Loadout} must allow exactly the same things. */
class CosmeticsWhitelistTest {
	private static JsonObject fields() throws IOException {
		String text = new String(Files.readAllBytes(Paths.get("server", "cosmetics.json")), StandardCharsets.UTF_8);
		return new JsonParser().parse(text).getAsJsonObject().getAsJsonObject("fields");
	}

	private static String[] choices(String field) {
		switch (field) {
			case "cape":
				return Loadout.CAPES;
			case "hat":
				return Loadout.HATS;
			case "wings":
				return Loadout.WINGS;
			case "shield":
				return Loadout.SHIELDS;
			default:
				return Loadout.NEON;
		}
	}

	private static int[] range(String field) {
		switch (field) {
			case "hat_scale":
				return new int[] {Loadout.HAT_SCALE_MIN, Loadout.HAT_SCALE_MAX};
			case "wings_scale":
				return new int[] {Loadout.WINGS_SCALE_MIN, Loadout.WINGS_SCALE_MAX};
			case "wings_flap_speed":
				return new int[] {Loadout.FLAP_SPEED_MIN, Loadout.FLAP_SPEED_MAX};
			default:
				return new int[] {Loadout.SHIELD_SCALE_MIN, Loadout.SHIELD_SCALE_MAX};
		}
	}

	@Test
	void theSameFields() throws IOException {
		Set<String> server = new HashSet<>();
		for (Map.Entry<String, JsonElement> e : fields().entrySet()) {
			server.add(e.getKey());
		}
		Set<String> client = new HashSet<>();
		for (Map.Entry<String, JsonElement> e : Loadout.NONE.toJson().entrySet()) {
			client.add(e.getKey());
		}
		assertEquals(server, client);
	}

	@Test
	void theSameChoicesRangesAndDefaults() throws IOException {
		JsonObject none = Loadout.NONE.toJson();
		for (Map.Entry<String, JsonElement> e : fields().entrySet()) {
			String field = e.getKey();
			JsonObject spec = e.getValue().getAsJsonObject();
			String type = spec.get("type").getAsString();
			assertEquals(spec.get("default"), none.get(field), "default of " + field);
			if (type.equals("choice")) {
				List<String> values = new ArrayList<>();
				for (JsonElement v : (JsonArray) spec.get("values")) {
					values.add(v.getAsString());
				}
				assertEquals(Arrays.asList(choices(field)), values, "choices of " + field);
			} else if (type.equals("int")) {
				assertArrayEquals(new int[] {spec.get("min").getAsInt(), spec.get("max").getAsInt()}, range(field), "range of " + field);
			}
		}
	}
}
