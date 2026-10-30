package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.render.NeonColors;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * The Rago cape on your own player, on by default, in one of six designs; the Rago design comes in the
 * {@link NeonColors}. A cape you already own (Minecraft or OptiFine)
 * is kept unless you choose to show Rago's instead. Only this client draws it: Minecraft servers
 * do not send cosmetics, so other players see it only if their client knows you wear it.
 */
public final class RagoCape extends Module {
	public static final ResourceLocation TEXTURE = new ResourceLocation("rago", "textures/cape.png");
	/** The other designs, drawn by tools/capes.py. */
	private static final String[] DESIGNS = {"rago", "midnight", "flame", "ocean", "royal", "frost"};
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

	/** The cape {@code player} wears, given the one the game would show ({@code vanilla}, may be null). */
	public ResourceLocation capeFor(AbstractClientPlayer player, ResourceLocation vanilla) {
		if (!RagoClient.isOwn(player)) {
			return vanilla;
		}
		return vanilla == null || overOwnCape.get() ? texture() : vanilla;
	}

	private ResourceLocation texture() {
		String d = design.get();
		if (d.equals("rago")) {
			return NeonColors.texture(TEXTURE, neon.get());
		}
		for (int i = 1; i < DESIGNS.length; i++) {
			if (DESIGNS[i].equals(d)) {
				return TEXTURES[i];
			}
		}
		return TEXTURE;
	}
}
