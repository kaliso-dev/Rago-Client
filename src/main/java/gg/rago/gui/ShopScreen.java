package gg.rago.gui;

import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.rewards.Rewards;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The shop: Rago Points buy cosmetics, never the other way round (nothing here costs money). The
 * day's showcase and the week's bundle change at midnight in Paris, the commons, uncommons and rares
 * are always for sale, and the forge finishes a cosmetic whose fragments you started.
 */
public final class ShopScreen extends GuiScreen {
	private static final float TILE_H = 112;
	private static final float GAP = 10;

	private final GuiScreen parent;
	private long openedNs;
	private float scroll;
	/** The purchase waiting for a yes: an item id, "bundle", or "forge:" and an item id. */
	private String confirm;
	private volatile String message;
	private volatile boolean messageError;

	public ShopScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void initGui() {
		openedNs = System.nanoTime();
		Rewards.refresh();
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
		Ui.icon("store", x + 28, y + 28, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.shop.title"), x + 64, y + 20, 15, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.shop.subtitle"), x + 64, y + 40, 9.5f, Theme.MUTED);
		Widgets.iconButton(x + pw - 46, y + 20, 26, "x");
		Ui.click(x + pw - 46, y + 20, 26, 26, () -> mc.displayGuiScreen(parent));
		Rewards.Status s = Rewards.status();
		if (s != null) {
			RewardsScreen.pointsPill(x + pw - 56, y + 21, s.points, true);
		}

		Rewards.Shop shop = Rewards.shop();
		float top = y + 70;
		float bottom = y + ph - 46;
		if (shop == null) {
			String e = Rewards.error();
			String text = e == null || "sign_in".equals(e) ? t("rago.rewards.connecting") : t("rago.rewards.offline");
			float ty = top + (bottom - top) / 2 - 20;
			Ui.icon(e == null ? "loader-circle" : "cloud-off", x + pw / 2 - 10, ty, 20, Theme.MUTED);
			for (String line : Ui.wrap(Ui.REGULAR, text, 10, 420)) {
				ty += 14;
				Ui.textCentered(Ui.REGULAR, line, x + pw / 2, ty + 18, 10, Theme.MUTED);
			}
		} else {
			body(x + 20, top, pw - 40, bottom - top, shop);
			long left = Math.max(0, shop.resetAt - System.currentTimeMillis());
			Ui.rect(x + 20, y + ph - 40, pw - 40, 1, 0x14FFFFFF);
			Ui.icon("clock", x + 22, y + ph - 28, 11, Theme.MUTED);
			Ui.text(Ui.REGULAR, t("rago.shop.next", RewardsScreen.duration(left)), x + 38, y + ph - 28, 9.5f, Theme.MUTED);
		}
		String msg = message;
		if (msg != null) {
			float mw = Ui.textWidth(Ui.MEDIUM, msg, 9.5f) + 20;
			Ui.text(Ui.MEDIUM, msg, x + pw - 20 - mw + 20, y + ph - 28, 9.5f, messageError ? 0xFFFF8A80 : Theme.GREEN);
		}
		if (confirm != null) {
			confirmDialog(w, h, shop);
		}
		Ui.opacity(1);
		Ui.end();
	}

	private void body(float x, float y, float w, float h, Rewards.Shop shop) {
		Ui.scissor(x - 4, y - 4, w + 8, h + 8);
		float cy = y - scroll;
		cy = section(x, cy, w, "sparkles", t("rago.shop.daily"), shop.daily, shop, 4);
		if (!shop.bundle.isEmpty()) {
			cy = bundle(x, cy, w, shop);
		}
		List<String> forge = new ArrayList<>();
		for (Map.Entry<String, Integer> e : Rewards.allFragments().entrySet()) {
			if (e.getValue() > 0 && !Rewards.owns(e.getKey()) && Rewards.item(e.getKey()) != null) {
				forge.add(e.getKey());
			}
		}
		if (!forge.isEmpty()) {
			cy = forgeSection(x, cy, w, forge);
		}
		cy = section(x, cy, w, "store", t("rago.shop.permanent"), shop.permanent, shop, 5);
		Ui.endScissor();
		float total = cy + scroll - y;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - h)));
	}

	private float section(float x, float y, float w, String icon, String title, List<String> items, Rewards.Shop shop, int cols) {
		if (items.isEmpty()) {
			return y;
		}
		Ui.icon(icon, x, y + 1, 12, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, title, x + 18, y, 11, Theme.TEXT);
		y += 22;
		float tw = (w - (cols - 1) * GAP) / cols;
		for (int i = 0; i < items.size(); i++) {
			float tx = x + (i % cols) * (tw + GAP);
			float ty = y + (i / cols) * (TILE_H + GAP);
			String id = items.get(i);
			Integer price = shop.prices.get(id);
			tile(tx, ty, tw, TILE_H, id, price == null ? 0 : price, "buy:" + id);
		}
		int rows = (items.size() + cols - 1) / cols;
		return y + rows * (TILE_H + GAP) + 14;
	}

	/** One cosmetic for sale: its rarity's colour, its type, its name and price, or "owned". */
	private void tile(float x, float y, float w, float h, String id, int price, String action) {
		Rewards.Item it = Rewards.item(id);
		String rarity = it == null ? "common" : it.rarity;
		int color = Rewards.color(rarity);
		boolean owned = !action.startsWith("forge:") && Rewards.owns(id);
		float hv = Widgets.hoverAmount("shop:" + action, x, y, w, h);
		Ui.glassBorder(x, y, w, h, 10, Ui.mix(0xFF151417, 0xFF1D1C20, hv), Ui.alpha(color, 0.35f + 0.45f * hv));
		Ui.glow(x + w / 2, y + 34, 40, Ui.alpha(color, 0.10f + 0.08f * hv));
		Ui.round(x + w / 2 - 18, y + 14, 36, 36, 10, Ui.alpha(color, 0.14f));
		Ui.icon(RewardsScreen.typeIcon(id), x + w / 2 - 10, y + 22, 20, color);
		String name = Rewards.name(id);
		Ui.textCentered(Ui.SEMIBOLD, Ui.wrap(Ui.SEMIBOLD, name, 9.5f, w - 12).get(0), x + w / 2, y + 58, 9.5f, Theme.TEXT);
		Ui.textCentered(Ui.REGULAR, Rewards.rarityName(rarity) + " · " + t("rago.item_type." + RewardsScreen.typeOf(id)), x + w / 2, y + 72, 8, color);
		if (owned) {
			Ui.icon("check", x + w / 2 - 22, y + h - 24, 11, Theme.GREEN);
			Ui.text(Ui.MEDIUM, t("rago.shop.owned"), x + w / 2 - 8, y + h - 24, 9, Theme.GREEN);
			return;
		}
		String n = RewardsScreen.number(price);
		float pw = Ui.textWidth(Ui.SEMIBOLD, n, 10) + 18;
		Ui.icon("coins", x + (w - pw) / 2, y + h - 25, 11, 0xFFFFC14D);
		Ui.text(Ui.SEMIBOLD, n, x + (w - pw) / 2 + 16, y + h - 25, 10, 0xFFFFE2A8);
		if (confirm == null) {
			Ui.click(x, y, w, h, () -> {
				confirm = action;
				message = null;
			});
		}
	}

	private float bundle(float x, float y, float w, Rewards.Shop shop) {
		Ui.icon("package", x, y + 1, 12, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.shop.bundle"), x + 18, y, 11, Theme.TEXT);
		y += 22;
		float bh = 64;
		float hv = Widgets.hoverAmount("shop:bundle", x, y, w, bh);
		Ui.glassBorder(x, y, w, bh, 10, Ui.mix(0xFF151417, 0xFF1D1C20, hv), Ui.mix(0x30FFAA00, 0x80FFAA00, hv));
		float ix = x + 14;
		boolean all = true;
		int full = 0;
		for (String id : shop.bundle) {
			Rewards.Item it = Rewards.item(id);
			int color = Rewards.color(it == null ? "common" : it.rarity);
			Ui.round(ix, y + 14, 36, 36, 10, Ui.alpha(color, 0.14f));
			Ui.icon(RewardsScreen.typeIcon(id), ix + 8, y + 22, 20, color);
			ix += 44;
			if (!Rewards.owns(id)) {
				all = false;
				Integer pr = shop.prices.get(id);
				full += pr == null ? 0 : pr;
			}
		}
		StringBuilder names = new StringBuilder();
		for (String id : shop.bundle) {
			if (names.length() > 0) {
				names.append(" + ");
			}
			names.append(Rewards.name(id));
		}
		Ui.text(Ui.SEMIBOLD, names.toString(), ix + 6, y + 16, 10, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.shop.bundle_note"), ix + 6, y + 34, 8.5f, Theme.MUTED);
		float rx = x + w - 16;
		if (all) {
			String owned = t("rago.shop.owned");
			Ui.text(Ui.MEDIUM, owned, rx - Ui.textWidth(Ui.MEDIUM, owned, 9.5f), y + 26, 9.5f, Theme.GREEN);
		} else {
			String n = RewardsScreen.number(shop.bundlePrice);
			String was = RewardsScreen.number(full);
			float nw = Ui.textWidth(Ui.SEMIBOLD, n, 11);
			Ui.text(Ui.SEMIBOLD, n, rx - nw, y + 18, 11, 0xFFFFE2A8);
			Ui.icon("coins", rx - nw - 16, y + 18, 12, 0xFFFFC14D);
			float ww = Ui.textWidth(Ui.REGULAR, was, 8.5f);
			Ui.text(Ui.REGULAR, was, rx - ww, y + 36, 8.5f, 0xFF7C7A82);
			Ui.rect(rx - ww, y + 41, ww, 1, 0xFF7C7A82);
			if (confirm == null) {
				Ui.click(x, y, w, bh, () -> {
					confirm = "bundle";
					message = null;
				});
			}
		}
		return y + bh + 24;
	}

	private float forgeSection(float x, float y, float w, List<String> items) {
		Ui.icon("hammer", x, y + 1, 12, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.shop.forge"), x + 18, y, 11, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.shop.forge_note"), x + 18 + Ui.textWidth(Ui.SEMIBOLD, t("rago.shop.forge"), 11) + 10, y + 2, 8.5f, Theme.MUTED);
		y += 22;
		int cols = 4;
		float tw = (w - (cols - 1) * GAP) / cols;
		for (int i = 0; i < items.size(); i++) {
			String id = items.get(i);
			float tx = x + (i % cols) * (tw + GAP);
			float ty = y + (i / cols) * (TILE_H + GAP);
			int have = Rewards.fragments(id);
			int need = pieces(id);
			tile(tx, ty, tw, TILE_H, id, forgeCost(id, have), "forge:" + id);
			Rewards.Item it = Rewards.item(id);
			int color = Rewards.color(it == null ? "common" : it.rarity);
			Ui.round(tx + 14, ty + 84, tw - 28, 4, 2, 0x22FFFFFF);
			Ui.round(tx + 14, ty + 84, (tw - 28) * Math.min(1, have / (float) Math.max(1, need)), 4, 2, color);
		}
		int rows = (items.size() + cols - 1) / cols;
		return y + rows * (TILE_H + GAP) + 14;
	}

	/** Fragments a cosmetic is made of, as the server counts them. */
	static int pieces(String id) {
		Rewards.Item it = Rewards.item(id);
		switch (it == null ? "" : it.rarity) {
			case "common":
				return 3;
			case "uncommon":
				return 4;
			case "rare":
				return 5;
			case "epic":
				return 6;
			default:
				return 0;
		}
	}

	static int price(String rarity) {
		switch (rarity) {
			case "common":
				return 120;
			case "uncommon":
				return 300;
			case "rare":
				return 800;
			case "epic":
				return 2000;
			case "legendary":
				return 4000;
			default:
				return 0;
		}
	}

	/** What the forge asks, as the server works it out: each missing fragment at its share of the price. */
	static int forgeCost(String id, int have) {
		Rewards.Item it = Rewards.item(id);
		int need = pieces(id);
		if (it == null || need == 0) {
			return 0;
		}
		int share = (price(it.rarity) + need - 1) / need;
		return Math.max(0, need - have) * share;
	}

	private void confirmDialog(float w, float h, Rewards.Shop shop) {
		Ui.rect(0, 0, w, h, 0x99000000);
		Ui.click(0, 0, w, h, () -> { });
		float dw = 360;
		float dh = 132;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2;
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		String c = confirm;
		String title;
		int cost;
		String item = null;
		if ("bundle".equals(c)) {
			title = t("rago.shop.confirm_bundle");
			cost = shop == null ? 0 : shop.bundlePrice;
		} else if (c.startsWith("forge:")) {
			item = c.substring(6);
			title = t("rago.shop.confirm_forge", Rewards.name(item));
			cost = forgeCost(item, Rewards.fragments(item));
		} else {
			item = c.substring(4);
			title = t("rago.shop.confirm_buy", Rewards.name(item));
			Integer pr = shop == null ? null : shop.prices.get(item);
			cost = pr == null ? 0 : pr;
		}
		Rewards.Status s = Rewards.status();
		int points = s == null ? 0 : s.points;
		Ui.text(Ui.SEMIBOLD, title, x + 20, y + 20, 12.5f, Theme.TEXT);
		String line = points >= cost ? t("rago.shop.confirm_cost", cost, points - cost) : t("rago.shop.missing", cost - points);
		Ui.text(Ui.REGULAR, line, x + 20, y + 44, 9.5f, points >= cost ? Theme.MUTED : 0xFFFF8A80);
		float bw = 110;
		Widgets.button(x + dw - 20 - bw * 2 - 10, y + dh - 44, bw, 26, "x", t("gui.cancel"), false, 10);
		Ui.click(x + dw - 20 - bw * 2 - 10, y + dh - 44, bw, 26, () -> confirm = null);
		boolean can = points >= cost && !Rewards.busy();
		Widgets.button(x + dw - 20 - bw, y + dh - 44, bw, 26, Rewards.busy() ? "loader-circle" : "coins", t("rago.shop.confirm_go"), can, 10);
		if (can) {
			String target = item;
			boolean forge = c.startsWith("forge:");
			Ui.click(x + dw - 20 - bw, y + dh - 44, bw, 26, () -> {
				confirm = null;
				java.util.function.Consumer<String> done = e -> {
					messageError = e != null;
					message = e == null ? t("rago.shop.bought", target == null ? t("rago.shop.bundle") : Rewards.name(target))
						: t("rago.shop.error." + (e.matches("[a-z_]+") ? e : "other"));
					Rewards.refresh();
				};
				if (forge) {
					Rewards.forge(target, done);
				} else {
					Rewards.buy(target, done);
				}
			});
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button != 0) {
			return;
		}
		Ui.updateMouse();
		if (Ui.dispatchClick()) {
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
	protected void keyTyped(char typedChar, int keyCode) {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (confirm != null) {
				confirm = null;
			} else {
				mc.displayGuiScreen(parent);
			}
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
