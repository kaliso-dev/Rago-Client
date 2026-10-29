package gg.rago.gui;

import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rago's resource-pack screen, in place of the vanilla one: every pack as a card with its icon, its
 * description and a strip of the textures it changes (sword, pearl, golden apple, wool...), a search
 * box (just type), and the player's own folders to sort packs into. Turning packs on, off and
 * reordering them works as in vanilla, and the change is applied once, when the screen closes.
 */
public final class PacksScreen extends GuiScreen {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final float SIDE_W = 168;
	private static final float CARD_H = 108;
	private static final float GAP = 10;
	/** Textures shown on a card when the pack changes them, in this order; the first five found are used. */
	private static final String[] PREVIEW = {
		"items/diamond_sword", "items/iron_sword", "items/ender_pearl", "items/apple_golden", "items/bow_standby",
		"items/fireball", "items/stone_sword", "items/diamond_chestplate", "items/potion_bottle_splash",
		"items/fishing_rod_uncast", "items/snowball", "items/egg"};
	private static final int PREVIEW_COUNT = 6;
	/** Blocks shown as little cubes on every card: the ones Bed Wars and PvP players see most. */
	private static final String[] BLOCKS = {"wool_colored_white", "end_stone", "planks_oak", "obsidian", "dirt", "glass"};
	/** Opening the vanilla screen from here must not open this one again. */
	private static boolean showVanilla;

	private final GuiScreen parent;
	private final ResourcePackRepository repo;
	private final PackFolders folders;
	/** All packs found, and the ones in use with the highest priority first. */
	private final List<ResourcePackRepository.Entry> all = new ArrayList<>();
	private final List<ResourcePackRepository.Entry> active = new ArrayList<>();
	private final Map<String, Visual> visuals = new HashMap<>();
	private long openedNs;
	private float scroll;
	private boolean changed;
	/** Sidebar choice: null all packs, "" the packs in use, otherwise a folder. */
	private String view;
	private String search = "";
	/** The name typed for a new folder, or null when not adding one. */
	private String newFolder;
	/** The pack whose folder menu is open, and where. */
	private String folderMenu;
	private float menuX;
	private float menuY;

	/** What a card shows besides text, loaded a few packs per frame. */
	private static final class Visual {
		ResourceLocation icon;
		final List<ResourceLocation> previews = new ArrayList<>();
		final List<float[]> previewUv = new ArrayList<>();
		/** One per entry of BLOCKS: the pack's texture, or the default one when the pack keeps it. */
		final List<ResourceLocation> blocks = new ArrayList<>();
		final List<float[]> blockUv = new ArrayList<>();
	}

	public PacksScreen(GuiScreen parent) {
		this.parent = parent;
		this.repo = net.minecraft.client.Minecraft.getMinecraft().getResourcePackRepository();
		this.folders = new PackFolders(gg.rago.core.RagoClient.configDir().resolve("rago-pack-folders.json"));
		reload();
	}

	/** True once, right after "classic screen" was picked, so the vanilla screen is let through. */
	public static boolean takeVanilla() {
		boolean v = showVanilla;
		showVanilla = false;
		return v;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	private void reload() {
		repo.updateRepositoryEntriesAll();
		all.clear();
		all.addAll(repo.getRepositoryEntriesAll());
		if (!changed) {
			active.clear();
			List<ResourcePackRepository.Entry> on = new ArrayList<>(repo.getRepositoryEntries());
			Collections.reverse(on);
			active.addAll(on);
		}
		// a pack that was in use and has been deleted from the folder
		active.retainAll(all);
		all.sort((a, b) -> a.getResourcePackName().compareToIgnoreCase(b.getResourcePackName()));
	}

	@Override
	public void initGui() {
		openedNs = System.nanoTime();
		Keyboard.enableRepeatEvents(true);
	}

	private List<ResourcePackRepository.Entry> shown() {
		List<ResourcePackRepository.Entry> list = new ArrayList<>();
		if ("".equals(view)) {
			list.addAll(active);
		} else {
			// alphabetical, so a card does not jump away when it is turned on; "In use" shows the order
			for (ResourcePackRepository.Entry e : all) {
				if (inView(e)) {
					list.add(e);
				}
			}
		}
		if (!search.isEmpty()) {
			String q = search.toLowerCase(Locale.ROOT);
			list.removeIf(e -> !plain(e.getResourcePackName()).toLowerCase(Locale.ROOT).contains(q));
		}
		return list;
	}

	private boolean inView(ResourcePackRepository.Entry e) {
		return view == null || view.equals(folders.folderOf(e.getResourcePackName()));
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		loadSomeVisuals();
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		Ui.backdrop(0.45f);
		Ui.captureGlass();
		float p = Anim.progress(openedNs, 260);
		Ui.opacity(p);

		float pw = Math.min(w - 40, 820);
		float ph = Math.min(h - 40, 520);
		float x = (w - pw) / 2;
		float y = (h - ph) / 2 + (1 - p) * 12;
		Ui.glassBorder(x, y, pw, ph, 14, 0xF2121114, Theme.BORDER_STRONG);
		boolean modal = newFolder != null || folderMenu != null;

		// header
		Ui.round(x + 20, y + 18, 32, 32, 9, Theme.ACCENT_SOFT);
		Ui.icon("layers", x + 28, y + 26, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.packs.title"), x + 64, y + 18, 15, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.packs.subtitle", active.size(), all.size()), x + 64, y + 38, 9.5f, Theme.MUTED);
		// search box: typing anywhere goes here
		float sw = 200;
		float sx = x + pw - 20 - sw;
		Ui.glassBorder(sx, y + 21, sw, 26, 13, 0xFF151417, search.isEmpty() ? Theme.BORDER : Ui.alpha(Theme.ACCENT, 0.6f));
		Ui.icon("search", sx + 10, y + 28, 12, Theme.MUTED);
		boolean caret = newFolder == null && (System.currentTimeMillis() / 500) % 2 == 0;
		Ui.text(Ui.REGULAR, search.isEmpty() ? t("rago.packs.search") : search + (caret ? "|" : ""), sx + 28,
			y + 21 + (26 - 10 * 1.21f) / 2, 10, search.isEmpty() ? Theme.DIM : Theme.TEXT);
		if (!search.isEmpty() && !modal) {
			Ui.icon("x", sx + sw - 22, y + 28, 12, Theme.MUTED);
			Ui.click(sx + sw - 28, y + 21, 26, 26, () -> search = "");
		}

		float top = y + 66;
		float bottom = y + ph - 58;
		sidebar(x + 14, top, bottom - top, modal);
		grid(x + 14 + SIDE_W + 10, top, pw - 38 - SIDE_W, bottom - top, modal);

		// footer
		float by = y + ph - 44;
		float bw = 130;
		Widgets.button(x + 20, by, bw, 28, "folder-open", t("rago.packs.open_folder"), false, 10);
		Widgets.button(x + 30 + bw, by, bw, 28, "refresh-cw", t("rago.packs.refresh"), false, 10);
		Widgets.button(x + 40 + bw * 2, by, bw, 28, "monitor", t("rago.packs.classic"), false, 10);
		Widgets.button(x + pw - 20 - bw, by, bw, 28, "check", t("gui.done"), true, 10);
		if (!modal) {
			Ui.click(x + 20, by, bw, 28, this::openFolder);
			Ui.click(x + 30 + bw, by, bw, 28, () -> {
				freeVisuals();
				reload();
			});
			Ui.click(x + 40 + bw * 2, by, bw, 28, () -> {
				apply();
				showVanilla = true;
				mc.displayGuiScreen(new GuiScreenResourcePacks(parent));
			});
			Ui.click(x + pw - 20 - bw, by, bw, 28, this::close);
		}

		if (folderMenu != null) {
			folderMenu(w, h);
		} else if (newFolder != null) {
			newFolderDialog(w, h);
		}
		Ui.opacity(1);
		Ui.end();
	}

	private void sidebar(float x, float y, float h, boolean modal) {
		Ui.glassBorder(x, y, SIDE_W, h, 10, 0xFF141316, 0x14FFFFFF);
		float ry = y + 8;
		ry = sideRow(x + 6, ry, "layers", t("rago.packs.all"), all.size(), view == null, null, modal, () -> view = null);
		ry = sideRow(x + 6, ry, "check", t("rago.packs.active"), active.size(), "".equals(view), null, modal, () -> view = "");
		Ui.rect(x + 12, ry + 3, SIDE_W - 24, 1, 0x14FFFFFF);
		ry += 10;
		Ui.text(Ui.SEMIBOLD, t("rago.packs.folders").toUpperCase(Locale.ROOT), x + 14, ry, 8, Theme.DIM);
		ry += 16;
		Ui.scissor(x, ry, SIDE_W, y + h - 40 - ry);
		for (String f : new ArrayList<>(folders.folders())) {
			ry = sideRow(x + 6, ry, "folder", f, folders.count(f), f.equals(view), f, modal, () -> view = f);
		}
		Ui.endScissor();
		float ny = y + h - 34;
		float hv = Widgets.hoverAmount("packs:newfolder", x + 6, ny, SIDE_W - 12, 26);
		Ui.round(x + 6, ny, SIDE_W - 12, 26, 8, Ui.mix(0x00FFFFFF, 0x10FFFFFF, hv));
		Ui.icon("folder-plus", x + 14, ny + 7, 12, Theme.ACCENT);
		Ui.text(Ui.MEDIUM, t("rago.packs.new_folder"), x + 34, ny + (26 - 9.5f * 1.21f) / 2, 9.5f, Theme.TEXT);
		if (!modal) {
			Ui.click(x + 6, ny, SIDE_W - 12, 26, () -> newFolder = "");
		}
	}

	private float sideRow(float x, float y, String icon, String label, int count, boolean selected, String folder,
		boolean modal, Runnable action) {
		float w = SIDE_W - 12;
		float rh = 26;
		float hv = Widgets.hoverAmount("packs:side:" + icon + label, x, y, w, rh);
		Ui.round(x, y, w, rh, 8, selected ? Theme.ACCENT_SOFT : Ui.mix(0x00FFFFFF, 0x10FFFFFF, hv));
		Ui.icon(icon, x + 8, y + 7, 12, selected ? Theme.ACCENT : Theme.MUTED);
		float right = x + w - 8;
		if (folder != null && hv > 0.5f && !modal) {
			// delete the folder (the packs stay)
			Ui.icon("trash-2", right - 12, y + 7, 12, Ui.hover(right - 16, y, 18, rh) ? Theme.ACCENT : Theme.MUTED);
			Ui.click(right - 16, y, 18, rh, () -> {
				folders.remove(folder);
				if (folder.equals(view)) {
					view = null;
				}
			});
			right -= 20;
		}
		String n = String.valueOf(count);
		Ui.textRight(Ui.MONO, n, right, y + (rh - 9 * 1.21f) / 2, 9, Theme.DIM);
		float maxW = right - Ui.textWidth(Ui.MONO, n, 9) - 8 - (x + 26);
		Ui.text(Ui.MEDIUM, ellipsize(Ui.MEDIUM, label, 10, maxW), x + 26, y + (rh - 10 * 1.21f) / 2, 10,
			selected ? Theme.TEXT : 0xFFD4D4D8);
		if (!modal) {
			Ui.click(x, y, w, rh, action);
		}
		return y + rh + 2;
	}

	private void grid(float x, float y, float w, float h, boolean modal) {
		List<ResourcePackRepository.Entry> list = shown();
		int cols = Math.max(1, (int) ((w + GAP) / (250 + GAP)));
		float cw = (w - GAP * (cols - 1)) / cols;
		int rows = (list.size() + cols - 1) / cols;
		float total = rows * (CARD_H + GAP) - GAP;
		scroll = Math.max(0, Math.min(scroll, total - h));
		if (list.isEmpty()) {
			String msg = all.isEmpty() ? t("rago.packs.none") : t("rago.packs.no_match");
			float ty = y + h / 2 - 20;
			for (String line : Ui.wrap(Ui.REGULAR, msg, 10, w - 60)) {
				Ui.textCentered(Ui.REGULAR, line, x + w / 2, ty, 10, Theme.MUTED);
				ty += 15;
			}
			return;
		}
		Ui.scissor(x - 2, y - 2, w + 4, h + 4);
		for (int i = 0; i < list.size(); i++) {
			float cx = x + (i % cols) * (cw + GAP);
			float cy = y + (i / cols) * (CARD_H + GAP) - scroll;
			if (cy + CARD_H < y || cy > y + h) {
				continue;
			}
			card(list.get(i), cx, cy, cw, modal || !Ui.hover(x, y, w, h));
		}
		Ui.endScissor();
		if (total > h) {
			float bh = Math.max(24, h * h / total);
			float by = y + (h - bh) * scroll / (total - h);
			Ui.round(x + w + 4, by, 3, bh, 1.5f, 0x40FFFFFF);
		}
	}

	private void card(ResourcePackRepository.Entry e, float x, float y, float w, boolean noClicks) {
		String name = e.getResourcePackName();
		int rank = active.indexOf(e);
		boolean on = rank >= 0;
		float hv = Widgets.hoverAmount("pack:" + name, x, y, w, CARD_H);
		Ui.glassBorder(x, y, w, CARD_H, 10, Ui.mix(0xFF151417, 0xFF1D1C20, hv), on ? Ui.alpha(Theme.ACCENT, 0.55f) : 0x14FFFFFF);

		Visual v = visuals.get(name);
		Ui.round(x + 10, y + 10, 50, 50, 7, 0xFF0B0A0D);
		if (v != null && v.icon != null) {
			Ui.texture(v.icon, x + 11, y + 11, 48, 48, 0, 0, 1, 1, 0xFFFFFFFF);
		} else {
			Ui.icon("package", x + 25, y + 25, 20, Theme.DIM);
		}
		if (on) {
			// priority: #1 is drawn over all the others
			String r = "#" + (rank + 1);
			float rw = Ui.textWidth(Ui.BOLD, r, 8) + 8;
			Ui.round(x + 10, y + 64, rw, 14, 7, Theme.ACCENT);
			Ui.text(Ui.BOLD, r, x + 14, y + 64 + (14 - 8 * 1.21f) / 2, 8, 0xFFFFFFFF);
		}

		float tx = x + 70;
		float right = x + w - 10;
		float textW = right - Widgets.PILL_W - 8 - tx;
		Ui.text(Ui.SEMIBOLD, ellipsize(Ui.SEMIBOLD, plain(name.replaceAll("(?i)\\.zip$", "")), 11, textW), tx, y + 9, 11, Theme.TEXT);
		String desc = plain(e.getTexturePackDescription());
		List<String> lines = Ui.wrap(Ui.REGULAR, desc, 8.5f, textW);
		boolean incompatible = e.func_183027_f() != 1;
		int maxLines = incompatible ? 1 : 2;
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			String line = lines.get(i);
			if (i == maxLines - 1 && lines.size() > maxLines) {
				line = ellipsize(Ui.REGULAR, line + "…", 8.5f, textW);
			}
			Ui.text(Ui.REGULAR, line, tx, y + 26 + i * 11, 8.5f, Theme.MUTED);
		}
		if (incompatible) {
			Ui.text(Ui.MEDIUM, ellipsize(Ui.MEDIUM, t("rago.packs.incompatible", e.func_183027_f()), 8, textW), tx, y + 37, 8, 0xFFE0A030);
		}

		// the key blocks as little cubes, then the items the pack changes
		if (v != null) {
			float bx0 = tx;
			for (int i = 0; i < v.blocks.size(); i++) {
				cube(v.blocks.get(i), v.blockUv.get(i), bx0 + i * 22, y + 52, 20);
			}
			float px = tx;
			float py = y + CARD_H - 26;
			for (int i = 0; i < v.previews.size(); i++) {
				float[] uv = v.previewUv.get(i);
				Ui.round(px, py, 18, 18, 4, 0xFF0B0A0D);
				Ui.texture(v.previews.get(i), px + 1, py + 1, 16, 16, 0, 0, uv[0], uv[1], 0xFFFFFFFF);
				px += 21;
			}
		}

		// on the right: on/off, then folder and priority up and down in a column
		Widgets.pill("packpill:" + name, right - Widgets.PILL_W, y + 10, on);
		float bx = right - 22;
		String f = folders.folderOf(name);
		float fy = y + 36;
		smallButton(bx, fy, f != null ? "folder" : "folder-plus", f != null);
		if (!noClicks) {
			Ui.click(bx, fy, 22, 22, () -> {
				folderMenu = name;
				menuX = bx;
				menuY = fy + 24;
			});
		}
		if (on) {
			smallButton(bx, y + 60, "chevron-up", false);
			smallButton(bx, y + 84, "chevron-down", false);
			if (!noClicks) {
				Ui.click(bx, y + 60, 22, 22, () -> move(e, -1));
				Ui.click(bx, y + 84, 22, 22, () -> move(e, 1));
			}
		}
		if (f != null) {
			// the folder's name under the pack icon
			float fw = Math.min(50, Ui.textWidth(Ui.MEDIUM, f, 8) + 10);
			Ui.round(x + 10, y + 84, fw, 14, 7, 0x14FFFFFF);
			Ui.text(Ui.MEDIUM, ellipsize(Ui.MEDIUM, f, 8, fw - 10), x + 15, y + 84 + (14 - 8 * 1.21f) / 2, 8, Theme.MUTED);
		}
		if (!noClicks) {
			Ui.click(right - Widgets.PILL_W, y + 10, Widgets.PILL_W, Widgets.PILL_H, () -> toggle(e));
		}
	}

	/** A block drawn as an isometric cube {@code s} wide: top lit, left and right sides shaded. */
	private static void cube(ResourceLocation tex, float[] uv, float x, float y, float s) {
		float cx = x + s / 2;
		float h = s / 2;
		float q = s / 4;
		float u = uv[0];
		float v = uv[1];
		Ui.textureQuad(tex, cx - h, y + q, cx, y, cx + h, y + q, cx, y + h, 0, 0, u, v, 0xFFFFFFFF);
		Ui.textureQuad(tex, cx - h, y + q, cx, y + h, cx, y + s, cx - h, y + 3 * q, 0, 0, u, v, 0xFFC8C8C8);
		Ui.textureQuad(tex, cx, y + h, cx + h, y + q, cx + h, y + 3 * q, cx, y + s, 0, 0, u, v, 0xFF999999);
	}

	private static void smallButton(float x, float y, String icon, boolean lit) {
		boolean hover = Ui.hover(x, y, 22, 22);
		Ui.round(x, y, 22, 22, 6, hover ? 0x20FFFFFF : 0x0CFFFFFF);
		Ui.icon(icon, x + 5, y + 5, 12, lit ? Theme.ACCENT : hover ? Theme.TEXT : Theme.MUTED);
	}

	private void toggle(ResourcePackRepository.Entry e) {
		if (!active.remove(e)) {
			// a pack turned on goes on top, as in vanilla
			active.add(0, e);
		}
		changed = true;
	}

	private void move(ResourcePackRepository.Entry e, int by) {
		int i = active.indexOf(e);
		int j = i + by;
		if (i < 0 || j < 0 || j >= active.size()) {
			return;
		}
		Collections.swap(active, i, j);
		changed = true;
	}

	private void folderMenu(float w, float h) {
		String pack = folderMenu;
		Ui.click(0, 0, w, h, () -> folderMenu = null);
		List<String> items = new ArrayList<>(folders.folders());
		float mw = 170;
		float mh = 12 + (items.size() + 2) * 24;
		float x = Math.min(menuX + 22 - mw, w - mw - 8);
		float y = menuY + mh > h - 8 ? menuY - 28 - mh : menuY;
		Ui.glassBorder(x, y, mw, mh, 10, 0xFA161519, Theme.BORDER_STRONG);
		String current = folders.folderOf(pack);
		float ry = y + 6;
		ry = menuRow(x + 6, ry, mw - 12, "x", t("rago.packs.no_folder"), current == null, () -> folders.put(pack, null));
		for (String f : items) {
			ry = menuRow(x + 6, ry, mw - 12, "folder", f, f.equals(current), () -> folders.put(pack, f));
		}
		menuRow(x + 6, ry, mw - 12, "folder-plus", t("rago.packs.new_folder"), false, () -> newFolder = "");
	}

	private float menuRow(float x, float y, float w, String icon, String label, boolean selected, Runnable action) {
		boolean hover = Ui.hover(x, y, w, 22);
		Ui.round(x, y, w, 22, 6, hover ? 0x18FFFFFF : 0x00000000);
		Ui.icon(icon, x + 8, y + 5, 12, selected ? Theme.ACCENT : Theme.MUTED);
		Ui.text(Ui.MEDIUM, ellipsize(Ui.MEDIUM, label, 9.5f, w - 50), x + 28, y + (22 - 9.5f * 1.21f) / 2, 9.5f, Theme.TEXT);
		if (selected) {
			Ui.icon("check", x + w - 20, y + 5, 12, Theme.ACCENT);
		}
		Ui.click(x, y, w, 22, () -> {
			// the folder menu stays open when "new folder" was picked, to file the pack in it after
			String pack = folderMenu;
			folderMenu = null;
			action.run();
			if (newFolder != null) {
				pendingPack = pack;
			}
		});
		return y + 24;
	}

	/** The pack to put in the folder being created from its folder menu. */
	private String pendingPack;

	private void newFolderDialog(float w, float h) {
		Ui.rect(0, 0, w, h, 0x99000000);
		Ui.click(0, 0, w, h, () -> { });
		float dw = 320;
		float dh = 128;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2;
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		Ui.round(x + 20, y + 18, 28, 28, 8, Theme.ACCENT_SOFT);
		Ui.icon("folder-plus", x + 27, y + 25, 14, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.packs.new_folder"), x + 58, y + 24, 12.5f, Theme.TEXT);
		Ui.glassBorder(x + 20, y + 54, dw - 40, 26, 8, 0xFF151417, Ui.alpha(Theme.ACCENT, 0.6f));
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		Ui.text(Ui.REGULAR, newFolder.isEmpty() && !caret ? t("rago.packs.folder_name") : newFolder + (caret ? "|" : ""),
			x + 30, y + 54 + (26 - 10 * 1.21f) / 2, 10, newFolder.isEmpty() && !caret ? Theme.DIM : Theme.TEXT);
		float bw = 100;
		Widgets.button(x + dw - 20 - bw * 2 - 10, y + dh - 38, bw, 26, "x", t("gui.cancel"), false, 10);
		Ui.click(x + dw - 20 - bw * 2 - 10, y + dh - 38, bw, 26, this::cancelNewFolder);
		Widgets.button(x + dw - 20 - bw, y + dh - 38, bw, 26, "check", t("rago.packs.create"), true, 10);
		Ui.click(x + dw - 20 - bw, y + dh - 38, bw, 26, this::confirmNewFolder);
	}

	private void confirmNewFolder() {
		String name = newFolder.trim();
		if (folders.add(name) || folders.folders().contains(name)) {
			if (pendingPack != null) {
				folders.put(pendingPack, name);
			}
			newFolder = null;
			pendingPack = null;
		}
	}

	private void cancelNewFolder() {
		newFolder = null;
		pendingPack = null;
	}

	// ---- pack images ----------------------------------------------------------------------------

	/** Reads the icon and preview textures of at most two packs per frame, so opening stays instant. */
	private void loadSomeVisuals() {
		int budget = 2;
		for (ResourcePackRepository.Entry e : shown()) {
			if (budget == 0) {
				return;
			}
			if (visuals.containsKey(e.getResourcePackName())) {
				continue;
			}
			visuals.put(e.getResourcePackName(), load(e));
			budget--;
		}
	}

	private Visual load(ResourcePackRepository.Entry e) {
		Visual v = new Visual();
		IResourcePack pack = e.getResourcePack();
		String key = "rago_pack_" + Integer.toHexString(e.getResourcePackName().hashCode());
		try {
			BufferedImage icon = pack.getPackImage();
			if (icon != null) {
				v.icon = mc.getTextureManager().getDynamicTextureLocation(key, new DynamicTexture(icon));
			}
		} catch (Exception ex) {
			LOG.debug("No icon for {}", e.getResourcePackName(), ex);
		}
		IResourcePack fallback = repo.rprDefaultResourcePack;
		for (String block : BLOCKS) {
			ResourceLocation loc = new ResourceLocation("textures/blocks/" + block + ".png");
			BufferedImage img = read(pack, loc);
			if (img == null) {
				img = read(fallback, loc);
			}
			if (img != null) {
				v.blocks.add(mc.getTextureManager().getDynamicTextureLocation(key + "_b" + v.blocks.size(), new DynamicTexture(img)));
				v.blockUv.add(new float[] {1, Math.min(1f, img.getWidth() / (float) img.getHeight())});
			}
		}
		for (String path : PREVIEW) {
			if (v.previews.size() >= PREVIEW_COUNT) {
				break;
			}
			ResourceLocation loc = new ResourceLocation("textures/" + path + ".png");
			try {
				if (!pack.resourceExists(loc)) {
					continue;
				}
				BufferedImage img;
				try (InputStream in = pack.getInputStream(loc)) {
					img = ImageIO.read(in);
				}
				if (img == null || img.getWidth() == 0) {
					continue;
				}
				v.previews.add(mc.getTextureManager().getDynamicTextureLocation(key + "_" + v.previews.size(), new DynamicTexture(img)));
				// animated textures are a tall strip of square frames: show the first
				v.previewUv.add(new float[] {1, Math.min(1f, img.getWidth() / (float) img.getHeight())});
			} catch (Exception ex) {
				LOG.debug("Could not read {} from {}", loc, e.getResourcePackName(), ex);
			}
		}
		return v;
	}

	private static BufferedImage read(IResourcePack pack, ResourceLocation loc) {
		try {
			if (pack == null || !pack.resourceExists(loc)) {
				return null;
			}
			try (InputStream in = pack.getInputStream(loc)) {
				BufferedImage img = ImageIO.read(in);
				return img == null || img.getWidth() == 0 ? null : img;
			}
		} catch (Exception e) {
			LOG.debug("Could not read {}", loc, e);
			return null;
		}
	}

	private void freeVisuals() {
		for (Visual v : visuals.values()) {
			if (v.icon != null) {
				mc.getTextureManager().deleteTexture(v.icon);
			}
			for (ResourceLocation r : v.previews) {
				mc.getTextureManager().deleteTexture(r);
			}
			for (ResourceLocation r : v.blocks) {
				mc.getTextureManager().deleteTexture(r);
			}
		}
		visuals.clear();
	}

	// ---- applying -------------------------------------------------------------------------------

	/** Same steps as the vanilla screen's Done button, only when something changed. */
	private void apply() {
		if (!changed) {
			return;
		}
		changed = false;
		List<ResourcePackRepository.Entry> list = new ArrayList<>(active);
		Collections.reverse(list);
		repo.setRepositories(list);
		mc.gameSettings.resourcePacks.clear();
		mc.gameSettings.incompatibleResourcePacks.clear();
		for (ResourcePackRepository.Entry e : list) {
			mc.gameSettings.resourcePacks.add(e.getResourcePackName());
			if (e.func_183027_f() != 1) {
				mc.gameSettings.incompatibleResourcePacks.add(e.getResourcePackName());
			}
		}
		mc.gameSettings.saveOptions();
		mc.refreshResources();
	}

	private void close() {
		apply();
		mc.displayGuiScreen(parent);
	}

	private void openFolder() {
		File dir = repo.getDirResourcepacks();
		try {
			Desktop.getDesktop().open(dir);
		} catch (Exception e) {
			Sys.openURL(dir.toURI().toString());
		}
	}

	// ---- text -----------------------------------------------------------------------------------

	private static String plain(String s) {
		String p = EnumChatFormatting.getTextWithoutFormattingCodes(s == null ? "" : s);
		return p == null ? "" : p.replace('\n', ' ');
	}

	private static String ellipsize(gg.rago.gui.ui.TtfFont font, String s, float size, float maxW) {
		if (Ui.textWidth(font, s, size) <= maxW) {
			return s;
		}
		String e = s;
		while (e.length() > 1 && Ui.textWidth(font, e + "…", size) > maxW) {
			e = e.substring(0, e.length() - 1);
		}
		return e.trim() + "…";
	}

	// ---- input ----------------------------------------------------------------------------------

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
		if (d != 0 && newFolder == null && folderMenu == null) {
			scroll -= Math.signum(d) * 40;
		}
	}

	@Override
	protected void keyTyped(char c, int keyCode) {
		if (newFolder != null) {
			if (keyCode == Keyboard.KEY_ESCAPE) {
				cancelNewFolder();
			} else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
				confirmNewFolder();
			} else if (keyCode == Keyboard.KEY_BACK) {
				newFolder = newFolder.isEmpty() ? "" : newFolder.substring(0, newFolder.length() - 1);
			} else if (ChatAllowedCharacters.isAllowedCharacter(c) && newFolder.length() < 24) {
				newFolder += c;
			}
			return;
		}
		if (folderMenu != null) {
			if (keyCode == Keyboard.KEY_ESCAPE) {
				folderMenu = null;
			}
			return;
		}
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (!search.isEmpty()) {
				search = "";
			} else {
				close();
			}
		} else if (keyCode == Keyboard.KEY_BACK) {
			search = search.isEmpty() ? "" : search.substring(0, search.length() - 1);
			scroll = 0;
		} else if (ChatAllowedCharacters.isAllowedCharacter(c) && search.length() < 32) {
			search += c;
			scroll = 0;
		}
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		freeVisuals();
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
