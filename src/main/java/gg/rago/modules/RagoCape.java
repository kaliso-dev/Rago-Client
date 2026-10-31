package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.hooks.Hooks;
import gg.rago.network.NetworkClient;
import gg.rago.render.NeonColors;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * The Rago cape on your own player, on by default, in one of six designs; the Rago design comes in the
 * {@link NeonColors}. A cape you already own (Minecraft or OptiFine) is kept unless you choose to show
 * Rago's instead. Minecraft servers do not send cosmetics: other Rago players see your cape through
 * {@link RagoNetwork}, and you see theirs (on players who have no Minecraft cape of their own).
 */
public final class RagoCape extends Module {
	public static final ResourceLocation TEXTURE = new ResourceLocation("rago", "textures/cape.png");
	/** The designs (Rago Network's capes but "none"); the others are drawn by tools/capes.py. */
	static final String[] DESIGNS = {"rago", "midnight", "flame", "ocean", "royal", "frost"};
	private static final ResourceLocation[] TEXTURES = new ResourceLocation[DESIGNS.length];

	static {
		TEXTURES[0] = TEXTURE;
		for (int i = 1; i < DESIGNS.length; i++) {
			TEXTURES[i] = new ResourceLocation("rago", "textures/capes/" + DESIGNS[i] + ".png");
		}
	}

	private final ChoiceOption design = addOption(new ChoiceOption("cape_design", "rago", DESIGNS));
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
	 * The cape {@code player} wears, given the one the game would show ({@code vanilla}, may be null):
	 * yours while this is on, another Rago player's from Rago Network when they have no other cape.
	 */
	public ResourceLocation capeFor(AbstractClientPlayer player, ResourceLocation vanilla) {
		if (RagoClient.isOwn(player)) {
			return isEnabled() && (vanilla == null || overOwnCape.get()) ? texture(design.get(), neon.get()) : vanilla;
		}
		RagoNetwork network = Hooks.network;
		if (vanilla != null || network == null) {
			return vanilla;
		}
		NetworkClient.Player p = network.player(player.getUniqueID());
		return p == null || p.loadout.cape.equals("none") ? null : texture(p.loadout.cape, p.loadout.capeNeon);
	}

	/** The design you chose. */
	public String design() {
		return design.get();
	}

	/** The colour of the Rago design. */
	public String neon() {
		return neon.get();
	}

	private static ResourceLocation texture(String d, String neonId) {
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
