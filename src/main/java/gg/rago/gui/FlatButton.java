package gg.rago.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.widget.ButtonWidget;

/** A vanilla button (same click and sound handling) drawn in Rago's flat style. */
final class FlatButton extends ButtonWidget {
	private final boolean accent;

	FlatButton(int id, int x, int y, int w, int h, String message, boolean accent) {
		super(id, x, y, w, h, message);
		this.accent = accent;
	}

	@Override
	public void render(Minecraft mc, int mouseX, int mouseY) {
		if (!visible) {
			return;
		}
		hovered = Theme.inside(mouseX, mouseY, x, y, width, height);
		int bg = accent ? (hovered ? Theme.ACCENT : Theme.ACCENT_DARK) : (hovered ? Theme.ROW_HOVER : Theme.ROW);
		Theme.rounded(x, y, width, height, bg);
		if (hovered && !accent) {
			GuiElement.fill(x, y + 2, x + 2, y + height - 2, Theme.ACCENT);
		}
		int color = active ? Theme.TEXT : Theme.MUTED;
		Theme.centered(mc.textRenderer, message, x + width / 2, y + (height - 8) / 2, color);
	}
}
