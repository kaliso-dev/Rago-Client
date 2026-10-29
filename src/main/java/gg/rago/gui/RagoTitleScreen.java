package gg.rago.gui;

import gg.rago.core.Module;
import gg.rago.core.QuickJoin;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.perf.FrameStats;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.FMLClientHandler;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Rago's home screen, shown instead of the vanilla title screen (can be switched off in the
 * mods menu). Laid out after the design mock-up; every panel shows real data or real actions.
 */
public final class RagoTitleScreen extends GuiScreen {
	private static final String HYPIXEL = "mc.hypixel.net";
	private static final String MINEMEN = "minemen.club";
	private static final int HISTORY = 48;
	private static final long SAMPLE_NS = 250_000_000L;

	private static final float[] fpsHistory = new float[HISTORY];
	private static int historyCount;
	private static long lastSampleNs;

	/** Blurred "world" squares behind the menu, fixed for the session. */
	private static float[][] blocks;

	@Override
	public void initGui() {
		if (blocks == null) {
			Random r = new Random(1337);
			blocks = new float[90][];
			for (int i = 0; i < blocks.length; i++) {
				blocks[i] = new float[]{r.nextFloat(), 0.45f + r.nextFloat() * 0.55f, 30 + r.nextInt(50), r.nextInt(3), 0.03f + r.nextFloat() * 0.07f};
			}
		}
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		sampleFps();
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		background(w, h);
		topBar(w);
		center(w, h);
		float side = Math.min(340, (w - 460) / 2 - 44);
		if (side >= 250) {
			quickJoin(24, 92, side);
			quickPlay(24, h - 44, side);
			news(w - 24 - side, 92, side);
			performance(w - 24 - side, h - 44, side);
		}
		String legal = "Rago Client " + RagoClient.version() + " · " + t("rago.title.legal");
		Ui.textCentered(Ui.MONO, legal, w / 2, h - 24, 10, Theme.DIM);
		Ui.end();
	}

	private static void sampleFps() {
		long now = System.nanoTime();
		if (now - lastSampleNs < SAMPLE_NS) {
			return;
		}
		lastSampleNs = now;
		FrameStats s = RagoClient.frames().stats();
		if (s == FrameStats.EMPTY) {
			return;
		}
		if (historyCount < HISTORY) {
			fpsHistory[historyCount++] = (float) s.avgFps;
		} else {
			System.arraycopy(fpsHistory, 1, fpsHistory, 0, HISTORY - 1);
			fpsHistory[HISTORY - 1] = (float) s.avgFps;
		}
	}

	// ---- background -----------------------------------------------------------------------------

	private static void background(float w, float h) {
		Ui.rect(0, 0, w, h, Theme.BG);
		int[] tints = {0xFF2F6B3A, 0xFF5A3A22, 0xFF2A4A2F};
		for (float[] b : blocks) {
			float size = b[2];
			float x = (float) Math.floor(b[0] * w / size) * size;
			float y = (float) Math.floor(b[1] * h / size) * size;
			Ui.rect(x, y, size, size, Ui.alpha(tints[(int) b[3]], b[4]));
		}
		Ui.gradient(0, 0, w, h * 0.6f, Theme.BG, 0x000B0A0D);
		Ui.glow(w / 2, h * 0.3f, 560, Ui.alpha(0xFF7A1410, 0.35f));
		Ui.glow(0, h, 520, Ui.alpha(0xFF5A0E0A, 0.30f));
		Ui.glow(w, 0, 420, Ui.alpha(0xFF5A0E0A, 0.22f));
	}

	// ---- top bar --------------------------------------------------------------------------------

	private void topBar(float w) {
		float x = 24;
		float y = 16;
		float bh = 52;
		Ui.roundBorder(x, y, w - 48, bh, bh / 2, 0xD90F0E11, Theme.BORDER);
		Ui.logo(x + 22, y + 15, 22, Theme.ACCENT);
		float tx = x + 54;
		tx += Ui.text(Ui.BOLD, "Rago", tx, y + 15, 18, Theme.TEXT);
		Ui.text(Ui.REGULAR, " Client", tx, y + 15, 18, 0xFFD4D4D8);

		float nx = x + 200;
		nx = navItem(nx, y + 8, "house", t("rago.title.home"), true, null);
		nx = navItem(nx, y + 8, "server", t("rago.title.servers"), false, () -> mc.displayGuiScreen(new GuiMultiplayer(this)));
		nx = navItem(nx, y + 8, "layout-grid", "Mods", false, () -> mc.displayGuiScreen(new RagoMenuScreen(this)));
		navItem(nx, y + 8, "sliders-horizontal", t("rago.title.settings"), false, () -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)));

		// Right side: account, then FPS
		String name = mc.getSession().getUsername();
		boolean online = isOnlineAccount();
		float pillH = 40;
		float nameW = Math.max(Ui.textWidth(Ui.SEMIBOLD, name, 14), Ui.textWidth(Ui.REGULAR, t("rago.title.offline"), 11) + 12);
		float pw = 8 + 26 + 10 + nameW + 16;
		float px = w - 24 - 6 - pw;
		float py = y + 6;
		Ui.roundBorder(px, py, pw, pillH, pillH / 2, 0xFF1A191D, Theme.BORDER);
		head(px + 8, py + 7, 26);
		Ui.text(Ui.SEMIBOLD, name, px + 44, py + 5, 14, Theme.TEXT);
		Ui.circle(px + 47, py + 28, 3, online ? Theme.GREEN : Theme.DIM);
		Ui.text(Ui.REGULAR, online ? "Microsoft" : I18n.format("rago.title.offline"), px + 55, py + 21, 11, Theme.MUTED);

		FrameStats s = RagoClient.frames().stats();
		String fps = s == FrameStats.EMPTY ? "-- FPS" : String.format(Locale.ROOT, "%.0f FPS", s.avgFps);
		float fw = Ui.textWidth(Ui.MONO, fps, 12) + 52;
		Widgets.statusPill(px - 10 - fw, py + 2, 36, Theme.GREEN, fps);
	}

	private boolean isOnlineAccount() {
		String token = mc.getSession().getToken();
		return token != null && token.length() > 20;
	}

	private float navItem(float x, float y, String icon, String label, boolean active, Runnable action) {
		float h = 36;
		float w = 14 + 16 + 8 + Ui.textWidth(Ui.MEDIUM, label, 14) + 16;
		boolean hover = Ui.hover(x, y, w, h);
		if (active || hover) {
			Ui.round(x, y, w, h, h / 2, active ? 0xFF232127 : 0xFF1A191D);
		}
		int color = active ? Theme.TEXT : hover ? 0xFFE4E4E7 : 0xFFB4B2BA;
		Ui.icon(icon, x + 14, y + 10, 16, color);
		Ui.text(active ? Ui.SEMIBOLD : Ui.MEDIUM, label, x + 38, y + 9, 14, color);
		if (action != null) {
			Ui.click(x, y, w, h, action);
		}
		return x + w + 6;
	}

	private void head(float x, float y, float size) {
		UUID id = mc.getSession().getProfile().getId();
		ResourceLocation skin = id == null ? DefaultPlayerSkin.getDefaultSkinLegacy() : DefaultPlayerSkin.getDefaultSkin(id);
		Ui.round(x - 1, y - 1, size + 2, size + 2, 7, 0xFF2A292E);
		Ui.texture(skin, x, y, size, size, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF);
		Ui.texture(skin, x, y, size, size, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF);
	}

	// ---- centre column --------------------------------------------------------------------------

	private void center(float w, float h) {
		float cx = w / 2;
		// The column below the emblem is 318 units tall; on short windows the emblem shrinks so the
		// icon row stays clear of the footer.
		float e = Math.max(0.55f, Math.min(1, (h - 40 - 318 - 72) / 250f));
		float ey = Math.max(72 + 118 * e, Math.min(h * 0.29f, h - 40 - 318 - 132 * e));
		Ui.glow(cx, ey, 300 * e, Ui.alpha(Theme.ACCENT, 0.18f));
		Ui.ring(cx, ey, 230 * e, Ui.alpha(Theme.ACCENT, 0.10f));
		Ui.ring(cx, ey, 170 * e, Ui.alpha(Theme.ACCENT, 0.20f));
		Ui.ring(cx, ey, 118 * e, Ui.alpha(Theme.ACCENT, 0.45f));
		Ui.circle(cx, ey, 116 * e, 0xF00E0C0F);
		Ui.logo(cx - 62 * e, ey - 62 * e, 124 * e, Theme.ACCENT);

		float y = ey + 132 * e;
		String tag = t("rago.title.tagline");
		float tw = Ui.textSpacedWidth(Ui.MONO, tag, 11, 3);
		Ui.textSpaced(Ui.MONO, tag, cx - tw / 2, y, 11, 3, Theme.MUTED);
		y += 20;
		float a = Ui.textWidth(Ui.BOLD, "Rago ", 64);
		float b = Ui.textWidth(Ui.BOLD, "Client", 64);
		Ui.text(Ui.BOLD, "Rago ", cx - (a + b) / 2, y, 64, Theme.TEXT);
		Ui.text(Ui.BOLD, "Client", cx - (a + b) / 2 + a, y, 64, Theme.ACCENT);
		y += 86;
		Ui.textCentered(Ui.REGULAR, t("rago.title.subtitle"), cx, y, 17, 0xFFC4C2CA);
		y += 46;

		float bw = 440;
		Widgets.button(cx - bw / 2, y, bw, 50, "play", t("rago.title.play_bedwars"), true, 16);
		Ui.click(cx - bw / 2, y, bw, 50, () -> QuickJoin.join(this, "Hypixel", HYPIXEL, "/lobby bedwars", null));
		y += 62;
		float half = (bw - 12) / 2;
		Widgets.button(cx - bw / 2, y, half, 46, "user", t("menu.singleplayer"), false, 15);
		Ui.click(cx - bw / 2, y, half, 46, () -> mc.displayGuiScreen(new GuiSelectWorld(this)));
		Widgets.button(cx + 6, y, half, 46, "globe", t("menu.multiplayer"), false, 15);
		Ui.click(cx + 6, y, half, 46, () -> mc.displayGuiScreen(new GuiMultiplayer(this)));
		y += 60;

		float size = 44;
		float gap = 14;
		String[] icons = {"layout-grid", "swords", "sliders-horizontal", "power"};
		Runnable[] actions = {
			() -> mc.displayGuiScreen(new RagoMenuScreen(this)),
			() -> QuickJoin.join(this, "Minemen Club", MINEMEN, null, null),
			() -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings)),
			() -> mc.shutdown()
		};
		float ix = cx - (icons.length * size + (icons.length - 1) * gap) / 2;
		for (int i = 0; i < icons.length; i++) {
			Widgets.iconButton(ix, y, size, icons[i]);
			Ui.click(ix, y, size, size, actions[i]);
			ix += size + gap;
		}
	}

	// ---- side cards -----------------------------------------------------------------------------

	private static void card(float x, float y, float w, float h) {
		Ui.roundBorder(x, y, w, h, 18, 0xE6111013, Theme.BORDER);
	}

	private static void label(String s, float x, float y) {
		Ui.textSpaced(Ui.MONO, s, x, y, 10.5f, 2, Theme.MUTED);
	}

	private void quickJoin(float x, float y, float w) {
		float h = 44 + 2 * 50 + 8;
		card(x, y, w, h);
		label(t("rago.title.quick_join"), x + 20, y + 20);
		float ry = y + 46;
		serverRow(x, ry, w, "Hypixel", t("rago.title.hypixel_sub"), "bed-double", () -> QuickJoin.join(this, "Hypixel", HYPIXEL, null, null));
		serverRow(x, ry + 50, w, "Minemen Club", t("rago.title.minemen_sub"), "swords", () -> QuickJoin.join(this, "Minemen Club", MINEMEN, null, null));
	}

	private void serverRow(float x, float y, float w, String name, String sub, String icon, Runnable action) {
		Ui.round(x + 18, y + 6, 34, 34, 10, 0xFF1F1D22);
		Ui.icon(icon, x + 27, y + 15, 16, 0xFFD4D4D8);
		Ui.text(Ui.SEMIBOLD, name, x + 64, y + 6, 14, Theme.TEXT);
		Ui.text(Ui.REGULAR, sub, x + 64, y + 24, 12, Theme.MUTED);
		joinPill(x + w - 18 - 58, y + 9, false, action);
	}

	private void joinPill(float x, float y, boolean primary, Runnable action) {
		float w = 58;
		float h = 30;
		boolean hover = Ui.hover(x, y, w, h);
		Ui.round(x, y, w, h, h / 2, primary ? (hover ? Theme.ACCENT_HOVER : Theme.ACCENT) : (hover ? 0xFF34323A : 0xFF27252B));
		Ui.textCentered(Ui.SEMIBOLD, t("rago.title.join"), x + w / 2, y + 7, 13, primary ? 0xFF1A0605 : Theme.TEXT);
		Ui.click(x, y, w, h, action);
	}

	private void quickPlay(float x, float bottom, float w) {
		String[][] modes = {
			{"solo", "bedwars_eight_one", "user"},
			{"doubles", "bedwars_eight_two", "users"},
			{"threes", "bedwars_four_three", "bed-double"},
			{"fours", "bedwars_four_four", "swords"},
		};
		float rowH = 58;
		float h = 70 + modes.length * rowH + 12;
		float y = bottom - h;
		card(x, y, w, h);
		label(t("rago.title.quick_play"), x + 22, y + 22);
		Ui.text(Ui.BOLD, "Bedwars", x + 22, y + 40, 18, Theme.TEXT);
		float ry = y + 72;
		for (String[] m : modes) {
			boolean last = m[1].equals(QuickJoin.lastMode());
			if (last) {
				Ui.roundBorder(x + 12, ry, w - 24, rowH - 4, 14, 0xFF1C1A1F, Theme.BORDER_STRONG);
			}
			Ui.round(x + 24, ry + 10, 34, 34, 10, last ? Theme.ACCENT_SOFT : 0xFF1F1D22);
			Ui.icon(m[2], x + 33, ry + 19, 16, last ? Theme.ACCENT : 0xFFD4D4D8);
			Ui.text(Ui.SEMIBOLD, t("rago.title." + m[0]), x + 70, ry + 9, 15, Theme.TEXT);
			Ui.text(Ui.REGULAR, t("rago.title." + m[0] + "_sub"), x + 70, ry + 29, 12, Theme.MUTED);
			String command = "/play " + m[1];
			String mode = m[1];
			joinPill(x + w - 24 - 58, ry + 12, last, () -> QuickJoin.join(this, "Hypixel", HYPIXEL, command, mode));
			ry += rowH;
		}
	}

	private void news(float x, float y, float w) {
		float h = 92;
		card(x, y, w, h);
		Ui.round(x + 18, y + 18, 56, 56, 14, 0xFF2A1212);
		Ui.logo(x + 31, y + 31, 30, Theme.ACCENT);
		label(t("rago.title.whats_new", RagoClient.version().split("\\+")[0]), x + 90, y + 18);
		Ui.text(Ui.SEMIBOLD, t("rago.title.news_title"), x + 90, y + 36, 15, Theme.TEXT);
		for (String line : Ui.wrap(Ui.REGULAR, t("rago.title.news_body"), 12, w - 110)) {
			Ui.text(Ui.REGULAR, line, x + 90, y + 58, 12, Theme.MUTED);
			y += 16;
		}
	}

	private void performance(float x, float bottom, float w) {
		float h = 312;
		float y = bottom - h;
		card(x, y, w, h);
		label(t("rago.title.performance"), x + 22, y + 22);
		Module profile = RagoClient.competitiveProfile();
		boolean boost = profile != null && profile.isEnabled();
		String badge = t(boost ? "rago.title.boost_on" : "rago.title.boost_off");
		float bw = Ui.textSpacedWidth(Ui.MONO, badge, 9.5f, 1.2f) + 14;
		Ui.round(x + w - 20 - bw, y + 17, bw, 20, 6, boost ? 0x2234D399 : 0x22FFFFFF);
		Ui.textSpaced(Ui.MONO, badge, x + w - 20 - bw + 7, y + 21, 9.5f, 1.2f, boost ? Theme.GREEN : Theme.MUTED);

		FrameStats s = RagoClient.frames().stats();
		boolean have = s != FrameStats.EMPTY;
		String fps = have ? String.format(Locale.ROOT, "%.0f", s.avgFps) : "--";
		float fw = Ui.text(Ui.BOLD, fps, x + 22, y + 44, 46, Theme.TEXT);
		Ui.text(Ui.REGULAR, "FPS", x + 28 + fw, y + 70, 15, Theme.MUTED);
		if (have) {
			boolean stable = s.onePercentLowFps >= s.avgFps * 0.5;
			String line = t(stable ? "rago.title.stable" : "rago.title.unstable") + " · "
				+ t("rago.title.low", String.format(Locale.ROOT, "%.0f", s.onePercentLowFps));
			Ui.text(Ui.MONO, line, x + 22, y + 104, 12, stable ? Theme.GREEN : Theme.YELLOW);
		}
		Ui.sparkline(x + 22, y + 126, w - 44, 70, fpsHistory, historyCount, Theme.GREEN);

		float ty = y + 206;
		float tw = (w - 44 - 16) / 3;
		int on = 0;
		for (Module m : RagoClient.modules().all()) {
			on += m.isEnabled() ? 1 : 0;
		}
		tile(x + 22, ty, tw, t("rago.title.frame"), have ? String.format(Locale.ROOT, "%.1f", s.p50Ms) : "--", "ms");
		int ping = RagoClient.ping().latest();
		tile(x + 22 + tw + 8, ty, tw, t("rago.title.ping"), ping >= 0 ? Integer.toString(ping) : "--", "ms");
		tile(x + 22 + (tw + 8) * 2, ty, tw, t("rago.title.mods_on"), Integer.toString(on), "/" + RagoClient.modules().all().size());

		float fy = y + h - 46;
		float fh = 34;
		Ui.round(x + 16, fy, w - 32, fh, 12, 0x22F2392C);
		Ui.icon("zap", x + 28, fy + 10, 14, Theme.ACCENT);
		boolean optifine = FMLClientHandler.instance().hasOptifine();
		String of = t(optifine ? "rago.title.optifine_yes" : "rago.title.optifine_no") + " · ";
		String status = t(boost ? "rago.title.profile_on" : "rago.title.profile_off");
		float room = w - 32 - 46;
		float size = 12;
		while (size > 10 && Ui.textWidth(Ui.REGULAR, of, size) + Ui.textWidth(Ui.SEMIBOLD, status, size) > room) {
			size -= 0.5f;
		}
		if (Ui.textWidth(Ui.REGULAR, of, size) + Ui.textWidth(Ui.SEMIBOLD, status, size) > room) {
			of = "";
		}
		float lx = x + 50;
		float ly = fy + (fh - size * 1.21f) / 2;
		lx += Ui.text(Ui.REGULAR, of, lx, ly, size, 0xFFD4D4D8);
		Ui.text(Ui.SEMIBOLD, status, lx, ly, size, Theme.TEXT);
		Ui.click(x + 16, fy, w - 32, fh, () -> mc.displayGuiScreen(new RagoMenuScreen(this)));
	}

	private static void tile(float x, float y, float w, String label, String value, String unit) {
		float h = 62;
		Ui.roundBorder(x, y, w, h, 12, 0xFF151417, Theme.BORDER);
		Ui.textSpaced(Ui.MONO, label, x + 12, y + 12, 9.5f, 1.2f, Theme.MUTED);
		float vw = Ui.text(Ui.MONO, value, x + 12, y + 30, 18, Theme.TEXT);
		Ui.text(Ui.MONO, unit, x + 16 + vw, y + 36, 11, Theme.MUTED);
	}

	// ---- input ----------------------------------------------------------------------------------

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button == 0) {
			Ui.updateMouse();
			if (Ui.dispatchClick()) {
				mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
			}
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		// Nothing to close on the home screen; Esc does nothing, as in vanilla.
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
