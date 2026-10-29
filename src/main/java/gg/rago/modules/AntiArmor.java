package gg.rago.modules;

import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/**
 * Anti Armor: your own armour is not drawn, so your skin shows in F5 and in the inventory. Only on
 * your screen and only on your player: your armour still protects you, and others still see it.
 */
public final class AntiArmor extends Module {
	public AntiArmor() {
		super("anti_armor", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** LayerArmorBase.doRenderLayer: whether to skip the armour of this entity. */
	public boolean hides(EntityLivingBase entity) {
		return entity == Minecraft.getMinecraft().thePlayer;
	}
}
