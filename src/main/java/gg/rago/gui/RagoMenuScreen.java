package gg.rago.gui;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;
import gg.rago.config.StringOption;
import gg.rago.core.Category;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.hud.HudElement;
import gg.rago.hud.HudPosition;
import gg.rago.hypixel.Danger;
import gg.rago.hypixel.HypixelApi;
import gg.rago.modules.BedwarsStats;
import gg.rago.modules.Overlays;
import gg.rago.optimize.OptiFineFields;
import gg.rago.profile.ModProfiles;
import gg.rago.settings.Button;
import gg.rago.settings.Choice;
import gg.rago.settings.Setting;
import gg.rago.settings.Slider;
import gg.rago.settings.Toggle;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Rago window (Right Shift): a Mods tab with profiles and mod cards, and a Settings tab with
 * General, Performance and Controls pages. Typing anywhere searches the current tab.
 */
public final class RagoMenuScreen extends GuiScreen {
	private static final int[] SWATCHES = {0xFFF2392C, 0xFFFFFFFF, 0xFF34D399, 0xFF60A5FA, 0xFFFACC15};
	private static final String OPTIFINE_URL = "https://optifine.net/downloads";
	private static final String HYPIXEL_DEV_URL = "https://developer.hypixel.net/dashboard";

	private enum Tab { MODS, SETTINGS }

	private enum Chip {
		ALL(null), ON(null), HUD(Category.HUD), PVP(Category.PVP), HYPIXEL(Category.HYPIXEL), VISUAL(Category.VISUAL),
		FIXES(Category.FIXES), PERFORMANCE(Category.PERFORMANCE);

		final Category category;

		Chip(Category category) {
			this.category = category;
		}
	}

	private final GuiScreen parent;
	private Tab tab = Tab.MODS;
	private Setting.Tab page = Setting.Tab.GENERAL;
	private Chip chip = Chip.ALL;
	private Module module;
	private String query = "";
	private final Map<String, Float> scrolls = new LinkedHashMap<>();
	private boolean fourColumns;
	private boolean sortByName;
	private String renaming;
	private String renameText = "";
	/** The Hypixel API key being typed on a Hypixel module's page; never saved in rago.json. */
	private String keyText = "";
	private boolean keyEditing;
	private boolean keyWrong;
	/** The text option being typed into, or null. */
	private StringOption editingText;
	private KeyBinding binding;
	private SliderDrag dragging;
	private String tooltip;
	/** The Overlays mod's page, made when first shown; keeps the overlay packs turned on or off there. */
	private OverlaysPanel overlaysPanel;
	private long openedNs;
	private long closingNs;
	private long contentNs;
	private boolean blurOn;

	// layout of the last frame, for the mouse wheel
	private float bodyX;
	private float bodyY;
	private float bodyW;
	private float bodyH;
	private float contentH;

	private static final class SliderDrag {
		final float x;
		final float w;
		final Consumer<Float> set;
		final float y;
		final float h;
		final java.util.function.BiConsumer<Float, Float> set2;

		SliderDrag(float x, float w, Consumer<Float> set) {
			this.x = x;
			this.w = w;
			this.set = set;
			this.y = 0;
			this.h = 1;
			this.set2 = null;
		}

		/** A drag over an area, reporting both fractions (colour square). */
		SliderDrag(float x, float y, float w, float h, java.util.function.BiConsumer<Float, Float> set2) {
			this.x = x;
			this.w = w;
			this.y = y;
			this.h = h;
			this.set = null;
			this.set2 = set2;
		}

		void apply() {
			float fx = Math.max(0f, Math.min(1f, (Ui.mouseX() - x) / w));
			if (set2 != null) {
				set2.accept(fx, Math.max(0f, Math.min(1f, (Ui.mouseY() - y) / h)));
			} else {
				set.accept(fx);
			}
		}
	}

	/** The colour option whose editor is open, and the hue it was last given (kept while grey). */
	private ColorOption editingColor;
	private float editHue;

	public RagoMenuScreen(GuiScreen parent) {
		this.parent = parent;
	}

	/** Shows a module's own page (its settings), as from the HUD editor's gear. */
	public RagoMenuScreen show(Module m) {
		tab = Tab.MODS;
		module = m;
		return this;
	}

	/** Opens the window on the Settings tab. */
	public static RagoMenuScreen settings(GuiScreen parent) {
		RagoMenuScreen s = new RagoMenuScreen(parent);
		s.tab = Tab.SETTINGS;
		return s;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	private static boolean has(String key) {
		return !I18n.format(key).equals(key);
	}

	@Override
	public void initGui() {
		if (openedNs == 0) {
			openedNs = System.nanoTime();
			contentNs = openedNs;
		}
		Keyboard.enableRepeatEvents(true);
		if (mc.theWorld != null && RagoClient.menuBlur() && OpenGlHelper.shadersSupported && !mc.entityRenderer.isShaderActive()) {
			mc.entityRenderer.loadShader(new ResourceLocation("shaders/post/blur.json"));
			blurOn = true;
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private String scrollKey() {
		return tab == Tab.MODS ? (module != null ? "module:" + module.id() : "mods") : "settings:" + page;
	}

	private float scroll() {
		Float v = scrolls.get(scrollKey());
		return v == null ? 0 : v;
	}

	private void switchContent(Runnable change) {
		change.run();
		contentNs = System.nanoTime();
	}

	private void close() {
		if (closingNs == 0) {
			closingNs = System.nanoTime();
			if (!Anim.enabled()) {
				finishClose();
			}
		}
	}

	private void finishClose() {
		mc.displayGuiScreen(parent);
	}

	// ---- frame ----------------------------------------------------------------------------------

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		tooltip = null;
		if (dragging != null) {
			if (Mouse.isButtonDown(0)) {
				dragging.apply();
			} else {
				dragging = null;
			}
		}
		float w = Ui.width();
		float h = Ui.height();
		float p = Anim.progress(openedNs, 240);
		if (closingNs != 0) {
			float c = Anim.progress(closingNs, 150);
			p = Math.min(p, 1 - c);
			if (c >= 1) {
				Ui.end();
				finishClose();
				return;
			}
		}
		if (mc.theWorld == null) {
			Ui.backdrop(0.62f);
		} else {
			Ui.rect(0, 0, w, h, Ui.alpha(0xFF000000, 0.35f * p));
		}
		Ui.captureGlass();

		float ww = Math.min(w - 24, Math.max(680, Math.min(820, w - 80)));
		float wh = Math.min(h - 40, 500);
		float x = (w - ww) / 2;
		float y = (h - wh) / 2;
		Ui.opacity(p);
		if (p < 1) {
			Ui.pushTransform(w / 2, h / 2, 0.955f + 0.045f * p, 0, (1 - p) * 10);
		}
		Ui.glassBorder(x, y, ww, wh, 14, 0xF50E0D10, 0x22FFFFFF);
		header(x, y, ww);
		Ui.rect(x + 1, y + 46, ww - 2, 1 / Ui.scale(), 0x14FFFFFF);
		float c = Anim.progress(contentNs, 200);
		Ui.opacity(p * c);
		if (tab == Tab.MODS) {
			modsTab(x, y + 47, ww, wh - 47, c);
		} else {
			settingsTab(x, y + 47, ww, wh - 47, c);
		}
		Ui.opacity(p);
		Ui.popTransform();
		if (tooltip != null && closingNs == 0) {
			drawTooltip(tooltip, w);
		}
		Ui.end();
	}

	private void header(float x, float y, float w) {
		Ui.logo(x + 18, y + 14, 18, Theme.ACCENT);
		float ts = 14;
		float rw = Ui.text(Ui.SEMIBOLD, "Rago ", x + 44, y + 14, ts, Theme.TEXT);
		Ui.text(Ui.REGULAR, "Client", x + 44 + rw, y + 14, ts, 0xFFB4B2B9);

		String[] labels = {t("rago.menu.tab.mods"), t("rago.menu.tab.settings")};
		Tab[] tabs = {Tab.MODS, Tab.SETTINGS};
		float pw = 92;
		float gap = 10;
		float px = x + w / 2 - (pw * 2 + gap) / 2;
		for (int i = 0; i < 2; i++) {
			Tab target = tabs[i];
			boolean active = tab == target;
			float hv = Widgets.hoverAmount("tab:" + i, px, y + 11, pw, 24);
			int fill = active ? 0x30FFFFFF : Ui.mix(0x00FFFFFF, 0x12FFFFFF, hv);
			Ui.roundBorder(px, y + 11, pw, 24, 12, fill, active ? 0x40FFFFFF : 0x22FFFFFF);
			Ui.textCentered(Ui.MEDIUM, labels[i], px + pw / 2, y + 11 + (24 - 10.5f * 1.21f) / 2, 10.5f, active ? Theme.TEXT : 0xFFC4C2CA);
			Ui.click(px, y + 11, pw, 24, () -> {
				if (tab != target) {
					switchContent(() -> {
						tab = target;
						query = "";
					});
				}
			});
			px += pw + gap;
		}

		float cs = 24;
		float cx = x + w - 14 - cs;
		float hv = Widgets.hoverAmount("close", cx, y + 11, cs, cs);
		Ui.roundBorder(cx, y + 11, cs, cs, cs / 2, Ui.mix(0x00FFFFFF, 0x1AFFFFFF, hv), 0x26FFFFFF);
		Ui.icon("x", cx + 6, y + 17, 12, Ui.mix(0xFFB4B2B9, Theme.TEXT, hv));
		Ui.click(cx, y + 11, cs, cs, this::close);
	}

	// ---- Mods tab -------------------------------------------------------------------------------

	private void modsTab(float x, float y, float w, float h, float c) {
		float sw = 168;
		sidebar(x + 10, y + 10, sw, h - 20);
		float mx = x + 10 + sw + 12;
		float mw = x + w - 12 - mx;
		if (module != null) {
			modulePage(module, mx, y + 10, mw, h - 20, c);
		} else {
			chips(mx, y + 10, mw);
			grid(mx, y + 42 + (1 - c) * 6, mw, h - 52);
		}
	}

	private void sidebar(float x, float y, float w, float h) {
		ModProfiles profiles = RagoClient.profiles();
		Ui.textSpaced(Ui.MONO, t("rago.menu.profiles"), x + 4, y + 2, 8, 1.6f, 0xFF7C7A82);
		float ry = y + 18;
		for (String name : profiles.names()) {
			profileRow(profiles, name, x, ry, w);
			ry += 27;
		}
		float boxY = ry + 4;
		float boxH = y + h - 72 - boxY;
		if (boxH > 40) {
			Ui.glassBorder(x, boxY, w, boxH, 8, 0x66000000, 0x12FFFFFF);
			int on = 0;
			for (Module m : RagoClient.modules().all()) {
				if (m.isEnabled()) {
					on++;
				}
			}
			float ty = boxY + 12;
			Ui.text(Ui.MEDIUM, t("rago.menu.profile_active", profiles.active()), x + 12, ty, 9.5f, Theme.TEXT);
			ty += 15;
			Ui.text(Ui.REGULAR, t("rago.menu.mods_enabled", on, RagoClient.modules().all().size()), x + 12, ty, 9, Theme.MUTED);
			ty += 18;
			if (boxY + boxH - ty > 30) {
				for (String line : Ui.wrap(Ui.REGULAR, t("rago.menu.profile_hint"), 8.5f, w - 24)) {
					if (ty > boxY + boxH - 14) {
						break;
					}
					Ui.text(Ui.REGULAR, line, x + 12, ty, 8.5f, 0xFF7C7A82);
					ty += 12;
				}
			}
		}
		float by = y + h - 62;
		float hv = Widgets.hoverAmount("saveprofile", x + 8, by, w - 16, 22);
		Ui.roundBorder(x + 8, by, w - 16, 22, 11, Ui.mix(0x00FFFFFF, 0x14FFFFFF, hv), 0x2EFFFFFF);
		Ui.textCentered(Ui.MEDIUM, t("rago.menu.save_profile"), x + w / 2, by + (22 - 9.5f * 1.21f) / 2, 9.5f, Theme.TEXT);
		Ui.click(x + 8, by, w - 16, 22, () -> {
			String name = profiles.saveAsNew(RagoClient.modules());
			renaming = name;
			renameText = name;
		});
		Ui.rect(x, by + 30, w, 1 / Ui.scale(), 0x14FFFFFF);
		float eh = 24;
		float ey = y + h - eh;
		hv = Widgets.hoverAmount("edithud", x + 4, ey, w - 8, eh);
		Ui.round(x + 4, ey, w - 8, eh, eh / 2, Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, hv));
		Ui.textCentered(Ui.SEMIBOLD, t("rago.menu.edit_layout"), x + w / 2, ey + (eh - 10 * 1.21f) / 2, 10, 0xFF1A0605);
		Ui.click(x + 4, ey, w - 8, eh, () -> mc.displayGuiScreen(new HudEditorScreen(this)));
	}

	private void profileRow(ModProfiles profiles, String name, float x, float y, float w) {
		boolean active = name.equals(profiles.active());
		float rh = 23;
		float hv = Widgets.hoverAmount("profile:" + name, x, y, w, rh);
		int fill = active ? 0x26FFFFFF : Ui.mix(0x0AFFFFFF, 0x16FFFFFF, hv);
		Ui.roundBorder(x, y, w, rh, 6, fill, active ? 0x33FFFFFF : 0x12FFFFFF);
		Ui.icon(profiles.icon(name), x + 9, y + 6, 11, active ? Theme.ACCENT : 0xFF9A98A0);
		boolean editing = name.equals(renaming);
		String label = editing ? renameText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : name;
		Ui.text(Ui.MEDIUM, label, x + 27, y + (rh - 10 * 1.21f) / 2, 10, editing ? Theme.ACCENT : Theme.TEXT);
		Ui.click(x, y, w, rh, () -> {
			renaming = null;
			profiles.switchTo(name, RagoClient.modules());
		});
		float ix = x + w - 18;
		Ui.icon("pencil", ix, y + 6.5f, 10, hv > 0.5f || editing ? 0xFFD4D2D9 : 0xFF6B6970);
		Ui.click(ix - 4, y, 18, rh, () -> {
			renaming = name;
			renameText = name;
		});
		if (hv > 0.5f && profiles.names().size() > 1 && !editing) {
			ix -= 16;
			Ui.icon("trash-2", ix, y + 6.5f, 10, 0xFF9A98A0);
			Ui.click(ix - 3, y, 15, rh, () -> profiles.delete(name, RagoClient.modules()));
		}
	}

	/** Room kept right of the chips for the two icon toggles. */
	private static final float TOGGLES_W = 52;
	private static final float SEARCH_MIN = 130;
	private static final float SEARCH_MAX = 170;

	private void chips(float x, float y, float w) {
		Chip[] all = Chip.values();
		String[] labels = new String[all.length];
		float textW = 0;
		for (int i = 0; i < all.length; i++) {
			labels[i] = t("rago.menu.chip." + all[i].name().toLowerCase(Locale.ROOT));
			textW += Ui.textWidth(Ui.MEDIUM, labels[i], 9.5f);
		}
		// the chips give up padding before they would run under the toggles and the search field
		float room = w - TOGGLES_W - SEARCH_MIN - 8;
		float[] fit = chipFit(textW, all.length, room);
		float pad = fit[0];
		float gap = fit[1];
		float cx = x;
		for (int i = 0; i < all.length; i++) {
			Chip ch = all[i];
			String label = labels[i];
			float cw = Ui.textWidth(Ui.MEDIUM, label, 9.5f) + pad;
			boolean active = chip == ch;
			float hv = Widgets.hoverAmount("chip:" + ch, cx, y, cw, 22);
			int fill;
			int border;
			int text;
			if (active && ch == Chip.ON) {
				fill = 0x3334D399;
				border = 0x6634D399;
				text = Theme.GREEN;
			} else if (active) {
				fill = Theme.ACCENT;
				border = Theme.ACCENT;
				text = 0xFFFFFFFF;
			} else {
				fill = Ui.mix(0x0FFFFFFF, 0x1CFFFFFF, hv);
				border = 0x1FFFFFFF;
				text = ch == Chip.ON ? Theme.GREEN : 0xFFC4C2CA;
			}
			Ui.roundBorder(cx, y, cw, 22, 11, fill, border);
			Ui.textCentered(Ui.MEDIUM, label, cx + cw / 2, y + (22 - 9.5f * 1.21f) / 2, 9.5f, text);
			Ui.click(cx, y, cw, 22, () -> {
				chip = ch;
				scrolls.remove("mods");
			});
			cx += cw + gap;
		}
		float sw = Math.max(SEARCH_MIN, Math.min(SEARCH_MAX, x + w - cx - TOGGLES_W - 6));
		float sx = x + w - sw;
		searchField(sx, y, sw);
		float ix = sx - TOGGLES_W + 2;
		iconToggle("grid-3x3", ix, y, fourColumns, () -> fourColumns = !fourColumns);
		iconToggle("arrow-up-down", ix + 24, y, sortByName, () -> sortByName = !sortByName);
	}

	/**
	 * The padding inside each chip and the gap between them so that {@code n} chips whose labels are
	 * {@code textW} wide together fit in {@code room}: 24 and 6 when they can, down to 12 and 4.
	 */
	static float[] chipFit(float textW, int n, float room) {
		float pad = 24;
		float gap = 6;
		float need = textW + n * pad + (n - 1) * gap;
		if (need > room) {
			gap = 4;
			pad = Math.max(12, (room - textW - (n - 1) * gap) / n);
		}
		return new float[] {pad, gap};
	}

	private void iconToggle(String icon, float x, float y, boolean on, Runnable action) {
		float hv = Widgets.hoverAmount("it:" + icon, x, y, 22, 22);
		Ui.icon(icon, x + 4, y + 4, 14, on ? Theme.ACCENT : Ui.mix(0xFF9A98A0, Theme.TEXT, hv));
		Ui.click(x, y, 22, 22, action);
	}

	private void searchField(float x, float y, float w) {
		boolean focused = !query.isEmpty();
		Ui.glassBorder(x, y, w, 22, 11, 0xFF141316, focused ? 0x55F2392C : 0x1FFFFFFF);
		Ui.icon("search", x + 9, y + 5.5f, 11, 0xFF8A888F);
		String shown = query.isEmpty() ? t(tab == Tab.MODS ? "rago.menu.search_mods" : "rago.menu.search_settings") : query;
		float room = w - 26 - (query.isEmpty() ? 10 : 24);
		if (query.isEmpty()) {
			shown = Ui.ellipsize(Ui.REGULAR, shown, 9.5f, room);
		} else {
			// the end of what is typed stays in view
			while (shown.length() > 1 && Ui.textWidth(Ui.REGULAR, shown, 9.5f) > room) {
				shown = shown.substring(1);
			}
		}
		Ui.text(Ui.REGULAR, shown, x + 26, y + (22 - 9.5f * 1.21f) / 2, 9.5f, query.isEmpty() ? 0xFF7C7A82 : Theme.TEXT);
		if (!query.isEmpty()) {
			Ui.icon("x", x + w - 18, y + 6, 10, 0xFF9A98A0);
			Ui.click(x + w - 22, y, 22, 22, () -> query = "");
		}
	}

	private List<Module> visibleModules() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>();
		for (Module m : RagoClient.modules().all()) {
			if (chip == Chip.ON && !m.isEnabled() || chip.category != null && m.category() != chip.category) {
				continue;
			}
			if (!q.isEmpty() && !t(m.nameKey()).toLowerCase(Locale.ROOT).contains(q)
				&& !t(ModuleInfo.descKey(m)).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			list.add(m);
		}
		if (sortByName) {
			list.sort(Comparator.comparing(m -> t(m.nameKey()).toLowerCase(Locale.ROOT)));
		}
		return list;
	}

	private void grid(float x, float y, float w, float h) {
		List<Module> mods = visibleModules();
		int cols = fourColumns ? 4 : 3;
		float gap = 10;
		float cw = (w - 8 - gap * (cols - 1)) / cols;
		float ch = fourColumns ? 136 : 152;
		int rows = (mods.size() + cols - 1) / cols;
		float total = rows * (ch + gap) - gap;
		setBody(x, y, w, h, total);
		float sc = scroll();
		Ui.scissor(x - 2, y, w + 4, h);
		boolean inside = true;
		for (int i = 0; i < mods.size(); i++) {
			float cx = x + (i % cols) * (cw + gap);
			float cy = y + (i / cols) * (ch + gap) - sc;
			if (cy + ch < y || cy > y + h) {
				continue;
			}
			card(mods.get(i), cx, cy, cw, ch, inside);
		}
		if (mods.isEmpty()) {
			Ui.textCentered(Ui.REGULAR, t("rago.menu.no_results"), x + w / 2, y + 40, 11, Theme.MUTED);
		}
		Ui.endScissor();
		scrollbar(x + w - 3, y, h, total, sc);
	}

	private void card(Module m, float x, float y, float w, float h, boolean inside) {
		float topH = h - 58;
		float hv = Widgets.hoverAmount("card:" + m.id(), x, y, w, topH);
		float on = Anim.to("cardon:" + m.id(), m.isEnabled() ? 1 : 0, 160);
		float lift = hv * 2;
		Ui.glassBorder(x, y - lift, w, topH, 8, Ui.mix(0xFF141316, 0xFF1A191D, hv), Ui.mix(0x14FFFFFF, 0x26FFFFFF, hv));
		float is = fourColumns ? 26 : 30;
		int iconColor = Ui.mix(0xFF8A888F, Theme.ACCENT, on);
		Ui.icon(ModuleInfo.icon(m), x + (w - is) / 2, y - lift + topH / 2 - is / 2 - 10, is, iconColor);
		String name = fit(Ui.REGULAR, t(m.nameKey()), fourColumns ? 11.5f : 13, w - 12);
		Ui.textCentered(Ui.REGULAR, name, x + w / 2, y - lift + topH / 2 + 14, fourColumns ? 11.5f : 13, Ui.mix(0xFFB4B2B9, Theme.TEXT, on));
		Option<?> riskyOption = ModuleInfo.risky(m) ? null : ModuleInfo.riskyOptionOn(m);
		if (ModuleInfo.risky(m) || riskyOption != null) {
			// risky on Hypixel: a warning, never a lock
			float bx = x + w - 22;
			float by = y - lift + 6;
			Ui.icon("triangle-alert", bx + 4, by + 4, 11, Theme.WARNING);
			if (inside && Ui.hover(bx, by, 19, 19)) {
				tooltip = riskyOption == null ? t("rago.risk.tooltip", t(ModuleInfo.riskKey(m.id())))
					: t("rago.risk.tooltip_option", t("rago.option." + riskyOption.key()), t(ModuleInfo.riskKey(riskyOption.key())));
			}
		}
		if (inside) {
			Ui.click(x, y, w, topH, () -> m.setEnabled(!m.isEnabled()));
		}

		float oy = y + topH + 4;
		float bw = w - 28;
		float oh = Widgets.hoverAmount("opt:" + m.id(), x, oy, bw, 24);
		Ui.glassBorder(x, oy, bw, 24, 5, Ui.mix(0xFF151417, 0xFF1D1C20, oh), 0x14FFFFFF);
		Ui.textCentered(Ui.REGULAR, t("rago.menu.options"), x + bw / 2, oy + (24 - 9.5f * 1.21f) / 2, 9.5f, 0xFFC4C2CA);
		float sh = Widgets.hoverAmount("optico:" + m.id(), x + w - 24, oy, 24, 24);
		Ui.glassBorder(x + w - 24, oy, 24, 24, 5, Ui.mix(0xFF151417, 0xFF1D1C20, sh), 0x14FFFFFF);
		Ui.icon("sliders-horizontal", x + w - 18, oy + 6, 12, 0xFFB4B2B9);
		if (inside) {
			Runnable open = () -> switchContent(() -> module = m);
			Ui.click(x, oy, bw, 24, open);
			Ui.click(x + w - 24, oy, 24, 24, open);
		}

		float ey = oy + 28;
		float eh = Widgets.hoverAmount("en:" + m.id(), x, ey, w, 24);
		int fill = Ui.mix(Ui.mix(0xFF1D1C20, 0xFF26252A, eh), Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, eh), on);
		Ui.glass(x, ey, w, 24, 5, 0);
		Ui.round(x, ey, w, 24, 5, Ui.alpha(fill, 0.72f + 0.16f * on));
		String label = t(m.isEnabled() ? "rago.menu.enabled" : "rago.menu.disabled");
		Ui.textCentered(Ui.MEDIUM, label, x + w / 2, ey + (24 - 9.5f * 1.21f) / 2, 9.5f, Ui.mix(0xFF9A98A0, 0xFFFFFFFF, on));
		if (inside) {
			Ui.click(x, ey, w, 24, () -> m.setEnabled(!m.isEnabled()));
		}
	}

	private static String fit(gg.rago.gui.ui.TtfFont font, String s, float size, float max) {
		if (Ui.textWidth(font, s, size) <= max) {
			return s;
		}
		String r = s;
		while (r.length() > 2 && Ui.textWidth(font, r + "…", size) > max) {
			r = r.substring(0, r.length() - 1);
		}
		return r.trim() + "…";
	}

	// ---- one mod's options ----------------------------------------------------------------------

	private void modulePage(Module m, float x, float y, float w, float h, float c) {
		float bh = Widgets.hoverAmount("back", x, y, 24, 24);
		Ui.roundBorder(x, y, 24, 24, 12, Ui.mix(0x00FFFFFF, 0x1AFFFFFF, bh), 0x26FFFFFF);
		Ui.icon("arrow-left", x + 6, y + 6, 12, Theme.TEXT);
		Ui.click(x, y, 24, 24, () -> switchContent(() -> module = null));
		Ui.icon(ModuleInfo.icon(m), x + 36, y + 3, 18, m.isEnabled() ? Theme.ACCENT : 0xFF8A888F);
		Ui.text(Ui.SEMIBOLD, t(m.nameKey()), x + 60, y + 2, 14, Theme.TEXT);
		Widgets.pill("modpill:" + m.id(), x + w - Widgets.PILL_W - 6, y + 2, m.isEnabled());
		Ui.click(x + w - Widgets.PILL_W - 6, y + 2, Widgets.PILL_W, Widgets.PILL_H, () -> m.setEnabled(!m.isEnabled()));

		float top = y + 34;
		float area = h - 34;
		float sc = scroll();
		float cy = top - sc + (1 - c) * 6;
		Ui.scissor(x - 2, top, w + 4, area);
		boolean inside = true;
		for (String line : Ui.wrap(Ui.REGULAR, t(ModuleInfo.descKey(m)), 10, w - 12)) {
			Ui.text(Ui.REGULAR, line, x + 2, cy, 10, Theme.MUTED);
			cy += 14;
		}
		cy += 8;
		if (ModuleInfo.risky(m)) {
			cy = riskBox(t(ModuleInfo.riskKey(m.id())), x, cy, w - 8) + 10;
		}
		if (ModuleInfo.usesHypixelKey(m)) {
			cy = hypixelKey(x, cy, w - 8, inside) + 10;
		}
		if (m instanceof BedwarsStats) {
			cy = statsLegend((BedwarsStats) m, x, cy, w - 8) + 10;
		}
		if (m instanceof Overlays) {
			if (overlaysPanel == null) {
				overlaysPanel = new OverlaysPanel();
			}
			cy = overlaysPanel.draw((Overlays) m, x, cy, w - 8, inside, s -> tooltip = s) + 14;
		}
		if (m instanceof HudElement) {
			preview((HudElement) m, x, cy, w - 8, 110);
			cy += 122;
		}
		List<Option<?>> opts = m.options();
		if (opts.isEmpty() && !(m instanceof HudElement)) {
			Ui.text(Ui.REGULAR, t("rago.menu.no_options"), x + 2, cy, 10, Theme.MUTED);
			cy += 20;
		}
		Map<String, List<Option<?>>> groups = new LinkedHashMap<>();
		for (Option<?> o : opts) {
			List<Option<?>> group = groups.computeIfAbsent(o.section() == null ? "" : o.section(), k -> new java.util.ArrayList<>());
			if (o instanceof ChoiceOption && "style".equals(o.key())) {
				group.add(0, o); // the preset comes before the details it fills in
			} else {
				group.add(o);
			}
		}
		for (Map.Entry<String, List<Option<?>>> g : groups.entrySet()) {
			if (!g.getKey().isEmpty()) {
				cy += 6;
				Ui.textSpaced(Ui.SEMIBOLD, t("rago.modsection." + g.getKey()).toUpperCase(Locale.ROOT), x + 2, cy, 8.5f, 1.4f, 0xFF7C7A82);
				cy += 18;
			}
			for (Option<?> o : g.getValue()) {
				if (o.visible()) {
					cy += optionRow(o, x, cy, w - 8, inside) + 4;
				}
			}
		}
		if (m instanceof HudElement) {
			HudPosition p = ((HudElement) m).position();
			float f = (p.scale() - HudPosition.MIN_SCALE) / (HudPosition.MAX_SCALE - HudPosition.MIN_SCALE);
			rowFrame(x, cy, w - 8, 30);
			Ui.text(Ui.REGULAR, t("rago.option.scale"), x + 12, cy + (30 - 10.5f * 1.21f) / 2, 10.5f, Theme.TEXT);
			sliderControl("scale:" + m.id(), x + w - 8 - 12 - 46 - 170, cy, 170, f, String.format(Locale.ROOT, "%.1f×", p.scale()), x + w - 20, inside,
				v -> p.setScale(Math.round((HudPosition.MIN_SCALE + v * (HudPosition.MAX_SCALE - HudPosition.MIN_SCALE)) * 10) / 10f));
			cy += 40;
			float half = (w - 8 - 10) / 2;
			Widgets.button(x, cy, half, 26, "rotate-ccw", t("rago.menu.reset_default"), false, 10);
			Widgets.button(x + half + 10, cy, half, 26, "move", t("rago.menu.move"), true, 10);
			if (inside) {
				Ui.click(x, cy, half, 26, m::reset);
				Ui.click(x + half + 10, cy, half, 26, () -> mc.displayGuiScreen(new HudEditorScreen(this)));
			}
			cy += 36;
		} else if (!opts.isEmpty()) {
			Widgets.button(x, cy, 160, 26, "rotate-ccw", t("rago.menu.reset_default"), false, 10);
			if (inside) {
				Ui.click(x, cy, 160, 26, () -> {
					boolean on = m.isEnabled();
					m.reset();
					m.setEnabled(on);
				});
			}
			cy += 36;
		}
		Ui.endScissor();
		float total = cy + sc - top;
		setBody(x, top, w, area, total);
		scrollbar(x + w - 3, top, area, total, sc);
	}

	/** Why a mod is risky on Hypixel, and that Rago leaves the choice to the player. */
	private static float riskBox(String reason, float x, float y, float w) {
		List<String> lines = Ui.wrap(Ui.REGULAR, reason + " " + t("rago.risk.note"), 9, w - 46);
		float h = 30 + lines.size() * 12;
		Ui.round(x, y, w, h, 8, Ui.alpha(Theme.WARNING, 0.07f));
		Ui.outline(x, y, w, h, 8, Ui.alpha(Theme.WARNING, 0.38f));
		Ui.icon("triangle-alert", x + 12, y + 10, 14, Theme.WARNING);
		Ui.text(Ui.MEDIUM, t("rago.risk.title"), x + 34, y + 9, 10, Theme.WARNING);
		float ly = y + 24;
		for (String line : lines) {
			Ui.text(Ui.REGULAR, line, x + 34, ly, 9, 0xFFD4D2D9);
			ly += 12;
		}
		return y + h;
	}

	/** The Hypixel API key: where it stands, a field to paste a new one, and where to get one. */
	private float hypixelKey(float x, float y, float w, boolean inside) {
		HypixelApi api = RagoClient.hypixel();
		HypixelApi.Status st = api.status();
		float h = 86;
		rowFrame(x, y, w, h);
		Ui.icon("key-round", x + 12, y + 10, 13, Theme.ACCENT);
		Ui.text(Ui.MEDIUM, t("rago.apikey.title"), x + 32, y + 10, 10.5f, Theme.TEXT);
		int dot;
		String state;
		switch (st) {
			case OK:
				if (api.pausedSeconds() > 0) {
					// a good key, but Hypixel asks to wait: say so instead of a green "OK"
					dot = Theme.YELLOW;
					state = api.dailyQuota()
						? t("rago.apikey.daily", api.limit() > 0 ? String.valueOf(api.limit()) : "?", HypixelApi.duration(api.pausedSeconds()))
						: t("rago.apikey.paused", HypixelApi.duration(api.pausedSeconds()));
				} else {
					dot = Theme.GREEN;
					state = t("rago.apikey.ok", api.keyHint());
				}
				break;
			case CHECKING:
				dot = Theme.YELLOW;
				state = t("rago.apikey.checking");
				break;
			case BAD_KEY:
				dot = Theme.ACCENT;
				state = t("rago.apikey.bad");
				break;
			case UNREACHABLE:
				dot = Theme.YELLOW;
				state = t("rago.apikey.unreachable");
				break;
			default:
				dot = Theme.ACCENT;
				state = t("rago.apikey.none");
		}
		float sw = Ui.textWidth(Ui.REGULAR, state, 9) + 22;
		Ui.roundBorder(x + w - 12 - sw, y + 7, sw, 18, 9, 0xCC141316, 0x1FFFFFFF);
		Ui.circle(x + w - 12 - sw + 10, y + 16, 3, dot);
		Ui.text(Ui.REGULAR, state, x + w - 12 - sw + 17, y + 7 + (18 - 9 * 1.21f) / 2, 9, 0xFFD4D2D9);
		if (st == HypixelApi.Status.OK) {
			requestsLeft(api, x + 32 + Ui.textWidth(Ui.MEDIUM, t("rago.apikey.title"), 10.5f) + 12, y + 11, x + w - 12 - sw - 10);
		}

		float fy = y + 32;
		float bw = 96;
		float lw = 120;
		float fw = w - 24 - bw - lw - 12;
		Ui.glassBorder(x + 12, fy, fw, 24, 6, 0xFF0E0D10, keyEditing ? 0x66F2392C : keyWrong ? 0x99F2392C : 0x1FFFFFFF);
		String shown;
		int color;
		if (keyText.isEmpty()) {
			shown = t(keyEditing ? "rago.apikey.paste" : api.hasKey() ? "rago.apikey.replace" : "rago.apikey.click");
			color = 0xFF7C7A82;
		} else {
			// the key is a password for the account: only its end is shown
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < Math.max(0, keyText.length() - 4); i++) {
				b.append(keyText.charAt(i) == '-' ? '-' : '*');
			}
			shown = b + keyText.substring(Math.max(0, keyText.length() - 4));
			color = Theme.TEXT;
		}
		boolean caret = keyEditing && (System.currentTimeMillis() / 500) % 2 == 0;
		Ui.text(Ui.MONO, shown + (caret && !keyText.isEmpty() ? "_" : ""), x + 20, fy + (24 - 9 * 1.3f) / 2, 9, color);
		Widgets.button(x + 12 + fw + 6, fy, bw, 24, "check", t("rago.apikey.save"), true, 9.5f);
		Widgets.button(x + w - 12 - lw, fy, lw, 24, "external-link", t("rago.apikey.get"), false, 9.5f);
		if (inside) {
			Ui.click(x + 12, fy, fw, 24, () -> keyEditing = true);
			Ui.click(x + 12 + fw + 6, fy, bw, 24, this::saveKey);
			Ui.click(x + w - 12 - lw, fy, lw, 24, () -> Sys.openURL(HYPIXEL_DEV_URL));
		}
		String note = keyWrong ? t("rago.apikey.format") : t("rago.apikey.note");
		Ui.text(Ui.REGULAR, note, x + 12, fy + 32, 8.5f, keyWrong ? Theme.ACCENT : 0xFF7C7A82);
		return y + h;
	}

	/** "287 / 300 requests left, reset in 3 min" next to the title, as far as {@code right}. */
	private static void requestsLeft(HypixelApi api, float x, float y, float right) {
		int[] b = api.budget();
		String full;
		String brief;
		int color = 0xFFA9A7AF;
		if (b == null) {
			full = brief = t("rago.apikey.requests_unknown");
		} else {
			brief = t("rago.apikey.requests_left", b[0], b[1]);
			full = b[2] > 0 ? t("rago.apikey.requests", b[0], b[1], HypixelApi.duration(b[2])) : brief;
			if (b[0] * 10 < b[1]) {
				color = Theme.YELLOW;
			}
		}
		for (String s : new String[] {full, brief}) {
			if (x + Ui.textWidth(Ui.REGULAR, s, 9) <= right) {
				Ui.text(Ui.REGULAR, s, x, y, 9, color);
				return;
			}
		}
	}

	private void saveKey() {
		String k = keyText.trim();
		if (!HypixelApi.isKey(k)) {
			keyWrong = true;
			keyEditing = true;
			return;
		}
		try {
			RagoClient.hypixel().setKey(k, RagoClient.ownId());
			keyText = "";
			keyWrong = false;
			keyEditing = false;
		} catch (IOException e) {
			RagoClient.LOG.error("Could not save the Hypixel API key", e);
			keyWrong = true;
		}
	}

	/** FKDR colour steps, as in the tab list: the lowest ratio of each step and its colour. */
	private static final String[] FKDR_STEPS = {"< 1", "1+", "5+", "10+", "20+"};
	private static final int[] FKDR_COLORS = {0xFFAAAAAA, 0xFF55FF55, 0xFF00AA00, 0xFFFF5555, 0xFFAA0000};
	/** Tier colours by danger level, 1 (D) to 5 (S), as in the tab list. */
	private static final int[] TIER_COLORS = {0xFFAAAAAA, 0xFF55FF55, 0xFF00AA00, 0xFFFF5555, 0xFFAA0000};

	/** What the numbers next to the names in the tab list mean, with the FKDR colours. */
	private float statsLegend(BedwarsStats stats, float x, float y, float w) {
		float h = 148;
		rowFrame(x, y, w, h);
		Ui.icon("list-ordered", x + 12, y + 10, 13, Theme.ACCENT);
		Ui.text(Ui.MEDIUM, t("rago.stats.legend_title"), x + 32, y + 10, 10.5f, Theme.TEXT);
		float ly = y + 32;
		Ui.text(Ui.REGULAR, t("rago.stats.legend_star"), x + 12, ly, 9.5f, 0xFFD4D2D9);
		ly += 18;
		String fkdr = t("rago.stats.legend_fkdr");
		Ui.text(Ui.REGULAR, fkdr, x + 12, ly, 9.5f, 0xFFD4D2D9);
		float cx = x + 12 + Ui.textWidth(Ui.REGULAR, fkdr, 9.5f) + 10;
		for (int i = 0; i < FKDR_STEPS.length; i++) {
			float cw = Ui.textWidth(Ui.MEDIUM, FKDR_STEPS[i], 9) + 14;
			Ui.roundBorder(cx, ly - 3, cw, 16, 8, 0xCC141316, 0x1FFFFFFF);
			Ui.text(Ui.MEDIUM, FKDR_STEPS[i], cx + 7, ly - 3 + (16 - 9 * 1.21f) / 2, 9, FKDR_COLORS[i]);
			cx += cw + 4;
		}
		ly += 18;
		Ui.text(Ui.REGULAR, t("rago.stats.legend_danger"), x + 12, ly, 9.5f, 0xFFD4D2D9);
		ly += 18;
		cx = x + 12;
		for (int level = 5; level >= 1; level--) {
			String chip = Danger.tier(level) + "  " + t("rago.stats.tier_" + Character.toLowerCase(Danger.tier(level)));
			float cw = Ui.textWidth(Ui.MEDIUM, chip, 9) + 14;
			Ui.roundBorder(cx, ly - 3, cw, 16, 8, 0xCC141316, 0x1FFFFFFF);
			Ui.text(Ui.MEDIUM, chip, cx + 7, ly - 3 + (16 - 9 * 1.21f) / 2, 9, TIER_COLORS[level - 1]);
			cx += cw + 4;
		}
		ly += 18;
		Ui.icon("triangle-alert", x + 12, ly - 1, 11, 0xFFAA0000);
		Ui.text(Ui.REGULAR, t("rago.stats.legend_mark", stats.strongFkdr(), stats.strongStar()), x + 28, ly, 9.5f, 0xFFD4D2D9);
		ly += 18;
		Ui.text(Ui.MEDIUM, "[S]", x + 12, ly, 9.5f, 0xFFFF55FF);
		Ui.text(Ui.REGULAR, t("rago.stats.legend_sniper"), x + 12 + Ui.textWidth(Ui.MEDIUM, "[S]", 9.5f) + 6, ly, 9.5f, 0xFFD4D2D9);
		return y + h;
	}

	private static void rowFrame(float x, float y, float w, float h) {
		Ui.glassBorder(x, y, w, h, 7, 0xFF131215, 0x12FFFFFF);
	}

	private float optionRow(Option<?> option, float x, float y, float w, boolean inside) {
		boolean editing = option == editingColor;
		rowFrame(x, y, w, editing ? 30 + COLOR_EDITOR_H : 30);
		boolean risky = ModuleInfo.risky(option);
		String label = fit(Ui.REGULAR, t("rago.option." + option.key()), 10.5f, w * 0.45f - (risky ? 16 : 0));
		Ui.text(Ui.REGULAR, label, x + 12, y + (30 - 10.5f * 1.21f) / 2, 10.5f, Theme.TEXT);
		float labelEnd = x + 12 + Ui.textWidth(Ui.REGULAR, label, 10.5f);
		if (risky) {
			Ui.icon("triangle-alert", labelEnd + 6, y + 9.5f, 11, Theme.WARNING);
			labelEnd += 17;
		}
		if (inside && dragging == null && editingText == null && Ui.hover(x, y, Math.max(labelEnd - x + 8, w * 0.4f), 30)) {
			String help = "rago.option." + option.key() + ".help";
			String text = has(help) ? t(help) : null;
			if (risky) {
				String warning = t("rago.risk.tooltip", t(ModuleInfo.riskKey(option.key())));
				text = text == null ? warning : warning + " " + text;
			}
			if (text != null) {
				tooltip = text;
			}
		}
		float right = x + w - 12;
		if (option instanceof BooleanOption) {
			BooleanOption b = (BooleanOption) option;
			Widgets.pill("opt:" + option.key() + System.identityHashCode(option), right - Widgets.PILL_W, y + 5, b.get());
			if (inside) {
				Ui.click(x, y, w, 30, b::toggle);
			}
		} else if (option instanceof IntOption) {
			IntOption io = (IntOption) option;
			float f = io.max() == io.min() ? 0 : (float) (io.get() - io.min()) / (io.max() - io.min());
			String key = "rago.option." + io.key() + "." + io.get();
			String format = "rago.option." + io.key() + ".format";
			String value = has(key) ? t(key) : has(format) ? t(format, io.formatValue()) : io.display();
			sliderControl("opt:" + io.key() + System.identityHashCode(io), right - 46 - 170, y, 170, f, value, right, inside,
				v -> io.set(io.min() + Math.round(v * (io.max() - io.min()))));
		} else if (option instanceof StringOption) {
			textControl((StringOption) option, x + w * 0.42f, y, right, inside);
		} else if (option instanceof ChoiceOption) {
			choiceControl((ChoiceOption) option, x + w * 0.42f, y, right, inside);
		} else if (option instanceof ColorOption) {
			ColorOption co = (ColorOption) option;
			float sx = right - (SWATCHES.length + 1) * 22 - 64;
			Ui.textRight(Ui.MONO, ColorOption.format(co.get()), right, y + (30 - 9 * 1.3f) / 2, 9, 0xFF8A888F);
			for (int color : SWATCHES) {
				boolean sel = (co.get() | 0xFF000000) == color;
				if (sel) {
					Ui.circle(sx + 9, y + 15, 10, Theme.ACCENT);
					Ui.circle(sx + 9, y + 15, 8.5f, 0xFF131215);
				}
				Ui.circle(sx + 9, y + 15, 7, color);
				if (inside) {
					// presets keep the option's own transparency
					Ui.click(sx, y + 4, 20, 22, () -> co.set(color & 0xFFFFFF | co.get() & 0xFF000000));
				}
				sx += 22;
			}
			// custom colour: a ring around the current colour opens the editor
			Ui.annulus(sx + 9, y + 15, 8.5f, 1.6f, editing ? Theme.ACCENT : 0xFF8A888F);
			Ui.circle(sx + 9, y + 15, 5.5f, co.get() | 0xFF000000);
			if (inside) {
				Ui.click(sx, y + 4, 20, 22, () -> {
					editingColor = editing ? null : co;
					editHue = hsb(co.get())[0];
				});
			}
			if (editing) {
				colorEditor(co, x + 12, y + 34, w - 24, inside);
				return 30 + COLOR_EDITOR_H;
			}
		}
		return 30;
	}

	private static final float COLOR_EDITOR_H = 96;

	private static float[] hsb(int argb) {
		return java.awt.Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
	}

	/** Saturation/brightness square, hue bar and opacity bar for one colour. */
	private void colorEditor(ColorOption co, float x, float y, float w, boolean inside) {
		int argb = co.get();
		float[] hsb = hsb(argb);
		if (hsb[1] > 0.02f && hsb[2] > 0.02f) {
			editHue = hsb[0];
		}
		float hue = editHue;
		int alpha = argb >>> 24;
		float sqW = 130;
		float sqH = 80;
		int pure = 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 1, 1);
		Ui.hgradient(x, y, sqW, sqH, 0xFFFFFFFF, pure);
		Ui.gradient(x, y, sqW, sqH, 0x00000000, 0xFF000000);
		Ui.outline(x, y, sqW, sqH, 0, 0x30FFFFFF);
		float mx = x + hsb[1] * sqW;
		float my = y + (1 - hsb[2]) * sqH;
		Ui.annulus(mx, my, 4, 1.5f, 0xFFFFFFFF);
		if (inside) {
			SliderDrag d = new SliderDrag(x, y, sqW, sqH, (fx, fy) ->
				co.set(co.get() & 0xFF000000 | java.awt.Color.HSBtoRGB(editHue, fx, 1 - fy) & 0xFFFFFF));
			Ui.click(x - 4, y - 4, sqW + 8, sqH + 8, () -> {
				dragging = d;
				d.apply();
			});
		}
		float bx = x + sqW + 16;
		float bw = w - sqW - 16 - 64;
		// hue
		Ui.text(Ui.REGULAR, t("rago.color.hue"), bx, y, 9, Theme.MUTED);
		float hy = y + 14;
		for (int i = 0; i < 6; i++) {
			int c0 = 0xFF000000 | java.awt.Color.HSBtoRGB(i / 6f, 1, 1);
			int c1 = 0xFF000000 | java.awt.Color.HSBtoRGB((i + 1) / 6f, 1, 1);
			Ui.hgradient(bx + bw * i / 6f, hy, bw / 6f + 0.5f, 10, c0, c1);
		}
		Ui.rect(bx + hue * bw - 1, hy - 2, 2, 14, 0xFFFFFFFF);
		if (inside) {
			SliderDrag d = new SliderDrag(bx, bw, f -> {
				editHue = Math.min(0.999f, f);
				float[] cur = hsb(co.get());
				float sat = cur[1] < 0.02f ? 1 : cur[1];
				float bri = cur[2] < 0.02f ? 1 : cur[2];
				co.set(co.get() & 0xFF000000 | java.awt.Color.HSBtoRGB(editHue, sat, bri) & 0xFFFFFF);
			});
			Ui.click(bx - 4, hy - 4, bw + 8, 18, () -> {
				dragging = d;
				d.apply();
			});
		}
		// opacity (none for colours that stay opaque)
		if (co.hasFixedAlpha()) {
			colorPreview(argb, x, y, w);
			return;
		}
		Ui.text(Ui.REGULAR, t("rago.color.opacity"), bx, y + 40, 9, Theme.MUTED);
		Ui.textRight(Ui.MONO, Math.round(alpha / 2.55f) + "%", bx + bw, y + 40, 9, 0xFFD4D4D8);
		float ay = y + 54;
		for (int i = 0; i * 5 < bw; i++) {
			Ui.rect(bx + i * 5, ay + (i % 2) * 5, Math.min(5, bw - i * 5), 5, 0xFF3A383E);
			Ui.rect(bx + i * 5, ay + (1 - i % 2) * 5, Math.min(5, bw - i * 5), 5, 0xFF222125);
		}
		Ui.hgradient(bx, ay, bw, 10, argb & 0xFFFFFF, argb | 0xFF000000);
		Ui.rect(bx + alpha / 255f * bw - 1, ay - 2, 2, 14, 0xFFFFFFFF);
		if (inside) {
			SliderDrag d = new SliderDrag(bx, bw, f -> co.set(Math.round(f * 255) << 24 | co.get() & 0xFFFFFF));
			Ui.click(bx - 4, ay - 4, bw + 8, 18, () -> {
				dragging = d;
				d.apply();
			});
		}
		colorPreview(argb, x, y, w);
	}

	private void colorPreview(int argb, float x, float y, float w) {
		float px = x + w - 52;
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				Ui.rect(px + i * 13, y + j * 13 + 14, 13, 13, (i + j) % 2 == 0 ? 0xFF3A383E : 0xFF222125);
			}
		}
		Ui.rect(px, y + 14, 52, 52, argb);
		Ui.outline(px, y + 14, 52, 52, 0, 0x30FFFFFF);
		Ui.text(Ui.REGULAR, t("rago.color.preview"), px, y, 9, Theme.MUTED);
	}

	/** Segmented buttons when the choices fit, otherwise arrows around the current choice. */
	/** A line of text: click it to type, Enter or a click elsewhere to finish. */
	private void textControl(StringOption so, float left, float y, float right, boolean inside) {
		boolean editing = so == editingText;
		float fw = right - left;
		Ui.glassBorder(left, y + 4, fw, 22, 6, 0xFF0E0D10, editing ? 0x66F2392C : 0x1FFFFFFF);
		String value = so.get();
		boolean caret = editing && (System.currentTimeMillis() / 500) % 2 == 0;
		String shown = value.isEmpty() && !editing ? t("rago.menu.click_to_type") : value + (caret ? "_" : "");
		Ui.text(Ui.REGULAR, fit(Ui.REGULAR, shown, 9.5f, fw - 14), left + 7, y + 4 + (22 - 9.5f * 1.21f) / 2, 9.5f,
			value.isEmpty() && !editing ? 0xFF7C7A82 : Theme.TEXT);
		if (inside) {
			Ui.click(left, y + 4, fw, 22, () -> editingText = so);
		}
	}

	private void choiceControl(ChoiceOption co, float left, float y, float right, boolean inside) {
		List<String> ids = co.choices();
		float total = 0;
		String[] labels = new String[ids.size()];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = choiceLabel(co, ids.get(i));
			total += Ui.textWidth(Ui.MEDIUM, labels[i], 9.5f) + 18;
		}
		if (total <= right - left) {
			float cx = right - total;
			Ui.roundBorder(cx - 3, y + 4, total + 6, 22, 11, 0xFF0F0E11, 0x14FFFFFF);
			for (int i = 0; i < labels.length; i++) {
				String id = ids.get(i);
				float bw = Ui.textWidth(Ui.MEDIUM, labels[i], 9.5f) + 18;
				boolean sel = co.is(id);
				float hv = Widgets.hoverAmount("ch:" + co.key() + System.identityHashCode(co) + id, cx, y + 6, bw, 18);
				if (sel) {
					Ui.round(cx, y + 6, bw, 18, 9, Theme.ACCENT);
				} else if (hv > 0) {
					Ui.round(cx, y + 6, bw, 18, 9, Ui.alpha(0x1AFFFFFF, hv));
				}
				Ui.textCentered(Ui.MEDIUM, labels[i], cx + bw / 2, y + 6 + (18 - 9.5f * 1.21f) / 2, 9.5f, sel ? 0xFFFFFFFF : 0xFFB4B2B9);
				if (inside) {
					Ui.click(cx, y + 6, bw, 18, () -> co.select(id));
				}
				cx += bw;
			}
			return;
		}
		String label = choiceLabel(co, co.get());
		float lw = Math.max(90, Ui.textWidth(Ui.MEDIUM, label, 9.5f) + 16);
		float bx = right - lw - 44;
		Ui.roundBorder(bx, y + 4, lw + 44, 22, 11, 0xFF0F0E11, 0x14FFFFFF);
		Ui.icon("chevron-left", bx + 6, y + 9, 12, 0xFFB4B2B9);
		Ui.icon("chevron-right", bx + lw + 26, y + 9, 12, 0xFFB4B2B9);
		Ui.textCentered(Ui.MEDIUM, label, bx + 22 + lw / 2, y + 4 + (22 - 9.5f * 1.21f) / 2, 9.5f, Theme.TEXT);
		if (inside) {
			Ui.click(bx, y + 4, 22, 22, () -> co.cycle(-1));
			Ui.click(bx + 22, y + 4, lw + 22, 22, () -> co.cycle(1));
		}
	}

	private String choiceLabel(ChoiceOption co, String id) {
		String own = "rago.option." + co.key() + "." + id;
		if (has(own)) {
			return t(own);
		}
		String shared = "rago.choice." + id;
		return has(shared) ? t(shared) : id;
	}

	private void sliderControl(String key, float x, float y, float w, float f, String value, float right, boolean inside, Consumer<Float> set) {
		float fa = Anim.to("slider:" + key, f, 90);
		Widgets.slider(x, y + 15 - 9, w, dragging != null ? f : fa);
		Ui.textRight(Ui.MONO, value, right, y + (30 - 9.5f * 1.3f) / 2, 9.5f, 0xFFD4D4D8);
		if (inside) {
			SliderDrag s = new SliderDrag(x, w, set);
			Ui.click(x - 8, y, w + 16, 30, () -> {
				dragging = s;
				s.apply();
			});
		}
	}

	/** Draws the HUD element itself, with sample data when there is no live data. */
	private void preview(HudElement e, float x, float y, float w, float h) {
		Ui.glassBorder(x, y, w, h, 10, 0xFF121114, 0x14FFFFFF);
		for (float gx = x + 12; gx < x + w - 6; gx += 12) {
			for (float gy = y + 12; gy < y + h - 6; gy += 12) {
				Ui.rect(gx, gy, 1.2f, 1.2f, 0x12FFFFFF);
			}
		}
		Ui.textSpaced(Ui.MONO, t("rago.menu.preview"), x + 12, y + 10, 8, 1.6f, 0xFF6B6970);
		boolean placeholder = !e.hasContent();
		// a new frame for the element's text: options changed in this menu show at once
		gg.rago.hud.HudManager.nextFrame();
		int ew = Math.max(1, e.width(fontRendererObj, placeholder));
		int eh = Math.max(1, e.height(fontRendererObj, placeholder));
		float s = Ui.scale();
		float k = Math.min(2.2f * s, Math.min((w - 40) * s / ew, (h - 36) * s / eh));
		float dx = (x + w / 2) * s - ew * k / 2;
		float dy = (y + 8 + h / 2) * s - eh * k / 2;
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

	// ---- Settings tab ---------------------------------------------------------------------------

	private void settingsTab(float x, float y, float w, float h, float c) {
		float cx = x + 14;
		float ty = y + 10;
		Setting.Tab[] pages = Setting.Tab.values();
		String[] icons = {"settings", "gauge", "keyboard"};
		for (int i = 0; i < pages.length; i++) {
			Setting.Tab pg = pages[i];
			String label = t("rago.settings.tab." + pg.name().toLowerCase(Locale.ROOT));
			float cw = Ui.textWidth(Ui.MEDIUM, label, 9.5f) + 38;
			boolean active = page == pg;
			float hv = Widgets.hoverAmount("page:" + pg, cx, ty, cw, 22);
			Ui.roundBorder(cx, ty, cw, 22, 11, active ? Theme.ACCENT : Ui.mix(0x0FFFFFFF, 0x1CFFFFFF, hv), active ? Theme.ACCENT : 0x1FFFFFFF);
			Ui.icon(icons[i], cx + 11, ty + 5.5f, 11, active ? 0xFFFFFFFF : 0xFFB4B2B9);
			Ui.text(Ui.MEDIUM, label, cx + 27, ty + (22 - 9.5f * 1.21f) / 2, 9.5f, active ? 0xFFFFFFFF : 0xFFC4C2CA);
			Ui.click(cx, ty, cw, 22, () -> {
				if (page != pg) {
					switchContent(() -> {
						page = pg;
						binding = null;
					});
				}
			});
			cx += cw + 6;
		}
		searchField(x + w - 14 - 190, ty, 190);

		float top = y + 42;
		float area = h - 52;
		float left = x + 14;
		float cw = w - 28 - 8;
		float sc = scroll();
		float cy = top - sc + (1 - c) * 6;
		Ui.scissor(x + 2, top, w - 4, area);
		boolean inside = true;
		if (page == Setting.Tab.CONTROLS) {
			cy = controls(left, cy, cw, inside);
		} else {
			cy = settingRows(left, cy, cw, inside);
		}
		Ui.endScissor();
		float total = cy + sc - top;
		setBody(x, top, w, area, total);
		scrollbar(x + w - 9, top, area, total, sc);
	}

	private boolean matches(String... texts) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return true;
		}
		for (String s : texts) {
			if (s.toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}
		return false;
	}

	private float settingRows(float x, float y, float w, boolean inside) {
		List<Setting> rows = new ArrayList<>();
		for (Setting s : RagoClient.settings().rows()) {
			if (s.tab() == page && s.visible() && matches(t(s.labelKey()), t(s.section()), has(s.helpKey()) ? t(s.helpKey()) : "")) {
				rows.add(s);
			}
		}
		if (page == Setting.Tab.PERFORMANCE && !OptiFineFields.installed() && query.isEmpty()) {
			y = optifineHint(x, y, w);
		}
		String section = null;
		float colW = (w - 12) / 2;
		int col = 0;
		for (Setting s : rows) {
			if (!s.section().equals(section)) {
				if (col == 1) {
					y += 32;
					col = 0;
				}
				section = s.section();
				y += section == null ? 0 : 6;
				Ui.textSpaced(Ui.SEMIBOLD, t(section).toUpperCase(Locale.ROOT), x + 2, y, 8.5f, 1.4f, 0xFF8A888F);
				y += 18;
			}
			if (s.wide()) {
				if (col == 1) {
					y += 32;
					col = 0;
				}
				wideRow(s, x, y, w, inside);
				y += 34;
			} else {
				toggleRow((Toggle) s, x + col * (colW + 12), y, colW, inside);
				if (col == 1) {
					y += 32;
				}
				col = 1 - col;
			}
		}
		if (col == 1) {
			y += 32;
		}
		if (rows.isEmpty()) {
			Ui.textCentered(Ui.REGULAR, t("rago.menu.no_results"), x + w / 2, y + 30, 11, Theme.MUTED);
			y += 60;
		}
		if (page == Setting.Tab.PERFORMANCE && query.isEmpty()) {
			y += 4;
			for (String line : Ui.wrap(Ui.REGULAR, t("rago.settings.perf_note"), 8.5f, w)) {
				Ui.text(Ui.REGULAR, line, x + 2, y, 8.5f, 0xFF6B6970);
				y += 12;
			}
		}
		return y + 10;
	}

	private float optifineHint(float x, float y, float w) {
		Ui.roundBorder(x, y, w, 40, 8, 0x1AF2392C, 0x40F2392C);
		Ui.icon("triangle-alert", x + 12, y + 13, 14, Theme.ACCENT);
		Ui.text(Ui.MEDIUM, t("rago.settings.no_optifine"), x + 34, y + 8, 10, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.settings.no_optifine.sub"), x + 34, y + 22, 8.5f, Theme.MUTED);
		Widgets.button(x + w - 150, y + 8, 140, 24, "download", t("rago.optifine.download"), true, 9.5f);
		Ui.click(x + w - 150, y + 8, 140, 24, () -> Sys.openURL(OPTIFINE_URL));
		return y + 50;
	}

	private void hoverHelp(Setting s, float x, float y, float w, float h) {
		if (Ui.hover(x, y, w, h) && has(s.helpKey())) {
			tooltip = t(s.helpKey());
		}
	}

	private void toggleRow(Toggle s, float x, float y, float w, boolean inside) {
		float hv = Widgets.hoverAmount("row:" + s.id(), x, y, w, 26);
		Ui.round(x, y, w, 26, 6, Ui.mix(0x00FFFFFF, 0x0CFFFFFF, hv));
		boolean on = s.get();
		Widgets.pill("set:" + s.id(), x + 4, y + 3, on);
		String label = fit(Ui.REGULAR, t(s.labelKey()), 10.5f, w - Widgets.PILL_W - 22);
		Ui.text(Ui.REGULAR, label, x + Widgets.PILL_W + 14, y + (26 - 10.5f * 1.21f) / 2, 10.5f, Theme.TEXT);
		if (inside) {
			Ui.click(x, y, w, 26, () -> s.set(!s.get()));
			hoverHelp(s, x, y, w, 26);
		}
	}

	private void wideRow(Setting s, float x, float y, float w, boolean inside) {
		float hv = Widgets.hoverAmount("row:" + s.id(), x, y, w, 30);
		Ui.round(x, y, w, 30, 6, Ui.mix(0x00FFFFFF, 0x0CFFFFFF, hv));
		Ui.text(Ui.REGULAR, fit(Ui.REGULAR, t(s.labelKey()), 10.5f, w * 0.42f), x + 8, y + (30 - 10.5f * 1.21f) / 2, 10.5f, Theme.TEXT);
		float right = x + w - 8;
		if (s instanceof Slider) {
			Slider sl = (Slider) s;
			float f = sl.max() == sl.min() ? 0 : (float) (sl.get() - sl.min()) / (sl.max() - sl.min());
			float sw = Math.min(300, w * 0.45f);
			sliderControl("set:" + sl.id(), right - 70 - sw, y, sw, f, sl.display(), right, inside,
				v -> sl.set(sl.min() + Math.round(v * (sl.max() - sl.min()))));
		} else if (s instanceof Choice) {
			Choice ch = (Choice) s;
			float cw = 170;
			Widgets.chooser(right - cw, y + 4, cw, 22, t(ch.valueKey()));
			if (inside) {
				Ui.click(right - cw, y + 4, 22, 22, () -> ch.cycle(-1));
				Ui.click(right - 22, y + 4, 22, 22, () -> ch.cycle(1));
				Ui.click(right - cw + 22, y + 4, cw - 44, 22, () -> ch.cycle(1));
			}
		} else if (s instanceof Button) {
			Button b = (Button) s;
			float bw = 170;
			Widgets.button(right - bw, y + 3, bw, 24, null, t(s.labelKey() + ".button"), b.isPrimary(), 9.5f);
			if (inside) {
				Ui.click(right - bw, y + 3, bw, 24, b::press);
			}
		}
		if (inside) {
			hoverHelp(s, x, y, w, 30);
		}
	}

	// ---- Controls -------------------------------------------------------------------------------

	private float controls(float x, float y, float w, boolean inside) {
		Widgets.button(x + w - 160, y, 160, 24, "rotate-ccw", t("rago.settings.reset_keys"), false, 9.5f);
		if (inside) {
			Ui.click(x + w - 160, y, 160, 24, () -> {
				for (KeyBinding k : mc.gameSettings.keyBindings) {
					k.setKeyCode(k.getKeyCodeDefault());
				}
				KeyBinding.resetKeyBindingArrayAndHash();
				mc.gameSettings.saveOptions();
				binding = null;
			});
		}
		Ui.text(Ui.REGULAR, t("rago.settings.keys_hint"), x + 2, y + 6, 9, Theme.MUTED);
		y += 34;
		KeyBinding[] keys = Arrays.copyOf(mc.gameSettings.keyBindings, mc.gameSettings.keyBindings.length);
		Arrays.sort(keys);
		String category = null;
		float colW = (w - 12) / 2;
		int col = 0;
		for (KeyBinding k : keys) {
			String name = t(k.getKeyDescription());
			if (!matches(name, t(k.getKeyCategory()))) {
				continue;
			}
			if (!k.getKeyCategory().equals(category)) {
				if (col == 1) {
					y += 30;
					col = 0;
				}
				category = k.getKeyCategory();
				y += 6;
				Ui.textSpaced(Ui.SEMIBOLD, t(category).toUpperCase(Locale.ROOT), x + 2, y, 8.5f, 1.4f, 0xFF8A888F);
				y += 18;
			}
			keyRow(k, name, x + col * (colW + 12), y, colW, inside);
			if (col == 1) {
				y += 30;
			}
			col = 1 - col;
		}
		if (col == 1) {
			y += 30;
		}
		return y + 10;
	}

	private void keyRow(KeyBinding k, String name, float x, float y, float w, boolean inside) {
		float bw = 96;
		Ui.text(Ui.REGULAR, fit(Ui.REGULAR, name, 10, w - bw - 16), x + 4, y + (24 - 10 * 1.21f) / 2, 10, Theme.TEXT);
		boolean waiting = k == binding;
		boolean conflict = false;
		if (k.getKeyCode() != 0) {
			for (KeyBinding o : mc.gameSettings.keyBindings) {
				if (o != k && o.getKeyCode() == k.getKeyCode()) {
					conflict = true;
					break;
				}
			}
		}
		float bx = x + w - bw;
		float hv = Widgets.hoverAmount("key:" + k.getKeyDescription(), bx, y, bw, 24);
		Ui.glassBorder(bx, y, bw, 24, 6, Ui.mix(0xFF151417, 0xFF1D1C20, hv), waiting ? Theme.ACCENT : conflict ? 0x99F2392C : 0x1FFFFFFF);
		String label = waiting ? "> ? <" : keyName(k.getKeyCode());
		int color = waiting ? Theme.ACCENT : conflict ? 0xFFFF6B5E : Theme.TEXT;
		Ui.textCentered(Ui.MEDIUM, fit(Ui.MEDIUM, label, 9.5f, bw - 8), bx + bw / 2, y + (24 - 9.5f * 1.21f) / 2, 9.5f, color);
		if (inside) {
			Ui.click(bx, y, bw, 24, () -> binding = k);
		}
	}

	private static String keyName(int code) {
		if (code == 0) {
			return I18n.format("rago.settings.key_none");
		}
		if (code < 0) {
			return I18n.format("rago.settings.mouse", code + 101);
		}
		String n = Keyboard.getKeyName(code);
		return n == null ? "#" + code : n;
	}

	private void setKey(int code) {
		binding.setKeyCode(code);
		KeyBinding.resetKeyBindingArrayAndHash();
		mc.gameSettings.saveOptions();
		binding = null;
	}

	// ---- shared bits ----------------------------------------------------------------------------

	private void setBody(float x, float y, float w, float h, float content) {
		bodyX = x;
		bodyY = y;
		bodyW = w;
		bodyH = h;
		contentH = content;
		float max = Math.max(0, content - h);
		float sc = scroll();
		if (sc > max || sc < 0) {
			scrolls.put(scrollKey(), Math.max(0, Math.min(max, sc)));
		}
	}

	private static void scrollbar(float x, float y, float h, float content, float scroll) {
		if (content <= h) {
			return;
		}
		float bh = Math.max(24, h * h / content);
		float by = y + (h - bh) * scroll / (content - h);
		Ui.round(x, by, 3, bh, 1.5f, 0x40FFFFFF);
	}

	private static void drawTooltip(String text, float w) {
		float size = 9;
		List<String> lines = Ui.wrap(Ui.REGULAR, text, size, 260);
		float tw = 0;
		for (String l : lines) {
			tw = Math.max(tw, Ui.textWidth(Ui.REGULAR, l, size));
		}
		float bw = tw + 16;
		float bh = lines.size() * 13 + 10;
		float x = Math.min(Ui.mouseX() + 12, w - bw - 6);
		float y = Ui.mouseY() + 14;
		if (y + bh > Ui.height() - 6) {
			y = Math.max(6, Ui.mouseY() - bh - 6);
		}
		Ui.glassBorder(x, y, bw, bh, 6, 0xF7151417, 0x2EFFFFFF);
		float ly = y + 5;
		for (String l : lines) {
			Ui.text(Ui.REGULAR, l, x + 8, ly, size, 0xFFD4D2D9);
			ly += 13;
		}
	}

	// ---- input ----------------------------------------------------------------------------------

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		Ui.updateMouse();
		if (binding != null) {
			if (button == 0 && !Ui.hover(bodyX, bodyY, bodyW, bodyH)) {
				binding = null;
			} else {
				setKey(button - 100);
				return;
			}
		}
		if (button != 0 || closingNs != 0) {
			return;
		}
		String wasRenaming = renaming;
		editingText = null;
		if (Ui.dispatchClick()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		}
		if (wasRenaming != null && wasRenaming.equals(renaming)) {
			commitRename();
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) {
			return;
		}
		float max = Math.max(0, contentH - bodyH);
		float next = scroll() - Integer.signum(wheel) * 40;
		scrolls.put(scrollKey(), Math.max(0, Math.min(max, next)));
	}

	private void commitRename() {
		if (renaming != null) {
			RagoClient.profiles().rename(renaming, renameText);
			renaming = null;
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (binding != null) {
			setKey(keyCode == Keyboard.KEY_ESCAPE ? 0 : keyCode);
			return;
		}
		if (editingText != null) {
			String v = editingText.get();
			if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_ESCAPE) {
				editingText = null;
			} else if (keyCode == Keyboard.KEY_BACK) {
				editingText.set(isCtrlKeyDown() || v.isEmpty() ? "" : v.substring(0, v.length() - 1));
			} else if (isKeyComboCtrlV(keyCode)) {
				editingText.set(v + getClipboardString());
			} else if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
				editingText.set(v + typedChar);
			}
			return;
		}
		if (keyEditing && module != null && ModuleInfo.usesHypixelKey(module)) {
			if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
				saveKey();
			} else if (keyCode == Keyboard.KEY_ESCAPE) {
				keyEditing = false;
				keyText = "";
				keyWrong = false;
			} else if (keyCode == Keyboard.KEY_BACK) {
				keyText = isCtrlKeyDown() || keyText.isEmpty() ? "" : keyText.substring(0, keyText.length() - 1);
			} else if (isKeyComboCtrlV(keyCode)) {
				keyText = getClipboardString().trim();
				keyWrong = false;
			} else if ((Character.digit(typedChar, 16) >= 0 || typedChar == '-') && keyText.length() < 36) {
				keyText += typedChar;
				keyWrong = false;
			}
			return;
		}
		if (renaming != null) {
			if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
				commitRename();
			} else if (keyCode == Keyboard.KEY_ESCAPE) {
				renaming = null;
			} else if (keyCode == Keyboard.KEY_BACK) {
				if (!renameText.isEmpty()) {
					renameText = renameText.substring(0, renameText.length() - 1);
				}
			} else if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && renameText.length() < 24) {
				renameText += typedChar;
			}
			return;
		}
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (!query.isEmpty()) {
				query = "";
			} else if (module != null) {
				switchContent(() -> module = null);
			} else {
				close();
			}
			return;
		}
		if (keyCode == RagoClient.menuKeyCode()) {
			close();
			return;
		}
		if (keyCode == Keyboard.KEY_BACK) {
			if (!query.isEmpty()) {
				query = isCtrlKeyDown() ? "" : query.substring(0, query.length() - 1);
				scrolls.remove(scrollKey());
			}
			return;
		}
		if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && query.length() < 32) {
			if (module != null) {
				module = null;
			}
			query += typedChar;
			scrolls.remove(scrollKey());
		}
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		if (overlaysPanel != null) {
			overlaysPanel.close();
		}
		if (blurOn && mc.entityRenderer.isShaderActive()) {
			mc.entityRenderer.stopUseShader();
		}
		blurOn = false;
		Anim.clear();
		RagoClient.saveConfig();
	}
}
