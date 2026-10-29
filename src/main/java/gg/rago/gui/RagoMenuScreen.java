package gg.rago.gui;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;
import gg.rago.core.Category;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.hud.HudElement;
import gg.rago.hud.HudPosition;
import gg.rago.perf.FrameStats;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Rago mods menu (Right Shift, or Mods on the home screen), after the design mock-up:
 * categories on the left, mods as cards in the middle, the selected mod's settings with a live
 * preview on the right. Typing anywhere searches.
 */
public final class RagoMenuScreen extends GuiScreen {
	private static final int[] SWATCHES = {0xFFF2392C, 0xFFFFFFFF, 0xFF34D399, 0xFF60A5FA, 0xFFFACC15};
	private static final float CARD_H = 158;
	private static final float GAP = 16;
	private static final float ROW_H = 54;

	private enum Filter { ALL, ON, OFF }

	private final GuiScreen parent;
	private Category category;           // null = all
	private Filter filter = Filter.ALL;
	private Module selected;
	private String query = "";
	private float scroll;
	private float settingsScroll;
	private boolean confirmReset;
	private Slider dragging;

	// Layout of the last frame, used for scrolling
	private float gridX;
	private float gridY;
	private float gridW;
	private float gridH;
	private float gridContentH;
	private float setX;
	private float setY;
	private float setW;
	private float setH;
	private float setContentH;

	/** A slider registered while drawing; dragging it calls {@code set} with 0..1. */
	private static final class Slider {
		final float x;
		final float w;
		final Consumer<Float> set;

		Slider(float x, float w, Consumer<Float> set) {
			this.x = x;
			this.w = w;
			this.set = set;
		}
	}

	public RagoMenuScreen(GuiScreen parent) {
		this.parent = parent;
		this.selected = RagoClient.modules().get("keystrokes").orElse(null);
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	// ---- data -----------------------------------------------------------------------------------

	private List<Module> inCategory() {
		List<Module> list = new ArrayList<>();
		for (Module m : RagoClient.modules().all()) {
			if (category == null || m.category() == category) {
				list.add(m);
			}
		}
		return list;
	}

	private List<Module> visible() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>();
		for (Module m : inCategory()) {
			if (filter == Filter.ON && !m.isEnabled() || filter == Filter.OFF && m.isEnabled()) {
				continue;
			}
			if (!q.isEmpty() && !I18n.format(m.nameKey()).toLowerCase(Locale.ROOT).contains(q)
				&& !I18n.format(ModuleInfo.descKey(m)).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			list.add(m);
		}
		return list;
	}

	private static int countOn(List<Module> modules) {
		int n = 0;
		for (Module m : modules) {
			n += m.isEnabled() ? 1 : 0;
		}
		return n;
	}

	// ---- drawing --------------------------------------------------------------------------------

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		if (dragging != null) {
			if (Mouse.isButtonDown(0)) {
				dragging.set.accept(Math.max(0f, Math.min(1f, (Ui.mouseX() - dragging.x) / dragging.w)));
			} else {
				dragging = null;
			}
		}
		float w = Ui.width();
		float h = Ui.height();
		if (mc.theWorld == null) {
			Ui.rect(0, 0, w, h, Theme.BG);
			Ui.glow(w * 0.2f, h, 640, Ui.alpha(0xFF5A0E0A, 0.35f));
			Ui.glow(w * 0.85f, 0, 520, Ui.alpha(0xFF5A0E0A, 0.25f));
		} else {
			Ui.rect(0, 0, w, h, 0x99000000);
		}

		FrameStats s = RagoClient.frames().stats();
		int ping = RagoClient.ping().latest();
		String top = (s == FrameStats.EMPTY ? "--" : String.format(Locale.ROOT, "%.0f", s.avgFps)) + " FPS"
			+ (ping >= 0 ? " · " + ping + " ms" : "");
		Ui.text(Ui.MONO, top, 28, 20, 12, Theme.MUTED);

		float pw = Math.min(w - 48, 1240);
		float ph = Math.min(h - 70, 680);
		float px = (w - pw) / 2;
		float py = (h - ph) / 2 + 12;
		Ui.roundBorder(px, py, pw, ph, 24, Theme.PANEL, Theme.BORDER_STRONG);

		float sw = pw >= 1000 ? 236 : 196;
		float rw = pw >= 1080 ? 340 : 0;
		Ui.rect(px + sw, py + 1, 1 / Ui.scale(), ph - 2, Theme.BORDER);
		if (rw > 0) {
			Ui.rect(px + pw - rw, py + 1, 1 / Ui.scale(), ph - 2, Theme.BORDER);
		}
		sidebar(px, py, sw, ph);
		if (rw == 0 && selected != null && showSettingsOnly) {
			settings(px + sw, py, pw - sw, ph);
		} else {
			main(px + sw, py, pw - sw - rw, ph);
			if (rw > 0) {
				settings(px + pw - rw, py, rw, ph);
			}
		}
		Ui.end();
	}

	/** On narrow windows the settings replace the grid until Esc or Back. */
	private boolean showSettingsOnly;

	private void sidebar(float x, float y, float w, float h) {
		Ui.logo(x + 26, y + 26, 22, Theme.ACCENT);
		float tx = x + 58;
		tx += Ui.text(Ui.BOLD, "Rago", tx, y + 26, 17, Theme.TEXT);
		Ui.text(Ui.REGULAR, " Client", tx, y + 26, 17, 0xFFD4D4D8);

		float ry = y + 76;
		String[] icons = {"layout-grid", "monitor", "swords", "eye", "zap"};
		for (int i = -1; i < Category.values().length; i++) {
			Category c = i < 0 ? null : Category.values()[i];
			String label = c == null ? t("rago.menu.all_mods") : t(c.key());
			int count = 0;
			for (Module m : RagoClient.modules().all()) {
				count += c == null || m.category() == c ? 1 : 0;
			}
			boolean active = c == category;
			boolean hover = Ui.hover(x + 14, ry, w - 28, 40);
			if (active || hover) {
				Ui.round(x + 14, ry, w - 28, 40, 12, active ? 0xFF1F1E22 : 0xFF19181B);
			}
			int color = active ? Theme.TEXT : 0xFFB4B2BA;
			Ui.icon(icons[i + 1], x + 28, ry + 12, 16, active ? Theme.ACCENT : color);
			Ui.text(active ? Ui.SEMIBOLD : Ui.MEDIUM, label, x + 54, ry + 11, 14, color);
			Ui.textRight(Ui.MONO, Integer.toString(count), x + w - 28, ry + 12, 12, Theme.DIM);
			Category target = c;
			Ui.click(x + 14, ry, w - 28, 40, () -> {
				category = target;
				scroll = 0;
			});
			ry += 44;
		}

		ry += 18;
		Ui.textSpaced(Ui.MONO, t("rago.menu.profiles"), x + 28, ry, 10.5f, 2, Theme.DIM);
		ry += 22;
		Module profile = RagoClient.competitiveProfile();
		if (profile != null) {
			boolean hover = Ui.hover(x + 14, ry, w - 28, 38);
			if (hover) {
				Ui.round(x + 14, ry, w - 28, 38, 12, 0xFF19181B);
			}
			Ui.circle(x + 32, ry + 19, 3.5f, profile.isEnabled() ? Theme.ACCENT : Theme.DIM);
			Ui.text(Ui.MEDIUM, t(profile.nameKey()), x + 46, ry + 10, 14, profile.isEnabled() ? Theme.TEXT : 0xFFB4B2BA);
			Ui.click(x + 14, ry, w - 28, 38, () -> profile.setEnabled(!profile.isEnabled()));
		}

		float by = y + h - 104;
		Ui.roundBorder(x + 14, by, w - 28, 88, 14, 0xFF141316, Theme.BORDER);
		List<Module> all = RagoClient.modules().all();
		Ui.textSpaced(Ui.MONO, t("rago.menu.status", countOn(all), all.size()), x + 28, by + 16, 10.5f, 1.5f, Theme.MUTED);
		float ly = by + 38;
		for (String line : Ui.wrap(Ui.REGULAR, t("rago.menu.autosave"), 12, w - 60)) {
			Ui.text(Ui.REGULAR, line, x + 28, ly, 12, 0xFFB4B2BA);
			ly += 16;
		}
	}

	private void main(float x, float y, float w, float h) {
		float cx = x + 28;
		float cw = w - 56;
		Ui.textSpaced(Ui.MONO, t("rago.menu.mods"), cx, y + 28, 10.5f, 2, Theme.DIM);
		String title = category == null ? t("rago.menu.all_mods") : t(category.key());
		float tw = Ui.text(Ui.BOLD, title, cx, y + 44, 30, Theme.TEXT);
		Ui.text(Ui.MONO, t("rago.menu.on_count", countOn(inCategory())), cx + tw + 12, y + 60, 13, Theme.MUTED);

		// Header buttons, right-aligned: search, reset, edit HUD
		float bx = cx + cw;
		float by = y + 38;
		float ew = Ui.textWidth(Ui.SEMIBOLD, t("rago.menu.edit_layout"), 14) + 62;
		bx -= ew;
		Widgets.button(bx, by, ew, 44, "move", t("rago.menu.edit_layout"), true, 14);
		Ui.click(bx, by, ew, 44, () -> mc.displayGuiScreen(new HudEditorScreen(this)));
		String resetLabel = confirmReset ? t("rago.menu.reset_confirm") : t("rago.menu.reset");
		float rw = Ui.textWidth(Ui.SEMIBOLD, resetLabel, 14) + 40;
		bx -= rw + 10;
		boolean rh = Ui.hover(bx, by, rw, 44);
		Ui.roundBorder(bx, by, rw, 44, 22, confirmReset ? 0x40F2392C : rh ? 0xFF222126 : 0xFF19181C, Theme.BORDER_STRONG);
		Ui.textCentered(Ui.SEMIBOLD, resetLabel, bx + rw / 2, by + 13, 14, confirmReset ? Theme.ACCENT : Theme.TEXT);
		Ui.click(bx, by, rw, 44, () -> {
			if (confirmReset) {
				for (Module m : RagoClient.modules().all()) {
					m.reset();
				}
				confirmReset = false;
			} else {
				confirmReset = true;
			}
		});
		// Filter chips
		float fy = y + 104;
		float fx = cx;
		List<Module> cat = inCategory();
		int on = countOn(cat);
		fx = chip(fx, fy, t("rago.menu.filter_all"), cat.size(), Filter.ALL);
		fx = chip(fx, fy, t("rago.menu.filter_on"), on, Filter.ON);
		fx = chip(fx, fy, t("rago.menu.filter_off"), cat.size() - on, Filter.OFF);
		searchField(Math.max(fx + 12, cx + cw - 280), fy - 4, Math.min(280, cx + cw - fx - 12));

		// Grid
		gridX = cx;
		gridY = y + 152;
		gridW = cw;
		gridH = y + h - 18 - gridY;
		List<Module> modules = visible();
		int cols = Math.max(1, (int) ((cw + GAP) / (250 + GAP)));
		float cardW = (cw - (cols - 1) * GAP) / cols;
		int rows = (modules.size() + cols - 1) / cols;
		gridContentH = rows * (CARD_H + GAP) - GAP;
		scroll = Math.max(0, Math.min(Math.max(0, gridContentH - gridH), scroll));
		if (modules.isEmpty()) {
			Ui.textCentered(Ui.REGULAR, t("rago.menu.no_results"), cx + cw / 2, gridY + 40, 14, Theme.MUTED);
		}
		Ui.scissor(cx - 4, gridY - 4, cw + 8, gridH + 8);
		for (int i = 0; i < modules.size(); i++) {
			float x0 = cx + (i % cols) * (cardW + GAP);
			float y0 = gridY + (i / cols) * (CARD_H + GAP) - scroll;
			if (y0 + CARD_H < gridY - 4 || y0 > gridY + gridH + 4) {
				continue;
			}
			card(modules.get(i), x0, y0, cardW, Ui.hover(cx, gridY, cw, gridH));
		}
		Ui.endScissor();
		if (gridContentH > gridH) {
			float bar = gridH * gridH / gridContentH;
			float pos = (gridH - bar) * scroll / (gridContentH - gridH);
			Ui.round(cx + cw + 10, gridY + pos, 3, bar, 1.5f, 0x33FFFFFF);
		}
	}

	/** Search box; it is always focused, so typing anywhere in the menu searches. */
	private void searchField(float x, float y, float w) {
		if (w < 120) {
			return;
		}
		float h = 40;
		Ui.roundBorder(x, y, w, h, 14, Theme.FIELD, Theme.BORDER_STRONG);
		Ui.icon("search", x + 14, y + 12, 16, Theme.MUTED);
		boolean caret = System.currentTimeMillis() / 500 % 2 == 0;
		if (query.isEmpty()) {
			Ui.text(Ui.REGULAR, t("rago.menu.search"), x + 40, y + 11, 14, Theme.DIM);
		} else {
			float qw = Ui.text(Ui.REGULAR, query, x + 40, y + 11, 14, Theme.TEXT);
			if (caret) {
				Ui.rect(x + 41 + qw, y + 11, 1.2f, 17, Theme.TEXT);
			}
		}
	}

	private float chip(float x, float y, String label, int count, Filter f) {
		boolean active = filter == f;
		float lw = Ui.textWidth(Ui.MEDIUM, label, 13);
		String c = Integer.toString(count);
		float w = 16 + lw + 8 + Ui.textWidth(Ui.MONO, c, 11) + 16;
		boolean hover = Ui.hover(x, y, w, 32);
		if (active) {
			Ui.round(x, y, w, 32, 16, Theme.CHIP_ACTIVE);
		} else {
			Ui.roundBorder(x, y, w, 32, 16, hover ? 0xFF222126 : Theme.CHIP, Theme.BORDER_STRONG);
		}
		Ui.text(Ui.MEDIUM, label, x + 16, y + 8, 13, active ? 0xFF0B0A0D : 0xFFD4D4D8);
		Ui.text(Ui.MONO, c, x + 24 + lw, y + 10, 11, active ? 0xFF55535A : Theme.DIM);
		Ui.click(x, y, w, 32, () -> {
			filter = f;
			scroll = 0;
		});
		return x + w + 8;
	}

	private void card(Module m, float x, float y, float w, boolean gridHover) {
		boolean isSelected = m == selected;
		boolean hover = gridHover && Ui.hover(x, y, w, CARD_H);
		if (isSelected) {
			Ui.glow(x + w / 2, y + CARD_H / 2, w * 0.7f, Ui.alpha(Theme.ACCENT, 0.10f));
		}
		Ui.roundBorder(x, y, w, CARD_H, 16, hover ? Theme.CARD_HOVER : Theme.CARD,
			isSelected ? Ui.alpha(Theme.ACCENT, 0.85f) : Theme.BORDER_STRONG);
		Ui.round(x + 18, y + 18, 38, 38, 11, m.isEnabled() ? 0xFF2A1313 : 0xFF222126);
		Ui.icon(ModuleInfo.icon(m), x + 28, y + 28, 18, m.isEnabled() ? Theme.ACCENT : Theme.MUTED);
		Ui.text(Ui.SEMIBOLD, I18n.format(m.nameKey()), x + 68, y + 18, 15, Theme.TEXT);
		Ui.textSpaced(Ui.MONO, t(m.isEnabled() ? "rago.menu.on_label" : "rago.menu.off_label"), x + 68, y + 40, 10, 1.2f, Theme.DIM);
		float dy = y + 72;
		List<String> lines = Ui.wrap(Ui.REGULAR, I18n.format(ModuleInfo.descKey(m)), 12.5f, w - 36);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			Ui.text(Ui.REGULAR, lines.get(i), x + 18, dy, 12.5f, 0xFFB4B2BA);
			dy += 18;
		}
		float fy = y + CARD_H - 38;
		if (!m.options().isEmpty() || m instanceof HudElement) {
			Ui.icon("sliders-horizontal", x + 18, fy + 4, 13, Theme.MUTED);
			Ui.text(Ui.MEDIUM, t("rago.menu.options"), x + 38, fy + 2, 13, Theme.MUTED);
		}
		float tx = x + w - 18 - Widgets.SWITCH_W;
		Widgets.toggle(tx, fy, m.isEnabled());
		if (gridHover) {
			Ui.click(x, y, w, CARD_H, () -> {
				selected = m;
				settingsScroll = 0;
				showSettingsOnly = true;
			});
			Ui.click(tx - 6, fy - 6, Widgets.SWITCH_W + 12, Widgets.SWITCH_H + 12, () -> m.setEnabled(!m.isEnabled()));
		}
	}

	private void settings(float x, float y, float w, float h) {
		float cx = x + 24;
		float cw = w - 48;
		boolean overlay = w > 400; // narrow window: settings shown instead of the grid
		if (selected == null) {
			Ui.textSpaced(Ui.MONO, t("rago.menu.settings"), cx, y + 28, 10.5f, 2, Theme.DIM);
			for (String line : Ui.wrap(Ui.REGULAR, t("rago.menu.select"), 14, cw)) {
				Ui.text(Ui.REGULAR, line, cx, y + 56, 14, Theme.MUTED);
				y += 20;
			}
			return;
		}
		Module m = selected;
		if (overlay) {
			float bw = Ui.textWidth(Ui.MEDIUM, t("gui.back"), 13) + 30;
			Ui.roundBorder(x + w - 24 - bw, y + 22, bw, 30, 15, 0xFF19181C, Theme.BORDER_STRONG);
			Ui.textCentered(Ui.MEDIUM, t("gui.back"), x + w - 24 - bw / 2, y + 29, 13, Theme.TEXT);
			Ui.click(x + w - 24 - bw, y + 22, bw, 30, () -> showSettingsOnly = false);
		} else {
			Ui.textSpaced(Ui.MONO, t("rago.menu.settings"), cx, y + 28, 10.5f, 2, Theme.DIM);
		}
		Ui.text(Ui.BOLD, I18n.format(m.nameKey()), cx, y + (overlay ? 26 : 48), 24, Theme.TEXT);
		float tgx = overlay ? x + w - 24 - Widgets.SWITCH_W - 110 : cx + cw - Widgets.SWITCH_W;
		float tgy = y + (overlay ? 28 : 52);
		Widgets.toggle(tgx, tgy, m.isEnabled());
		Ui.click(tgx - 6, tgy - 6, Widgets.SWITCH_W + 12, Widgets.SWITCH_H + 12, () -> m.setEnabled(!m.isEnabled()));
		float ty = y + (overlay ? 64 : 88);
		for (String line : Ui.wrap(Ui.REGULAR, I18n.format(ModuleInfo.descKey(m)), 13, cw)) {
			Ui.text(Ui.REGULAR, line, cx, ty, 13, 0xFFB4B2BA);
			ty += 18;
		}

		boolean hud = m instanceof HudElement;
		float bottomH = 64;
		setX = cx;
		setY = ty + 14;
		setW = cw;
		setH = y + h - bottomH - setY - 8;
		setContentH = (hud ? 168 : 0) + rowsHeight(m);
		settingsScroll = Math.max(0, Math.min(Math.max(0, setContentH - setH), settingsScroll));
		Ui.scissor(cx - 2, setY, cw + 4, setH);
		float sy = setY - settingsScroll;
		if (hud) {
			preview((HudElement) m, cx, sy, cw, 154);
			sy += 168;
		}
		options(m, cx, sy, cw);
		Ui.endScissor();

		// Bottom buttons
		float by = y + h - bottomH + 8;
		float half = hud ? (cw - 10) / 2 : cw;
		Widgets.button(cx, by, half, 44, null, t("rago.menu.reset_default"), false, 13);
		Ui.click(cx, by, half, 44, m::reset);
		if (hud) {
			Widgets.button(cx + half + 10, by, half, 44, "move", t("rago.menu.move"), false, 13);
			Ui.click(cx + half + 10, by, half, 44, () -> mc.displayGuiScreen(new HudEditorScreen(this)));
		}
	}

	private float rowsHeight(Module m) {
		int rows = m.options().size() + (m instanceof HudElement ? 1 : 0);
		return Math.max(1, rows) * ROW_H;
	}

	/** Draws the HUD element itself, with sample data when there is no live data. */
	private void preview(HudElement e, float x, float y, float w, float h) {
		Ui.roundBorder(x, y, w, h, 16, 0xFF141316, Theme.BORDER);
		for (float gx = x + 16; gx < x + w - 8; gx += 16) {
			for (float gy = y + 16; gy < y + h - 8; gy += 16) {
				Ui.rect(gx, gy, 1.5f, 1.5f, 0x14FFFFFF);
			}
		}
		Ui.textSpaced(Ui.MONO, t("rago.menu.preview"), x + 16, y + 14, 9.5f, 2, Theme.DIM);
		boolean placeholder = !e.hasContent();
		int ew = Math.max(1, e.width(fontRendererObj, placeholder));
		int eh = Math.max(1, e.height(fontRendererObj, placeholder));
		float s = Ui.scale();
		float k = Math.min(2.2f * s, Math.min((w - 40) * s / ew, (h - 50) * s / eh));
		float dx = (x + w / 2) * s - ew * k / 2;
		float dy = (y + 12 + h / 2) * s - eh * k / 2;
		GlStateManager.pushMatrix();
		GlStateManager.translate(Math.round(dx), Math.round(dy), 0);
		GlStateManager.scale(k, k, 1);
		GlStateManager.enableTexture2D();
		GlStateManager.enableAlpha();
		e.render(fontRendererObj, placeholder);
		GlStateManager.popMatrix();
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
	}

	private void options(Module m, float x, float y, float w) {
		List<Option<?>> opts = m.options();
		int rows = opts.size() + (m instanceof HudElement ? 1 : 0);
		if (rows == 0) {
			Ui.roundBorder(x, y, w, ROW_H, 14, 0xFF141316, Theme.BORDER);
			Ui.text(Ui.REGULAR, t("rago.menu.no_options"), x + 18, y + 18, 13, Theme.MUTED);
			return;
		}
		Ui.roundBorder(x, y, w, rows * ROW_H, 16, 0xFF141316, Theme.BORDER);
		float ry = y;
		boolean inside = Ui.hover(setX, setY, setW, setH);
		for (int i = 0; i < opts.size(); i++) {
			optionRow(opts.get(i), x, ry, w, inside);
			ry += ROW_H;
			if (i < rows - 1) {
				Ui.rect(x + 1, ry, w - 2, 1 / Ui.scale(), Theme.BORDER);
			}
		}
		if (m instanceof HudElement) {
			HudPosition p = ((HudElement) m).position();
			Ui.text(Ui.MEDIUM, t("rago.option.scale"), x + 18, ry + 18, 14, Theme.TEXT);
			float f = (p.scale() - HudPosition.MIN_SCALE) / (HudPosition.MAX_SCALE - HudPosition.MIN_SCALE);
			slider(x + w - 18 - 50 - 100, ry, 100, f, String.format(Locale.ROOT, "%.1f×", p.scale()), x + w - 18, inside,
				v -> p.setScale(Math.round((HudPosition.MIN_SCALE + v * (HudPosition.MAX_SCALE - HudPosition.MIN_SCALE)) * 10) / 10f));
		}
	}

	private void optionRow(Option<?> option, float x, float y, float w, boolean inside) {
		String label = I18n.format("rago.option." + option.key());
		float right = x + w - 18;
		if (option instanceof BooleanOption) {
			BooleanOption b = (BooleanOption) option;
			Ui.text(Ui.MEDIUM, label, x + 18, y + 18, 14, Theme.TEXT);
			Widgets.toggle(right - Widgets.SWITCH_W, y + 16, b.get());
			if (inside) {
				Ui.click(x, y, w, ROW_H, b::toggle);
			}
		} else if (option instanceof IntOption) {
			IntOption io = (IntOption) option;
			fitLabel(label, x + 18, y + 18, w - 36 - 50 - 100 - 12);
			float f = io.max() == io.min() ? 0 : (float) (io.get() - io.min()) / (io.max() - io.min());
			String key = "rago.option." + io.key() + "." + io.get();
			String named = I18n.format(key);
			String value = named.equals(key) ? Integer.toString(io.get()) : named;
			slider(right - 50 - 100, y, 100, f, value, right, inside,
				v -> io.set(io.min() + Math.round(v * (io.max() - io.min()))));
		} else if (option instanceof ColorOption) {
			ColorOption c = (ColorOption) option;
			fitLabel(label, x + 18, y + 18, w - 200);
			float sx = right - SWATCHES.length * 26 + 4;
			for (int color : SWATCHES) {
				boolean sel = c.get() == color;
				if (sel) {
					Ui.circle(sx + 11, y + ROW_H / 2, 13, Theme.ACCENT);
					Ui.circle(sx + 11, y + ROW_H / 2, 11.5f, 0xFF141316);
				}
				Ui.circle(sx + 11, y + ROW_H / 2, 9.5f, color);
				if (inside) {
					Ui.click(sx, y + ROW_H / 2 - 12, 24, 24, () -> c.set(color));
				}
				sx += 26;
			}
		}
	}

	private static void fitLabel(String label, float x, float y, float maxW) {
		String s = label;
		while (s.length() > 3 && Ui.textWidth(Ui.MEDIUM, s, 14) > maxW) {
			s = s.substring(0, s.length() - 2);
		}
		Ui.text(Ui.MEDIUM, s.equals(label) ? s : s.trim() + "…", x, y, 14, Theme.TEXT);
	}

	private void slider(float x, float y, float w, float f, String value, float right, boolean inside, Consumer<Float> set) {
		Widgets.slider(x, y + ROW_H / 2 - 9, w, f);
		Ui.textRight(Ui.MONO, value, right, y + 19, 12, 0xFFD4D4D8);
		if (inside) {
			Slider s = new Slider(x, w, set);
			Ui.click(x - 10, y, w + 20, ROW_H, () -> {
				dragging = s;
				s.set.accept(Math.max(0f, Math.min(1f, (Ui.mouseX() - s.x) / s.w)));
			});
		}
	}

	// ---- input ----------------------------------------------------------------------------------

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button != 0) {
			return;
		}
		Ui.updateMouse();
		boolean wasConfirming = confirmReset;
		Ui.dispatchClick();
		if (wasConfirming) {
			confirmReset = false;
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) {
			return;
		}
		Ui.updateMouse();
		float step = Integer.signum(wheel) * 60;
		if (Ui.hover(setX, setY, setW, setH)) {
			settingsScroll -= step;
		} else {
			scroll -= step;
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (showSettingsOnly && Ui.width() < 1120) {
				showSettingsOnly = false;
			} else if (!query.isEmpty()) {
				query = "";
			} else {
				mc.displayGuiScreen(parent);
			}
			return;
		}
		if (keyCode == RagoClient.menuKeyCode()) {
			mc.displayGuiScreen(parent);
			return;
		}
		if (keyCode == Keyboard.KEY_BACK) {
			if (!query.isEmpty()) {
				query = isCtrlKeyDown() ? "" : query.substring(0, query.length() - 1);
				scroll = 0;
			}
			return;
		}
		if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && query.length() < 32) {
			query += typedChar;
			scroll = 0;
			showSettingsOnly = false;
		}
	}

	@Override
	public void onGuiClosed() {
		RagoClient.saveConfig();
	}
}
