package gg.rago.gui;

import gg.rago.accounts.Accounts;
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
	private static final String DISCORD_URL = "https://discord.gg/ragooptimizer";
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
		Ui.backdrop(0.3f);
		Ui.gradient(0, h * 0.5f, w, h * 0.5f, 0x00050406, 0xB0050406);
		Ui.captureGlass();

		center(w, h);
		dock(w, h);
		footer(w, h);
		account(w);
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
		float cy = Math.max(160, h * 0.36f);
		float a = intro(0, 650);
		Ui.opacity(a);
		logo(cx, cy, 96 * (0.88f + 0.12f * a));
		float ty = cy + 66;
		float size = 19;
		float rw = Ui.textWidth(Ui.SEMIBOLD, "Rago ", size);
		float cw = Ui.textWidth(Ui.REGULAR, "Client", size);
		float tx = cx - (rw + cw) / 2;
		Ui.text(Ui.SEMIBOLD, "Rago ", tx, ty, size, Theme.TEXT);
		Ui.text(Ui.REGULAR, "Client", tx + rw, ty, size, 0xFFB4B2B9);

		float bw = 302;
		float bh = 25;
		float by = ty + 40;
		String[] labels = {t("menu.singleplayer"), t("menu.multiplayer"), t("rago.title.join_discord")};
		Runnable[] actions = {
			() -> mc.displayGuiScreen(new GuiSelectWorld(this)),
			() -> mc.displayGuiScreen(new GuiMultiplayer(this)),
			() -> Sys.openURL(DISCORD_URL)
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

	/**
	 * The animated emblem: the R with its red glow and, every 3.2 s, two thin rings that ripple
	 * out from it and fade. Without menu animations it stays still.
	 */
	private static void logo(float cx, float cy, float size) {
		float cycle = 3.2f;
		float t = Anim.enabled() ? (System.nanoTime() / 1e9f) % cycle : 0;
		for (int i = 0; i < 2; i++) {
			float p = (t - 0.25f - i * 0.6f) / 1.9f;
			if (p <= 0 || p >= 1) {
				continue;
			}
			float eased = 1 - (1 - p) * (1 - p) * (1 - p);
			float radius = size * (0.42f + 0.5f * eased);
			float alpha = Math.min(1, p / 0.08f) * (float) Math.pow(1 - p, 1.4) * 0.8f;
			Ui.annulus(cx, cy, radius, 1.3f, Ui.alpha(Theme.ACCENT, alpha));
		}
		// the glow swells a little each time a ring leaves
		float pulse = t < 0.25f || t > 1.6f ? 0 : (float) Math.sin((t - 0.25f) / 1.35f * Math.PI);
		float half = size / 2;
		Ui.logoGlow(cx - half, cy - half, size, Ui.alpha(Theme.ACCENT, 0.42f + 0.15f * pulse));
		Ui.logo(cx - half, cy - half, size, Theme.ACCENT);
	}

	private static void homeButton(String key, float x, float y, float w, float h, String label, boolean primary) {
		float hv = Anim.to(key, Ui.hover(x, y, w, h) ? 1 : 0, 120);
		if (primary) {
			Ui.glass(x, y, w, h, h / 2, 0);
			Ui.round(x, y, w, h, h / 2, Ui.alpha(Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, hv), 0.88f));
		} else {
			Ui.glassBorder(x, y, w, h, h / 2, Ui.mix(0xE6131215, 0xF0222126, hv), Ui.mix(0x24FFFFFF, 0x40FFFFFF, hv));
		}
		float size = 10.5f;
		Ui.textCentered(Ui.MEDIUM, label, x + w / 2, y + (h - size * 1.21f) / 2, size, primary ? 0xFF1A0605 : Theme.TEXT);
	}

	// ---- dock -----------------------------------------------------------------------------------

	private void dock(float w, float h) {
		boolean noOptifine = !OptiFineFields.installed();
		String[] icons = noOptifine
			? new String[] {"logo", "shirt", "sliders-horizontal", "settings", "globe", "zap", "power"}
			: new String[] {"logo", "shirt", "sliders-horizontal", "settings", "globe", "power"};
		String[] tips = noOptifine
			? new String[] {"rago.dock.mods", "rago.dock.locker", "rago.dock.rago_settings", "rago.dock.options", "rago.dock.servers", "rago.dock.optifine", "rago.dock.quit"}
			: new String[] {"rago.dock.mods", "rago.dock.locker", "rago.dock.rago_settings", "rago.dock.options", "rago.dock.servers", "rago.dock.quit"};
		float size = 30;
		float gap = 8;
		float total = icons.length * size + (icons.length - 1) * gap;
		float p = intro(380, 500);
		Ui.opacity(p);
		float x = w / 2 - total / 2;
		float y = h - 62 + (1 - p) * 10;
		for (int i = 0; i < icons.length; i++) {
			String icon = icons[i];
			float hv = Anim.to("dock:" + icon, Ui.hover(x, y, size, size) ? 1 : 0, 120);
			float lift = hv * 2;
			Ui.glassBorder(x, y - lift, size, size, 8, Ui.mix(0xE6131215, 0xF0242328, hv), Ui.mix(0x1FFFFFFF, 0x3DFFFFFF, hv));
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
				Ui.glass(x + size / 2 - tw / 2, tipY, tw, 17, 6, 0xF0141316);
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
			case "shirt":
				return () -> mc.displayGuiScreen(new LockerScreen(this));
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

	/** The account in use, top right; opens the Accounts screen. */
	private void account(float w) {
		if (!Accounts.available()) {
			return;
		}
		String name = mc.getSession().getUsername();
		float size = 10;
		float tw = Ui.textWidth(Ui.MEDIUM, name, size);
		float cw = tw + 58;
		float x = w - 14 - cw;
		float y = 14;
		float p = intro(450, 500);
		Ui.opacity(p);
		float hv = Anim.to("account", Ui.hover(x, y, cw, 30) ? 1 : 0, 120);
		Ui.glassBorder(x, y, cw, 30, 15, Ui.mix(0xE6131215, 0xF0242328, hv), Ui.mix(0x1FFFFFFF, 0x3DFFFFFF, hv));
		AccountsScreen.head(name, x + 7, y + 7, 16);
		Ui.text(Ui.MEDIUM, name, x + 30, y + (30 - size * 1.21f) / 2, size, Theme.TEXT);
		Ui.icon("users", x + cw - 22, y + 9, 12, Ui.mix(0xFF9A98A0, Theme.TEXT, hv));
		if (hv > 0.01f) {
			String tip = t("rago.dock.accounts");
			float tsz = 9;
			float tipW = Ui.textWidth(Ui.MEDIUM, tip, tsz) + 14;
			Ui.opacity(p * hv);
			Ui.glass(x + cw - tipW, y + 36, tipW, 17, 6, 0xF0141316);
			Ui.textCentered(Ui.MEDIUM, tip, x + cw - tipW / 2, y + 36 + (17 - tsz * 1.21f) / 2, tsz, Theme.TEXT);
			Ui.opacity(p);
		}
		if (!optifineHelp) {
			Ui.click(x, y, cw, 30, () -> mc.displayGuiScreen(new AccountsScreen(this)));
		}
		Ui.opacity(1);
	}

	private void footer(float w, float h) {
		float size = 8.5f;
		Ui.opacity(intro(500, 500));
		Ui.text(Ui.MONO, "Rago Client 1.8.9 (" + RagoClient.version() + ")", 14, h - 20, size, 0xFF8A888F);
		Ui.textRight(Ui.REGULAR, t("rago.title.legal"), w - 14, h - 20, size, 0xFF8A888F);
		float made = Ui.textWidth(Ui.REGULAR, "Made by ", size);
		float mx = w / 2 - (made + Ui.textWidth(Ui.SEMIBOLD, "Rago", size)) / 2;
		Ui.text(Ui.REGULAR, "Made by ", mx, h - 20, size, 0xFF8A888F);
		Ui.text(Ui.SEMIBOLD, "Rago", mx + made, h - 20, size, Theme.ACCENT);
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
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
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
