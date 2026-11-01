package gg.rago.gui;

import gg.rago.accounts.Accounts;
import gg.rago.accounts.Skins;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.optimize.OptiFineFields;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Rago's home screen, over the menu video. The modern layout: a greeting, Hypixel and Minemen
 * (icon, players online, ping) to join in one click, singleplayer and multiplayer, the player in
 * 3D with their cape and cosmetics (a click opens the locker) and what is new in this version.
 * The classic layout (a setting): the emblem in the middle over three buttons. Both have the dock
 * of shortcuts, the account and the Rago Optimizer card.
 * Shown instead of the vanilla title screen unless the player picks the vanilla menu.
 */
public final class RagoTitleScreen extends GuiScreen {
	private static final String DISCORD_URL = "https://discord.gg/ragooptimizer";
	private static final String OPTIFINE_URL = "https://optifine.net/downloads";
	private static final ResourceLocation PROMO = new ResourceLocation("rago", "textures/promo/optimizer.png");
	/** The intro plays once per game start, not every time the player comes back here. */
	private static boolean introPlayed;
	/** The Rago Optimizer card was closed with its cross: hidden until the game starts again. */
	private static boolean promoClosed;

	private long openedNs;
	private boolean optifineHelp;
	private long helpOpenedNs;
	private PlayerPreview preview;

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void initGui() {
		openedNs = introPlayed ? System.nanoTime() - 5_000_000_000L : System.nanoTime();
		introPlayed = true;
		leaving = null;
		if (!RagoClient.classicHome()) {
			fastPlay().refresh();
			if (preview == null) {
				preview = PlayerPreview.create();
			}
		}
	}

	@Override
	public void updateScreen() {
		if (!RagoClient.classicHome()) {
			ServerStatus.tick();
		}
		if (preview != null) {
			preview.tick();
		}
	}

	@Override
	public void onGuiClosed() {
		ServerStatus.cancel();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		int layout = RagoClient.homeLayout();
		float out = 0;
		if (layout == RagoClient.HOME_CLASSIC) {
			Ui.backdrop(0.3f);
			Ui.gradient(0, h * 0.5f, w, h * 0.5f, 0x00050406, 0xB0050406);
			Ui.captureGlass();
			center(w, h);
		} else {
			out = leaving != null ? Anim.progress(leavingNs, LEAVE_MS) : 0;
			modern(w, h, partialTicks, layout == RagoClient.HOME_CENTRED, out);
		}
		dock(w, h);
		promo(w, h);
		footer(w, h);
		account(w);
		if (optifineHelp) {
			optifineDialog(w, h);
		}
		Ui.popTransform();
		if (out > 0) {
			// leaving: the screen sinks into the dark before the next one opens
			Ui.opacity(1);
			Ui.rect(0, 0, w, h, Ui.alpha(0xFF050406, out * 0.9f));
		}
		Ui.end();
		if (leaving != null && out >= 1) {
			Runnable next = leaving;
			leaving = null;
			next.run();
		}
	}

	/** Eased 0..1 progress of the intro, delayed by {@code delayMs}. */
	private float intro(float delayMs, float ms) {
		return Anim.progress(openedNs + (long) (delayMs * 1_000_000L), ms);
	}

	// ---- modern layouts -------------------------------------------------------------------------

	/** How long the screen takes to fade out before the next one opens. */
	private static final float LEAVE_MS = 170;
	/** What opens once the screen has faded out, or null. */
	private Runnable leaving;
	private long leavingNs;

	/** Opens the next screen after a short fade (at once without menu animations). */
	private void go(Runnable next) {
		if (leaving != null) {
			return;
		}
		if (!Anim.enabled()) {
			next.run();
			return;
		}
		leaving = next;
		leavingNs = System.nanoTime();
	}

	/** The server Fast Play joins: the one played the most, Hypixel until then. */
	private static ServerStatus fastPlay() {
		String address = gg.rago.core.ServerPlaytime.favourite();
		return ServerStatus.of(address, gg.rago.core.ServerPlaytime.name(address));
	}

	/**
	 * The centred layout (the buttons in the middle, the player on the left) and the left one (the
	 * buttons on the left, the player in the middle).
	 */
	private void modern(float w, float h, float partialTicks, boolean centred, float out) {
		// the background follows the mouse a little (parallax), lighter than the classic layout's,
		// darker behind the text and at the bottom behind the dock, blurred as the player chose
		boolean moving = Anim.enabled();
		float px = Anim.to("home:px", moving ? Ui.mouseX() / w - 0.5f : 0, 900);
		float py = Anim.to("home:py", moving ? Ui.mouseY() / h - 0.5f : 0, 900);
		Ui.backdrop(0.1f, -px * 24, -py * 14, 1.06f);
		if (!centred) {
			Ui.hgradient(0, 0, w * 0.62f, h, 0xD0070609, 0x00070609);
		}
		Ui.gradient(0, h * 0.55f, w, h * 0.45f, 0x00050406, 0xA8050406);
		Ui.captureGlass();
		Ui.blurScreen(RagoClient.homeBlur());
		if (centred) {
			Ui.glow(w / 2, h * 0.47f, 330, 0x99050406);
		}
		if (out > 0) {
			Ui.pushTransform(w / 2, h / 2, 1 + 0.035f * out, 0, 0);
		}

		float left = Math.max(36, Math.round(w * 0.056f));
		brand(left);
		float colW = 360;
		float top = Math.max(90, Math.round(h * 0.5f - 150));
		if (centred) {
			player(Math.round(Math.max(left + 90, w * 0.2f)), Math.round(h * 0.74f), Math.min(h * 0.19f, 150), px, partialTicks);
			hero(w / 2 - colW / 2, top, colW, true);
		} else {
			player(Math.round(w * 0.5f), Math.round(h * 0.79f), Math.min(h * 0.2f, 160), px, partialTicks);
			hero(left, top, colW, false);
		}
		news(left, h);
	}

	/** The logo, the name and the version, top left. */
	private void brand(float x) {
		Ui.opacity(intro(0, 500));
		Ui.logoGlow(x, 17, 24, Ui.alpha(Theme.ACCENT, 0.35f));
		Ui.logo(x, 17, 24, Theme.ACCENT);
		float size = 15;
		float tx = x + 33;
		float ty = 29 - size * 1.21f / 2;
		tx += Ui.text(Ui.SEMIBOLD, "Rago ", tx, ty, size, Theme.TEXT);
		tx += Ui.text(Ui.REGULAR, "Client", tx, ty, size, 0xFFB4B2B9);
		String version = RagoClient.version().replace("+1.8.9", "");
		float vs = 8.5f;
		float vw = Ui.textWidth(Ui.MEDIUM, version, vs) + 14;
		Ui.round(tx + 9, 21, vw, 16, 8, Theme.ACCENT_SOFT);
		Ui.text(Ui.MEDIUM, version, tx + 16, 29 - vs * 1.21f / 2, vs, Theme.ACCENT);
		Ui.opacity(1);
	}

	/** The greeting, Fast Play, singleplayer and multiplayer, in a column at {@code x}. */
	private void hero(float x, float top, float colW, boolean centred) {
		String name = mc.getSession().getUsername();
		float cx = x + colW / 2;

		slide(60, centred);
		label(Ui.MEDIUM, t("rago.home.welcome"), x, cx, top, 13, 0xFFB4B2B9, centred);
		slide(110, centred);
		label(Ui.BOLD, name, x, cx, top + 17, 36, Theme.TEXT, centred);
		slide(160, centred);
		label(Ui.REGULAR, t("rago.home.tagline"), x, cx, top + 64, 11, 0xFF9A98A0, centred);

		slide(230, centred);
		fastPlay(fastPlay(), x + slideX, top + 100 + slideY, colW, 64);
		slide(320, centred);
		float by = top + 176 + slideY;
		float bw = (colW - 10) / 2;
		pill("home:single", x + slideX, by, bw, 38, "user", t("menu.singleplayer"),
			() -> go(() -> mc.displayGuiScreen(new GuiSelectWorld(this))));
		pill("home:multi", x + slideX + bw + 10, by, bw, 38, "globe", t("menu.multiplayer"),
			() -> go(() -> mc.displayGuiScreen(new GuiMultiplayer(this))));
		Ui.opacity(1);
	}

	/** A line of the column, left-aligned at {@code x} or centred on {@code cx}, where it slides from. */
	private void label(gg.rago.gui.ui.TtfFont font, String text, float x, float cx, float y, float size, int color, boolean centred) {
		if (centred) {
			Ui.textCentered(font, text, cx, y + slideY, size, color);
		} else {
			Ui.text(font, text, x + slideX, y, size, color);
		}
	}

	/** How far the element being drawn still is from its place while it fades in. */
	private float slideX;
	private float slideY;

	/** Starts the next element of the column: fades it in, sliding from the left or from below. */
	private void slide(float delayMs, boolean centred) {
		float p = intro(delayMs, 520);
		Ui.opacity(p);
		slideX = centred ? 0 : -(1 - p) * 18;
		slideY = centred ? (1 - p) * 14 : 0;
	}

	/**
	 * The red Fast Play button: joins the server played the most, with its icon, players online
	 * and ping. It breathes softly and a light crosses it now and then (without menu animations,
	 * neither).
	 */
	private void fastPlay(ServerStatus s, float x, float y, float w, float h) {
		boolean over = leaving == null && !optifineHelp && Ui.hover(x, y, w, h);
		float hv = Anim.to("home:fast", over ? 1 : 0, 140);
		y -= hv * 2;
		float r = 15;
		float time = Anim.enabled() ? System.nanoTime() / 1e9f : 0;
		float breath = 0.5f + 0.5f * (float) Math.sin(time * 2.1f);
		Ui.glow(x + w / 2, y + h / 2, w * 0.66f, Ui.alpha(Theme.ACCENT, 0.13f + 0.07f * breath + 0.14f * hv));
		Ui.glass(x, y, w, h, r, 0);
		Ui.round(x, y, w, h, r, Ui.alpha(Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, hv), 0.94f));
		if (over) {
			sheen("home:fast:sheen", x, y, w, h, true);
		} else if (Anim.enabled()) {
			// once every five seconds
			float t = (time % 5f) / 0.9f;
			if (t < 1) {
				band(x, y, w, h, Anim.easeOut(t), 0x24);
			}
		}

		// the server's own icon, as in the multiplayer list
		float is = h - 20;
		Ui.round(x + 10, y + 10, is, is, 11, 0x30000000);
		if (s.icon() != null) {
			Ui.texture(s.icon(), x + 13, y + 13, is - 6, is - 6, 0, 0, 1, 1, 0xFFFFFFFF);
		} else {
			Ui.icon("zap", x + 10 + (is - 20) / 2, y + 10 + (is - 20) / 2, 20, 0xFFFFFFFF);
		}
		float tx = x + 10 + is + 13;
		Ui.text(Ui.BOLD, t("rago.home.fast_play"), tx, y + 13, 15, 0xFFFFFFFF);
		String players = s.playersText();
		String line = players != null ? s.name + "  \u00b7  " + t("rago.home.players", players)
			: s.offline() ? s.name + "  \u00b7  " + t("rago.title.offline") : s.name;
		Ui.text(Ui.REGULAR, line, tx, y + 35, 9.5f, 0xD9FFFFFF);

		// the ping, right; an arrow comes in while pointed at
		long ping = s.ping();
		String pingText = ping >= 0 ? ping + " ms" : s.offline() ? "\u2013" : "\u2026";
		float ps = 9;
		float pw = Ui.textWidth(Ui.MEDIUM, pingText, ps);
		float right = x + w - 16 - hv * 14;
		Ui.text(Ui.MEDIUM, pingText, right - pw, y + (h - ps * 1.21f) / 2, ps, 0xFFFFFFFF);
		Ui.icon("signal", right - pw - 15, y + (h - 11) / 2, 11, 0xFFFFFFFF);
		if (hv > 0.01f) {
			float o = Ui.opacity();
			Ui.opacity(o * hv);
			Ui.icon("chevron-right", x + w - 22, y + (h - 12) / 2, 12, 0xFFFFFFFF);
			Ui.opacity(o);
		}
		if (!optifineHelp && leaving == null) {
			Ui.click(x, y, w, h, () -> go(() -> s.join(this)));
		}
	}

	/** A light that crosses a button once when the mouse comes on it. */
	private static void sheen(String key, float x, float y, float w, float h, boolean over) {
		float t = Anim.to(key, over ? 1 : 0, 650);
		if (!over || t <= 0.01f || t >= 0.99f) {
			return;
		}
		band(x, y, w, h, t, 0x2E);
	}

	/** The light band at {@code t} (0..1) of its way across, kept inside the button's round corners. */
	private static void band(float x, float y, float w, float h, float t, int alpha) {
		float band = 46;
		float bx = x - band * 2 + (w + band * 2) * t;
		int light = alpha << 24 | 0xFFFFFF;
		Ui.scissor(x + 5, y + 5, w - 10, h - 10);
		Ui.hgradient(bx, y, band, h, 0x00FFFFFF, light);
		Ui.hgradient(bx + band, y, band, h, light, 0x00FFFFFF);
		Ui.endScissor();
	}

	/** A secondary button with an icon. */
	private void pill(String key, float x, float y, float w, float h, String icon, String label, Runnable action) {
		boolean over = leaving == null && !optifineHelp && Ui.hover(x, y, w, h);
		float hv = Anim.to(key, over ? 1 : 0, 120);
		Ui.glassBorder(x, y, w, h, h / 2, Ui.mix(0xE6131215, 0xF0222126, hv), Ui.mix(0x24FFFFFF, 0x45FFFFFF, hv));
		sheen(key + ":sheen", x, y, w, h, over);
		float size = 10.5f;
		float lw = Ui.textWidth(Ui.MEDIUM, label, size);
		float cx = x + (w - lw - 18) / 2;
		Ui.icon(icon, cx, y + (h - 12) / 2, 12, Ui.mix(0xFFC9C7CE, Theme.TEXT, hv));
		Ui.text(Ui.MEDIUM, label, cx + 18, y + (h - size * 1.21f) / 2, size, Theme.TEXT);
		if (!optifineHelp && leaving == null) {
			Ui.click(x, y, w, h, action);
		}
	}

	/** The player in 3D with their skin, cape and cosmetics, feet at ({@code cx}, {@code feetY}); a click opens the locker. */
	private void player(float cx, float feetY, float size, float px, float partialTicks) {
		if (preview == null) {
			return;
		}
		float top = feetY - size * 2.05f;
		float p = intro(200, 800);
		boolean over = leaving == null && !optifineHelp && Ui.hover(cx - size * 0.6f, top, size * 1.2f, feetY - top + 30);
		float hv = Anim.to("home:player", over ? 1 : 0, 160);
		Ui.opacity(p);
		Ui.glow(cx, feetY - size * 0.95f, size * 1.7f, Ui.alpha(Theme.ACCENT, 0.16f + 0.08f * hv));
		Ui.glow(cx, feetY, size * 0.75f, 0x60000000);
		float t = Anim.enabled() ? System.nanoTime() / 1e9f : 0;
		float yaw = -22 + px * 34 + (float) Math.sin(t * 0.45f) * 7;
		preview.draw(cx, feetY + (1 - p) * 10, size, yaw, Skins.worn(), partialTicks);

		// "Customize", under the feet
		String label = t("rago.home.customize");
		float ls = 9.5f;
		float lw = Ui.textWidth(Ui.MEDIUM, label, ls) + 36;
		float ly = feetY + 18;
		Ui.opacity(p * (0.55f + 0.45f * hv));
		Ui.glassBorder(cx - lw / 2, ly, lw, 24, 12, Ui.mix(0xD9131215, 0xF0222126, hv), Ui.mix(0x24FFFFFF, Ui.alpha(Theme.ACCENT, 0.6f), hv));
		Ui.icon("shirt", cx - lw / 2 + 12, ly + 6, 12, Ui.mix(0xFFC9C7CE, Theme.TEXT, hv));
		Ui.text(Ui.MEDIUM, label, cx - lw / 2 + 28, ly + (24 - ls * 1.21f) / 2, ls, Theme.TEXT);
		Ui.opacity(1);
		if (!optifineHelp && leaving == null) {
			Ui.click(cx - size * 0.6f, top, size * 1.2f, ly + 24 - top, () -> go(() -> mc.displayGuiScreen(new LockerScreen(this))));
		}
	}

	/** What is new in this version, bottom left. */
	private void news(float x, float h) {
		float nw = 280;
		float pad = 12;
		List<String> body = Ui.wrap(Ui.REGULAR, t("rago.title.news_body"), 9, nw - pad * 2);
		float nh = pad + 10 + 6 + 14 + 3 + body.size() * 12 + pad - 2;
		float y = h - 34 - nh;
		float p = intro(520, 600);
		Ui.opacity(p);
		y += (1 - p) * 10;
		Ui.glassBorder(x, y, nw, nh, 12, 0xE6131215, 0x24FFFFFF);
		String tag = t("rago.title.whats_new", RagoClient.version().replace("+1.8.9", ""));
		Ui.textSpaced(Ui.BOLD, tag, x + pad, y + pad, 7.5f, 0.6f, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.title.news_title"), x + pad, y + pad + 16, 11.5f, Theme.TEXT);
		float ty = y + pad + 33;
		for (String line : body) {
			Ui.text(Ui.REGULAR, line, x + pad, ty, 9, 0xFFB4B2B9);
			ty += 12;
		}
		Ui.opacity(1);
	}

	// ---- classic layout: the centre -------------------------------------------------------------

	private void center(float w, float h) {
		float cx = w / 2;
		float cy = Math.max(160, h * 0.36f);
		float a = intro(0, 650);
		Ui.opacity(a);
		logo(cx, cy, 96 * (0.88f + 0.12f * a));
		float ty = cy + 66;
		float size = 19;
		float rw = Ui.textWidth(Ui.SEMIBOLD, "Rago ", size);
		float cw = Ui.textWidth(Ui.REGULAR, "Client", size);
		float tx = cx - (rw + cw) / 2;
		Ui.text(Ui.SEMIBOLD, "Rago ", tx, ty, size, Theme.TEXT);
		Ui.text(Ui.REGULAR, "Client", tx + rw, ty, size, 0xFFB4B2B9);

		float bw = 302;
		float bh = 25;
		float by = ty + 40;
		String[] labels = {t("menu.singleplayer"), t("menu.multiplayer"), t("rago.title.join_discord")};
		Runnable[] actions = {
			() -> mc.displayGuiScreen(new GuiSelectWorld(this)),
			() -> mc.displayGuiScreen(new GuiMultiplayer(this)),
			() -> Sys.openURL(DISCORD_URL)
		};
		for (int i = 0; i < labels.length; i++) {
			float p = intro(120 + i * 70, 450);
			Ui.opacity(p);
			float y = by + i * 33 + (1 - p) * 12;
			homeButton("home:" + i, cx - bw / 2, y, bw, bh, labels[i], i == 2);
			if (!optifineHelp) {
				Ui.click(cx - bw / 2, y, bw, bh, actions[i]);
			}
		}
		Ui.opacity(1);
	}

	/**
	 * The animated emblem: the R with its red glow and, every 3.2 s, two thin rings that ripple
	 * out from it and fade. Without menu animations it stays still.
	 */
	private static void logo(float cx, float cy, float size) {
		float cycle = 3.2f;
		float t = Anim.enabled() ? (System.nanoTime() / 1e9f) % cycle : 0;
		for (int i = 0; i < 2; i++) {
			float p = (t - 0.25f - i * 0.6f) / 1.9f;
			if (p <= 0 || p >= 1) {
				continue;
			}
			float eased = 1 - (1 - p) * (1 - p) * (1 - p);
			float radius = size * (0.42f + 0.5f * eased);
			float alpha = Math.min(1, p / 0.08f) * (float) Math.pow(1 - p, 1.4) * 0.8f;
			Ui.annulus(cx, cy, radius, 1.3f, Ui.alpha(Theme.ACCENT, alpha));
		}
		// the glow swells a little each time a ring leaves
		float pulse = t < 0.25f || t > 1.6f ? 0 : (float) Math.sin((t - 0.25f) / 1.35f * Math.PI);
		float half = size / 2;
		Ui.logoGlow(cx - half, cy - half, size, Ui.alpha(Theme.ACCENT, 0.42f + 0.15f * pulse));
		Ui.logo(cx - half, cy - half, size, Theme.ACCENT);
	}

	private static void homeButton(String key, float x, float y, float w, float h, String label, boolean primary) {
		float hv = Anim.to(key, Ui.hover(x, y, w, h) ? 1 : 0, 120);
		if (primary) {
			Ui.glass(x, y, w, h, h / 2, 0);
			Ui.round(x, y, w, h, h / 2, Ui.alpha(Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, hv), 0.88f));
		} else {
			Ui.glassBorder(x, y, w, h, h / 2, Ui.mix(0xE6131215, 0xF0222126, hv), Ui.mix(0x24FFFFFF, 0x40FFFFFF, hv));
		}
		float size = 10.5f;
		Ui.textCentered(Ui.MEDIUM, label, x + w / 2, y + (h - size * 1.21f) / 2, size, primary ? 0xFF1A0605 : Theme.TEXT);
	}

	// ---- dock -----------------------------------------------------------------------------------

	private void dock(float w, float h) {
		boolean noOptifine = !OptiFineFields.installed();
		String[] icons = noOptifine
			? new String[] {"logo", "shirt", "sliders-horizontal", "settings", "globe", "zap", "power"}
			: new String[] {"logo", "shirt", "sliders-horizontal", "settings", "globe", "power"};
		String[] tips = noOptifine
			? new String[] {"rago.dock.mods", "rago.dock.locker", "rago.dock.rago_settings", "rago.dock.options", "rago.dock.servers", "rago.dock.optifine", "rago.dock.quit"}
			: new String[] {"rago.dock.mods", "rago.dock.locker", "rago.dock.rago_settings", "rago.dock.options", "rago.dock.servers", "rago.dock.quit"};
		float size = 30;
		float gap = 8;
		float total = icons.length * size + (icons.length - 1) * gap;
		float p = intro(380, 500);
		Ui.opacity(p);
		float x = w / 2 - total / 2;
		float y = h - 62 + (1 - p) * 10;
		for (int i = 0; i < icons.length; i++) {
			String icon = icons[i];
			float hv = Anim.to("dock:" + icon, Ui.hover(x, y, size, size) ? 1 : 0, 120);
			float lift = hv * 2;
			Ui.glassBorder(x, y - lift, size, size, 8, Ui.mix(0xE6131215, 0xF0242328, hv), Ui.mix(0x1FFFFFFF, 0x3DFFFFFF, hv));
			float is = 14;
			if ("logo".equals(icon)) {
				Ui.logo(x + (size - is) / 2, y - lift + (size - is) / 2, is, Theme.ACCENT);
			} else {
				Ui.icon(icon, x + (size - is) / 2, y - lift + (size - is) / 2, is, Ui.mix(0xFFC9C7CE, Theme.TEXT, hv));
			}
			if ("zap".equals(icon)) {
				Ui.circle(x + size - 3, y - lift + 3, 3.5f, Theme.ACCENT);
			}
			if (hv > 0.01f) {
				String tip = t(tips[i]);
				float ts = 9;
				float tw = Ui.textWidth(Ui.MEDIUM, tip, ts) + 14;
				float tipY = y - lift - 24;
				Ui.opacity(p * hv);
				Ui.glass(x + size / 2 - tw / 2, tipY, tw, 17, 6, 0xF0141316);
				Ui.textCentered(Ui.MEDIUM, tip, x + size / 2, tipY + (17 - ts * 1.21f) / 2, ts, Theme.TEXT);
				Ui.opacity(p);
			}
			if (!optifineHelp) {
				Ui.click(x, y, size, size, dockAction(icon));
			}
			x += size + gap;
		}
		Ui.opacity(1);
	}

	private Runnable dockAction(String icon) {
		switch (icon) {
			case "logo":
				return () -> mc.displayGuiScreen(new RagoMenuScreen(this));
			case "shirt":
				return () -> mc.displayGuiScreen(new LockerScreen(this));
			case "sliders-horizontal":
				return () -> mc.displayGuiScreen(RagoMenuScreen.settings(this));
			case "settings":
				return () -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
			case "globe":
				return () -> mc.displayGuiScreen(new GuiMultiplayer(this));
			case "zap":
				return () -> {
					optifineHelp = true;
					helpOpenedNs = System.nanoTime();
				};
			default:
				return () -> mc.shutdown();
		}
	}

	// ---- Rago Optimizer card --------------------------------------------------------------------

	/**
	 * The Rago Optimizer card, bottom right: the banner, a "Free cosmetic" tag and a button to the
	 * Discord (the whole card opens it). A cross on its picture hides it until the game starts again.
	 * Left out when the window is too narrow for it to stay clear of the buttons and the dock.
	 */
	private void promo(float w, float h) {
		if (promoClosed) {
			return;
		}
		float cw = 250;
		float ih = 113;
		float pad = 12;
		float titleSize = 12.5f;
		float bodySize = 9;
		List<String> body = Ui.wrap(Ui.REGULAR, t("rago.title.promo_body"), bodySize, cw - pad * 2);
		float ch = ih + pad + titleSize * 1.21f + 5 + body.size() * 12 + 10 + 22 + pad;
		float x = w - 16 - cw;
		float y = h - 34 - ch;
		// the centre buttons reach w / 2 + 151 and the dock w / 2 + 133; the account chip ends at 44
		if (x < w / 2 + 165 || y < 56) {
			return;
		}
		float p = intro(650, 650);
		Ui.opacity(p);
		x += (1 - p) * 18;
		boolean over = !optifineHelp && Ui.hover(x, y, cw, ch);
		float hv = Anim.to("promo", over ? 1 : 0, 140);
		y -= hv * 2;
		float pulse = Anim.enabled() ? 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1e9 * 2.2) : 0.5f;

		Ui.glow(x + cw / 2, y + ch / 2, cw * 0.78f, Ui.alpha(Theme.ACCENT, 0.07f + 0.10f * hv));
		int fill = Ui.mix(0xF2131215, 0xF61A181C, hv);
		Ui.glassBorder(x, y, cw, ch, 12, fill, 0);
		Ui.texture(PROMO, x, y, cw, ih, 0, 0, 1, 1, Ui.mix(0xFFD6D6D6, 0xFFFFFFFF, hv));
		Ui.gradient(x, y + ih - 34, cw, 34.5f, fill & 0x00FFFFFF, fill);
		Ui.outline(x, y, cw, ch, 12, Ui.mix(0x29FFFFFF, Ui.alpha(Theme.ACCENT, 0.7f), hv));

		// the tag on the picture
		float tagSize = 7.5f;
		String tag = t("rago.title.promo_tag");
		float tagW = Ui.textSpacedWidth(Ui.BOLD, tag, tagSize, 0.6f) + 27;
		float tagX = x + 10;
		float tagY = y + 10;
		Ui.glow(tagX + tagW / 2, tagY + 8, tagW * 0.75f, Ui.alpha(Theme.ACCENT, 0.18f + 0.22f * pulse));
		Ui.round(tagX, tagY, tagW, 16, 8, Theme.ACCENT);
		Ui.icon("sparkles", tagX + 6, tagY + 3.5f, 9, 0xFF1A0605);
		Ui.textSpaced(Ui.BOLD, tag, tagX + 19, tagY + (16 - tagSize * 1.21f) / 2, tagSize, 0.6f, 0xFF1A0605);

		// the cross, while the card is pointed at
		float xs = 18;
		float xx = x + cw - xs - 8;
		float xy = y + 8;
		if (hv > 0.01f) {
			Ui.opacity(p * hv);
			boolean onCross = Ui.hover(xx, xy, xs, xs);
			Ui.round(xx, xy, xs, xs, xs / 2, onCross ? 0xE6302E34 : 0xB30B0A0D);
			Ui.icon("x", xx + 4.5f, xy + 4.5f, 9, onCross ? Theme.TEXT : 0xFFC9C7CE);
			Ui.opacity(p);
		}

		// the text and the button
		float ty = y + ih + pad;
		Ui.text(Ui.SEMIBOLD, t("rago.title.promo_title"), x + pad, ty, titleSize, Theme.TEXT);
		ty += titleSize * 1.21f + 5;
		for (String line : body) {
			Ui.text(Ui.REGULAR, line, x + pad, ty, bodySize, 0xFFB4B2B9);
			ty += 12;
		}
		float by = ty + 10;
		String cta = t("rago.title.promo_cta");
		float ctaSize = 9.5f;
		float bw = Ui.textWidth(Ui.SEMIBOLD, cta, ctaSize) + 34;
		float bx = x + cw - pad - bw;
		float linkSize = 7.5f;
		String link = Ui.ellipsize(Ui.MONO, "discord.gg/ragooptimizer", linkSize, bx - x - pad * 2);
		Ui.text(Ui.MONO, link, x + pad, by + (22 - linkSize * 1.21f) / 2, linkSize, Theme.DIM);
		Ui.round(bx, by, bw, 22, 11, Ui.mix(Theme.ACCENT, Theme.ACCENT_HOVER, hv));
		Ui.text(Ui.SEMIBOLD, cta, bx + 12, by + (22 - ctaSize * 1.21f) / 2, ctaSize, 0xFF1A0605);
		Ui.icon("chevron-right", bx + bw - 19 + hv * 2, by + 5.5f, 11, 0xFF1A0605);

		if (!optifineHelp) {
			Ui.click(x, y, cw, ch, () -> Sys.openURL(DISCORD_URL));
			if (hv > 0.5f) {
				Ui.click(xx, xy, xs, xs, () -> promoClosed = true);
			}
		}
		Ui.opacity(1);
	}

	/** The account in use, top right; opens the Accounts screen. */
	private void account(float w) {
		if (!Accounts.available()) {
			return;
		}
		String name = mc.getSession().getUsername();
		float size = 10;
		float tw = Ui.textWidth(Ui.MEDIUM, name, size);
		float cw = tw + 58;
		float x = w - 14 - cw;
		float y = 14;
		float p = intro(450, 500);
		Ui.opacity(p);
		float hv = Anim.to("account", Ui.hover(x, y, cw, 30) ? 1 : 0, 120);
		Ui.glassBorder(x, y, cw, 30, 15, Ui.mix(0xE6131215, 0xF0242328, hv), Ui.mix(0x1FFFFFFF, 0x3DFFFFFF, hv));
		AccountsScreen.head(name, x + 7, y + 7, 16);
		Ui.text(Ui.MEDIUM, name, x + 30, y + (30 - size * 1.21f) / 2, size, Theme.TEXT);
		Ui.icon("users", x + cw - 22, y + 9, 12, Ui.mix(0xFF9A98A0, Theme.TEXT, hv));
		if (hv > 0.01f) {
			String tip = t("rago.dock.accounts");
			float tsz = 9;
			float tipW = Ui.textWidth(Ui.MEDIUM, tip, tsz) + 14;
			Ui.opacity(p * hv);
			Ui.glass(x + cw - tipW, y + 36, tipW, 17, 6, 0xF0141316);
			Ui.textCentered(Ui.MEDIUM, tip, x + cw - tipW / 2, y + 36 + (17 - tsz * 1.21f) / 2, tsz, Theme.TEXT);
			Ui.opacity(p);
		}
		if (!optifineHelp) {
			Ui.click(x, y, cw, 30, () -> mc.displayGuiScreen(new AccountsScreen(this)));
		}
		Ui.opacity(1);
	}

	private void footer(float w, float h) {
		float size = 8.5f;
		Ui.opacity(intro(500, 500));
		if (RagoClient.classicHome()) {
			Ui.text(Ui.MONO, "Rago Client 1.8.9 (" + RagoClient.version() + ")", 14, h - 20, size, 0xFF8A888F);
		} else {
			Ui.text(Ui.REGULAR, "Minecraft 1.8.9", Math.max(36, Math.round(w * 0.056f)), h - 20, size, 0xFF8A888F);
		}
		Ui.textRight(Ui.REGULAR, t("rago.title.legal"), w - 14, h - 20, size, 0xFF8A888F);
		float made = Ui.textWidth(Ui.REGULAR, "Made by ", size);
		float mx = w / 2 - (made + Ui.textWidth(Ui.SEMIBOLD, "Rago", size)) / 2;
		Ui.text(Ui.REGULAR, "Made by ", mx, h - 20, size, 0xFF8A888F);
		Ui.text(Ui.SEMIBOLD, "Rago", mx + made, h - 20, size, Theme.ACCENT);
		Ui.opacity(1);
	}

	// ---- OptiFine helper ------------------------------------------------------------------------

	private void optifineDialog(float w, float h) {
		float p = Anim.progress(helpOpenedNs, 220);
		Ui.opacity(p);
		Ui.rect(0, 0, w, h, 0xA0000000);
		Ui.click(0, 0, w, h, () -> { });
		float dw = 380;
		float dh = 196;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2 + (1 - p) * 14;
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		Ui.round(x + 20, y + 20, 30, 30, 9, Theme.ACCENT_SOFT);
		Ui.icon("zap", x + 27, y + 27, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.optifine.title"), x + 62, y + 21, 14, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.optifine.subtitle"), x + 62, y + 40, 9.5f, Theme.MUTED);
		float ty = y + 68;
		for (String line : Ui.wrap(Ui.REGULAR, t("rago.optifine.body"), 10, dw - 40)) {
			Ui.text(Ui.REGULAR, line, x + 20, ty, 10, 0xFFD4D2D9);
			ty += 15;
		}
		float bw = (dw - 52) / 3;
		float by = y + dh - 44;
		homeButton("of:download", x + 20, by, bw, 26, t("rago.optifine.download"), true);
		Ui.click(x + 20, by, bw, 26, () -> Sys.openURL(OPTIFINE_URL));
		homeButton("of:folder", x + 26 + bw, by, bw, 26, t("rago.optifine.open_mods"), false);
		Ui.click(x + 26 + bw, by, bw, 26, this::openModsFolder);
		homeButton("of:close", x + 32 + bw * 2, by, bw, 26, t("gui.done"), false);
		Ui.click(x + 32 + bw * 2, by, bw, 26, () -> optifineHelp = false);
		Ui.opacity(1);
	}

	private void openModsFolder() {
		File mods = new File(mc.mcDataDir, "mods");
		mods.mkdirs();
		try {
			Desktop.getDesktop().open(mods);
		} catch (Exception | LinkageError e) {
			Sys.openURL(mods.toURI().toString());
		}
	}

	// ---- input ----------------------------------------------------------------------------------

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button == 0 && leaving == null) {
			Ui.updateMouse();
			if (Ui.dispatchClick()) {
				mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
			}
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if (keyCode == 1 && optifineHelp) {
			optifineHelp = false;
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
