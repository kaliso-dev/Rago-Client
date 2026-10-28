package gg.rago.gui;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;
import gg.rago.core.Category;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Rago menu (Right Shift, or "Rago Mods" on the main menu): modules as cards, filtered by
 * category and search. Clicking a card's switch toggles it; clicking the card opens its options.
 */
public final class RagoMenuScreen extends Screen {
	private static final int HEADER_H = 32;
	private static final int FOOTER_H = 30;
	private static final int SIDEBAR_W = 84;
	private static final int CARD_W = 90;
	private static final int CARD_H = 58;
	private static final int CARD_GAP = 6;
	private static final int SWITCH_H = 14;
	private static final int ROW_H = 20;
	private static final int SLIDER_W = 90;

	private final Screen parent;
	private Category category;          // null = all
	private Module selected;            // non-null = options view
	private TextFieldWidget search;
	private int scroll;
	private boolean confirmReset;
	private IntOption draggingSlider;
	private int sliderX;
	private int lastMouseX;
	private int lastMouseY;

	public RagoMenuScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}

	// ---- layout -------------------------------------------------------------------------------

	private int panelW() {
		return Math.min(width - 24, 470);
	}

	private int panelH() {
		return Math.min(height - 24, 320);
	}

	private int px() {
		return (width - panelW()) / 2;
	}

	private int py() {
		return (height - panelH()) / 2;
	}

	private int contentX() {
		return px() + SIDEBAR_W + 8;
	}

	private int contentY() {
		return py() + HEADER_H + 4;
	}

	private int contentW() {
		return panelW() - SIDEBAR_W - 16;
	}

	private int contentH() {
		return panelH() - HEADER_H - FOOTER_H - 8;
	}

	@Override
	public void init() {
		buttons.clear();
		search = new TextFieldWidget(0, textRenderer, px() + panelW() - 150, py() + 10, 138, 12);
		search.setHasBorder(false);
		search.setMaxLength(32);
		search.setFocused(true);
	}

	// ---- data ---------------------------------------------------------------------------------

	private List<Module> visibleModules() {
		String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>();
		for (Module m : RagoClient.modules().all()) {
			if (category != null && m.category() != category) {
				continue;
			}
			if (!query.isEmpty() && !I18n.translate(m.nameKey()).toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			list.add(m);
		}
		return list;
	}

	private int columns() {
		return Math.max(1, (contentW() + CARD_GAP) / (CARD_W + CARD_GAP));
	}

	private int cardW() {
		int cols = columns();
		return (contentW() - (cols - 1) * CARD_GAP) / cols;
	}

	/** {x, y, w, h} of card i, before clipping. */
	private int[] card(int i) {
		int cols = columns();
		int w = cardW();
		int x = contentX() + (i % cols) * (w + CARD_GAP);
		int y = contentY() + (i / cols) * (CARD_H + CARD_GAP) - scroll;
		return new int[]{x, y, w, CARD_H};
	}

	private int contentHeight() {
		if (selected != null) {
			return (selected.options().size() + 1) * (ROW_H + 2) + 4;
		}
		int rows = (visibleModules().size() + columns() - 1) / columns();
		return rows * (CARD_H + CARD_GAP);
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(Math.max(0, contentHeight() - contentH()), scroll));
	}

	private boolean inContent(int y, int h) {
		return y >= contentY() && y + h <= contentY() + contentH();
	}

	// ---- rendering ----------------------------------------------------------------------------

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (draggingSlider != null) {
			if (Mouse.isButtonDown(0)) {
				setSlider(draggingSlider, sliderX, mouseX);
			} else {
				draggingSlider = null;
			}
		}
		clampScroll();
		if (minecraft.world == null) {
			fillGradient(0, 0, width, height, Theme.BG_TOP, Theme.BG_BOTTOM);
		} else {
			fillGradient(0, 0, width, height, Theme.SHADE, Theme.SHADE);
		}
		int px = px();
		int py = py();
		int pw = panelW();
		int ph = panelH();
		Theme.rounded(px, py, pw, ph, Theme.PANEL);
		GuiElement.fill(px + 1, py, px + pw - 1, py + 2, Theme.ACCENT);

		// Header: logo, version, search
		Theme.scaledText(textRenderer, "RAGO", px + 12, py + 9, 2f, Theme.TEXT, true);
		textRenderer.draw(RagoClient.version(), px + 12 + textRenderer.getWidth("RAGO") * 2 + 6, py + 16, Theme.MUTED);
		int sx = px + pw - 154;
		Theme.rounded(sx, py + 7, 146, 16, Theme.ROW);
		if (search.getText().isEmpty()) {
			textRenderer.draw(I18n.translate("rago.menu.search"), sx + 11, py + 11, Theme.MUTED);
		}
		search.x = sx + 5;
		search.y = py + 11;
		search.render();

		renderSidebar(mouseX, mouseY);
		if (selected == null) {
			renderCards(mouseX, mouseY);
		} else {
			renderOptions(mouseX, mouseY);
		}
		renderFooter(mouseX, mouseY);
	}

	private void renderSidebar(int mouseX, int mouseY) {
		int x = px() + 6;
		int y = py() + HEADER_H + 4;
		GuiElement.fill(px() + SIDEBAR_W + 2, py() + HEADER_H, px() + SIDEBAR_W + 3, py() + panelH() - FOOTER_H, 0xFF26222F);
		for (int i = -1; i < Category.values().length; i++) {
			Category c = i < 0 ? null : Category.values()[i];
			String label = I18n.translate(c == null ? "rago.category.all" : c.key());
			boolean active = c == category && selected == null;
			boolean hover = Theme.inside(mouseX, mouseY, x, y, SIDEBAR_W - 8, 18);
			if (active || hover) {
				Theme.rounded(x, y, SIDEBAR_W - 8, 18, active ? Theme.ROW_HOVER : Theme.ROW);
			}
			if (active) {
				GuiElement.fill(x, y + 3, x + 2, y + 15, Theme.ACCENT);
			}
			textRenderer.draw(label, x + 8, y + 5, active ? Theme.TEXT : Theme.MUTED);
			y += 20;
		}
	}

	private void renderCards(int mouseX, int mouseY) {
		List<Module> modules = visibleModules();
		if (modules.isEmpty()) {
			Theme.centered(textRenderer, I18n.translate("rago.menu.no_results"), contentX() + contentW() / 2, contentY() + 30, Theme.MUTED);
		}
		for (int i = 0; i < modules.size(); i++) {
			Module m = modules.get(i);
			int[] c = card(i);
			if (!inContent(c[1], c[3])) {
				continue;
			}
			boolean hover = Theme.inside(mouseX, mouseY, c[0], c[1], c[2], c[3]);
			Theme.rounded(c[0], c[1], c[2], c[3], hover ? Theme.CARD_HOVER : Theme.CARD);
			List<String> lines = textRenderer.split(I18n.translate(m.nameKey()), c[2] - 8);
			int textY = c[1] + 8;
			for (int l = 0; l < Math.min(2, lines.size()); l++) {
				Theme.centered(textRenderer, lines.get(l), c[0] + c[2] / 2, textY, Theme.TEXT);
				textY += 10;
			}
			String tag = I18n.translate("rago.class." + m.featureClass().name().toLowerCase(Locale.ROOT));
			if (!m.options().isEmpty() && hover) {
				tag = I18n.translate("rago.menu.options");
			}
			Theme.centered(textRenderer, tag, c[0] + c[2] / 2, c[1] + c[3] - SWITCH_H - 12, Theme.MUTED);

			int sy = c[1] + c[3] - SWITCH_H;
			boolean switchHover = Theme.inside(mouseX, mouseY, c[0], sy, c[2], SWITCH_H);
			int color = m.isEnabled() ? Theme.ON : Theme.OFF;
			if (switchHover) {
				color = m.isEnabled() ? 0xFF4FD08F : 0xFF5A5668;
			}
			GuiElement.fill(c[0] + 1, sy, c[0] + c[2] - 1, sy + SWITCH_H - 1, color);
			GuiElement.fill(c[0] + 2, sy + SWITCH_H - 1, c[0] + c[2] - 2, sy + SWITCH_H, color);
			Theme.centered(textRenderer, I18n.translate(m.isEnabled() ? "rago.menu.on" : "rago.menu.off"),
				c[0] + c[2] / 2, sy + 3, Theme.TEXT);
		}
	}

	private void renderOptions(int mouseX, int mouseY) {
		int x = contentX();
		int w = contentW();
		int y = contentY() - scroll;
		// Title row: back, name, switch
		if (inContent(y, ROW_H)) {
			boolean backHover = Theme.inside(mouseX, mouseY, x, y, 40, ROW_H);
			Theme.rounded(x, y, 40, ROW_H, backHover ? Theme.ROW_HOVER : Theme.ROW);
			Theme.centered(textRenderer, "< " + I18n.translate("gui.back"), x + 20, y + 6, Theme.TEXT);
			textRenderer.drawWithShadow(I18n.translate(selected.nameKey()), x + 48, y + 6, Theme.TEXT);
			drawSwitch(x + w - 26, y + 5, selected.isEnabled());
		}
		y += ROW_H + 6;
		for (Option<?> option : selected.options()) {
			if (inContent(y, ROW_H)) {
				boolean hover = Theme.inside(mouseX, mouseY, x, y, w, ROW_H);
				Theme.rounded(x, y, w, ROW_H, hover ? Theme.ROW_HOVER : Theme.ROW);
				textRenderer.draw(I18n.translate("rago.option." + option.key()), x + 8, y + 6, Theme.TEXT);
				drawControl(option, x + w, y);
			}
			y += ROW_H + 2;
		}
	}

	private void drawControl(Option<?> option, int right, int y) {
		if (option instanceof BooleanOption b) {
			drawSwitch(right - 26, y + 5, b.get());
		} else if (option instanceof ColorOption c) {
			GuiElement.fill(right - 28, y + 4, right - 6, y + 16, Theme.TEXT);
			GuiElement.fill(right - 27, y + 5, right - 7, y + 15, c.get());
		} else if (option instanceof IntOption i) {
			int sx = right - 8 - SLIDER_W;
			String value = Integer.toString(i.get());
			textRenderer.draw(value, sx - 6 - textRenderer.getWidth(value), y + 6, Theme.MUTED);
			GuiElement.fill(sx, y + 9, sx + SLIDER_W, y + 11, Theme.OFF);
			float f = i.max() == i.min() ? 0 : (float) (i.get() - i.min()) / (i.max() - i.min());
			int knob = sx + Math.round(f * (SLIDER_W - 6));
			GuiElement.fill(sx, y + 9, knob + 3, y + 11, Theme.ACCENT);
			Theme.rounded(knob, y + 5, 6, 10, Theme.TEXT);
		}
	}

	private static void drawSwitch(int x, int y, boolean on) {
		Theme.rounded(x, y + 1, 20, 8, on ? Theme.ON : Theme.OFF);
		int knob = on ? x + 12 : x;
		Theme.rounded(knob, y, 8, 10, Theme.TEXT);
	}

	private String[] footerLabels() {
		return new String[]{
			I18n.translate("rago.menu.edit_hud"),
			I18n.translate(confirmReset ? "rago.menu.reset_confirm" : "rago.menu.reset"),
			I18n.translate("gui.done")};
	}

	private int[] footerButton(int index) {
		int bw = 90;
		int y = py() + panelH() - FOOTER_H + 6;
		int x = switch (index) {
			case 0 -> px() + 8;
			case 1 -> px() + 8 + bw + 6;
			default -> px() + panelW() - 8 - bw;
		};
		return new int[]{x, y, bw, 18};
	}

	private void renderFooter(int mouseX, int mouseY) {
		String[] labels = footerLabels();
		for (int i = 0; i < 3; i++) {
			int[] b = footerButton(i);
			boolean hover = Theme.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
			int color = i == 1 && confirmReset ? Theme.DANGER
				: i == 0 ? (hover ? Theme.ACCENT : Theme.ACCENT_DARK)
				: hover ? Theme.ROW_HOVER : Theme.ROW;
			Theme.rounded(b[0], b[1], b[2], b[3], color);
			Theme.centered(textRenderer, labels[i], b[0] + b[2] / 2, b[1] + 5, Theme.TEXT);
		}
	}

	// ---- input --------------------------------------------------------------------------------

	@Override
	public void tick() {
		search.tick();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		search.mouseClicked(mouseX, mouseY, button);
		search.setFocused(true); // typing anywhere searches, as in other client menus
		if (button != 0) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			int[] b = footerButton(i);
			if (Theme.inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
				footerClicked(i);
				return;
			}
		}
		confirmReset = false;
		if (clickSidebar(mouseX, mouseY)) {
			return;
		}
		if (!Theme.inside(mouseX, mouseY, contentX(), contentY(), contentW(), contentH())) {
			return;
		}
		if (selected == null) {
			clickCards(mouseX, mouseY);
		} else {
			clickOptions(mouseX, mouseY);
		}
	}

	private boolean clickSidebar(int mouseX, int mouseY) {
		int x = px() + 6;
		int y = py() + HEADER_H + 4;
		for (int i = -1; i < Category.values().length; i++) {
			if (Theme.inside(mouseX, mouseY, x, y, SIDEBAR_W - 8, 18)) {
				category = i < 0 ? null : Category.values()[i];
				selected = null;
				scroll = 0;
				return true;
			}
			y += 20;
		}
		return false;
	}

	private void clickCards(int mouseX, int mouseY) {
		List<Module> modules = visibleModules();
		for (int i = 0; i < modules.size(); i++) {
			int[] c = card(i);
			if (!Theme.inside(mouseX, mouseY, c[0], c[1], c[2], c[3])) {
				continue;
			}
			Module m = modules.get(i);
			boolean onSwitch = mouseY >= c[1] + c[3] - SWITCH_H;
			if (onSwitch || m.options().isEmpty()) {
				m.setEnabled(!m.isEnabled());
			} else {
				selected = m;
				scroll = 0;
			}
			return;
		}
	}

	private void clickOptions(int mouseX, int mouseY) {
		int x = contentX();
		int w = contentW();
		int y = contentY() - scroll;
		if (Theme.inside(mouseX, mouseY, x, y, 40, ROW_H)) {
			selected = null;
			scroll = 0;
			return;
		}
		if (Theme.inside(mouseX, mouseY, x + w - 30, y, 30, ROW_H)) {
			selected.setEnabled(!selected.isEnabled());
			return;
		}
		y += ROW_H + 6;
		for (Option<?> option : selected.options()) {
			if (Theme.inside(mouseX, mouseY, x, y, w, ROW_H)) {
				if (option instanceof BooleanOption b) {
					b.toggle();
				} else if (option instanceof ColorOption c) {
					c.cycle();
				} else if (option instanceof IntOption i) {
					int sx = x + w - 8 - SLIDER_W;
					if (mouseX >= sx - 4) {
						draggingSlider = i;
						sliderX = sx;
						setSlider(i, sx, mouseX);
					}
				}
				return;
			}
			y += ROW_H + 2;
		}
	}

	private static void setSlider(IntOption option, int sliderX, int mouseX) {
		float f = Math.max(0f, Math.min(1f, (mouseX - sliderX - 3) / (float) (SLIDER_W - 6)));
		option.set(option.min() + Math.round(f * (option.max() - option.min())));
	}

	private void footerClicked(int index) {
		switch (index) {
			case 0 -> minecraft.openScreen(new HudEditorScreen(this));
			case 1 -> {
				if (confirmReset) {
					RagoClient.modules().all().forEach(Module::reset);
					confirmReset = false;
				} else {
					confirmReset = true;
				}
			}
			default -> close();
		}
	}

	private void close() {
		minecraft.openScreen(parent);
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0 && Theme.inside(lastMouseX, lastMouseY, contentX(), contentY(), contentW(), contentH())) {
			scroll -= Integer.signum(wheel) * (CARD_H + CARD_GAP) / 2;
			clampScroll();
		}
	}

	@Override
	protected void keyPressed(char typedChar, int keyCode) {
		if (keyCode == 1) { // Escape: leave the options view first, then the menu
			if (selected != null) {
				selected = null;
				scroll = 0;
			} else {
				close();
			}
			return;
		}
		if (keyCode == RagoClient.menuKeyCode()) {
			close();
			return;
		}
		if (search.keyPressed(typedChar, keyCode)) {
			selected = null;
			scroll = 0;
		}
	}

	@Override
	public void removed() {
		RagoClient.saveConfig();
	}
}
