package gg.rago.gui;

import gg.rago.core.RagoClient;
import gg.rago.hud.HudElement;
import gg.rago.hud.HudManager;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Mouse;

import java.util.List;

/**
 * Drag HUD elements to move them, scroll over one to resize it, right-click to reset it.
 * Elements snap to the screen's centre lines. Esc saves and returns to the Rago menu.
 */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 4;
	private static final float SCALE_STEP = 0.1f;

	private final Screen parent;
	private HudElement dragging;
	private int grabX;
	private int grabY;
	private int lastMouseX;
	private int lastMouseY;
	private boolean snappedX;
	private boolean snappedY;

	public HudEditorScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}

	private List<HudElement> elements() {
		return RagoClient.hud().elements().stream().filter(HudElement::isEnabled).toList();
	}

	/** Live data when there is some, otherwise a sample value, so every element can be placed. */
	private static boolean placeholder(HudElement element) {
		return !element.hasContent();
	}

	private HudElement elementAt(int mx, int my) {
		List<HudElement> list = elements();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudElement e = list.get(i);
			int[] b = HudManager.bounds(e, textRenderer, width, height, placeholder(e));
			if (Theme.inside(mx, my, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2)) {
				return e;
			}
		}
		return null;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		fillGradient(0, 0, width, height, 0x44000000, 0x44000000);

		if (dragging != null) {
			drag(mouseX, mouseY);
			if (snappedX) {
				GuiElement.fill(width / 2, 0, width / 2 + 1, height, Theme.ACCENT);
			}
			if (snappedY) {
				GuiElement.fill(0, height / 2, width, height / 2 + 1, Theme.ACCENT);
			}
		} else {
			GuiElement.fill(width / 2, 0, width / 2 + 1, height, Theme.GUIDE);
			GuiElement.fill(0, height / 2, width, height / 2 + 1, Theme.GUIDE);
		}

		HudElement hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
		for (HudElement e : elements()) {
			int[] b = HudManager.draw(e, textRenderer, width, height, placeholder(e));
			Theme.outline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, e == hovered ? Theme.OUTLINE_ACTIVE : Theme.OUTLINE);
		}

		String help = I18n.translate("rago.editor.help");
		int y = height - 20;
		GuiElement.fill(width / 2 - textRenderer.getWidth(help) / 2 - 6, y - 4, width / 2 + textRenderer.getWidth(help) / 2 + 6, y + 12, Theme.PANEL);
		drawCenteredString(textRenderer, help, width / 2, y, Theme.TEXT);
		if (hovered != null) {
			String info = I18n.translate(hovered.nameKey()) + "  " + Math.round(hovered.position().scale() * 100) + "%";
			drawCenteredString(textRenderer, info, width / 2, y - 16, Theme.MUTED);
		}
	}

	private void drag(int mouseX, int mouseY) {
		int[] b = HudManager.bounds(dragging, textRenderer, width, height, placeholder(dragging));
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
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		HudElement e = elementAt(mouseX, mouseY);
		if (e == null) {
			return;
		}
		if (button == 0) {
			int[] b = HudManager.bounds(e, textRenderer, width, height, placeholder(e));
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
	public void handleMouse() {
		super.handleMouse();
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
	protected void keyPressed(char typedChar, int keyCode) {
		if (keyCode == 1) { // Escape
			minecraft.openScreen(parent);
			return;
		}
		super.keyPressed(typedChar, keyCode);
	}

	@Override
	public void removed() {
		RagoClient.saveConfig();
	}
}
