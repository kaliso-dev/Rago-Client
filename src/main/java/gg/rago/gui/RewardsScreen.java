package gg.rago.gui;

import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.hooks.Hooks;
import gg.rago.rewards.Rewards;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;

/**
 * The daily chest: a Minecraft chest (drawn with the game's own chest texture) that shakes and opens
 * on three cards face down, each already in the colour of its rarity; a click turns one over. The
 * server draws the cards; this screen only shows them. Once opened, the day's cards stay here until
 * the next chest, with the time left before it.
 */
public final class RewardsScreen extends GuiScreen {
	private static final ResourceLocation CHEST = new ResourceLocation("textures/entity/chest/normal.png");
	private static final float CARD_W = 118;
	private static final float CARD_H = 164;
	/** The chest's width on screen. */
	private static final float CHEST_SIZE = 92;
	private static final long SHAKE_MS = 650;
	private static final long LID_MS = 450;
	private static final long RISE_MS = 420;
	private static final long FLIP_MS = 380;

	private final GuiScreen parent;
	private final ModelChest model = new ModelChest();
	private long openedNs;
	/** When "open" was clicked (ms), -1 before. */
	private long openClicked = -1;
	/** When the lid started to open: the answer had come by then. */
	private long lidAt = -1;
	private volatile Rewards.Chest drawn;
	private volatile boolean failed;
	private long[] flipAt = new long[0];
	private boolean showOdds;
	private long nextRefresh;
	private boolean soundPlayed;

	public RewardsScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void initGui() {
		openedNs = System.nanoTime();
		Rewards.refresh();
		nextRefresh = System.currentTimeMillis() + 5000;
	}

	@Override
	public void updateScreen() {
		long now = System.currentTimeMillis();
		Rewards.Status s = Rewards.status();
		// while the server wakes up, or once the next chest has come, ask again
		if (now >= nextRefresh && openClicked < 0 && (s == null || s.opened && now >= s.resetAt)) {
			Rewards.refresh();
			nextRefresh = now + 8000;
		}
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
		float pw = Math.min(640, w - 40);
		float ph = Math.min(430, h - 40);
		float x = (w - pw) / 2;
		float y = (h - ph) / 2 + (1 - p) * 12;
		Ui.glassBorder(x, y, pw, ph, 14, 0xF2121114, Theme.BORDER_STRONG);

		Rewards.Status s = Rewards.status();
		header(x, y, pw, s);
		float top = y + 70;
		float bottom = y + ph - 54;
		// a chest just drawn stays shut, shaking, until closed() opens its lid
		Rewards.Chest shown = drawn != null && lidAt > 0 ? drawn : s != null && s.opened && openClicked < 0 ? s.chest : null;
		if (s == null && openClicked < 0) {
			waiting(x, top, pw, bottom - top);
		} else if (shown == null) {
			closed(x, top, pw, bottom - top, s, partialTicks);
		} else {
			if (openClicked < 0) {
				// opened earlier today: the cards are shown face up
				lidAt = 0;
				ensureFlips(shown.cards.size(), true);
			}
			opened(x, top, pw, bottom - top, shown, partialTicks);
		}
		footer(x, y + ph - 46, pw, s);
		if (showOdds) {
			odds(w, h);
		}
		Ui.opacity(1);
		Ui.end();
	}

	// ---- parts ----------------------------------------------------------------------------------

	private void header(float x, float y, float w, Rewards.Status s) {
		Ui.round(x + 20, y + 20, 32, 32, 9, Theme.ACCENT_SOFT);
		Ui.icon("gift", x + 28, y + 28, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.rewards.title"), x + 64, y + 20, 15, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.rewards.subtitle"), x + 64, y + 40, 9.5f, Theme.MUTED);
		Widgets.iconButton(x + w - 46, y + 20, 26, "x");
		Ui.click(x + w - 46, y + 20, 26, 26, () -> mc.displayGuiScreen(parent));
		if (s != null) {
			pointsPill(x + w - 56, y + 21, s.points, true);
		}
	}

	/** The player's points, right-aligned at {@code right}. */
	static float pointsPill(float right, float y, int points, boolean alignRight) {
		String n = String.format("%,d", points).replace(',', ' ');
		float tw = Ui.textWidth(Ui.SEMIBOLD, n, 11) + 40;
		float x = alignRight ? right - tw : right;
		Ui.glassBorder(x, y, tw, 24, 12, 0xFF1A1714, 0x40FFAA00);
		Ui.icon("coins", x + 9, y + 6, 12, 0xFFFFC14D);
		Ui.text(Ui.SEMIBOLD, n, x + 27, y + (24 - 11 * 1.21f) / 2, 11, 0xFFFFE2A8);
		return tw;
	}

	private void waiting(float x, float y, float w, float h) {
		String e = Rewards.error();
		float cy = y + h / 2 - 30;
		if (Hooks.network == null || !Hooks.network.isEnabled()) {
			message(x, cy, w, "cloud-off", t("rago.rewards.off"), false);
			return;
		}
		if (e == null || "sign_in".equals(e)) {
			Ui.icon("loader-circle", x + w / 2 - 10, cy, 20, Theme.MUTED);
			Ui.textCentered(Ui.REGULAR, t("rago.rewards.connecting"), x + w / 2, cy + 32, 10, Theme.MUTED);
			return;
		}
		message(x, cy, w, "cloud-off", t("rago.rewards.offline"), true);
	}

	private void message(float x, float y, float w, String icon, String text, boolean retry) {
		Ui.icon(icon, x + w / 2 - 10, y, 20, Theme.MUTED);
		float ty = y + 32;
		for (String line : Ui.wrap(Ui.REGULAR, text, 10, Math.min(420, w - 60))) {
			Ui.textCentered(Ui.REGULAR, line, x + w / 2, ty, 10, Theme.MUTED);
			ty += 14;
		}
		if (retry) {
			float bw = Ui.textWidth(Ui.SEMIBOLD, t("rago.rewards.retry"), 10) + 44;
			Widgets.button(x + (w - bw) / 2, ty + 10, bw, 28, "refresh-cw", t("rago.rewards.retry"), false, 10);
			Ui.click(x + (w - bw) / 2, ty + 10, bw, 28, Rewards::refresh);
		}
	}

	private void closed(float x, float y, float w, float h, Rewards.Status s, float partialTicks) {
		long now = System.currentTimeMillis();
		float cx = x + w / 2;
		float cy = y + h * 0.42f;
		float shake = 0;
		float lid = 0;
		if (openClicked >= 0) {
			long t = now - openClicked;
			// shakes harder and harder until the answer is there, then the lid opens
			float k = Math.min(1, t / (float) SHAKE_MS);
			shake = (float) Math.sin(t / 32.0) * (2 + 5 * k);
			if (drawn != null && t >= SHAKE_MS) {
				if (lidAt < 0) {
					lidAt = now;
					ensureFlips(drawn.cards.size(), false);
				}
			}
			if (failed) {
				openClicked = -1;
			}
		} else {
			// waiting to be opened: a slow bob
			shake = 0;
		}
		Ui.glow(cx, cy, 150, Ui.alpha(0xFFFFAA00, openClicked >= 0 ? 0.22f : 0.12f));
		float bob = openClicked >= 0 ? 0 : (float) Math.sin(now / 520.0) * 3;
		drawChest(cx + shake, cy + 40 + bob, CHEST_SIZE, lid, partialTicks);

		if (s != null) {
			streak(x, y + h - 86, w, s);
		}
		float bw = 200;
		boolean busy = openClicked >= 0;
		Widgets.button(cx - bw / 2, y + h - 34, bw, 32, busy ? "loader-circle" : "gift",
			busy ? t("rago.rewards.opening") : t("rago.rewards.open"), true, 11);
		if (!busy && s != null && !s.opened) {
			Ui.click(cx - bw / 2, y + h - 34, bw, 32, this::open);
		}
		String e = Rewards.error();
		if (failed && e != null) {
			Ui.textCentered(Ui.REGULAR, t("offline".equals(e) || "sign_in".equals(e) ? "rago.rewards.offline_short" : "rago.rewards.error", e),
				cx, y + h - 50 - 26, 9, 0xFFFF8A80);
		}
	}

	private void opened(float x, float y, float w, float h, Rewards.Chest chest, float partialTicks) {
		long now = System.currentTimeMillis();
		float cx = x + w / 2;
		float rise = lidAt <= 0 ? 1 : Math.min(1, (now - lidAt - LID_MS * 0.6f) / RISE_MS);
		rise = Math.max(0, rise);
		float lid = lidAt <= 0 ? 1 : Math.min(1, (now - lidAt) / (float) LID_MS);
		if (lidAt > 0 && rise < 1) {
			drawChest(cx, y + h * 0.42f + 40, CHEST_SIZE, lid, partialTicks);
		}
		List<Rewards.Card> cards = chest.cards;
		int n = cards.size();
		ensureFlips(n, lidAt <= 0);
		float gap = 16;
		float cw = Math.min(CARD_W, (w - 60 - (n - 1) * gap) / n);
		float ch = cw * CARD_H / CARD_W;
		float total = n * cw + (n - 1) * gap;
		float ease = 1 - (1 - rise) * (1 - rise) * (1 - rise);
		boolean allUp = true;
		for (int i = 0; i < n; i++) {
			float tx = x + (w - total) / 2 + i * (cw + gap);
			float ty = y + 8;
			float sx = cx - cw / 2;
			float sy = y + h * 0.42f;
			float px = sx + (tx - sx) * ease;
			float py = sy + (ty - sy) * ease;
			float scale = 0.4f + 0.6f * ease;
			float flip = flipAt[i] < 0 ? 0 : Math.min(1, (now - flipAt[i]) / (float) FLIP_MS);
			if (flip < 1) {
				allUp = false;
			}
			Rewards.Card c = cards.get(i);
			if (rise > 0) {
				card(px + cw * (1 - scale) / 2, py, cw * scale, ch * scale, c, flip, i);
			}
			if (rise >= 1 && flipAt[i] < 0) {
				int index = i;
				Ui.click(tx, ty, cw, ch, () -> flip(index));
			}
		}
		float by = y + 8 + ch + 18;
		if (rise >= 1 && !allUp) {
			String label = t("rago.rewards.reveal_all");
			float bw = Ui.textWidth(Ui.SEMIBOLD, label, 10) + 44;
			Ui.textCentered(Ui.REGULAR, t("rago.rewards.click_card"), cx, by, 9.5f, Theme.MUTED);
			Widgets.button(cx - bw / 2, by + 18, bw, 28, "sparkles", label, false, 10);
			Ui.click(cx - bw / 2, by + 18, bw, 28, this::flipAll);
		} else if (allUp) {
			StringBuilder line = new StringBuilder(t("rago.rewards.won", chest.won));
			Ui.textCentered(Ui.SEMIBOLD, line.toString(), cx, by, 11, 0xFFFFE2A8);
			if (!chest.gifts.isEmpty()) {
				StringBuilder g = new StringBuilder();
				for (String gift : chest.gifts) {
					if (g.length() > 0) {
						g.append(", ");
					}
					g.append(Rewards.name(gift));
				}
				Ui.textCentered(Ui.MEDIUM, t("rago.rewards.gifts", g.toString()), cx, by + 18, 9.5f, 0xFFFFC14D);
			}
			Rewards.Status s = Rewards.status();
			if (s != null) {
				streak(x, by + 38, w, s);
			}
		}
	}

	/** The streak line and what the next days bring. */
	private void streak(float x, float y, float w, Rewards.Status s) {
		int days = s.opened ? s.streak : s.todayStreak;
		String label = days <= 1 ? t("rago.rewards.streak_one") : t("rago.rewards.streak", days);
		float lw = Ui.textWidth(Ui.SEMIBOLD, label, 10.5f) + 22;
		float cx = x + w / 2;
		Ui.icon("flame", cx - lw / 2, y, 13, 0xFFFF8A3D);
		Ui.text(Ui.SEMIBOLD, label, cx - lw / 2 + 20, y, 10.5f, Theme.TEXT);
		// the week's seven days, today's included
		int inWeek = days <= 0 ? 0 : (days - 1) % 7 + 1;
		float dw = 16;
		float dx = cx - (7 * dw + 6 * 5) / 2;
		for (int i = 0; i < 7; i++) {
			boolean done = i < inWeek;
			Ui.round(dx + i * (dw + 5), y + 18, dw, 5, 2.5f, done ? 0xFFFF8A3D : 0x22FFFFFF);
		}
		String hint = s.epicIn > 0 && s.epicIn <= 10 ? t("rago.rewards.pity", s.epicIn) : t("rago.rewards.streak_hint");
		Ui.textCentered(Ui.REGULAR, hint, cx, y + 30, 8.5f, 0xFF7C7A82);
	}

	private void footer(float x, float y, float w, Rewards.Status s) {
		Ui.rect(x + 20, y - 8, w - 40, 1, 0x14FFFFFF);
		if (s != null) {
			long left = Math.max(0, s.resetAt - System.currentTimeMillis());
			Ui.icon("clock", x + 22, y + 9, 11, Theme.MUTED);
			Ui.text(Ui.REGULAR, t("rago.rewards.next", duration(left)), x + 38, y + 9, 9.5f, Theme.MUTED);
		}
		float bx = x + w - 20;
		String[] labels = {t("rago.rewards.shop"), t("rago.rewards.locker"), t("rago.dock.pass"), t("rago.rewards.odds")};
		String[] icons = {"store", "shirt", "ticket", "percent"};
		for (int i = 0; i < labels.length; i++) {
			float bw = Ui.textWidth(Ui.SEMIBOLD, labels[i], 9.5f) + 40;
			bx -= bw;
			Widgets.button(bx, y + 3, bw, 26, icons[i], labels[i], false, 9.5f);
			int which = i;
			Ui.click(bx, y + 3, bw, 26, () -> {
				if (which == 0) {
					mc.displayGuiScreen(new ShopScreen(this));
				} else if (which == 1) {
					mc.displayGuiScreen(new LockerScreen(this));
				} else if (which == 2) {
					mc.displayGuiScreen(new PassScreen(this));
				} else {
					showOdds = true;
				}
			});
			bx -= 8;
		}
	}

	private void odds(float w, float h) {
		Ui.rect(0, 0, w, h, 0x99000000);
		Ui.click(0, 0, w, h, () -> showOdds = false);
		float dw = 300;
		float dh = 70 + Rewards.RARITIES.length * 22;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2;
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		Ui.text(Ui.SEMIBOLD, t("rago.rewards.odds_title"), x + 20, y + 18, 12, Theme.TEXT);
		float ry = y + 46;
		for (String r : Rewards.RARITIES) {
			int odds = Rewards.odds(r);
			Ui.circle(x + 26, ry + 6, 4, Rewards.color(r));
			Ui.text(Ui.MEDIUM, Rewards.rarityName(r), x + 38, ry, 10, Rewards.color(r));
			String pct = odds <= 0 ? "-" : (odds % 100 == 0 ? String.valueOf(odds / 100) : String.valueOf(odds / 100.0).replace('.', ',')) + " %";
			float pw = Ui.textWidth(Ui.SEMIBOLD, pct, 10);
			Ui.text(Ui.SEMIBOLD, pct, x + dw - 20 - pw, ry, 10, Theme.TEXT);
			ry += 22;
		}
		Ui.textCentered(Ui.REGULAR, t("rago.rewards.odds_note"), x + dw / 2, ry + 4, 8.5f, 0xFF7C7A82);
	}

	// ---- a card ---------------------------------------------------------------------------------

	private void card(float x, float y, float w, float h, Rewards.Card c, float flip, int index) {
		// turning over: narrows to nothing, then widens with the other side
		float k = (float) Math.abs(Math.cos(Math.PI * flip));
		boolean front = flip >= 0.5f;
		float cw = Math.max(1, w * k);
		float cx = x + (w - cw) / 2;
		int color = Rewards.color(c.rarity);
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0 + index);
		Ui.glow(x + w / 2, y + h / 2, w * 0.9f, Ui.alpha(color, (front ? 0.18f : 0.10f + 0.10f * pulse) * Math.max(0.3f, k)));
		if (!front) {
			Ui.glassBorder(cx, y, cw, h, 10, Ui.mix(0xFF141316, color, 0.10f), Ui.alpha(color, 0.75f));
			if (k > 0.3f) {
				Ui.round(cx + cw / 2 - 18 * k, y + h / 2 - 18, 36 * k, 36, 10, Ui.alpha(color, 0.18f));
				Ui.icon("sparkles", cx + cw / 2 - 9 * k, y + h / 2 - 9, 18 * k, Ui.alpha(color, 0.9f));
				boolean hover = Ui.hover(x, y, w, h) && flipAt[index] < 0;
				if (hover) {
					Ui.outline(cx, y, cw, h, 10, Ui.alpha(color, 0.9f));
				}
			}
			return;
		}
		Ui.glassBorder(cx, y, cw, h, 10, 0xFF151417, Ui.alpha(color, 0.9f));
		if (k < 0.35f) {
			return;
		}
		Ui.opacity(Ui.opacity() * Math.min(1, (k - 0.35f) / 0.4f));
		float mid = x + w / 2;
		String rarity = Rewards.rarityName(c.rarity).toUpperCase(java.util.Locale.ROOT);
		Ui.textCentered(Ui.BOLD, rarity, mid, y + 10, 8, color);
		String icon;
		String title;
		String sub;
		int subColor = Theme.MUTED;
		switch (c.kind) {
			case "item":
				icon = typeIcon(c.item);
				title = Rewards.name(c.item);
				sub = t("rago.rewards.new");
				subColor = Theme.GREEN;
				break;
			case "duplicate":
				icon = typeIcon(c.item);
				title = Rewards.name(c.item);
				sub = t("rago.rewards.duplicate", c.points);
				break;
			case "fragment":
				icon = "puzzle";
				title = Rewards.name(c.item);
				sub = c.completed ? t("rago.rewards.completed") : t("rago.rewards.fragment", c.have, c.need);
				subColor = c.completed ? Theme.GREEN : Theme.MUTED;
				break;
			default:
				icon = "coins";
				title = "+" + c.points;
				sub = t("rago.rewards.points");
				break;
		}
		float iy = y + h * 0.26f;
		Ui.round(mid - 22, iy, 44, 44, 12, Ui.alpha(color, 0.14f));
		Ui.icon(icon, mid - 12, iy + 10, 24, "coins".equals(icon) ? 0xFFFFC14D : color);
		float ty = iy + 56;
		float size = "points".equals(c.kind) ? 15 : 10.5f;
		for (String line : Ui.wrap(Ui.SEMIBOLD, title, size, w - 16)) {
			Ui.textCentered(Ui.SEMIBOLD, line, mid, ty, size, Theme.TEXT);
			ty += size * 1.3f;
		}
		if (!"points".equals(c.kind) && c.item != null) {
			Ui.textCentered(Ui.REGULAR, t("rago.item_type." + typeOf(c.item)), mid, ty, 8, 0xFF7C7A82);
			ty += 13;
		}
		for (String line : Ui.wrap(Ui.MEDIUM, sub, 8.5f, w - 14)) {
			Ui.textCentered(Ui.MEDIUM, line, mid, ty + 2, 8.5f, subColor);
			ty += 12;
		}
		if ("fragment".equals(c.kind) && c.need > 0) {
			float bw = w - 36;
			Ui.round(x + 18, y + h - 18, bw, 5, 2.5f, 0x22FFFFFF);
			Ui.round(x + 18, y + h - 18, bw * Math.min(1, c.have / (float) c.need), 5, 2.5f, color);
		}
		Ui.opacity(1);
	}

	static String typeOf(String item) {
		int colon = item.indexOf(':');
		return colon < 0 ? "" : item.substring(0, colon);
	}

	static String typeIcon(String item) {
		switch (typeOf(item)) {
			case "cape":
				return "flag";
			case "wings":
				return "wind";
			case "hat":
				return "crown";
			case "shield":
				return "shield";
			case "dance":
				return "music-2";
			case "name":
				return "type-outline";
			case "icon":
				return "smile";
			case "title":
				return "tag";
			case "card":
				return "image";
			case "badge":
				return "badge-check";
			default:
				return "sparkles";
		}
	}

	// ---- the chest ------------------------------------------------------------------------------

	/** The game's chest, seen from the front and a little above, its lid opened by {@code lid} (0 to 1). */
	private void drawChest(float cx, float bottomY, float size, float lid, float partialTicks) {
		float s = Ui.scale();
		float ease = 1 - (1 - lid) * (1 - lid);
		model.chestLid.rotateAngleX = -(ease * (float) Math.PI / 2.2f);
		model.chestKnob.rotateAngleX = model.chestLid.rotateAngleX;
		GlStateManager.pushMatrix();
		GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
		GlStateManager.enableRescaleNormal();
		GlStateManager.color(1, 1, 1, Ui.opacity());
		GlStateManager.translate(cx * s, bottomY * s, 150);
		// the model is in blocks (its box 14/16 of one wide): size is the chest's width on screen
		float k = size * s * 16 / 14f;
		// y up from here, as for items drawn in menus; seen a little from above and from the side
		GlStateManager.scale(k, -k, k);
		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.rotate(22, 1, 0, 0);
		GlStateManager.rotate(-28, 0, 1, 0);
		mc.getTextureManager().bindTexture(CHEST);
		// the model hangs down from y = 0 with its front at z = 1/16, as the game draws a chest block:
		// turned the right way up, its base centred on the origin and its front toward the player
		GlStateManager.translate(-0.5f, 0, -0.5f);
		GlStateManager.scale(1, -1, -1);
		GlStateManager.translate(0, -1, -1);
		try {
			model.renderAll();
		} catch (RuntimeException ignored) {
			// no chest texture: the cards still come
		}
		GlStateManager.popMatrix();
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableRescaleNormal();
		GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.disableTexture2D();
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.enableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.disableCull();
		GlStateManager.disableAlpha();
		GlStateManager.color(1, 1, 1, 1);
	}

	// ---- actions --------------------------------------------------------------------------------

	private void open() {
		openClicked = System.currentTimeMillis();
		lidAt = -1;
		failed = false;
		drawn = null;
		soundPlayed = false;
		Rewards.open(chest -> {
			if (chest == null) {
				failed = true;
			} else {
				drawn = chest;
			}
		});
	}

	private void ensureFlips(int n, boolean up) {
		if (flipAt.length != n) {
			flipAt = new long[n];
			java.util.Arrays.fill(flipAt, up ? 1 : -1);
		}
	}

	private void flip(int i) {
		if (i < flipAt.length && flipAt[i] < 0) {
			flipAt[i] = System.currentTimeMillis();
			Rewards.Chest c = drawn;
			if (c != null && i < c.cards.size() && Rewards.rank(c.cards.get(i).rarity) >= 4) {
				mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("random.levelup"), 1.0F));
			}
		}
	}

	private void flipAll() {
		long now = System.currentTimeMillis();
		for (int i = 0; i < flipAt.length; i++) {
			if (flipAt[i] < 0) {
				flipAt[i] = now + i * 160L;
			}
		}
	}

	static String duration(long ms) {
		long minutes = (ms + 59_999) / 60_000;
		long hours = minutes / 60;
		minutes %= 60;
		return hours > 0 ? I18n.format("rago.rewards.hours", hours, minutes) : I18n.format("rago.rewards.minutes", minutes);
	}

	// ---- input ----------------------------------------------------------------------------------

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
	protected void keyTyped(char typedChar, int keyCode) {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (showOdds) {
				showOdds = false;
			} else {
				mc.displayGuiScreen(parent);
			}
		} else if ((keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN) && !showOdds) {
			Rewards.Status s = Rewards.status();
			if (drawn != null) {
				flipAll();
			} else if (s != null && !s.opened && openClicked < 0) {
				open();
			}
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
