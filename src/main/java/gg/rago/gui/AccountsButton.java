package gg.rago.gui;

import gg.rago.accounts.SkinHeads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

/**
 * A small button with the current account's head, placed right of Cancel in the server list, that
 * opens the Accounts screen.
 */
public final class AccountsButton extends GuiButton {
	public static final int ID = 0x5241;

	public AccountsButton(int x, int y) {
		super(ID, x, y, 20, 20, "");
	}

	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY) {
		if (!visible) {
			return;
		}
		super.drawButton(mc, mouseX, mouseY);
		String name = mc.getSession().getUsername();
		float vh = SkinHeads.skinHeight(name);
		mc.getTextureManager().bindTexture(SkinHeads.skin(name));
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.enableBlend();
		int s = 12;
		int x = xPosition + 4;
		int y = yPosition + 4;
		Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, s, s, 64, vh);
		Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, s, s, 64, vh);
		if (hovered) {
			String tip = I18n.format("rago.dock.accounts");
			int w = mc.fontRendererObj.getStringWidth(tip);
			int tx = xPosition + width / 2 - w / 2;
			int ty = yPosition - 12;
			Gui.drawRect(tx - 3, ty - 2, tx + w + 3, ty + 10, 0xE0101014);
			mc.fontRendererObj.drawStringWithShadow(tip, tx, ty, 0xFFFFFFFF);
		}
	}
}
