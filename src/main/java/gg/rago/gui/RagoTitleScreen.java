package gg.rago.gui;

import gg.rago.accounts.Accounts;
import gg.rago.accounts.Skins;
import gg.rago.core.RagoClient;
import gg.rago.debug.FrameProfile;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Background;
import gg.rago.gui.ui.Ui;
import gg.rago.optimize.OptiFineFields;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/** Translations, looked up once while the screen is open (the language changes in another screen). */
	private static final Map<String, String> TEXT = new HashMap<>();

	private static String t(String key) {
		String s = TEXT.get(key);
		if (s == null) {
			s = I18n.format(key);
			TEXT.put(key, s);
		}
		return s;
	}

	/** The server shown under the buttons, picked when the screen opens. */
	private ServerStatus server;

	@Override
	public void initGui() {
		openedNs = introPlayed ? System.nanoTime() - 5_000_000_000L : System.nanoTime();
		introPlayed = true;
		leaving = null;
		TEXT.clear();
		textCache.clear();
		if (!RagoClient.classicHome()) {
			server = fastPlay();
			server.refresh();
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
		long profileNs = FrameProfile.ON ? System.nanoTime() : 0;
		long profileBytes = FrameProfile.ON ? FrameProfile.bytes() : 0;
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
		backgroundButton(w);
		backgroundPicker(w, h);
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
		if (FrameProfile.ON) {
			FrameProfile.element("home screen", System.nanoTime() - profileNs, FrameProfile.bytesSince(profileBytes));
		}
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

	/** The width of the column of buttons and of the server entry under them. */
	private static final float COLUMN_WIDTH = 380;
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
		long profileNs = FrameProfile.ON ? System.nanoTime() : 0;
		Ui.backdrop(0.1f, -px * 24, -py * 14, 1.06f);
		if (!centred) {
			Ui.hgradient(0, 0, w * 0.62f, h, 0xD0070609, 0x00070609);
		}
		Ui.gradient(0, h * 0.55f, w, h * 0.45f, 0x00050406, 0xA8050406);
		Ui.captureGlass();
		Ui.blurScreen(RagoClient.homeBlur());
		if (FrameProfile.ON) {
			FrameProfile.element("home background", System.nanoTime() - profileNs);
		}
		if (centred) {
			Ui.glow(w / 2, h * 0.47f, 330, 0x99050406);
		}
		if (out > 0) {
			Ui.pushTransform(w / 2, h / 2, 1 + 0.035f * out, 0, 0);
		}

		float left = Math.max(36, Math.round(w * 0.056f));
		brand(left);
		float colW = COLUMN_WIDTH;
		float top = Math.max(76, Math.round(h * 0.48f - heroHeight(colW) / 2));
		if (centred) {
			player(Math.round(Math.max(left + 90, w * 0.2f)), Math.round(h * 0.74f), Math.min(h * 0.19f, 150), px, partialTicks);
			hero(w / 2 - colW / 2, top, colW, true);
		} else {
			player(Math.round(w * 0.5f), Math.round(h * 0.79f), Math.min(h * 0.2f, 160), px, partialTicks);
			hero(left, top, colW, false);
		}
	}

	private static String shortVersion;

	/** Rago's version without the Minecraft one ("1.9.6"). */
	private static String shortVersion() {
		if (shortVersion == null) {
			shortVersion = RagoClient.version().replace("+1.8.9", "");
		}
		return shortVersion;
	}

	/**
	 * Wrapped and shortened texts, worked out once per text and width rather than every frame:
	 * the key is the text, its size and width.
	 */
	private final Map<String, Object> textCache = new HashMap<>();

	@SuppressWarnings("unchecked")
	private List<String> wrapped(gg.rago.gui.ui.TtfFont font, String text, float size, float width) {
		String key = text + '\u0000' + size + '\u0000' + width;
		Object v = textCache.get(key);
		if (v == null) {
			v = Ui.wrap(font, text, size, width);
			textCache.put(key, v);
		}
		return (List<String>) v;
	}

	private String ellipsized(gg.rago.gui.ui.TtfFont font, String text, float size, float width) {
		String key = text + '\u0001' + size + '\u0000' + width;
		Object v = textCache.get(key);
		if (v == null) {
			v = Ui.ellipsize(font, text, size, width);
			textCache.put(key, v);
		}
		return (String) v;
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
		String version = shortVersion();
		float vs = 8.5f;
		float vw = Ui.textWidth(Ui.MEDIUM, version, vs) + 14;
		Ui.round(tx + 9, 21, vw, 16, 8, Theme.ACCENT_SOFT);
		Ui.text(Ui.MEDIUM, version, tx + 16, 29 - vs * 1.21f / 2, vs, Theme.ACCENT);
		Ui.opacity(1);
	}

	/**
	 * The greeting, then singleplayer, multiplayer and the server played the most (as the
	 * multiplayer list shows it), stacked in a column at {@code x}.
	 */
	private void hero(float x, float top, float colW, boolean centred) {
		String name = mc.getSession().getUsername();
		float cx = x + colW / 2;

		slide(60, centred);
		label(Ui.MEDIUM, t("rago.home.welcome"), x, cx, top, 13, 0xFFB4B2B9, centred);
		slide(110, centred);
		label(Ui.BOLD, name, x, cx, top + 17, 36, Theme.TEXT, centred);
		slide(160, centred);
		label(Ui.REGULAR, t("rago.home.tagline"), x, cx, top + 64, 11, 0xFF9A98A0, centred);

		float y = top + 98;
		slide(220, centred);
		pill("home:single", x + slideX, y + slideY, colW, 38, "user", t("menu.singleplayer"),
			() -> go(() -> mc.displayGuiScreen(new GuiSelectWorld(this))));
		y += 46;
		slide(290, centred);
		pill("home:multi", x + slideX, y + slideY, colW, 38, "globe", t("menu.multiplayer"),
			() -> go(() -> mc.displayGuiScreen(new GuiMultiplayer(this))));
		y += 46;
		slide(360, centred);
		if (server != null) {
			serverEntry(server, x + slideX, y + slideY, colW);
		}
		Ui.opacity(1);
	}

	/** The column's height below {@code top}, for placing it. */
	private static float heroHeight(float colW) {
		return 98 + 46 * 2 + serverEntryHeight(colW);
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

	private static final ResourceLocation UNKNOWN_SERVER = new ResourceLocation("textures/misc/unknown_server.png");
	private static final ResourceLocation SERVER_SELECTION = new ResourceLocation("textures/gui/server_selection.png");
	private static final float ENTRY_PAD = 8;

	/** Points of the multiplayer list the entry should hold across, for its text to fit as in the list (305). */
	private static final int ENTRY_POINTS = 220;

	/**
	 * Screen pixels a point of the multiplayer list is drawn with in an entry {@code w} units wide:
	 * as many as leave it {@link #ENTRY_POINTS} points across, in whole pixels so its font stays sharp.
	 */
	private static int entryPixels(float w) {
		return Math.max(1, (int) ((w - ENTRY_PAD * 2) * Ui.scale() / ENTRY_POINTS));
	}

	private static float serverEntryHeight(float w) {
		return 32f * entryPixels(w) / Ui.scale() + ENTRY_PAD * 2;
	}

	private String motdText;
	private int motdWidth;
	private List<String> motdLines;

	/** The message of the day in lines for a list {@code listW} points wide, worked out again only when it changes. */
	private List<String> motdLines(net.minecraft.client.gui.FontRenderer font, String motd, int listW) {
		if (motdLines == null || listW != motdWidth || !motd.equals(motdText)) {
			motdText = motd;
			motdWidth = listW;
			motdLines = font.listFormattedStringToWidth(fitMotd(motd, listW - 34), listW - 32 - 2);
		}
		return motdLines;
	}

	/**
	 * The message of the day for a list {@code points} wide: servers centre their lines with spaces
	 * for the multiplayer list's 271 points; narrower, the spaces are cut down in proportion so the
	 * lines stay centred instead of wrapping.
	 */
	static String fitMotd(String motd, int points) {
		if (points >= 271 || motd.indexOf(' ') < 0) {
			return motd;
		}
		StringBuilder out = new StringBuilder(motd.length());
		String[] lines = motd.split("\n", -1);
		for (int l = 0; l < lines.length; l++) {
			String line = lines[l];
			if (l > 0) {
				out.append('\n');
			}
			int i = 0;
			int spaces = 0;
			StringBuilder codes = new StringBuilder();
			while (i < line.length()) {
				char c = line.charAt(i);
				if (c == '\u00a7' && i + 1 < line.length()) {
					codes.append(c).append(line.charAt(i + 1));
					i += 2;
				} else if (c == ' ') {
					spaces++;
					i++;
				} else {
					break;
				}
			}
			int keep = Math.round(spaces * points / 271f);
			out.append(codes);
			for (int n = 0; n < keep; n++) {
				out.append(' ');
			}
			out.append(line, i, line.length());
		}
		return out.toString();
	}

	/**
	 * The server played the most (Hypixel until another one has more), drawn as the multiplayer
	 * list draws it, with Minecraft's own font, icons and colours: its icon, name, message of the
	 * day, players and ping bars; pointed at, the icon darkens under the play arrow. A click joins it.
	 */
	private void serverEntry(ServerStatus s, float x, float y, float w) {
		float h = serverEntryHeight(w);
		boolean over = leaving == null && !optifineHelp && Ui.hover(x, y, w, h);
		float hv = Anim.to("home:server", over ? 1 : 0, 140);
		y -= hv * 2;
		Ui.glow(x + w / 2, y + h / 2, w * 0.62f, Ui.alpha(Theme.ACCENT, 0.16f * hv));
		Ui.glassBorder(x, y, w, h, 12, Ui.mix(0xE6131215, 0xF01A191D, hv), Ui.mix(0x24FFFFFF, Ui.alpha(Theme.ACCENT, 0.75f), hv));
		sheen("home:server:sheen", x, y, w, h, over);

		float a = Ui.opacity();
		if (a < 0.03f) {
			return;
		}
		float scale = Ui.scale();
		int k = entryPixels(w);
		int listW = (int) Math.floor((w - ENTRY_PAD * 2) * scale / k);
		int alpha = Math.round(a * 255) << 24;
		net.minecraft.client.gui.FontRenderer font = mc.fontRendererObj;
		GlStateManager.pushMatrix();
		GlStateManager.translate(Math.round((x + ENTRY_PAD) * scale), Math.round((y + ENTRY_PAD) * scale), 0);
		GlStateManager.scale(k, k, 1);
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

		// icon, and the play arrow over it while pointed at
		GlStateManager.color(1, 1, 1, a);
		mc.getTextureManager().bindTexture(s.icon() != null ? s.icon() : UNKNOWN_SERVER);
		Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 32, 32, 32, 32);
		if (over) {
			Gui.drawRect(0, 0, 32, 32, 0xA0909090);
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
			GlStateManager.color(1, 1, 1, a);
			mc.getTextureManager().bindTexture(SERVER_SELECTION);
			Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 32, 32, 32, 256, 256);
		}

		// name (cut short if it would run into the players), message of the day (two lines at most), players
		String players = s.population();
		int playersX = listW - font.getStringWidth(players) - 15 - 2;
		String name = s.name;
		if (35 + font.getStringWidth(name) > playersX - 4) {
			name = font.trimStringToWidth(name, Math.max(0, playersX - 4 - 35 - font.getStringWidth("..."))) + "...";
		}
		font.drawString(name, 35, 1, alpha | 0xFFFFFF);
		List<String> motd = motdLines(font, s.motd(), listW);
		for (int i = 0; i < Math.min(2, motd.size()); i++) {
			font.drawString(motd.get(i), 35, 12 + font.FONT_HEIGHT * i, alpha | 0x808080);
		}
		font.drawString(players, playersX, 1, alpha | 0x808080);

		// ping bars: five for a good ping, a cross when unreachable, moving while waiting
		int column = 0;
		int row;
		long ping = s.ping();
		if (s.waiting()) {
			column = 1;
			row = (int) (Minecraft.getSystemTime() / 100L & 7L);
			if (row > 4) {
				row = 8 - row;
			}
		} else if (ping < 0) {
			row = 5;
		} else {
			row = ping < 150 ? 0 : ping < 300 ? 1 : ping < 600 ? 2 : ping < 1000 ? 3 : 4;
		}
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, a);
		mc.getTextureManager().bindTexture(Gui.icons);
		Gui.drawModalRectWithCustomSizedTexture(listW - 15, 0, column * 10, 176 + row * 8, 10, 8, 256, 256);
		GlStateManager.popMatrix();
		// back to the state Ui draws in
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.color(1, 1, 1, 1);

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
		long profileNs = FrameProfile.ON ? System.nanoTime() : 0;
		long profileBytes = FrameProfile.ON ? FrameProfile.bytes() : 0;
		preview.draw(cx, feetY + (1 - p) * 10, size, yaw, Skins.worn(), partialTicks);
		if (FrameProfile.ON) {
			FrameProfile.element("home player", System.nanoTime() - profileNs, FrameProfile.bytesSince(profileBytes));
		}

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
		String[] icons = noOptifine ? DOCK_ICONS_OPTIFINE_HELP : DOCK_ICONS;
		String[] tips = noOptifine ? DOCK_TIPS_OPTIFINE_HELP : DOCK_TIPS;
		String[] keys = noOptifine ? DOCK_KEYS_OPTIFINE_HELP : DOCK_KEYS;
		if (dockActions == null || dockActions.length != icons.length) {
			dockActions = new Runnable[icons.length];
			for (int i = 0; i < icons.length; i++) {
				dockActions[i] = dockAction(icons[i]);
			}
		}
		float size = 30;
		float gap = 8;
		float total = icons.length * size + (icons.length - 1) * gap;
		float p = intro(380, 500);
		Ui.opacity(p);
		float x = w / 2 - total / 2;
		float y = h - 62 + (1 - p) * 10;
		for (int i = 0; i < icons.length; i++) {
			String icon = icons[i];
			float hv = Anim.to(keys[i], Ui.hover(x, y, size, size) ? 1 : 0, 120);
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
				Ui.click(x, y, size, size, dockActions[i]);
			}
			x += size + gap;
		}
		Ui.opacity(1);
	}

	/** The dock's buttons, with the OptiFine helper's when OptiFine is missing; their tips and animation keys. */
	private static final String[] DOCK_ICONS = {"logo", "shirt", "sliders-horizontal", "settings", "globe", "power"};
	private static final String[] DOCK_ICONS_OPTIFINE_HELP = {"logo", "shirt", "sliders-horizontal", "settings", "globe", "zap", "power"};
	private static final String[] DOCK_TIPS = {"rago.dock.mods", "rago.dock.locker", "rago.dock.rago_settings", "rago.dock.options",
		"rago.dock.servers", "rago.dock.quit"};
	private static final String[] DOCK_TIPS_OPTIFINE_HELP = {"rago.dock.mods", "rago.dock.locker", "rago.dock.rago_settings",
		"rago.dock.options", "rago.dock.servers", "rago.dock.optifine", "rago.dock.quit"};
	private static final String[] DOCK_KEYS = keys("dock:", DOCK_ICONS);
	private static final String[] DOCK_KEYS_OPTIFINE_HELP = keys("dock:", DOCK_ICONS_OPTIFINE_HELP);
	private Runnable[] dockActions;

	private static String[] keys(String prefix, String[] names) {
		String[] out = new String[names.length];
		for (int i = 0; i < names.length; i++) {
			out[i] = prefix + names[i];
		}
		return out;
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
		List<String> body = wrapped(Ui.REGULAR, t("rago.title.promo_body"), bodySize, cw - pad * 2);
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
		String link = ellipsized(Ui.MONO, "discord.gg/ragooptimizer", linkSize, bx - x - pad * 2);
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

	/** The width of the account's button, top right. */
	private float accountWidth() {
		return Ui.textWidth(Ui.MEDIUM, mc.getSession().getUsername(), 10) + 58;
	}

	// ---- background picker ----------------------------------------------------------------------

	private static final String[] CARD_KEYS = keys("bg:card", Background.KEYS);
	private static final float PICKER_CARD_W = 150;
	private static final float PICKER_THUMB_H = 84;
	private static final float PICKER_CARD_H = PICKER_THUMB_H + 30;
	/** Whether the background menu is open, and where its right edge is (that of its button). */
	private boolean pickerOpen;
	private float pickerRight;

	/** The background button, top right, left of the account; opens the background menu. */
	private void backgroundButton(float w) {
		String label = t("rago.home.background");
		float size = 10;
		float cw = Ui.textWidth(Ui.MEDIUM, label, size) + 58;
		float right = w - 14 - (Accounts.available() ? accountWidth() + 8 : 0);
		float x = right - cw;
		float y = 14;
		pickerRight = right;
		float p = intro(420, 500);
		Ui.opacity(p);
		boolean over = Ui.hover(x, y, cw, 30);
		float hv = Anim.to("bg:button", over || pickerOpen ? 1 : 0, 120);
		Ui.glassBorder(x, y, cw, 30, 15, Ui.mix(0xE6131215, 0xF0242328, hv), Ui.mix(0x1FFFFFFF, 0x3DFFFFFF, hv));
		int fg = Ui.mix(0xFF9A98A0, Theme.TEXT, hv);
		Ui.icon("image", x + 11, y + 9, 12, pickerOpen ? Theme.ACCENT : fg);
		Ui.text(Ui.MEDIUM, label, x + 29, y + (30 - size * 1.21f) / 2, size, Theme.TEXT);
		Ui.icon(pickerOpen ? "chevron-up" : "chevron-down", x + cw - 22, y + 10, 10, fg);
		if (!optifineHelp && leaving == null) {
			Ui.click(x, y, cw, 30, () -> pickerOpen = !pickerOpen);
		}
		Ui.opacity(1);
	}

	/**
	 * The background menu under its button: each background in a small live preview, the chosen
	 * one marked; a click shows and saves it. A click anywhere else closes the menu.
	 */
	private void backgroundPicker(float w, float h) {
		float p = Anim.to("bg:picker", pickerOpen && leaving == null ? 1 : 0, 160);
		if (p < 0.01f) {
			return;
		}
		if (pickerOpen) {
			// a click outside closes the menu (and does nothing else)
			Ui.click(0, 0, w, h, () -> pickerOpen = false);
		}
		float pad = 12;
		float gap = 10;
		int columns = 3;
		int rows = (Background.KEYS.length + columns - 1) / columns;
		float pw = PICKER_CARD_W * columns + gap * (columns - 1) + pad * 2;
		float ph = pad + 22 + PICKER_CARD_H * rows + gap * (rows - 1) + pad;
		float x = Math.max(8, pickerRight - pw);
		float y = 52 - (1 - p) * 6;
		Ui.opacity(p);
		Ui.glassBorder(x, y, pw, ph, 14, 0xF2121114, Theme.BORDER_STRONG);
		if (pickerOpen) {
			Ui.click(x, y, pw, ph, () -> { });
		}
		Ui.icon("image", x + pad, y + pad + 1, 12, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.home.background"), x + pad + 18, y + pad, 11, Theme.TEXT);
		int chosen = Background.get();
		for (int i = 0; i < Background.KEYS.length; i++) {
			float cx = x + pad + (i % columns) * (PICKER_CARD_W + gap);
			float cy = y + pad + 22 + (i / columns) * (PICKER_CARD_H + gap);
			pickerCard(i, cx, cy, i == chosen, p);
		}
		Ui.opacity(1);
	}

	private void pickerCard(int id, float x, float y, boolean chosen, float alpha) {
		boolean own = id == Background.OWN_IMAGE;
		boolean missing = own && !Background.hasOwnImage();
		boolean over = pickerOpen && Ui.hover(x, y, PICKER_CARD_W, PICKER_CARD_H);
		float hv = Anim.to(CARD_KEYS[id], over ? 1 : 0, 120);
		Ui.round(x, y, PICKER_CARD_W, PICKER_CARD_H, 10, Ui.mix(0xFF19181C, 0xFF222126, hv));
		// the preview, live
		float tx = x + 4;
		float ty = y + 4;
		float tw = PICKER_CARD_W - 8;
		float th = PICKER_THUMB_H - 4;
		if (missing) {
			Ui.round(tx, ty, tw, th, 7, 0xFF0E0D10);
			Ui.icon("folder-plus", tx + tw / 2 - 9, ty + 12, 18, Ui.mix(0xFF8A888F, Theme.TEXT, hv));
			float ly = ty + 38;
			for (String line : wrapped(Ui.REGULAR, t("rago.home.background_own_hint"), 7.5f, tw - 16)) {
				Ui.textCentered(Ui.REGULAR, line, tx + tw / 2, ly, 7.5f, 0xFF9A98A0);
				ly += 10;
			}
		} else {
			Ui.scissor(tx, ty, tw, th);
			Background.draw(id, tx, ty, tw, th, 0, 0, 1, alpha);
			Ui.endScissor();
			Ui.outline(tx, ty, tw, th, 7, 0x22FFFFFF);
		}
		Ui.outline(x, y, PICKER_CARD_W, PICKER_CARD_H, 10, chosen ? Theme.ACCENT : Ui.mix(0x1AFFFFFF, 0x40FFFFFF, hv));
		if (chosen) {
			Ui.round(x + PICKER_CARD_W - 25, y + 9, 16, 16, 8, Theme.ACCENT);
			Ui.icon("check", x + PICKER_CARD_W - 22, y + 12, 10, 0xFFFFFFFF);
		}
		// its name, and what it costs
		float ny = y + PICKER_THUMB_H + 8;
		Ui.text(Ui.MEDIUM, t(Background.KEYS[id]), x + 9, ny, 10, chosen ? Theme.TEXT : 0xFFD4D2D9);
		String tag = id == Background.NIGHT_DESERT ? t("rago.home.background_video")
			: id == Background.OWN_IMAGE ? null : t("rago.home.background_light");
		if (tag != null) {
			Ui.textRight(Ui.REGULAR, tag, x + PICKER_CARD_W - 9, ny + 1.5f, 8, id == Background.NIGHT_DESERT ? 0xFF8A888F : 0xFF6FCF7F);
		}
		if (pickerOpen) {
			Ui.click(x, y, PICKER_CARD_W, PICKER_CARD_H, () -> {
				if (missing) {
					openConfigFolder();
				} else {
					RagoClient.setHomeBackground(id);
				}
			});
		}
	}

	private void openConfigFolder() {
		File dir = RagoClient.configDir() != null ? RagoClient.configDir().toFile() : new File(mc.mcDataDir, "config");
		dir.mkdirs();
		try {
			Desktop.getDesktop().open(dir);
		} catch (Exception | LinkageError e) {
			Sys.openURL(dir.toURI().toString());
		}
	}

	/** The account in use, top right; opens the Accounts screen. */
	private void account(float w) {
		if (!Accounts.available()) {
			return;
		}
		String name = mc.getSession().getUsername();
		float size = 10;
		float cw = accountWidth();
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
		for (String line : wrapped(Ui.REGULAR, t("rago.optifine.body"), 10, dw - 40)) {
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
		} else if (keyCode == 1 && pickerOpen) {
			pickerOpen = false;
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
