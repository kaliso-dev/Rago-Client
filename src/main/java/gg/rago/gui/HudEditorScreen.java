package gg.rago.gui;

import gg.rago.core.RagoClient;
import gg.rago.hud.HudElement;
import gg.rago.hud.HudManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Drag HUD elements to move them, scroll over one to resize it, right-click to reset it, click the
 * gear by the one under the mouse (or the last one clicked) to open its settings. Elements snap to
 * the screen's centre lines. Esc saves and returns to the Rago menu.
 */
public final class HudEditorScreen extends GuiScreen {
	private static final int SNAP = 4;
	private static final float SCALE_STEP = 0.1f;
	private static final int GEAR = 10;

	private final GuiScreen parent;
	private HudElement dragging;
	private int grabX;
	private int grabY;
	private int lastMouseX;
	private int lastMouseY;
	private boolean snappedX;
	private boolean snappedY;
	/** The element last clicked: its gear stays while the mouse goes to it. */
	private HudElement selected;
	/** The element whose gear was drawn last frame, and where: {x, y, size}. */
	private HudElement gearFor;
	private final int[] gear = new int[3];

	public HudEditorScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private List<HudElement> elements() {
		return RagoClient.hud().elements().stream().filter(e -> e.isEnabled() && e.onScreen()).collect(Collectors.toList());
	}

	/** Live data when there is some, otherwise a sample value, so every element can be placed. */
	private static boolean placeholder(HudElement element) {
		return !element.hasContent();
	}

	private HudElement elementAt(int mx, int my) {
		List<HudElement> list = elements();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudElement e = list.get(i);
			int[] b = HudManager.bounds(e, fontRendererObj, width, height, placeholder(e));
			if (Theme.inside(mx, my, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2)) {
				return e;
			}
		}
		return null;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		HudManager.nextFrame();
		HudManager.stack(elements(), fontRendererObj, height, HudEditorScreen::placeholder);
		drawGradientRect(0, 0, width, height, 0x44000000, 0x44000000);

		if (dragging != null) {
			drag(mouseX, mouseY);
			if (snappedX) {
				Gui.drawRect(width / 2, 0, width / 2 + 1, height, Theme.ACCENT);
			}
			if (snappedY) {
				Gui.drawRect(0, height / 2, width, height / 2 + 1, Theme.ACCENT);
			}
		} else {
			Gui.drawRect(width / 2, 0, width / 2 + 1, height, Theme.GUIDE);
			Gui.drawRect(0, height / 2, width, height / 2 + 1, Theme.GUIDE);
		}

		HudElement hovered = dragging != null ? dragging : onGear(mouseX, mouseY) ? gearFor : elementAt(mouseX, mouseY);
		HudElement withGear = dragging != null ? null : hovered != null ? hovered : selected != null && selected.isEnabled() ? selected : null;
		gearFor = null;
		for (HudElement e : elements()) {
			int[] b = HudManager.draw(e, fontRendererObj, width, height, placeholder(e));
			Theme.outline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, e == hovered || e == withGear ? Theme.OUTLINE_ACTIVE : Theme.OUTLINE);
			if (e == withGear) {
				placeGear(b);
				gearFor = e;
			}
		}
		if (gearFor != null) {
			drawGear(onGear(mouseX, mouseY));
		}

		// On a narrow screen (a big GUI scale) the help goes on two lines or more.
		List<String> help = fontRendererObj.listFormattedStringToWidth(I18n.format("rago.editor.help"), width - 16);
		int widest = 0;
		for (String line : help) {
			widest = Math.max(widest, fontRendererObj.getStringWidth(line));
		}
		int y = height - 10 - help.size() * 10;
		Gui.drawRect(width / 2 - widest / 2 - 6, y - 4, width / 2 + widest / 2 + 6, y + help.size() * 10 + 2, Theme.PANEL);
		for (int i = 0; i < help.size(); i++) {
			drawCenteredString(fontRendererObj, help.get(i), width / 2, y + i * 10, Theme.TEXT);
		}
		if (hovered != null) {
			String info = I18n.format(hovered.nameKey()) + "  " + Math.round(hovered.position().scale() * 100) + "%";
			drawCenteredString(fontRendererObj, info, width / 2, y - 16, Theme.MUTED);
		}
	}

	/** The gear goes just outside the element's top right corner, or inside it at the screen's edge. */
	private void placeGear(int[] b) {
		int x = b[0] + b[2] + 2;
		int y = b[1] - GEAR - 2;
		if (x + GEAR > width) {
			x = b[0] + b[2] - GEAR;
		}
		if (y < 0) {
			y = b[1] + b[3] + 2 + GEAR <= height ? b[1] + b[3] + 2 : b[1];
		}
		gear[0] = x;
		gear[1] = y;
		gear[2] = GEAR;
	}

	private boolean onGear(int mx, int my) {
		return gearFor != null && Theme.inside(mx, my, gear[0] - 1, gear[1] - 1, gear[2] + 2, gear[2] + 2);
	}

	/** The gear icon, drawn with the menu's icons (their units are real pixels over the menu's scale). */
	private void drawGear(boolean hover) {
		Gui.drawRect(gear[0] - 1, gear[1] - 1, gear[0] + gear[2] + 1, gear[1] + gear[2] + 1, hover ? Theme.ACCENT : Theme.PANEL);
		gg.rago.gui.ui.Ui.begin();
		float k = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor() / gg.rago.gui.ui.Ui.scale();
		gg.rago.gui.ui.Ui.icon("settings", (gear[0] + 1) * k, (gear[1] + 1) * k, (gear[2] - 2) * k, 0xFFFFFFFF);
		gg.rago.gui.ui.Ui.end();
	}

	/** Opens the element's own page in the Rago menu, the one this editor came from when it did. */
	private void openSettings(HudElement e) {
		RagoMenuScreen menu = parent instanceof RagoMenuScreen ? (RagoMenuScreen) parent : new RagoMenuScreen(parent);
		mc.displayGuiScreen(menu.show(e));
	}

	private void drag(int mouseX, int mouseY) {
		int[] b = HudManager.bounds(dragging, fontRendererObj, width, height, placeholder(dragging));
		int x = mouseX - grabX;
		int y = mouseY - grabY;
		int centerX = x + b[2] / 2;
		int centerY = y + b[3] / 2;
		snappedX = Math.abs(centerX - width / 2) <= SNAP;
		snappedY = Math.abs(centerY - height / 2) <= SNAP;
		if (snappedX) {
			x = width / 2 - b[2] / 2;
		}
		if (snappedY) {
			y = height / 2 - b[3] / 2;
		}
		dragging.position().moveTo(x, y, b[2], b[3], width, height);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button == 0 && onGear(mouseX, mouseY)) {
			openSettings(gearFor);
			return;
		}
		HudElement e = elementAt(mouseX, mouseY);
		if (e == null) {
			return;
		}
		selected = e;
		if (button == 0) {
			int[] b = HudManager.bounds(e, fontRendererObj, width, height, placeholder(e));
			dragging = e;
			grabX = mouseX - b[0];
			grabY = mouseY - b[1];
		} else if (button == 1) {
			e.position().reset();
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		dragging = null;
		snappedX = false;
		snappedY = false;
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) {
			return;
		}
		HudElement e = dragging != null ? dragging : elementAt(lastMouseX, lastMouseY);
		if (e != null) {
			e.position().setScale(e.position().scale() + Integer.signum(wheel) * SCALE_STEP);
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) { // Escape
			mc.displayGuiScreen(parent);
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	@Override
	public void onGuiClosed() {
		RagoClient.saveConfig();
	}
}
