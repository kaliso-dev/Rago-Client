package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.NameTags;
import gg.rago.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Shows your own name above your head in third person (F5), as others see it. */
public final class OwnNametag extends Module {
	private final BooleanOption background = addOption(new BooleanOption("background", true));

	public OwnNametag() {
		super("own_nametag", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** Whether the name is drawn right now, so other labels can go above it. */
	public boolean shown() {
		Minecraft mc = Minecraft.getMinecraft();
		return isEnabled() && mc.gameSettings.thirdPersonView != 0 && Minecraft.isGuiEnabled();
	}

	@SubscribeEvent
	public void onNameDrawn(RenderLivingEvent.Specials.Post<?> event) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayerSP me = mc.thePlayer;
		if (event.entity != me || me == null || !shown() || me.isInvisible()) {
			return;
		}
		String name = me.getDisplayName().getFormattedText();
		WorldDraw.label(name, event.x, NameTags.nameTop(me, event.y), event.z, background.get());
	}
}
