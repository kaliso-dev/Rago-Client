package gg.rago.gui;

import gg.rago.accounts.Accounts;
import gg.rago.accounts.SkinHeads;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/**
 * Switch Minecraft accounts without restarting: the saved Microsoft accounts with their skin faces,
 * the one in use marked, and a button to add another (the Microsoft sign-in opens in the browser).
 * The sign-in itself is In-Game Account Switcher's; this screen is its Rago-styled front.
 */
public final class AccountsScreen extends GuiScreen {
	private static final float PANEL_W = 430;
	private static final float ROW_H = 46;

	private final GuiScreen parent;
	private long openedNs;
	private float scroll;
	/** Sign-in in progress (null when idle). */
	private volatile String working;
	private volatile String state = "";
	private volatile String error;
	private volatile boolean cancellable;
	private String confirmDelete;

	public AccountsScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void initGui() {
		openedNs = System.nanoTime();
	}

	private final Accounts.Listener listener = new Accounts.Listener() {
		@Override
		public void state(String text) {
			state = text;
		}

		@Override
		public void error(String message) {
			error = message;
			working = null;
		}

		@Override
		public void done(String name) {
			working = null;
			state = "";
		}

		@Override
		public void cancellable(boolean c) {
			cancellable = c;
		}
	};

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		Ui.backdrop(0.45f);
		Ui.captureGlass();
		float p = Anim.progress(openedNs, 260);
		Ui.opacity(p);

		boolean available = Accounts.available();
		List<String> names = Accounts.names();
		String current = mc.getSession().getUsername();
		float listH = available ? Math.max(ROW_H, Math.min(names.size(), 5) * (ROW_H + 6) - 6) : 60;
		float ph = 74 + listH + 64;
		float x = (w - PANEL_W) / 2;
		float y = (h - ph) / 2 + (1 - p) * 12;
		Ui.glassBorder(x, y, PANEL_W, ph, 14, 0xF2121114, Theme.BORDER_STRONG);

		// header
		Ui.round(x + 20, y + 20, 32, 32, 9, Theme.ACCENT_SOFT);
		Ui.icon("users", x + 28, y + 28, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.accounts.title"), x + 64, y + 20, 15, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.accounts.current", current), x + 64, y + 40, 9.5f, Theme.MUTED);
		boolean busy = working != null;

		float ly = y + 74;
		if (!available) {
			for (String line : Ui.wrap(Ui.REGULAR, t("rago.accounts.unavailable"), 10, PANEL_W - 40)) {
				Ui.text(Ui.REGULAR, line, x + 20, ly, 10, Theme.MUTED);
				ly += 15;
			}
		} else if (names.isEmpty()) {
			Ui.glassBorder(x + 20, ly, PANEL_W - 40, ROW_H, 9, 0xFF151417, 0x14FFFFFF);
			Ui.textCentered(Ui.REGULAR, t("rago.accounts.empty"), x + PANEL_W / 2, ly + (ROW_H - 10 * 1.21f) / 2, 10, Theme.MUTED);
		} else {
			float total = names.size() * (ROW_H + 6) - 6;
			scroll = Math.max(0, Math.min(scroll, total - listH));
			Ui.scissor(x + 14, ly - 2, PANEL_W - 28, listH + 4);
			float ry = ly - scroll;
			for (String name : names) {
				row(name, name.equalsIgnoreCase(current), x + 20, ry, PANEL_W - 40, busy);
				ry += ROW_H + 6;
			}
			Ui.endScissor();
		}

		// footer
		float by = y + ph - 46;
		float bw = (PANEL_W - 50) / 2;
		Widgets.button(x + 20, by, bw, 28, "plus", t("rago.accounts.add"), true, 10);
		Widgets.button(x + 30 + bw, by, bw, 28, "arrow-left", t("gui.done"), false, 10);
		if (!busy && confirmDelete == null) {
			if (available) {
				Ui.click(x + 20, by, bw, 28, () -> start(t("rago.accounts.adding"), () -> Accounts.add(listener)));
			}
			Ui.click(x + 30 + bw, by, bw, 28, () -> mc.displayGuiScreen(parent));
		}

		if (busy || error != null) {
			overlay(w, h);
		} else if (confirmDelete != null) {
			confirm(w, h);
		}
		Ui.opacity(1);
		Ui.end();
	}

	private void row(String name, boolean active, float x, float y, float w, boolean busy) {
		float hv = Widgets.hoverAmount("acc:" + name, x, y, w, ROW_H);
		Ui.glassBorder(x, y, w, ROW_H, 9, Ui.mix(0xFF151417, 0xFF1D1C20, hv), active ? Ui.alpha(Theme.ACCENT, 0.7f) : 0x14FFFFFF);
		head(name, x + 10, y + 9, 28);
		Ui.text(Ui.SEMIBOLD, name, x + 48, y + 9, 11.5f, Theme.TEXT);
		Ui.text(Ui.REGULAR, t(active ? "rago.accounts.in_use" : "rago.accounts.microsoft"), x + 48, y + 25, 9, active ? Theme.GREEN : Theme.MUTED);
		float right = x + w - 10;
		// delete
		float dx = right - 26;
		float dh = Widgets.hoverAmount("accdel:" + name, dx, y + 10, 26, 26);
		Ui.round(dx, y + 10, 26, 26, 7, Ui.mix(0x00FFFFFF, 0x26F2392C, dh));
		Ui.icon("trash-2", dx + 7, y + 17, 12, Ui.mix(0xFF8A888F, Theme.ACCENT, dh));
		// use
		if (!active) {
			float uw = Ui.textWidth(Ui.MEDIUM, t("rago.accounts.use"), 9.5f) + 34;
			float ux = dx - 8 - uw;
			float uh = Widgets.hoverAmount("accuse:" + name, ux, y + 10, uw, 26);
			Ui.round(ux, y + 10, uw, 26, 13, Ui.alpha(Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, uh), 0.85f + 0.15f * uh));
			Ui.icon("log-in", ux + 10, y + 17, 12, 0xFFFFFFFF);
			Ui.text(Ui.MEDIUM, t("rago.accounts.use"), ux + 26, y + 10 + (26 - 9.5f * 1.21f) / 2, 9.5f, 0xFFFFFFFF);
			if (!busy && confirmDelete == null) {
				Ui.click(x, y, dx - 4 - x, ROW_H, () -> start(t("rago.accounts.switching", name), () -> Accounts.login(name, listener)));
			}
		} else {
			Widgets.statusPill(dx - 70, y + 13, 20, Theme.GREEN, t("rago.accounts.active"));
		}
		if (!busy && confirmDelete == null) {
			Ui.click(dx, y + 10, 26, 26, () -> confirmDelete = name);
		}
	}

	/** The player's face from their skin, with the hat layer on top. */
	static void head(String name, float x, float y, float size) {
		ResourceLocation skin = SkinHeads.skin(name);
		float vh = SkinHeads.skinHeight(name);
		Ui.round(x - 1, y - 1, size + 2, size + 2, 5, 0xFF0B0A0D);
		Ui.texture(skin, x, y, size, size, 8 / 64f, 8 / vh, 16 / 64f, 16 / vh, 0xFFFFFFFF);
		Ui.texture(skin, x - size / 16, y - size / 16, size * 9 / 8, size * 9 / 8, 40 / 64f, 8 / vh, 48 / 64f, 16 / vh, 0xFFFFFFFF);
	}

	private void start(String what, Runnable action) {
		error = null;
		state = t("rago.accounts.waiting");
		cancellable = true;
		working = what;
		action.run();
	}

	private void overlay(float w, float h) {
		Ui.rect(0, 0, w, h, 0x99000000);
		Ui.click(0, 0, w, h, () -> { });
		float dw = 340;
		float dh = 132;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2;
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		String err = error;
		if (err != null) {
			Ui.round(x + 20, y + 20, 30, 30, 9, Theme.ACCENT_SOFT);
			Ui.icon("triangle-alert", x + 27, y + 27, 16, Theme.ACCENT);
			Ui.text(Ui.SEMIBOLD, t("rago.accounts.failed"), x + 62, y + 22, 13, Theme.TEXT);
			float ty = y + 42;
			for (String line : Ui.wrap(Ui.REGULAR, err, 9.5f, dw - 82)) {
				Ui.text(Ui.REGULAR, line, x + 62, ty, 9.5f, Theme.MUTED);
				ty += 13;
			}
			Widgets.button(x + dw - 120, y + dh - 44, 100, 26, "check", t("gui.done"), false, 10);
			Ui.click(x + dw - 120, y + dh - 44, 100, 26, () -> error = null);
			return;
		}
		// three dots pulsing one after the other while the sign-in runs
		float time = System.nanoTime() / 1e9f;
		for (int i = 0; i < 3; i++) {
			float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 6 - i * 0.9f));
			Ui.circle(x + 30 + i * 11, y + 35, 3.5f, Ui.alpha(Theme.ACCENT, a));
		}
		Ui.text(Ui.SEMIBOLD, working == null ? "" : working, x + 70, y + 22, 12.5f, Theme.TEXT);
		float ty = y + 42;
		for (String line : Ui.wrap(Ui.REGULAR, state, 9.5f, dw - 90)) {
			Ui.text(Ui.REGULAR, line, x + 70, ty, 9.5f, Theme.MUTED);
			ty += 13;
		}
		if (cancellable) {
			Widgets.button(x + dw - 120, y + dh - 44, 100, 26, "x", t("gui.cancel"), false, 10);
			Ui.click(x + dw - 120, y + dh - 44, 100, 26, () -> {
				Accounts.cancel();
				working = null;
			});
		}
	}

	private void confirm(float w, float h) {
		Ui.rect(0, 0, w, h, 0x99000000);
		Ui.click(0, 0, w, h, () -> { });
		float dw = 340;
		float dh = 120;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2;
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		head(confirmDelete, x + 20, y + 20, 28);
		Ui.text(Ui.SEMIBOLD, t("rago.accounts.remove_title", confirmDelete), x + 60, y + 20, 12.5f, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.accounts.remove_body"), x + 60, y + 38, 9.5f, Theme.MUTED);
		float bw = 100;
		Widgets.button(x + dw - 20 - bw * 2 - 10, y + dh - 44, bw, 26, "x", t("gui.cancel"), false, 10);
		Ui.click(x + dw - 20 - bw * 2 - 10, y + dh - 44, bw, 26, () -> confirmDelete = null);
		Widgets.button(x + dw - 20 - bw, y + dh - 44, bw, 26, "trash-2", t("rago.accounts.remove"), true, 10);
		String name = confirmDelete;
		Ui.click(x + dw - 20 - bw, y + dh - 44, bw, 26, () -> {
			Accounts.remove(name);
			confirmDelete = null;
		});
	}

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
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			scroll -= Math.signum(d) * 30;
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if (keyCode == 1) {
			if (working != null || error != null || confirmDelete != null) {
				if (working != null && cancellable) {
					Accounts.cancel();
					working = null;
				}
				error = null;
				confirmDelete = null;
			} else {
				mc.displayGuiScreen(parent);
			}
		}
	}

	@Override
	public void onGuiClosed() {
		if (working != null) {
			Accounts.cancel();
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
