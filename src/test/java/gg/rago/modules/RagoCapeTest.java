package gg.rago.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.network.Loadout;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Which cape another player shows: their Minecraft or OptiFine one, or their Rago cape. */
class RagoCapeTest {
	private static final ResourceLocation MINECRAFT_CAPE = new ResourceLocation("skins/abc");
	private static final ResourceLocation MFGA = new ResourceLocation("rago", "textures/capes/mfga.png");

	private static Loadout cape(String design, boolean overOwn) {
		return new Loadout(design, "red", overOwn, "none", "red", Loadout.NO_TINT, 100, "none", "red", Loadout.NO_TINT, 100,
			true, 100, "none", "red", 100, true);
	}

	@Test
	void playersWithoutRagoKeepTheirCape() {
		assertSame(MINECRAFT_CAPE, RagoCape.otherCape(null, MINECRAFT_CAPE));
		assertNull(RagoCape.otherCape(null, null));
		assertSame(MINECRAFT_CAPE, RagoCape.otherCape(cape("none", true), MINECRAFT_CAPE));
	}

	@Test
	void theRagoCapeShowsWhereThereIsNoOther() {
		assertEquals(MFGA, RagoCape.otherCape(cape("mfga", false), null));
	}

	/** The fix of 1.8: "Replace my Minecraft cape" now counts for the other Rago players too. */
	@Test
	void theRagoCapeReplacesAnotherWhenThePlayerChoseSo() {
		assertSame(MINECRAFT_CAPE, RagoCape.otherCape(cape("mfga", false), MINECRAFT_CAPE));
		assertEquals(MFGA, RagoCape.otherCape(cape("mfga", true), MINECRAFT_CAPE));
	}

	/** Every design is a cape Rago Network passes on, and every cape it passes on is a design. */
	@Test
	void theDesignsAreRagoNetworksCapes() {
		String[] network = Arrays.copyOfRange(Loadout.CAPES, 1, Loadout.CAPES.length);
		assertArrayEquals(network, RagoCape.DESIGNS);
		for (String d : RagoCape.DESIGNS) {
			assertNotNull(RagoCape.class.getResource("/assets/rago/" + RagoCape.texture(d, "red").getResourcePath()), d);
		}
	}

	/** The creator's own capes are the ones the server keeps for the creator and their other accounts. */
	@Test
	void theCreatorsCapesAreTheServersExclusiveOnes() throws IOException {
		String text = new String(Files.readAllBytes(Paths.get("server", "cosmetics.json")), StandardCharsets.UTF_8);
		JsonObject capes = new JsonParser().parse(text).getAsJsonObject().getAsJsonObject("exclusive").getAsJsonObject("cape");
		Set<String> server = new HashSet<>();
		capes.entrySet().forEach(e -> {
			server.add(e.getKey());
			Set<String> owners = new HashSet<>();
			e.getValue().getAsJsonArray().forEach(o -> owners.add(o.getAsString()));
			Set<String> expected = new HashSet<>(RagoCape.CREATOR_ACCOUNTS);
			expected.add("creator");
			assertEquals(expected, owners, e.getKey());
		});
		assertEquals(server, RagoCape.CREATOR_ONLY);
	}
}
