package gg.rago.gui;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Rago menu (Right Shift by default): every module with an on/off switch, its
 * options when expanded, and buttons for the HUD editor and a full reset.
 */
public final class RagoMenuScreen extends Screen {
	private static final int PANEL_W = 280;
	private static final int ROW_H = 18;
	private static final int GAP = 2;
	private static final int HEADER_H = 28;
	private static final int FOOTER_H = 30;

	private final Set<String> expanded = new HashSet<>();
	private int scroll;
	private boolean confirmReset;
	private int lastMouseX;
	private int lastMouseY;

	/** One clickable line of the list, laid out fresh every frame so drawing and clicks always agree. */
	private record Row(int x, int y, int w, int h, String label, String tag, Control control,
					   Runnable onToggle, Runnable onLabel, int color, String expander) {
		boolean child() {
			return expander == null;
		}
	}

	private enum Control { TOGGLE_ON, TOGGLE_OFF, COLOR, NUMBER }

	private static final int TOGGLE_W = 30;

	@Override
	public boolean shouldPauseGame() {
		return false;
	}

	private int panelX() {
		return (width - PANEL_W) / 2;
	}

	private int panelY() {
		return Math.max(8, height / 10);
	}

	private int panelH() {
		return height - panelY() * 2;
	}

	private int listTop() {
		return panelY() + HEADER_H;
	}

	private int listBottom() {
		return panelY() + panelH() - FOOTER_H;
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		int x = panelX() + 8;
		int w = PANEL_W - 16;
		int y = listTop() - scroll;
		for (Module module : RagoClient.modules().all()) {
			boolean open = expanded.contains(module.id());
			boolean hasOptions = !module.options().isEmpty();
			String tag = I18n.translate("rago.class." + module.featureClass().name().toLowerCase(Locale.ROOT));
			Runnable toggle = () -> module.setEnabled(!module.isEnabled());
			Runnable expand = hasOptions ? () -> {
				if (!expanded.remove(module.id())) {
					expanded.add(module.id());
				}
			} : toggle;
			rows.add(new Row(x, y, w, ROW_H, I18n.translate(module.nameKey()), tag,
				module.isEnabled() ? Control.TOGGLE_ON : Control.TOGGLE_OFF, toggle, expand, 0,
				hasOptions ? (open ? "-" : "+") : ""));
			y += ROW_H + GAP;
			if (open) {
				for (Option<?> option : module.options()) {
					rows.add(optionRow(option, x + 12, y, w - 12));
					y += ROW_H + GAP;
				}
			}
		}
		return rows;
	}

	private Row optionRow(Option<?> option, int x, int y, int w) {
		String label = I18n.translate("rago.option." + option.key());
		if (option instanceof BooleanOption b) {
			return new Row(x, y, w, ROW_H, label, "", b.get() ? Control.TOGGLE_ON : Control.TOGGLE_OFF, b::toggle, b::toggle, 0, null);
		}
		if (option instanceof ColorOption c) {
			return new Row(x, y, w, ROW_H, label, "", Control.COLOR, c::cycle, c::cycle, c.get(), null);
		}
		if (option instanceof IntOption i) {
			Runnable step = () -> i.set(i.get() >= i.max() ? i.min() : i.get() + 1);
			return new Row(x, y, w, ROW_H, label + ": " + i.get(), "", Control.NUMBER, step, step, 0, null);
		}
		Runnable none = () -> {
		};
		return new Row(x, y, w, ROW_H, label, "", Control.NUMBER, none, none, 0, null);
	}

	private int contentHeight() {
		int h = 0;
		for (Module module : RagoClient.modules().all()) {
			h += ROW_H + GAP;
			if (expanded.contains(module.id())) {
				h += module.options().size() * (ROW_H + GAP);
			}
		}
		return h;
	}

	private void clampScroll() {
		int max = Math.max(0, contentHeight() - (listBottom() - listTop()));
		scroll = Math.max(0, Math.min(max, scroll));
	}

	// Footer buttons: [Edit HUD] [Reset] [Done]
	private int[] footerButton(int index) {
		int bw = (PANEL_W - 16 - 8) / 3;
		int x = panelX() + 8 + index * (bw + 4);
		int y = listBottom() + 8;
		return new int[]{x, y, bw, 16};
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		clampScroll();
		fillGradient(0, 0, width, height, Theme.SHADE, Theme.SHADE);
		int px = panelX();
		int py = panelY();
		GuiElement.fill(px, py, px + PANEL_W, py + panelH(), Theme.PANEL);
		GuiElement.fill(px, py, px + PANEL_W, py + 2, Theme.ACCENT);
		textRenderer.drawWithShadow("Rago Client", px + 8, py + 10, Theme.TEXT);
		String version = RagoClient.version();
		textRenderer.draw(version, px + PANEL_W - 8 - textRenderer.getWidth(version), py + 10, Theme.MUTED);

		int top = listTop();
		int bottom = listBottom();
		for (Row row : rows()) {
			if (row.y() < top || row.y() + row.h() > bottom) {
				continue;
			}
			boolean hover = Theme.inside(mouseX, mouseY, row.x(), row.y(), row.w(), row.h());
			GuiElement.fill(row.x(), row.y(), row.x() + row.w(), row.y() + row.h(), hover ? Theme.ROW_HOVER : Theme.ROW);
			int textX = row.x() + (row.child() ? 6 : 20);
			if (!row.child()) {
				textRenderer.draw(row.expander(), row.x() + 7, row.y() + 5, Theme.ACCENT);
			}
			textRenderer.draw(row.label(), textX, row.y() + 5, row.child() ? Theme.MUTED : Theme.TEXT);
			if (!row.tag().isEmpty()) {
				textRenderer.draw(row.tag(), textX + textRenderer.getWidth(row.label()) + 6, row.y() + 5, Theme.MUTED);
			}
			drawControl(row);
		}

		String[] labels = {I18n.translate("rago.menu.edit_hud"),
			confirmReset ? I18n.translate("rago.menu.reset_confirm") : I18n.translate("rago.menu.reset"),
			I18n.translate("gui.done")};
		for (int i = 0; i < 3; i++) {
			int[] b = footerButton(i);
			boolean hover = Theme.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
			int color = i == 1 && confirmReset ? Theme.DANGER : hover ? Theme.ROW_HOVER : Theme.ROW;
			GuiElement.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], color);
			drawCenteredString(textRenderer, labels[i], b[0] + b[2] / 2, b[1] + 4, Theme.TEXT);
		}
	}

	private void drawControl(Row row) {
		int right = row.x() + row.w() - 4;
		int y = row.y() + 4;
		switch (row.control()) {
			case TOGGLE_ON, TOGGLE_OFF -> {
				boolean on = row.control() == Control.TOGGLE_ON;
				int x = right - 20;
				GuiElement.fill(x, y + 1, x + 20, y + 9, on ? Theme.ON : Theme.OFF);
				int knob = on ? x + 12 : x;
				GuiElement.fill(knob, y, knob + 8, y + 10, Theme.TEXT);
			}
			case COLOR -> {
				int color = row.color();
				GuiElement.fill(right - 20, y, right, y + 10, Theme.TEXT);
				GuiElement.fill(right - 19, y + 1, right - 1, y + 9, color);
			}
			default -> {
			}
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
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
		if (mouseY < listTop() || mouseY >= listBottom()) {
			return;
		}
		for (Row row : rows()) {
			if (!Theme.inside(mouseX, mouseY, row.x(), row.y(), row.w(), row.h())) {
				continue;
			}
			boolean onToggle = mouseX >= row.x() + row.w() - TOGGLE_W;
			(onToggle ? row.onToggle() : row.onLabel()).run();
			return;
		}
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
			default -> minecraft.openScreen(null);
		}
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0 && Theme.inside(lastMouseX, lastMouseY, panelX(), listTop(), PANEL_W, listBottom() - listTop())) {
			scroll -= Integer.signum(wheel) * (ROW_H + GAP) * 2;
			clampScroll();
		}
	}

	@Override
	protected void keyPressed(char typedChar, int keyCode) {
		if (keyCode == RagoClient.menuKeyCode()) {
			minecraft.openScreen(null);
			return;
		}
		super.keyPressed(typedChar, keyCode);
	}

	@Override
	public void removed() {
		RagoClient.saveConfig();
	}
}
