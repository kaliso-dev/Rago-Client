package gg.rago.gui;

import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.TextColors;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.UiTheme;
import gg.rago.gui.ui.Widgets;
import gg.rago.optimize.PlayPreset;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

/**
 * The welcome guide, on the first start (and from Settings › General): what you play (the mods for
 * Hypixel Bed Wars, 1.8 PvP or both), your look (menu theme and colour-blind team colours, which
 * change as you click) and performance (max FPS, your own video settings, or beautiful). Nothing is
 * applied before the last step but the look; Skip or Escape changes nothing else.
 */
public final class WelcomeScreen extends GuiScreen {
	static final int STEPS = 3;
	/** Bed Wars teams in their sidebar order: red, blue, green, yellow, aqua, white, pink, gray. */
	private static final String TEAMS = "c9aebfd8";

	private final GuiScreen parent;
	private final long openedNs = System.nanoTime();
	private int step;
	private long stepNs = System.nanoTime();
	private boolean backwards;
	private PlayPreset.Kind game;
	/** 0 max FPS, 1 keep the player's own video settings, 2 beautiful. */
	private int performance = 1;

	public WelcomeScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
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
		Ui.backdrop(0.55f);
		Ui.captureGlass();
		float open = Anim.easeOut(Anim.progress(openedNs, 320));
		Ui.opacity(open);
		float pw = Math.min(w - 32, 660);
		float ph = Math.min(h - 32, 386);
		float px = (w - pw) / 2;
		float py = (h - ph) / 2 + (1 - open) * 12;
		Ui.glassBorder(px, py, pw, ph, 18, Theme.PANEL, Theme.BORDER_STRONG);
		header(px, py, pw);

		float t = Anim.easeOut(Anim.progress(stepNs, 240));
		Ui.opacity(open * t);
		float shift = (1 - t) * 28 * (backwards ? -1 : 1);
		float cx = px + 28 + shift;
		float cy = py + 92;
		float cw = pw - 56;
		float ch = ph - 92 - 62;
		switch (step) {
			case 0:
				play(cx, cy, cw, ch);
				break;
			case 1:
				look(cx, cy, cw, ch);
				break;
			default:
				performance(cx, cy, cw, ch);
		}
		Ui.opacity(open);
		footer(px, py + ph - 52, pw);
		Ui.opacity(1);
		Ui.end();
	}

	private void header(float x, float y, float w) {
		float logo = 30;
		Ui.logoGlow(x + 26, y + 24, logo, 0x66F2392C);
		Ui.logo(x + 26, y + 24, logo, 0xFFFFFFFF);
		Ui.text(Ui.SEMIBOLD, t("rago.welcome.title"), x + 68, y + 22, 17, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.welcome.subtitle"), x + 68, y + 45, 10, Theme.MUTED);
		// the steps: a bar each, filled up to the one shown
		float bw = 22;
		float gap = 6;
		float bx = x + w - 26 - STEPS * bw - (STEPS - 1) * gap;
		for (int i = 0; i < STEPS; i++) {
			float fill = Anim.to("welcome-step-" + i, i <= step ? 1f : 0f, 220);
			Ui.round(bx + i * (bw + gap), y + 30, bw, 4, 2, Ui.mix(Theme.SWITCH_OFF, Theme.ACCENT, fill));
		}
		String n = t("rago.welcome.step", step + 1, STEPS);
		Ui.text(Ui.MEDIUM, n, x + w - 26 - Ui.textWidth(Ui.MEDIUM, n, 9), y + 40, 9, Theme.DIM);
		Ui.rect(x + 1, y + 74, w - 2, 1, Theme.BORDER);
	}

	private void title(float x, float y, String title, String hint) {
		Ui.text(Ui.SEMIBOLD, title, x, y, 14, Theme.TEXT);
		Ui.text(Ui.REGULAR, hint, x, y + 21, 9.5f, Theme.DIM);
	}

	// ---- step 1: what you play ------------------------------------------------------------------

	private void play(float x, float y, float w, float h) {
		title(x, y, t("rago.welcome.play.title"), t("rago.welcome.play.hint"));
		float top = y + 46;
		float gap = 12;
		float cw = (w - gap * 2) / 3;
		float ch = Math.min(h - 46, 170);
		card("play-bedwars", x, top, cw, ch, "bed", t("rago.welcome.play.bedwars"), t("rago.welcome.play.bedwars.desc"),
			game == PlayPreset.Kind.BEDWARS, () -> game = game == PlayPreset.Kind.BEDWARS ? null : PlayPreset.Kind.BEDWARS);
		card("play-pvp", x + cw + gap, top, cw, ch, "swords", t("rago.welcome.play.pvp"), t("rago.welcome.play.pvp.desc"),
			game == PlayPreset.Kind.PVP, () -> game = game == PlayPreset.Kind.PVP ? null : PlayPreset.Kind.PVP);
		card("play-both", x + (cw + gap) * 2, top, cw, ch, "layers", t("rago.welcome.play.both"), t("rago.welcome.play.both.desc"),
			game == PlayPreset.Kind.BOTH, () -> game = game == PlayPreset.Kind.BOTH ? null : PlayPreset.Kind.BOTH);
	}

	// ---- step 2: your look ----------------------------------------------------------------------

	private void look(float x, float y, float w, float h) {
		title(x, y, t("rago.welcome.look.title"), t("rago.welcome.look.hint"));
		float gap = 12;
		float cw = (w - gap * 2) / 3;
		float rowH = 58;
		float top = y + 50;
		Ui.text(Ui.MEDIUM, t("rago.welcome.look.theme"), x, top, 10, Theme.MUTED);
		String[] themes = {"rago.value.theme_dark", "rago.value.theme_oled", "rago.value.theme_contrast"};
		String[] themeIcons = {"monitor", "eye-off", "contrast"};
		int theme = UiTheme.current();
		for (int i = 0; i < 3; i++) {
			final int v = i;
			option("theme-" + i, x + i * (cw + gap), top + 16, cw, rowH, themeIcons[i], t(themes[i]), theme == i, () -> RagoClient.setUiTheme(v), -1);
		}
		top += 16 + rowH + 18;
		Ui.text(Ui.MEDIUM, t("rago.welcome.look.colors"), x, top, 10, Theme.MUTED);
		String[] palettes = {"rago.value.colors_normal", "rago.value.colors_red_green", "rago.value.colors_blue_yellow"};
		int palette = TextColors.palette();
		for (int i = 0; i < 3; i++) {
			final int v = i;
			option("colors-" + i, x + i * (cw + gap), top + 16, cw, rowH, null, t(palettes[i]), palette == i, () -> RagoClient.setTeamColors(v), i);
		}
	}

	// ---- step 3: performance --------------------------------------------------------------------

	private void performance(float x, float y, float w, float h) {
		title(x, y, t("rago.welcome.perf.title"), t("rago.welcome.perf.hint"));
		float top = y + 46;
		float gap = 12;
		float cw = (w - gap * 2) / 3;
		float ch = Math.min(h - 46, 170);
		card("perf-max", x, top, cw, ch, "zap", t("rago.welcome.perf.max"), t("rago.welcome.perf.max.desc"),
			performance == 0, () -> performance = 0);
		card("perf-keep", x + cw + gap, top, cw, ch, "gauge", t("rago.welcome.perf.keep"), t("rago.welcome.perf.keep.desc"),
			performance == 1, () -> performance = 1);
		card("perf-quality", x + (cw + gap) * 2, top, cw, ch, "sparkles", t("rago.welcome.perf.quality"), t("rago.welcome.perf.quality.desc"),
			performance == 2, () -> performance = 2);
	}

	// ---- parts ----------------------------------------------------------------------------------

	/** A big choice: an icon, a title and a few lines saying what it does. */
	private void card(String key, float x, float y, float w, float h, String icon, String title, String desc, boolean selected, Runnable action) {
		frame(key, x, y, w, h, selected);
		float s = Anim.to(key + "-sel", selected ? 1f : 0f, 180);
		Ui.round(x + 16, y + 16, 34, 34, 10, Ui.mix(0xFF26242A, Theme.ACCENT, s));
		Ui.icon(icon, x + 24, y + 24, 18, Ui.mix(Theme.TEXT, 0xFFFFFFFF, s));
		Ui.text(Ui.SEMIBOLD, title, x + 16, y + 64, 12, Theme.TEXT);
		List<String> lines = Ui.wrap(Ui.REGULAR, desc, 9.5f, w - 32);
		float ly = y + 86;
		for (String line : lines) {
			if (ly + 12 > y + h - 8) {
				break;
			}
			Ui.text(Ui.REGULAR, line, x + 16, ly, 9.5f, Theme.MUTED);
			ly += 13;
		}
		Ui.click(x, y, w, h, action);
	}

	/** A small choice in a row: an icon or the team colours of a palette, and its name. */
	private void option(String key, float x, float y, float w, float h, String icon, String label, boolean selected, Runnable action, int palette) {
		frame(key, x, y, w, h, selected);
		float tx = x + 16;
		if (icon != null) {
			Ui.icon(icon, x + 16, y + (h - 16) / 2, 16, selected ? Theme.ACCENT : Theme.MUTED);
			tx += 26;
		}
		if (palette >= 0) {
			Ui.text(Ui.SEMIBOLD, label, tx, y + 13, 10.5f, Theme.TEXT);
			float dx = tx + 5;
			for (int i = 0; i < TEAMS.length(); i++) {
				int code = Character.digit(TEAMS.charAt(i), 16);
				Ui.circle(dx, y + h - 17, 5, 0xFF000000 | TextColors.rgb(palette, code));
				dx += 14;
			}
		} else {
			Ui.text(Ui.SEMIBOLD, label, tx, y + (h - 10.5f * 1.21f) / 2, 10.5f, Theme.TEXT);
		}
		Ui.click(x, y, w, h, action);
	}

	/** The card behind a choice, outlined in the accent colour when chosen, with a tick. */
	private void frame(String key, float x, float y, float w, float h, boolean selected) {
		float hover = Widgets.hoverAmount(key, x, y, w, h);
		float s = Anim.to(key + "-sel", selected ? 1f : 0f, 180);
		Ui.round(x, y, w, h, 12, Ui.mix(Theme.CARD, Theme.CARD_HOVER, hover));
		if (s > 0) {
			Ui.round(x, y, w, h, 12, Ui.alpha(Theme.ACCENT, 0.16f * s));
		}
		Ui.outline(x, y, w, h, 12, Ui.mix(Theme.BORDER_STRONG, Theme.ACCENT, s));
		if (s > 0.01f) {
			float r = 9;
			Ui.circle(x + w - 16, y + 16, r * s, Theme.ACCENT);
			Ui.icon("check", x + w - 16 - 6, y + 16 - 6, 12, Ui.alpha(0xFFFFFFFF, s));
		}
	}

	private void footer(float x, float y, float w) {
		Ui.rect(x + 1, y - 10, w - 2, 1, Theme.BORDER);
		String left = step == 0 ? t("rago.welcome.skip") : t("rago.welcome.back");
		float lw = Ui.textWidth(Ui.MEDIUM, left, 10.5f) + 24;
		boolean hover = Ui.hover(x + 16, y + 6, lw, 30);
		Ui.text(Ui.MEDIUM, left, x + 28, y + 14, 10.5f, hover ? Theme.TEXT : Theme.MUTED);
		Ui.click(x + 16, y + 6, lw, 30, step == 0 ? this::skip : this::back);
		String next = step == STEPS - 1 ? t("rago.welcome.finish") : t("rago.welcome.next");
		float bw = Math.max(120, Ui.textWidth(Ui.SEMIBOLD, next, 11) + 52);
		float bx = x + w - 20 - bw;
		Widgets.button(bx, y + 6, bw, 32, step == STEPS - 1 ? "play" : "chevron-right", next, true, 11);
		Ui.click(bx, y + 6, bw, 32, this::next);
	}

	private void next() {
		if (step < STEPS - 1) {
			step++;
			backwards = false;
			stepNs = System.nanoTime();
			return;
		}
		RagoClient.finishWelcome(game, performance);
		mc.displayGuiScreen(parent);
	}

	private void back() {
		if (step > 0) {
			step--;
			backwards = true;
			stepNs = System.nanoTime();
		}
	}

	/** Leaves the guide: the look chosen stays, nothing else changes, and it does not open again by itself. */
	private void skip() {
		RagoClient.finishWelcome(null, 1);
		mc.displayGuiScreen(parent);
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
			skip();
		} else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
			next();
		} else if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_LEFT) {
			back();
		} else if (keyCode == Keyboard.KEY_RIGHT) {
			if (step < STEPS - 1) {
				next();
			}
		}
	}
}
