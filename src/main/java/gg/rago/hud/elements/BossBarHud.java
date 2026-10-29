package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.boss.BossStatus;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** The boss bar (Hypixel uses it for announcements) as a movable element; name and bar can be hidden. */
public final class BossBarHud extends HudElement {
	private static final int BAR_W = 182;

	private final BooleanOption showName = addOption(new BooleanOption("show_name", true));
	private final BooleanOption showBar = addOption(new BooleanOption("show_bar", true));
	private String name;
	private float health;

	public BossBarHud() {
		super("boss_bar", FeatureClass.VISUAL, false, Anchor.TOP_CENTER, 0, 2);
	}

	/** Vanilla's bar is skipped; its countdown still has to run, as vanilla runs it while drawing. */
	@SubscribeEvent
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (!isEnabled() || event.type != RenderGameOverlayEvent.ElementType.BOSSHEALTH) {
			return;
		}
		event.setCanceled(true);
		if (BossStatus.bossName != null && BossStatus.statusBarTime > 0) {
			BossStatus.statusBarTime--;
			name = BossStatus.bossName;
			health = BossStatus.healthScale;
		} else {
			name = null;
		}
	}

	@Override
	public boolean replacesVanilla() {
		return true;
	}

	@Override
	public boolean hasContent() {
		return name != null && (showName.get() || showBar.get());
	}

	private String name(boolean placeholder) {
		return placeholder && name == null ? "§e§lPLAYING §b§lBED WARS §eon §a§lMC.HYPIXEL.NET" : name;
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		String n = name(placeholder);
		int w = showBar.get() ? BAR_W : 0;
		return showName.get() && n != null ? Math.max(w, textWidth(n)) : Math.max(w, 1);
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		return (showName.get() ? 10 : 0) + (showBar.get() ? 5 : 0);
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		String n = name(placeholder);
		float hp = placeholder && name == null ? 1f : health;
		int w = width(text, placeholder);
		int y = 0;
		if (showName.get() && n != null) {
			drawText(text, n, (w - textWidth(n)) / 2, 0, textColor.get());
			y = 10;
		}
		if (showBar.get()) {
			Minecraft mc = Minecraft.getMinecraft();
			GlStateManager.color(1, 1, 1, 1);
			GlStateManager.enableBlend();
			mc.getTextureManager().bindTexture(Gui.icons);
			int x = (w - BAR_W) / 2;
			Gui gui = mc.ingameGUI;
			gui.drawTexturedModalRect(x, y, 0, 74, BAR_W, 5);
			int fill = (int) (hp * (BAR_W + 1));
			if (fill > 0) {
				gui.drawTexturedModalRect(x, y, 0, 79, Math.min(fill, BAR_W), 5);
			}
		}
	}
}
