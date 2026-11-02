package gg.rago.gui;

import gg.rago.accounts.Skins;
import gg.rago.bedwars.Debrief;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.hooks.Hooks;
import gg.rago.modules.GameDebrief;
import gg.rago.modules.NickHider;
import gg.rago.modules.Screenshots;
import gg.rago.modules.SkinChanger;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The debrief of your Bed Wars games of this session: the picture of one game (the last at first,
 * the arrows go back), which can be copied to paste into Discord or saved in the screenshots folder.
 * The picture is drawn on a thread of its own (the game goes on meanwhile), with the game's font,
 * icons and your skin read once when the screen opens, and again when the game's stats arrive late.
 */
public final class DebriefScreen extends GuiScreen {
	private static final ResourceLocation CARD = new ResourceLocation("rago", "debrief_card");

	private final GameDebrief module;
	private final long openedNs = System.nanoTime();
	private int index;
	private BufferedImage image;
	/** The game and version of the picture drawn or being drawn; whether one is being drawn; whether the screen is gone. */
	private int shown = -1;
	private int shownVersion = -1;
	private boolean drawing;
	private boolean closed;
	private CardArt art;
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
		} else {
			Ui.round(x, y, iw, ih, 6, Ui.fade(0xFF0B0A0D));
			Ui.textCentered(Ui.MEDIUM, t("rago.debrief.drawing"), w / 2, y + ih / 2 - 5, 10, Theme.MUTED);
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

	/**
	 * Draws the picture of the game shown, on another thread: once per game, and again when its
	 * stats change (Hypixel saves the game a little after it ends). The last picture stays shown
	 * until the new one is ready.
	 */
	private void show(List<Debrief> games) {
		Debrief d = games.get(index);
		if (drawing || shown == index && shownVersion == d.version()) {
			return;
		}
		shown = index;
		shownVersion = d.version();
		int wins = 0;
		int finals = 0;
		for (Debrief g : games) {
			wins += g.result() == Debrief.Result.WIN ? 1 : 0;
			finals += g.finalKills();
		}
		DebriefCard.Session session = new DebriefCard.Session(games.size(), wins, finals);
		Locale locale = locale();
		boolean signature = module.signature();
		CardArt withSkin;
		String name;
		try {
			if (art == null) {
				art = CardArt.load(this::resource, null, false);
			}
			withSkin = skinFor(d);
			name = Hooks.nickPlain(d.player);
		} catch (RuntimeException | LinkageError e) {
			RagoClient.LOG.warn("Could not read the debrief's pictures", e);
			withSkin = null;
			name = d.player;
		}
		CardArt a = withSkin;
		String n = name;
		int forIndex = index;
		int forVersion = shownVersion;
		drawing = true;
		Thread worker = new Thread(() -> {
			BufferedImage img;
			try {
				img = DebriefCard.render(d, session, I18n::format, locale, signature, a, n);
			} catch (RuntimeException | LinkageError e) {
				RagoClient.LOG.warn("Could not draw the debrief", e);
				img = null;
			}
			BufferedImage done = img;
			mc.addScheduledTask(() -> shown(done, forIndex, forVersion));
		}, "Rago debrief picture");
		worker.setDaemon(true);
		worker.start();
	}

	/** A picture is ready (null: it failed): shown unless the screen closed or moved to another game meanwhile. */
	private void shown(BufferedImage img, int forIndex, int forVersion) {
		drawing = false;
		if (closed) {
			return;
		}
		if (img == null) {
			say(t("rago.debrief.failed"), true);
			return;
		}
		if (forIndex != index) {
			return; // the next frame draws the game shown now
		}
		image = img;
		mc.getTextureManager().deleteTexture(CARD);
		mc.getTextureManager().loadTexture(CARD, new CardTexture(img));
	}

	/** A file of the game's resources (the resource pack's when it has one), or null. */
	private InputStream resource(String path) {
		try {
			return mc.getResourceManager().getResource(new ResourceLocation("minecraft", path)).getInputStream();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * The pictures with the skin your player shows: the Nick Hider's while it hides you, the locker
	 * skin you wear, your account's, else Minecraft's default one for your account. A game played on
	 * another account of this session gets the default skin.
	 */
	private CardArt skinFor(Debrief d) {
		String me = mc.getSession().getUsername();
		if (me == null || !me.equals(d.player)) {
			return art.withSkin(null, false);
		}
		NickHider nick = Hooks.nickHider;
		if (nick != null && nick.isEnabled()) {
			switch (nick.skinChoice()) {
				case "steve":
					return art.withSkin(null, false);
				case "alex":
					return art.withSkin(null, true);
				case "locker":
					Skins.Skin s = Skins.find(nick.lockerSkinName());
					if (s != null && s.image() != null) {
						return art.withSkin(s.image(), s.slim);
					}
					break;
				default:
					break;
			}
		}
		SkinChanger changer = Hooks.skins;
		Skins.Skin worn = changer != null && changer.isEnabled() ? Skins.worn() : null;
		if (worn != null && worn.image() != null) {
			return art.withSkin(worn.image(), worn.slim);
		}
		Skins.Skin account = Skins.account();
		if (account != null && account.image() != null) {
			return art.withSkin(account.image(), account.slim);
		}
		UUID id = mc.thePlayer != null ? mc.thePlayer.getUniqueID() : RagoClient.ownId();
		return art.withSkin(null, id != null && "slim".equals(DefaultPlayerSkin.getSkinType(id)));
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
		closed = true;
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
