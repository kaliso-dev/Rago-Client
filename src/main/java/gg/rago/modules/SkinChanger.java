package gg.rago.modules;

import gg.rago.accounts.Skins;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Wears the skin picked in the locker (home screen, shirt button) on your own player, in this
 * client only. The locker can also make it your account's skin, for everyone.
 */
public final class SkinChanger extends Module {
	public SkinChanger() {
		super("skin_changer", FeatureClass.VISUAL, true);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	private static Skins.Skin wornBy(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getMinecraft();
		if (player != mc.thePlayer && !player.getUniqueID().equals(mc.getSession().getProfile().getId())) {
			return null;
		}
		return Skins.worn();
	}

	/** The skin texture {@code player} shows, given the game's ({@code vanilla}). */
	public ResourceLocation skinFor(AbstractClientPlayer player, ResourceLocation vanilla) {
		Skins.Skin s = wornBy(player);
		ResourceLocation tex = s == null ? null : s.texture();
		return tex != null ? tex : vanilla;
	}

	/** "slim" or "default": the arms that go with the worn skin. */
	public String modelFor(AbstractClientPlayer player, String vanilla) {
		Skins.Skin s = wornBy(player);
		return s == null || s.texture() == null ? vanilla : s.slim ? "slim" : "default";
	}
}
