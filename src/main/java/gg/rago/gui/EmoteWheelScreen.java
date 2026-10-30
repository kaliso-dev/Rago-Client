package gg.rago.gui;

import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.modules.Emotes;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/** The emote wheel: each dance on a circle around the cursor; a click plays it. */
public final class EmoteWheelScreen extends GuiScreen {
	private static final float RADIUS = 110;
	private static final float CHIP_W = 104;
	private static final float CHIP_H = 30;

	private final Emotes emotes;
	private final long openedNs = System.nanoTime();

	public EmoteWheelScreen(Emotes emotes) {
		this.emotes = emotes;
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		float p = Anim.progress(openedNs, 180);
		Ui.opacity(p);
		float cx = w / 2;
		float cy = h / 2;
		Ui.circle(cx, cy, 34, 0xCC121114);
		Ui.textCentered(Ui.SEMIBOLD, I18n.format("rago.emotes.title"), cx, cy - 7, 10, Theme.TEXT);
		String[] dances = Emotes.DANCES;
		for (int i = 0; i < dances.length; i++) {
			double a = -Math.PI / 2 + i * 2 * Math.PI / dances.length;
			float r = RADIUS * (0.8f + 0.2f * p);
			float x = cx + (float) Math.cos(a) * r - CHIP_W / 2;
			float y = cy + (float) Math.sin(a) * r - CHIP_H / 2;
			boolean hover = Ui.hover(x, y, CHIP_W, CHIP_H);
			Ui.glassBorder(x, y, CHIP_W, CHIP_H, CHIP_H / 2, hover ? Theme.ACCENT : 0xE6141316, hover ? Theme.ACCENT : Theme.BORDER_STRONG);
			Ui.textCentered(Ui.MEDIUM, I18n.format("rago.emote." + dances[i]), x + CHIP_W / 2, y + (CHIP_H - 10 * 1.21f) / 2, 10,
				hover ? 0xFFFFFFFF : Theme.TEXT);
			String dance = dances[i];
			Ui.click(x, y, CHIP_W, CHIP_H, () -> {
				emotes.play(dance);
				mc.displayGuiScreen(null);
			});
		}
		Ui.textCentered(Ui.REGULAR, I18n.format("rago.emotes.hint"), cx, cy + RADIUS + 40, 9.5f, Theme.TEXT);
		Ui.end();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button != 0) {
			return;
		}
		Ui.updateMouse();
		if (Ui.dispatchClick()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == emotes.key().getKeyCode()) {
			mc.displayGuiScreen(null);
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}
}
