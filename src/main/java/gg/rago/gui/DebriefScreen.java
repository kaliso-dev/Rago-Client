package gg.rago.gui;

import gg.rago.bedwars.Debrief;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.modules.GameDebrief;
import gg.rago.modules.Screenshots;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The debrief of your Bed Wars games of this session: the picture of one game (the last at first,
 * the arrows go back), which can be copied to paste into Discord or saved in the screenshots folder.
 */
public final class DebriefScreen extends GuiScreen {
	private static final ResourceLocation CARD = new ResourceLocation("rago", "debrief_card");

	private final GameDebrief module;
	private final long openedNs = System.nanoTime();
	private int index;
	private BufferedImage image;
	private int shown = -1;
	private String message;
	private boolean messageError;
	private long messageNs;
	private static final long MESSAGE_MS = 4000;

	public DebriefScreen(GameDebrief module) {
		this.module = module;
		this.index = module.games().size() - 1;
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		float p = Anim.progress(openedNs, 200);
		Ui.rect(0, 0, w, h, Ui.alpha(0xFF050406, 0.78f * p));
		Ui.opacity(p);
		List<Debrief> games = module.games();
		if (games.isEmpty()) {
			Ui.textCentered(Ui.MEDIUM, t("rago.debrief.none_yet"), w / 2, h / 2 - 20, 11, Theme.MUTED);
			float bw = 110;
			Widgets.button((w - bw) / 2, h / 2 + 2, bw, 28, "x", t("gui.done"), false, 10);
			Ui.click((w - bw) / 2, h / 2 + 2, bw, 28, () -> mc.displayGuiScreen(null));
			Ui.opacity(1);
			Ui.end();
			return;
		}
		index = Math.max(0, Math.min(index, games.size() - 1));
		show(games);

		// the picture, as large as the screen allows above the buttons
		float iw = Math.min(w - 40, (h - 84) * DebriefCard.W / DebriefCard.H);
		float ih = iw * DebriefCard.H / DebriefCard.W;
		float x = (w - iw) / 2;
		float y = Math.max(12, (h - ih - 52) / 2) + (1 - p) * 10;
		Ui.round(x - 1, y - 1, iw + 2, ih + 2, 7, Theme.BORDER_STRONG);
		if (image != null) {
			Ui.texture(CARD, x, y, iw, ih, 0, 0, 1, 1, Ui.fade(0xFFFFFFFF));
		}

		// the buttons
		float by = y + ih + 14;
		float bh = 28;
		float copyW = 150;
		float saveW = 118;
		float closeW = 96;
		float total = copyW + saveW + closeW + 16;
		float bx = (w - total) / 2;
		if (games.size() > 1) {
			Widgets.iconButton(x, by, bh, "chevron-left");
			Ui.click(x, by, bh, bh, () -> index = Math.max(0, index - 1));
			Widgets.iconButton(x + bh + 6, by, bh, "chevron-right");
			Ui.click(x + bh + 6, by, bh, bh, () -> index = Math.min(module.games().size() - 1, index + 1));
			Ui.text(Ui.MEDIUM, t("rago.debrief.count", index + 1, games.size()), x + bh * 2 + 16, by + 8, 9, Theme.MUTED);
		}
		Widgets.button(bx, by, copyW, bh, "image", t("rago.debrief.copy"), true, 10);
		Ui.click(bx, by, copyW, bh, this::copy);
		Widgets.button(bx + copyW + 8, by, saveW, bh, "download", t("rago.debrief.save"), false, 10);
		Ui.click(bx + copyW + 8, by, saveW, bh, this::save);
		Widgets.button(bx + copyW + saveW + 16, by, closeW, bh, "x", t("gui.done"), false, 10);
		Ui.click(bx + copyW + saveW + 16, by, closeW, bh, () -> mc.displayGuiScreen(null));
		drawMessage(w, y + ih);
		Ui.opacity(1);
		Ui.end();
	}

	/** The last message ("copied", "saved in ...") for a few seconds, on a pill over the picture's foot. */
	private void drawMessage(float w, float bottom) {
		if (message == null) {
			return;
		}
		long ms = (System.nanoTime() - messageNs) / 1_000_000L;
		if (ms > MESSAGE_MS) {
			message = null;
			return;
		}
		float a = Math.min(1, Math.min(ms / 150f, (MESSAGE_MS - ms) / 400f));
		float size = 10;
		float mw = Ui.textWidth(Ui.MEDIUM, message, size) + 32;
		float mh = 24;
		float mx = (w - mw) / 2;
		float my = bottom - mh - 12;
		Ui.round(mx, my, mw, mh, mh / 2, Ui.alpha(0xFF141317, 0.95f * a));
		Ui.outline(mx, my, mw, mh, mh / 2, Ui.alpha(messageError ? Theme.ACCENT : Theme.GREEN, 0.55f * a));
		Ui.textCentered(Ui.MEDIUM, message, w / 2, my + 7, size, Ui.alpha(messageError ? Theme.ACCENT : Theme.GREEN, a));
	}

	private void say(String text, boolean error) {
		message = text;
		messageError = error;
		messageNs = System.nanoTime();
	}

	/** Draws the picture of the game shown, once per game. */
	private void show(List<Debrief> games) {
		if (shown == index && image != null) {
			return;
		}
		shown = index;
		int wins = 0;
		int finals = 0;
		for (Debrief d : games) {
			wins += d.result() == Debrief.Result.WIN ? 1 : 0;
			finals += d.finalKills();
		}
		try {
			Locale locale = locale();
			image = DebriefCard.render(games.get(index), new DebriefCard.Session(games.size(), wins, finals), I18n::format, locale, module.signature());
			mc.getTextureManager().deleteTexture(CARD);
			mc.getTextureManager().loadTexture(CARD, new CardTexture(image));
		} catch (RuntimeException | LinkageError e) {
			RagoClient.LOG.warn("Could not draw the debrief", e);
			image = null;
			say(t("rago.debrief.failed"), true);
		}
	}

	/** The game's language as a locale ("fr_FR" gives French numbers and dates). */
	private Locale locale() {
		String code = mc.getLanguageManager().getCurrentLanguage().getLanguageCode();
		String[] parts = code.split("_");
		return parts.length == 2 ? new Locale(parts[0], parts[1].toUpperCase(Locale.ROOT)) : Locale.ENGLISH;
	}

	private void copy() {
		if (image == null) {
			return;
		}
		Screenshots.copyImage(image, ok -> say(t(ok ? "rago.debrief.copied" : "rago.debrief.failed"), !ok));
	}

	private void save() {
		if (image == null) {
			return;
		}
		BufferedImage img = image;
		File dir = new File(mc.mcDataDir, "screenshots");
		File file = new File(dir, "rago-debrief_" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".png");
		Thread worker = new Thread(() -> {
			boolean ok;
			try {
				ok = (dir.isDirectory() || dir.mkdirs()) && ImageIO.write(img, "png", file);
			} catch (IOException | RuntimeException e) {
				RagoClient.LOG.warn("Could not save the debrief to {}", file, e);
				ok = false;
			}
			boolean saved = ok;
			mc.addScheduledTask(() -> say(saved ? t("rago.debrief.saved", file.getName()) : t("rago.debrief.failed"), !saved));
		}, "Rago debrief save");
		worker.setDaemon(true);
		worker.start();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		Ui.updateMouse();
		if (button == 0 && Ui.dispatchClick()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			mc.displayGuiScreen(null);
		} else if (keyCode == Keyboard.KEY_LEFT) {
			index = Math.max(0, index - 1);
		} else if (keyCode == Keyboard.KEY_RIGHT) {
			index = Math.min(module.games().size() - 1, index + 1);
		} else if (keyCode == Keyboard.KEY_C && isCtrlKeyDown()) {
			copy();
		}
	}

	@Override
	public void onGuiClosed() {
		mc.getTextureManager().deleteTexture(CARD);
		image = null;
	}

	/** The picture uploaded once, smoothed when drawn smaller than it is. */
	private static final class CardTexture extends AbstractTexture {
		CardTexture(BufferedImage image) {
			TextureUtil.uploadTextureImageAllocate(getGlTextureId(), image, true, true);
		}

		@Override
		public void loadTexture(IResourceManager manager) {
		}
	}
}
