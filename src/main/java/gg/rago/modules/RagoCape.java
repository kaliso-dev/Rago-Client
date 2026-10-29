package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * The Rago cape on your own player, on by default. A cape you already own (Minecraft or OptiFine)
 * is kept unless you choose to show Rago's instead. Only this client draws it: Minecraft servers
 * do not send cosmetics, so other players see it only if their client knows you wear it.
 */
public final class RagoCape extends Module {
	public static final ResourceLocation TEXTURE = new ResourceLocation("rago", "textures/cape.png");

	private final BooleanOption overOwnCape = addOption(new BooleanOption("cape_over_own", false));

	public RagoCape() {
		super("rago_cape", FeatureClass.VISUAL, true);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** The cape {@code player} wears, given the one the game would show ({@code vanilla}, may be null). */
	public ResourceLocation capeFor(AbstractClientPlayer player, ResourceLocation vanilla) {
		Minecraft mc = Minecraft.getMinecraft();
		if (player != mc.thePlayer && !player.getUniqueID().equals(mc.getSession().getProfile().getId())) {
			return vanilla;
		}
		return vanilla == null || overOwnCape.get() ? TEXTURE : vanilla;
	}
}
