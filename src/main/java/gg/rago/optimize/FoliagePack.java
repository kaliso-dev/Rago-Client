package gg.rago.optimize;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A built-in resource pack that swaps the block models of grass, ferns, flowers and dead bushes
 * for an empty model ({@code assets/rago/models/block/empty.json}), so they are not drawn at all.
 * It sits under every other pack, but blockstate files from higher packs replace ours only if a
 * pack ships its own foliage blockstates, which PvP packs rarely do.
 */
final class FoliagePack implements IResourcePack {
	static final Set<String> GRASS = ImmutableSet.of("tall_grass", "fern", "double_grass", "double_fern");
	static final Set<String> FLOWERS = ImmutableSet.of("dandelion", "poppy", "blue_orchid", "allium", "houstonia",
		"red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "sunflower", "syringa", "double_rose", "paeonia");
	static final Set<String> DEAD_BUSH = ImmutableSet.of("dead_bush");

	private final BooleanSupplier grass;
	private final BooleanSupplier flowers;
	private final BooleanSupplier deadBush;

	FoliagePack(BooleanSupplier grass, BooleanSupplier flowers, BooleanSupplier deadBush) {
		this.grass = grass;
		this.flowers = flowers;
		this.deadBush = deadBush;
	}

	private boolean hides(ResourceLocation rl) {
		if (!"minecraft".equals(rl.getResourceDomain())) {
			return false;
		}
		String p = rl.getResourcePath();
		if (!p.startsWith("blockstates/") || !p.endsWith(".json")) {
			return false;
		}
		String name = p.substring("blockstates/".length(), p.length() - ".json".length());
		return grass.getAsBoolean() && GRASS.contains(name)
			|| flowers.getAsBoolean() && FLOWERS.contains(name)
			|| deadBush.getAsBoolean() && DEAD_BUSH.contains(name);
	}

	@Override
	public InputStream getInputStream(ResourceLocation rl) throws IOException {
		if (!hides(rl)) {
			throw new FileNotFoundException(rl.toString());
		}
		JsonObject state;
		try (InputStream in = Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(rl)) {
			state = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		JsonElement variants = state.get("variants");
		if (variants != null && variants.isJsonObject()) {
			for (Map.Entry<String, JsonElement> v : variants.getAsJsonObject().entrySet()) {
				emptyModels(v.getValue());
			}
		}
		return new ByteArrayInputStream(state.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static void emptyModels(JsonElement variant) {
		if (variant.isJsonObject()) {
			JsonObject o = variant.getAsJsonObject();
			o.addProperty("model", "rago:empty");
			o.remove("x");
			o.remove("y");
		} else if (variant.isJsonArray()) {
			variant.getAsJsonArray().forEach(FoliagePack::emptyModels);
		}
	}

	@Override
	public boolean resourceExists(ResourceLocation rl) {
		return hides(rl);
	}

	@Override
	public Set<String> getResourceDomains() {
		return ImmutableSet.of("minecraft");
	}

	@Override
	public <T extends IMetadataSection> T getPackMetadata(IMetadataSerializer serializer, String section) {
		return null;
	}

	@Override
	public BufferedImage getPackImage() throws IOException {
		throw new FileNotFoundException("pack.png");
	}

	@Override
	public String getPackName() {
		return "Rago foliage";
	}
}
