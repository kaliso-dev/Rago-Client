package gg.rago.gui;

import gg.rago.core.QuickJoin;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.optimize.OptiFineFields;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/**
 * Rago's home screen: the emblem, three buttons and a dock of shortcuts over a blurred landscape.
 * Shown instead of the vanilla title screen unless the player picks the vanilla menu.
 */
public final class RagoTitleScreen extends GuiScreen {
	private static final String HYPIXEL = "mc.hypixel.net";
	private static final String OPTIFINE_URL = "https://optifine.net/downloads";
	/** The intro plays once per game start, not every time the player comes back here. */
	private static boolean introPlayed;

	private long openedNs;
	private boolean optifineHelp;
	private long helpOpenedNs;

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void initGui() {
		openedNs = introPlayed ? System.nanoTime() - 5_000_000_000L : System.nanoTime();
		introPlayed = true;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		Ui.backdrop(0.58f);
		Ui.glow(0, 0, Math.max(w, h) * 0.75f, Ui.alpha(Theme.ACCENT, 0.16f));
		Ui.gradient(0, h * 0.55f, w, h * 0.45f, 0x00050406, 0xC0050406);

		center(w, h);
		dock(w, h);
		footer(w, h);
		if (optifineHelp) {
			optifineDialog(w, h);
		}
		Ui.end();
	}

	/** Eased 0..1 progress of the intro, delayed by {@code delayMs}. */
	private float intro(float delayMs, float ms) {
		return Anim.progress(openedNs + (long) (delayMs * 1_000_000L), ms);
	}

	// ---- centre ---------------------------------------------------------------------------------

	private void center(float w, float h) {
		float cx = w / 2;
		float cy = Math.max(170, h * 0.4f);
		float a = intro(0, 650);
		Ui.opacity(a);
		float breathe = Anim.enabled() ? (float) (Math.sin(System.nanoTime() / 1.1e9) * 0.5 + 0.5) : 0.5f;
		float grow = 0.9f + 0.1f * a;
		Ui.glow(cx, cy, 190 * grow, Ui.alpha(Theme.ACCENT, 0.10f + 0.05f * breathe));
		Ui.ring(cx, cy, 172 * grow, Ui.alpha(Theme.ACCENT, 0.06f + 0.04f * breathe));
		Ui.ring(cx, cy, 138 * grow, Ui.alpha(Theme.ACCENT, 0.12f));
		Ui.ring(cx, cy, 98 * grow, Ui.alpha(Theme.ACCENT, 0.22f));
		Ui.circle(cx, cy, 66 * grow, 0xF20B0A0C);
		Ui.ring(cx, cy, 67 * grow, Ui.alpha(Theme.ACCENT, 0.55f));
		float ls = 58 * grow;
		Ui.logo(cx - ls / 2, cy - ls / 2 - 2, ls, Theme.ACCENT);

		float ty = cy + 80;
		float size = 19;
		float rw = Ui.textWidth(Ui.SEMIBOLD, "Rago ", size);
		float cw = Ui.textWidth(Ui.REGULAR, "Client", size);
		float tx = cx - (rw + cw) / 2;
		Ui.text(Ui.SEMIBOLD, "Rago ", tx, ty, size, Theme.TEXT);
		Ui.text(Ui.REGULAR, "Client", tx + rw, ty, size, 0xFFB4B2B9);

		float bw = 290;
		float bh = 25;
		float by = ty + 40;
		String[] labels = {t("menu.singleplayer"), t("menu.multiplayer"), t("rago.title.play_hypixel")};
		Runnable[] actions = {
			() -> mc.displayGuiScreen(new GuiSelectWorld(this)),
			() -> mc.displayGuiScreen(new GuiMultiplayer(this)),
			() -> QuickJoin.join(this, "Hypixel", HYPIXEL, null, null)
		};
		for (int i = 0; i < labels.length; i++) {
			float p = intro(120 + i * 70, 450);
			Ui.opacity(p);
			float y = by + i * 33 + (1 - p) * 12;
			homeButton("home:" + i, cx - bw / 2, y, bw, bh, labels[i], i == 2);
			if (!optifineHelp) {
				Ui.click(cx - bw / 2, y, bw, bh, actions[i]);
			}
		}
		Ui.opacity(1);
	}

	private static void homeButton(String key, float x, float y, float w, float h, String label, boolean primary) {
		float hv = Anim.to(key, Ui.hover(x, y, w, h) ? 1 : 0, 120);
		if (primary) {
			Ui.round(x, y, w, h, h / 2, Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, hv));
		} else {
			Ui.roundBorder(x, y, w, h, h / 2, Ui.mix(0xE6131215, 0xF0222126, hv), Ui.mix(0x24FFFFFF, 0x40FFFFFF, hv));
		}
		float size = 10.5f;
		Ui.textCentered(Ui.MEDIUM, label, x + w / 2, y + (h - size * 1.21f) / 2, size, primary ? 0xFF1A0605 : Theme.TEXT);
	}

	// ---- dock -----------------------------------------------------------------------------------

	private void dock(float w, float h) {
		boolean noOptifine = !OptiFineFields.installed();
		String[] icons = noOptifine
			? new String[] {"logo", "sliders-horizontal", "settings", "globe", "zap", "power"}
			: new String[] {"logo", "sliders-horizontal", "settings", "globe", "power"};
		String[] tips = noOptifine
			? new String[] {"rago.dock.mods", "rago.dock.rago_settings", "rago.dock.options", "rago.dock.servers", "rago.dock.optifine", "rago.dock.quit"}
			: new String[] {"rago.dock.mods", "rago.dock.rago_settings", "rago.dock.options", "rago.dock.servers", "rago.dock.quit"};
		float size = 30;
		float gap = 8;
		float total = icons.length * size + (icons.length - 1) * gap;
		float p = intro(380, 500);
		Ui.opacity(p);
		float x = w / 2 - total / 2;
		float y = h - 54 + (1 - p) * 10;
		for (int i = 0; i < icons.length; i++) {
			String icon = icons[i];
			float hv = Anim.to("dock:" + icon, Ui.hover(x, y, size, size) ? 1 : 0, 120);
			float lift = hv * 2;
			Ui.roundBorder(x, y - lift, size, size, 8, Ui.mix(0xE6131215, 0xF0242328, hv), Ui.mix(0x1FFFFFFF, 0x3DFFFFFF, hv));
			float is = 14;
			if ("logo".equals(icon)) {
				Ui.logo(x + (size - is) / 2, y - lift + (size - is) / 2, is, Theme.ACCENT);
			} else {
				Ui.icon(icon, x + (size - is) / 2, y - lift + (size - is) / 2, is, Ui.mix(0xFFC9C7CE, Theme.TEXT, hv));
			}
			if ("zap".equals(icon)) {
				Ui.circle(x + size - 3, y - lift + 3, 3.5f, Theme.ACCENT);
			}
			if (hv > 0.01f) {
				String tip = t(tips[i]);
				float ts = 9;
				float tw = Ui.textWidth(Ui.MEDIUM, tip, ts) + 14;
				float tipY = y - lift - 24;
				Ui.opacity(p * hv);
				Ui.round(x + size / 2 - tw / 2, tipY, tw, 17, 6, 0xF0141316);
				Ui.textCentered(Ui.MEDIUM, tip, x + size / 2, tipY + (17 - ts * 1.21f) / 2, ts, Theme.TEXT);
				Ui.opacity(p);
			}
			if (!optifineHelp) {
				Ui.click(x, y, size, size, dockAction(icon));
			}
			x += size + gap;
		}
		Ui.opacity(1);
	}

	private Runnable dockAction(String icon) {
		switch (icon) {
			case "logo":
				return () -> mc.displayGuiScreen(new RagoMenuScreen(this));
			case "sliders-horizontal":
				return () -> mc.displayGuiScreen(RagoMenuScreen.settings(this));
			case "settings":
				return () -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
			case "globe":
				return () -> mc.displayGuiScreen(new GuiMultiplayer(this));
			case "zap":
				return () -> {
					optifineHelp = true;
					helpOpenedNs = System.nanoTime();
				};
			default:
				return () -> mc.shutdown();
		}
	}

	private void footer(float w, float h) {
		float size = 8.5f;
		Ui.opacity(intro(500, 500));
		Ui.text(Ui.MONO, "Rago Client 1.8.9 (" + RagoClient.version() + ")", 14, h - 20, size, 0xFF8A888F);
		Ui.textRight(Ui.REGULAR, t("rago.title.legal"), w - 14, h - 20, size, 0xFF8A888F);
		Ui.opacity(1);
	}

	// ---- OptiFine helper ------------------------------------------------------------------------

	private void optifineDialog(float w, float h) {
		float p = Anim.progress(helpOpenedNs, 220);
		Ui.opacity(p);
		Ui.rect(0, 0, w, h, 0xA0000000);
		Ui.click(0, 0, w, h, () -> { });
		float dw = 380;
		float dh = 196;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2 + (1 - p) * 14;
		Ui.roundBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		Ui.round(x + 20, y + 20, 30, 30, 9, Theme.ACCENT_SOFT);
		Ui.icon("zap", x + 27, y + 27, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.optifine.title"), x + 62, y + 21, 14, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.optifine.subtitle"), x + 62, y + 40, 9.5f, Theme.MUTED);
		float ty = y + 68;
		for (String line : Ui.wrap(Ui.REGULAR, t("rago.optifine.body"), 10, dw - 40)) {
			Ui.text(Ui.REGULAR, line, x + 20, ty, 10, 0xFFD4D2D9);
			ty += 15;
		}
		float bw = (dw - 52) / 3;
		float by = y + dh - 44;
		homeButton("of:download", x + 20, by, bw, 26, t("rago.optifine.download"), true);
		Ui.click(x + 20, by, bw, 26, () -> Sys.openURL(OPTIFINE_URL));
		homeButton("of:folder", x + 26 + bw, by, bw, 26, t("rago.optifine.open_mods"), false);
		Ui.click(x + 26 + bw, by, bw, 26, this::openModsFolder);
		homeButton("of:close", x + 32 + bw * 2, by, bw, 26, t("gui.done"), false);
		Ui.click(x + 32 + bw * 2, by, bw, 26, () -> optifineHelp = false);
		Ui.opacity(1);
	}

	private void openModsFolder() {
		File mods = new File(mc.mcDataDir, "mods");
		mods.mkdirs();
		try {
			Desktop.getDesktop().open(mods);
		} catch (Exception | LinkageError e) {
			Sys.openURL(mods.toURI().toString());
		}
	}

	// ---- input ----------------------------------------------------------------------------------

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button == 0) {
			Ui.updateMouse();
			if (Ui.dispatchClick()) {
				mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
			}
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if (keyCode == 1 && optifineHelp) {
			optifineHelp = false;
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
