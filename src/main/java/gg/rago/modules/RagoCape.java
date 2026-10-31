package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.hooks.Hooks;
import gg.rago.network.Loadout;
import gg.rago.network.NetworkClient;
import gg.rago.render.HdTexture;
import gg.rago.render.NeonColors;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The Rago cape on your own player, on by default, in one of ten designs; the Rago design comes in the
 * {@link NeonColors}. The other designs are big pictures shown smooth ({@link HdTexture}), read the
 * first time they are worn. A cape you already own (Minecraft or OptiFine) is kept unless you choose to show
 * Rago's instead. Minecraft servers do not send cosmetics: other Rago players see your cape through
 * {@link RagoNetwork}, and you see theirs, with that choice too (players without Rago see your
 * Minecraft or OptiFine cape).
 */
public final class RagoCape extends Module {
	public static final ResourceLocation TEXTURE = new ResourceLocation("rago", "textures/cape.png");
	/**
	 * The designs (Rago Network's capes but "none"); the others are drawn by tools/capes.py and, in HD,
	 * tools/hd_capes.py.
	 */
	static final String[] DESIGNS = {"rago", "midnight", "flame", "ocean", "royal", "frost", "black_cat", "pixel_cat", "night_cat",
		"qrago"};
	/**
	 * The designs only the creator of Rago wears: their Discord picture and their photo. Rago Network
	 * shows them only on the creator (server/cosmetics.json, "exclusive"); the menu offers them to no one else.
	 */
	static final Set<String> CREATOR_ONLY = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("black_cat", "qrago")));
	private static final ResourceLocation[] TEXTURES = new ResourceLocation[DESIGNS.length];

	static {
		TEXTURES[0] = TEXTURE;
		for (int i = 1; i < DESIGNS.length; i++) {
			TEXTURES[i] = new ResourceLocation("rago", "textures/capes/" + DESIGNS[i] + ".png");
		}
	}

	private final ChoiceOption design = addOption(new ChoiceOption("cape_design", "rago", DESIGNS)
		.allowIf(id -> !CREATOR_ONLY.contains(id) || "creator".equals(role())));
	private final ChoiceOption neon = addOption(new ChoiceOption("cape_neon", "red", NeonColors.IDS));
	private final BooleanOption overOwnCape = addOption(new BooleanOption("cape_over_own", false));

	public RagoCape() {
		super("rago_cape", FeatureClass.VISUAL, true);
		neon.showIf(() -> design.is("rago"));
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/**
	 * The cape {@code player} wears, given the one the game would show ({@code vanilla}: Minecraft's or
	 * OptiFine's, may be null): yours while this is on; another Rago player's from Rago Network when
	 * they have no other cape, or over theirs when they chose to replace it.
	 */
	public ResourceLocation capeFor(AbstractClientPlayer player, ResourceLocation vanilla) {
		if (RagoClient.isOwn(player)) {
			return isEnabled() && (vanilla == null || overOwnCape.get()) ? shown(texture(design.get(), neon.get()), vanilla) : vanilla;
		}
		RagoNetwork network = Hooks.network;
		NetworkClient.Player p = network == null ? null : network.player(player.getUniqueID());
		return shown(otherCape(p == null ? null : p.loadout, vanilla), vanilla);
	}

	/** Another player's cape from their loadout (null: not a Rago player) and the one the game would show. */
	static ResourceLocation otherCape(Loadout loadout, ResourceLocation vanilla) {
		if (loadout == null || loadout.cape.equals("none") || vanilla != null && !loadout.capeOverOwn) {
			return vanilla;
		}
		return texture(loadout.cape, loadout.capeNeon);
	}

	/**
	 * {@code cape} once it can be drawn: a design's picture is read the first time it is worn, and until
	 * then the player shows {@code vanilla} (their own cape, or none) rather than the game stalling.
	 */
	private static ResourceLocation shown(ResourceLocation cape, ResourceLocation vanilla) {
		for (int i = 1; i < TEXTURES.length; i++) {
			if (TEXTURES[i].equals(cape)) {
				ResourceLocation ready = HdTexture.ready(cape);
				return ready != null ? ready : vanilla;
			}
		}
		return cape;
	}

	/** Your role on Rago Network (the creator of Rago wears designs no one else does), null if not signed in. */
	private static String role() {
		RagoNetwork network = Hooks.network;
		return network == null ? null : network.ownRole();
	}

	/** Whether your Rago cape is shown over your Minecraft or OptiFine cape, for you and other Rago players. */
	public boolean overOwn() {
		return overOwnCape.get();
	}

	/** The design you chose. */
	public String design() {
		return design.get();
	}

	/** The colour of the Rago design. */
	public String neon() {
		return neon.get();
	}

	static ResourceLocation texture(String d, String neonId) {
		if (d.equals("rago")) {
			return NeonColors.texture(TEXTURE, neonId);
		}
		for (int i = 1; i < DESIGNS.length; i++) {
			if (DESIGNS[i].equals(d)) {
				return TEXTURES[i];
			}
		}
		return TEXTURE;
	}
}
