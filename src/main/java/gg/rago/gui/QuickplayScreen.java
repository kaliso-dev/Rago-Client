package gg.rago.gui;

import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.TtfFont;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.hypixel.Hypixel;
import gg.rago.modules.Quickplay;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Quickplay menu: favourites, the Bed Wars modes, the Dream modes and your own commands, as
 * tiles or a list in the colours and size chosen on the mod's page. Click joins, right click pins a
 * favourite, 1 to 9 join the first nine, and the pencil shows hidden entries to bring them back.
 */
public final class QuickplayScreen extends GuiScreen {
	private static final float GAP = 8;
	private static final float PAD = 20;

	private final Quickplay quickplay;
	private final long openedNs = System.nanoTime();
	private boolean editing;
	private float scroll;
	private String message;
	private boolean messageError;
	/** The entry under the mouse this frame, for the right click. */
	private Quickplay.Mode hovered;
	/** The entries in the order shown, for the number keys. */
	private final List<Quickplay.Mode> shown = new ArrayList<>();

	public QuickplayScreen(Quickplay quickplay) {
		this.quickplay = quickplay;
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		Ui.backdrop(quickplay.dim());
		Ui.captureGlass();
		float p = Anim.progress(openedNs, 200);
		Ui.opacity(p);
		float s = quickplay.scale();
		boolean grid = quickplay.grid();
		int cols = grid ? quickplay.columns() : 1;
		float tileW = grid ? 116 * s : 340 * s;
		float tileH = grid ? 84 * s : 38 * s;
		float gap = GAP * s;
		float pw = Math.min(w - 24, cols * tileW + (cols - 1) * gap + PAD * 2);
		if (grid && pw < cols * tileW + (cols - 1) * gap + PAD * 2) {
			tileW = (pw - PAD * 2 - (cols - 1) * gap) / cols;
		}

		// sections: favourites, then each group without them; hidden entries only while editing
		List<Quickplay.Mode> all = quickplay.all();
		List<List<Quickplay.Mode>> sections = new ArrayList<>();
		List<String> titles = new ArrayList<>();
		if (!editing) {
			List<Quickplay.Mode> fav = new ArrayList<>();
			for (Quickplay.Mode m : all) {
				if (quickplay.isFavourite(m) && !quickplay.isHidden(m)) {
					fav.add(m);
				}
			}
			if (!fav.isEmpty()) {
				sections.add(fav);
				titles.add(t("rago.quickplay.favourites"));
			}
		}
		for (String group : new String[] {Quickplay.CORE, Quickplay.DREAM, Quickplay.OTHER}) {
			List<Quickplay.Mode> list = new ArrayList<>();
			for (Quickplay.Mode m : all) {
				if (m.group.equals(group) && (editing || !quickplay.isHidden(m) && !quickplay.isFavourite(m))) {
					list.add(m);
				}
			}
			if (!list.isEmpty()) {
				sections.add(list);
				titles.add(t("rago.quickplay.group." + group));
			}
		}
		float content = 0;
		for (List<Quickplay.Mode> list : sections) {
			int rows = (list.size() + cols - 1) / cols;
			content += 22 * s + rows * tileH + (rows - 1) * gap + 14 * s;
		}
		float headerH = 64;
		float footerH = 34;
		float ph = Math.min(h - 24, headerH + content + footerH);
		float x = (w - pw) / 2;
		float y = (h - ph) / 2 + (1 - p) * 10;
		Ui.glassBorder(x, y, pw, ph, 14, 0xF2121114, Theme.BORDER_STRONG);

		// header
		int accent = quickplay.accent();
		Ui.round(x + PAD, y + 18, 30, 30, 9, Ui.alpha(accent, 0.18f));
		Ui.icon("zap", x + PAD + 7, y + 25, 16, accent);
		Ui.text(Ui.SEMIBOLD, t("rago.quickplay.title"), x + PAD + 42, y + 17, 14, Theme.TEXT);
		boolean hypixel = Hypixel.connected();
		Ui.text(Ui.REGULAR, t(hypixel ? "rago.quickplay.subtitle" : "rago.quickplay.not_hypixel"), x + PAD + 42, y + 36, 9,
			hypixel ? Theme.MUTED : Theme.YELLOW);
		float bx = x + pw - PAD - 26;
		Widgets.iconButton(bx, y + 18, 26, "x");
		Ui.click(bx, y + 18, 26, 26, () -> mc.displayGuiScreen(null));
		Widgets.iconButton(bx - 32, y + 18, 26, editing ? "check" : "pencil");
		Ui.click(bx - 32, y + 18, 26, 26, () -> {
			editing = !editing;
			message = editing ? t("rago.quickplay.edit_hint") : null;
			messageError = false;
		});

		// the entries
		float top = y + headerH;
		float bottom = y + ph - footerH;
		float maxScroll = Math.max(0, content - (bottom - top));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		hovered = null;
		shown.clear();
		Ui.scissor(x, top, pw, bottom - top);
		float cy = top - scroll;
		for (int i = 0; i < sections.size(); i++) {
			Ui.text(Ui.SEMIBOLD, titles.get(i).toUpperCase(java.util.Locale.ROOT), x + PAD, cy + 4 * s, 8 * s, Theme.DIM);
			cy += 22 * s;
			List<Quickplay.Mode> list = sections.get(i);
			for (int j = 0; j < list.size(); j++) {
				float tx = x + PAD + (j % cols) * (tileW + gap);
				float ty = cy + (j / cols) * (tileH + gap);
				Quickplay.Mode m = list.get(j);
				shown.add(m);
				if (ty + tileH < top || ty > bottom) {
					continue;
				}
				boolean inside = Ui.hover(x, top, pw, bottom - top);
				if (grid) {
					tile(m, tx, ty, tileW, tileH, s, shown.size(), inside);
				} else {
					row(m, tx, ty, pw - PAD * 2, tileH, s, shown.size(), inside);
				}
			}
			int rows = (list.size() + cols - 1) / cols;
			cy += rows * tileH + (rows - 1) * gap + 14 * s;
		}
		Ui.endScissor();

		// footer: what just happened, or how to use the menu
		String foot = message != null ? message : t("rago.quickplay.hint");
		Ui.textCentered(Ui.REGULAR, foot, x + pw / 2, y + ph - footerH + 11, 9,
			message == null ? Theme.DIM : messageError ? Theme.YELLOW : Theme.MUTED);
		Ui.opacity(1);
		Ui.end();
	}

	private void tile(Quickplay.Mode m, float x, float y, float w, float h, float s, int number, boolean inside) {
		boolean hidden = quickplay.isHidden(m);
		boolean confirming = quickplay.confirming(m);
		int accent = quickplay.accent();
		float hv = inside ? Widgets.hoverAmount("qp:" + m.id, x, y, w, h) : 0;
		if (inside && Ui.hover(x, y, w, h)) {
			hovered = m;
		}
		float lift = hv * 2 * s;
		float before = Ui.opacity();
		if (editing && hidden) {
			Ui.opacity(before * 0.4f);
		}
		int fill = Ui.mix(quickplay.tileColor(), Ui.mix(quickplay.tileColor(), accent, 0.22f), confirming ? 1 : hv);
		int border = confirming ? accent : Ui.mix(Theme.BORDER_STRONG, accent, hv);
		Ui.glassBorder(x, y - lift, w, h, quickplay.radius() * s, fill, border);
		int ink = Ui.mix(Theme.TEXT, accent, hv);
		float textTop = y - lift + 12 * s;
		if (quickplay.showIcons()) {
			float is = 20 * s;
			Ui.icon(editing && hidden ? "eye-off" : m.icon, x + (w - is) / 2, textTop, is, ink);
			textTop += is + 8 * s;
		} else {
			textTop += 10 * s;
		}
		String name = confirming ? t("rago.quickplay.again") : m.name();
		Ui.textCentered(Ui.SEMIBOLD, fit(Ui.SEMIBOLD, name, 10.5f * s, w - 10 * s), x + w / 2, textTop, 10.5f * s, Theme.TEXT);
		String sub = quickplay.showCommands() || m.label != null ? m.command : t("rago.quickplay.sub." + m.id);
		TtfFont subFont = quickplay.showCommands() || m.label != null ? Ui.MONO : Ui.REGULAR;
		Ui.textCentered(subFont, fit(subFont, sub, 8 * s, w - 10 * s), x + w / 2, textTop + 15 * s, 8 * s, Theme.MUTED);
		// corner marks: its number key, favourite, last joined
		if (number <= 9 && !editing) {
			Ui.text(Ui.MONO, String.valueOf(number), x + 7 * s, y - lift + 6 * s, 7.5f * s, Theme.DIM);
		}
		if (quickplay.isFavourite(m)) {
			Ui.icon("star", x + w - 16 * s, y - lift + 6 * s, 10 * s, accent);
		} else if (m.id.equals(quickplay.lastId())) {
			Ui.icon("rotate-ccw", x + w - 16 * s, y - lift + 6 * s, 10 * s, Theme.MUTED);
		}
		Ui.opacity(before);
		Ui.click(x, y, w, h, () -> activate(m));
	}

	private void row(Quickplay.Mode m, float x, float y, float w, float h, float s, int number, boolean inside) {
		boolean hidden = quickplay.isHidden(m);
		boolean confirming = quickplay.confirming(m);
		int accent = quickplay.accent();
		float hv = inside ? Widgets.hoverAmount("qp:" + m.id, x, y, w, h) : 0;
		if (inside && Ui.hover(x, y, w, h)) {
			hovered = m;
		}
		float before = Ui.opacity();
		if (editing && hidden) {
			Ui.opacity(before * 0.4f);
		}
		int fill = Ui.mix(quickplay.tileColor(), Ui.mix(quickplay.tileColor(), accent, 0.22f), confirming ? 1 : hv);
		Ui.glassBorder(x, y, w, h, Math.min(quickplay.radius(), 12) * s, fill, confirming ? accent : Ui.mix(Theme.BORDER_STRONG, accent, hv));
		float tx = x + 12 * s;
		if (number <= 9 && !editing) {
			Ui.text(Ui.MONO, String.valueOf(number), tx, y + (h - 9 * s) / 2, 8 * s, Theme.DIM);
		}
		// the number's place is kept on every row, so the icons stay in one column
		tx += 14 * s;
		if (quickplay.showIcons()) {
			float is = 16 * s;
			Ui.icon(editing && hidden ? "eye-off" : m.icon, tx, y + (h - is) / 2, is, Ui.mix(Theme.TEXT, accent, hv));
			tx += is + 10 * s;
		}
		// right side first (marks, command), so the description only takes the room that is left
		float right = x + w - 12 * s;
		if (quickplay.isFavourite(m)) {
			Ui.icon("star", right - 10 * s, y + (h - 10 * s) / 2, 10 * s, accent);
			right -= 18 * s;
		} else if (m.id.equals(quickplay.lastId())) {
			Ui.icon("rotate-ccw", right - 10 * s, y + (h - 10 * s) / 2, 10 * s, Theme.MUTED);
			right -= 18 * s;
		}
		if (quickplay.showCommands() || m.label != null) {
			right -= Ui.textWidth(Ui.MONO, m.command, 8 * s) + 10 * s;
			Ui.text(Ui.MONO, m.command, right + 10 * s, y + (h - 9 * s) / 2, 8 * s, Theme.MUTED);
		}
		String name = confirming ? t("rago.quickplay.again") : m.name();
		name = fit(Ui.SEMIBOLD, name, 10.5f * s, Math.max(20 * s, right - tx));
		float nameW = Ui.text(Ui.SEMIBOLD, name, tx, y + (h - 12 * s) / 2, 10.5f * s, Theme.TEXT);
		String sub = m.label != null ? null : t("rago.quickplay.sub." + m.id);
		float subX = tx + nameW + 8 * s;
		if (sub != null && subX + Ui.textWidth(Ui.REGULAR, sub, 8.5f * s) <= right) {
			Ui.text(Ui.REGULAR, sub, subX, y + (h - 10 * s) / 2, 8.5f * s, Theme.MUTED);
		}
		Ui.opacity(before);
		Ui.click(x, y, w, h, () -> activate(m));
	}

	private static String fit(TtfFont font, String text, float size, float max) {
		if (Ui.textWidth(font, text, size) <= max) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && Ui.textWidth(font, s + "…", size) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	private void activate(Quickplay.Mode m) {
		if (editing) {
			quickplay.toggleHidden(m);
			return;
		}
		String result = quickplay.join(m);
		if (result == null) {
			if (quickplay.closeAfterJoin()) {
				mc.displayGuiScreen(null);
			} else {
				message = t("rago.quickplay.sent", m.command);
				messageError = false;
			}
		} else {
			message = result;
			messageError = true;
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		Ui.updateMouse();
		if (button == 1) {
			if (hovered != null) {
				quickplay.toggleFavourite(hovered);
				message = t(quickplay.isFavourite(hovered) ? "rago.quickplay.pinned" : "rago.quickplay.unpinned", hovered.name());
				messageError = false;
			}
			return;
		}
		if (button == 0 && Ui.dispatchClick()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			scroll -= Math.signum(d) * 40;
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == quickplay.key().getKeyCode() || keyCode == Keyboard.KEY_ESCAPE) {
			mc.displayGuiScreen(null);
			return;
		}
		if (!editing && keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
			int i = keyCode - Keyboard.KEY_1;
			if (i < shown.size()) {
				activate(shown.get(i));
			}
		}
	}
}
