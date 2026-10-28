package gg.rago.gui;

import gg.rago.core.RagoClient;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.resource.language.I18n;

/** Rago's main menu, shown instead of the vanilla title screen (can be switched off in the menu). */
public final class RagoTitleScreen extends Screen {
	private static final int SINGLEPLAYER = 0;
	private static final int MULTIPLAYER = 1;
	private static final int HYPIXEL = 2;
	private static final int MINEMEN = 3;
	private static final int MODS = 4;
	private static final int OPTIONS = 5;
	private static final int QUIT = 6;

	private static final int BUTTON_W = 200;
	private static final int BUTTON_H = 20;
	private static final int GAP = 4;

	@Override
	public void init() {
		buttons.clear();
		int x = width / 2 - BUTTON_W / 2;
		int y = height / 2 - 18;
		int half = (BUTTON_W - GAP) / 2;
		buttons.add(new FlatButton(SINGLEPLAYER, x, y, BUTTON_W, BUTTON_H, I18n.translate("menu.singleplayer"), false));
		y += BUTTON_H + GAP;
		buttons.add(new FlatButton(MULTIPLAYER, x, y, BUTTON_W, BUTTON_H, I18n.translate("menu.multiplayer"), false));
		y += BUTTON_H + GAP;
		buttons.add(new FlatButton(HYPIXEL, x, y, half, BUTTON_H, "Hypixel", false));
		buttons.add(new FlatButton(MINEMEN, x + half + GAP, y, BUTTON_W - half - GAP, BUTTON_H, "Minemen Club", false));
		y += BUTTON_H + GAP;
		buttons.add(new FlatButton(MODS, x, y, BUTTON_W, BUTTON_H, I18n.translate("rago.title.mods"), true));
		y += BUTTON_H + GAP + 6;
		buttons.add(new FlatButton(OPTIONS, x, y, half, BUTTON_H, I18n.translate("menu.options"), false));
		buttons.add(new FlatButton(QUIT, x + half + GAP, y, BUTTON_W - half - GAP, BUTTON_H, I18n.translate("menu.quit"), false));
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		switch (button.id) {
			case SINGLEPLAYER -> minecraft.openScreen(new SelectWorldScreen(this));
			case MULTIPLAYER -> minecraft.openScreen(new MultiplayerScreen(this));
			case HYPIXEL -> join("Hypixel", "mc.hypixel.net");
			case MINEMEN -> join("Minemen Club", "minemen.club");
			case MODS -> minecraft.openScreen(new RagoMenuScreen(this));
			case OPTIONS -> minecraft.openScreen(new OptionsScreen(this, minecraft.options));
			case QUIT -> minecraft.stop();
			default -> {
			}
		}
	}

	private void join(String name, String address) {
		minecraft.openScreen(new ConnectScreen(this, minecraft, new ServerListEntry(name, address, false)));
	}

	@Override
	protected void keyPressed(char typedChar, int keyCode) {
		// The title screen has nothing to close; Esc does nothing, as in vanilla.
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		fillGradient(0, 0, width, height, Theme.BG_TOP, Theme.BG_BOTTOM);
		// Soft accent band behind the logo
		fillGradient(0, height / 2 - 110, width, height / 2 - 40, 0x00000000, 0x228B6CF0);

		String logo = "RAGO";
		float scale = 5f;
		int logoW = Math.round(textRenderer.getWidth(logo) * scale);
		int logoY = height / 2 - 92;
		Theme.scaledText(textRenderer, logo, width / 2 - logoW / 2, logoY, scale, Theme.TEXT, true);
		String sub = "C L I E N T";
		Theme.centered(textRenderer, sub, width / 2, logoY + Math.round(9 * scale) + 4, Theme.ACCENT);
		GuiElement.fill(width / 2 - 30, logoY + Math.round(9 * scale) + 16, width / 2 + 30, logoY + Math.round(9 * scale) + 17, Theme.ACCENT_DARK);

		super.render(mouseX, mouseY, delta);

		textRenderer.draw("Rago " + RagoClient.version(), 4, height - 12, Theme.MUTED);
		String user = minecraft.getSession().getUsername();
		textRenderer.draw(user, width - 4 - textRenderer.getWidth(user), height - 12, Theme.MUTED);
	}
}
