package gg.rago.gui;

import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.hooks.Hooks;
import gg.rago.rewards.Rewards;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * The play pass: one tier for each hour played in a world (time away from the keyboard does not
 * count, nor more than a few hours a day), each giving Rago Points or a cosmetic, three of them found
 * nowhere else. Free: only time played counts. The server keeps the minutes and gives the tiers.
 */
public final class PassScreen extends GuiScreen {
	private static final float TILE_H = 92;
	private static final float GAP = 8;
	private static final int COLS = 6;

	private final GuiScreen parent;
	private long openedNs;
	private float scroll;

	public PassScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void initGui() {
		openedNs = System.nanoTime();
		Rewards.refreshPass();
		Rewards.inventory();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		Ui.backdrop(0.55f);
		Ui.captureGlass();
		float p = Anim.progress(openedNs, 260);
		Ui.opacity(p);
		float pw = Math.min(760, w - 40);
		float ph = Math.min(470, h - 40);
		float x = (w - pw) / 2;
		float y = (h - ph) / 2 + (1 - p) * 12;
		Ui.glassBorder(x, y, pw, ph, 14, 0xF2121114, Theme.BORDER_STRONG);
		Ui.round(x + 20, y + 20, 32, 32, 9, Theme.ACCENT_SOFT);
		Ui.icon("ticket", x + 28, y + 28, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.pass.title"), x + 64, y + 20, 15, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.pass.subtitle"), x + 64, y + 40, 9.5f, Theme.MUTED);
		Widgets.iconButton(x + pw - 46, y + 20, 26, "x");
		Ui.click(x + pw - 46, y + 20, 26, 26, () -> mc.displayGuiScreen(parent));
		Rewards.Status s = Rewards.status();
		if (s != null) {
			RewardsScreen.pointsPill(x + pw - 56, y + 21, s.points, true);
		}
		Rewards.Pass pass = Rewards.pass();
		float top = y + 70;
		float bottom = y + ph - 20;
		if (pass == null || pass.items.isEmpty()) {
			String e = Rewards.error();
			boolean off = Hooks.network == null || !Hooks.network.isEnabled();
			boolean waiting = !off && (e == null || "sign_in".equals(e));
			String text = off ? t("rago.rewards.off") : waiting ? t("rago.rewards.connecting") : t("rago.rewards.offline");
			float ty = top + (bottom - top) / 2 - 20;
			Ui.icon(waiting ? "loader-circle" : "cloud-off", x + pw / 2 - 10, ty, 20, Theme.MUTED);
			float ly = ty + 32;
			for (String line : Ui.wrap(Ui.REGULAR, text, 10, Math.min(420, pw - 60))) {
				Ui.textCentered(Ui.REGULAR, line, x + pw / 2, ly, 10, Theme.MUTED);
				ly += 14;
			}
		} else {
			float cardH = progress(x + 20, top, pw - 40, pass);
			track(x + 20, top + cardH + 14, pw - 40, bottom - (top + cardH + 14), pass);
		}
		Ui.opacity(1);
		Ui.end();
	}

	/** The tier reached, the bar to the next one and the minutes counted today. */
	private float progress(float x, float y, float w, Rewards.Pass pass) {
		float h = 66;
		Ui.glassBorder(x, y, w, h, 12, 0xFF151417, 0x1FFFFFFF);
		int total = pass.items.size();
		boolean done = pass.tier >= total;
		Ui.text(Ui.SEMIBOLD, t("rago.pass.progress", pass.tier, total), x + 16, y + 12, 13, Theme.TEXT);
		int into = pass.minutes - pass.tier * pass.minutesPerTier;
		float k = done ? 1 : Math.max(0, Math.min(1, into / (float) pass.minutesPerTier));
		String next = done ? t("rago.pass.done") : t("rago.pass.next", RewardsScreen.duration((pass.minutesPerTier - into) * 60_000L), pass.tier + 1);
		Ui.text(Ui.REGULAR, next, x + w - 16 - Ui.textWidth(Ui.REGULAR, next, 9.5f), y + 15, 9.5f, Theme.MUTED);
		Ui.round(x + 16, y + 36, w - 32, 6, 3, 0x22FFFFFF);
		Ui.round(x + 16, y + 36, (w - 32) * k, 6, 3, Theme.ACCENT);
		String today = t("rago.pass.today", RewardsScreen.duration(pass.today * 60_000L), RewardsScreen.duration(pass.dailyCap * 60_000L));
		Ui.icon("clock", x + 16, y + 48, 10, Theme.MUTED);
		Ui.text(Ui.REGULAR, today, x + 30, y + 48, 8.5f, Theme.MUTED);
		String how = t("rago.pass.how");
		Ui.text(Ui.REGULAR, how, x + w - 16 - Ui.textWidth(Ui.REGULAR, how, 8.5f), y + 48, 8.5f, 0xFF7C7A82);
		return h;
	}

	/** Every tier, in rows: what it gives, reached ones ticked, the next one lit. */
	private void track(float x, float y, float w, float h, Rewards.Pass pass) {
		Ui.scissor(x - 4, y - 4, w + 8, h + 8);
		float tw = (w - (COLS - 1) * GAP) / COLS;
		float cy = y - scroll;
		for (int i = 0; i < pass.items.size(); i++) {
			float tx = x + (i % COLS) * (tw + GAP);
			float ty = cy + (i / COLS) * (TILE_H + GAP);
			if (ty + TILE_H < y - 4 || ty > y + h + 4) {
				continue;
			}
			tile(tx, ty, tw, i + 1, pass.items.get(i), pass.points.get(i), pass);
		}
		Ui.endScissor();
		int rows = (pass.items.size() + COLS - 1) / COLS;
		float total = rows * (TILE_H + GAP);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - h)));
	}

	private void tile(float x, float y, float w, int n, String item, int points, Rewards.Pass pass) {
		boolean reached = n <= pass.tier;
		boolean next = n == pass.tier + 1;
		Rewards.Item it = item == null ? null : Rewards.item(item);
		int color = it == null ? 0xFFFFC14D : Rewards.color(it.rarity);
		float hv = Widgets.hoverAmount("pass:" + n, x, y, w, TILE_H);
		int edge = next ? Theme.ACCENT : reached ? Ui.alpha(Theme.GREEN, 0.5f) : Ui.alpha(color, 0.25f + 0.3f * hv);
		Ui.glassBorder(x, y, w, TILE_H, 10, reached ? 0xFF131A15 : Ui.mix(0xFF151417, 0xFF1D1C20, hv), edge);
		Ui.text(Ui.MEDIUM, t("rago.pass.tier", n), x + 10, y + 8, 8.5f, next ? Theme.ACCENT : Theme.MUTED);
		if (reached) {
			Ui.icon("check", x + w - 20, y + 7, 11, Theme.GREEN);
		}
		Ui.round(x + w / 2 - 15, y + 24, 30, 30, 9, Ui.alpha(color, 0.14f));
		Ui.icon(item == null ? "coins" : RewardsScreen.typeIcon(item), x + w / 2 - 8, y + 31, 16, color);
		String name = item == null ? t("rago.pass.points", points) : Rewards.name(item);
		Ui.textCentered(Ui.SEMIBOLD, Ui.wrap(Ui.SEMIBOLD, name, 9, w - 10).get(0), x + w / 2, y + 60, 9, Theme.TEXT);
		if (it != null) {
			boolean only = it.sources.size() == 1 && it.from("pass");
			String sub = only ? t("rago.pass.only_here") : Rewards.rarityName(it.rarity);
			Ui.textCentered(Ui.REGULAR, sub, x + w / 2, y + 74, 7.5f, only ? Theme.ACCENT : color);
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			scroll -= Math.signum(wheel) * 40;
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button != 0) {
			return;
		}
		Ui.updateMouse();
		Ui.dispatchClick();
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) {
			mc.displayGuiScreen(parent);
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
