package gg.rago.gui;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.TextColors;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.modules.Overlays;
import gg.rago.optimize.OptiFineFields;
import gg.rago.overlays.AtlasPaint;
import gg.rago.overlays.BlockArt;
import gg.rago.overlays.SkyArt;
import gg.rago.overlays.UserOverlays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.init.Items;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;
import org.lwjgl.opengl.GL11;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The Overlays mod's page in the menu. Each choice is shown as what it looks like: wool and clay as a
 * little platform of blocks in the team colours, skies as a view of the horizon, block hits as a
 * moving sword. Then the overlay packs found in the player's resourcepacks folder, to turn on over the
 * main pack (applied in one reload, when the menu closes or on Apply).
 */
final class OverlaysPanel {
	/** The wool on the cards: the red, blue, green (lime wool) and yellow Bed Wars teams. */
	private static final int[] WOOL_SHOWN = {14, 11, 5, 4};
	/** The clay on the cards: The Bridge's red and blue, as a checkerboard. */
	private static final int[] CLAY_SHOWN = {14, 11, 11, 14};
	private static final float GAP = 8;
	private static final int THUMB_W = 224;
	private static final int THUMB_H = 126;
	/** How often the resourcepacks folder is read again while the page is open, in ms. */
	private static final long RESCAN_MS = 4000;
	/** A block hit every this many ms on the moving sword; the swing takes the first 300 (6 ticks). */
	private static final long HIT_PERIOD_MS = 900;

	/** Views of the skies, painted once a game on the overlays' worker: the game's sky, then Rago's. */
	private static CompletableFuture<int[][]> skyJob;
	private static ResourceLocation[] skyThumbs;

	private final Minecraft mc = Minecraft.getMinecraft();
	private final Map<BlockArt.Style, Strip> wool = new EnumMap<>(BlockArt.Style.class);
	private final Map<BlockArt.Style, Strip> clay = new EnumMap<>(BlockArt.Style.class);
	private String colorsShown = "";
	/** The packs' own wool and clay (the "Your pack" cards), one texture each, read again after a reload. */
	private final ResourceLocation[] packWool = new ResourceLocation[WOOL_SHOWN.length];
	private final ResourceLocation[] packClay = new ResourceLocation[CLAY_SHOWN.length];
	private int packReloads = -1;

	private List<UserOverlays.Found> found = Collections.emptyList();
	private CompletableFuture<List<UserOverlays.Found>> scanning;
	private long scannedAt;
	/** Whether the folder has been read once since the page opened (until then: "looking"). */
	private boolean scannedOnce;
	/** Overlays turned on or off here and not applied yet: true to turn on. */
	private final Map<ResourcePackRepository.Entry, Boolean> pending = new LinkedHashMap<>();
	private Consumer<String> tooltip = s -> { };

	/** A row of 16x16 blocks in one texture, painted again when the team colours change. */
	private static final class Strip {
		final DynamicTexture texture;
		final ResourceLocation location;
		final int count;

		Strip(TextureManager textures, String name, int count) {
			this.count = count;
			this.texture = new DynamicTexture(16 * count, 16);
			this.location = textures.getDynamicTextureLocation(name, texture);
		}

		void set(int slot, int[] pixels) {
			int[] data = texture.getTextureData();
			int w = 16 * count;
			for (int y = 0; y < 16; y++) {
				System.arraycopy(pixels, y * 16, data, y * w + slot * 16, 16);
			}
		}

		float u0(int slot) {
			return slot / (float) count;
		}

		float u1(int slot) {
			return (slot + 1) / (float) count;
		}
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	/** Draws the page from {@code y}; returns where it ends. */
	float draw(Overlays m, float x, float y, float w, boolean inside, Consumer<String> tooltip) {
		this.tooltip = tooltip;
		prepareBlocks(m);
		float cy = header("brick-wall", t("rago.overlays.wool"), t("rago.overlays.wool.help"), x, y, w);
		cy = blockCards("wool", m.woolOption(), false, x, cy, w, inside) + 10;
		cy = header("brick-wall", t("rago.overlays.clay"), t("rago.overlays.clay.help"), x, cy, w);
		cy = blockCards("clay", m.clayOption(), true, x, cy, w, inside) + 10;
		cy = teamColorsRow(m.teamColorsOption(), x, cy, w, inside) + 20;
		cy = header("cloud-sun", t("rago.overlays.sky"), t("rago.overlays.sky.help"), x, cy, w);
		cy = skyCards(m.skyOption(), x, cy, w, inside) + 10;
		cy = packSkiesRow(m, x, cy, w, inside) + 20;
		cy = header("sword", t("rago.overlays.blockhit"), t("rago.overlays.blockhit.help"), x, cy, w);
		cy = hitCards(m.blockhitOption(), x, cy, w, inside) + 20;
		cy = header("layers", t("rago.overlays.yours"), t("rago.overlays.yours.help"), x, cy, w);
		return userOverlays(x, cy, w, inside);
	}

	/** The menu is closing: overlays turned on or off here are applied now, in one reload. */
	void close() {
		apply();
	}

	private void apply() {
		if (pending.isEmpty()) {
			return;
		}
		Map<ResourcePackRepository.Entry, Boolean> changes = new LinkedHashMap<>(pending);
		pending.clear();
		scannedAt = 0;
		try {
			UserOverlays.apply(changes);
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("Overlays: could not apply the overlay packs", e);
		}
	}

	// ---- parts of the page ----------------------------------------------------------------------

	private static float header(String icon, String title, String help, float x, float y, float w) {
		Ui.icon(icon, x + 2, y - 1, 11, Theme.ACCENT);
		Ui.textSpaced(Ui.SEMIBOLD, title.toUpperCase(Locale.ROOT), x + 19, y, 8.5f, 1.4f, 0xFF7C7A82);
		y += 17;
		for (String line : Ui.wrap(Ui.REGULAR, help, 9.5f, w - 4)) {
			Ui.text(Ui.REGULAR, line, x + 2, y, 9.5f, Theme.MUTED);
			y += 13;
		}
		return y + 6;
	}

	/** A card to pick; the chosen one has the accent border and a check. */
	private static void card(String key, float x, float y, float w, float h, boolean selected) {
		float hv = Widgets.hoverAmount(key, x, y, w, h);
		Ui.glassBorder(x, y, w, h, 8, Ui.mix(0xFF131215, 0xFF1C1B20, hv),
			selected ? Theme.ACCENT : Ui.mix(0x12FFFFFF, 0x30FFFFFF, hv));
		if (selected) {
			Ui.outline(x - 1, y - 1, w + 2, h + 2, 9, Ui.alpha(Theme.ACCENT, 0.45f));
			Ui.circle(x + w - 11, y + 11, 6.5f, Theme.ACCENT);
			Ui.icon("check", x + w - 15.5f, y + 6.5f, 9, 0xFFFFFFFF);
		}
	}

	private static void cardLabel(String label, float cx, float y, float maxW, boolean selected) {
		String s = Ui.ellipsize(Ui.MEDIUM, label, 9.5f, maxW);
		Ui.textCentered(Ui.MEDIUM, s, cx, y, 9.5f, selected ? Theme.TEXT : 0xFFB4B2B9);
	}

	private static void rowFrame(float x, float y, float w, float h) {
		Ui.glassBorder(x, y, w, h, 7, 0xFF131215, 0x12FFFFFF);
	}

	// ---- wool and clay --------------------------------------------------------------------------

	private float blockCards(String what, ChoiceOption option, boolean isClay, float x, float y, float w, boolean inside) {
		List<String> ids = option.ids();
		int n = ids.size();
		float cw = (w - GAP * (n - 1)) / n;
		float s = Math.min(30, (cw - 22) / 2);
		float ch = 14 + 1.5f * s + 12 + 12 + 10;
		for (int i = 0; i < n; i++) {
			String id = ids.get(i);
			float cx = x + i * (cw + GAP);
			boolean selected = option.is(id);
			card(what + ":" + id, cx, y, cw, ch, selected);
			platform(isClay, Overlays.style(id), cx + cw / 2, y + 14, s);
			cardLabel(t("rago.option." + option.key() + "." + id), cx + cw / 2, y + ch - 21, cw - 10, selected);
			if (inside) {
				Ui.click(cx, y, cw, ch, () -> option.select(id));
				if (Ui.hover(cx, y, cw, ch)) {
					tooltip.accept(t("off".equals(id) ? "rago.overlays.your_pack.help" : "rago.overlays.style." + id + ".help"));
				}
			}
		}
		return y + ch;
	}

	/**
	 * Four blocks as a 2x2 piece of floor seen from above, like a bridge: {@code s} the width of one
	 * block, centred on {@code ox}, the top at {@code oy}. Null style: the packs' own blocks.
	 */
	private void platform(boolean isClay, BlockArt.Style style, float ox, float oy, float s) {
		float h = s / 2;
		float q = s / 4;
		// back to front: (0,0), then (1,0) and (0,1), then (1,1)
		int[][] order = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
		for (int[] cell : order) {
			int slot = cell[1] * 2 + cell[0];
			float bx = ox + (cell[0] - cell[1]) * h - h;
			float by = oy + (cell[0] + cell[1]) * q;
			if (style == null) {
				ResourceLocation tex = (isClay ? packClay : packWool)[slot];
				if (tex != null) {
					cube(tex, 0, 1, bx, by, s);
				} else {
					emptyCube(bx, by, s);
				}
			} else {
				Strip strip = (isClay ? clay : wool).get(style);
				if (strip != null) {
					cube(strip.location, strip.u0(slot), strip.u1(slot), bx, by, s);
				}
			}
		}
	}

	/** A block as an isometric cube {@code s} wide: top lit, left and right sides shaded. */
	private static void cube(ResourceLocation tex, float u0, float u1, float x, float y, float s) {
		float cx = x + s / 2;
		float h = s / 2;
		float q = s / 4;
		Ui.textureQuad(tex, cx - h, y + q, cx, y, cx + h, y + q, cx, y + h, u0, 0, u1, 1, 0xFFFFFFFF);
		Ui.textureQuad(tex, cx - h, y + q, cx, y + h, cx, y + s, cx - h, y + 3 * q, u0, 0, u1, 1, 0xFFC8C8C8);
		Ui.textureQuad(tex, cx, y + h, cx + h, y + q, cx + h, y + 3 * q, cx, y + s, u0, 0, u1, 1, 0xFF999999);
	}

	private static void emptyCube(float x, float y, float s) {
		Ui.circle(x + s / 2, y + s / 2, s * 0.3f, 0x22FFFFFF);
	}

	/** Paints the cards' blocks again when the team colours change, and reads the pack's after a reload. */
	private void prepareBlocks(Overlays m) {
		TextureManager textures = mc.getTextureManager();
		if (wool.isEmpty()) {
			for (BlockArt.Style style : BlockArt.Style.values()) {
				String name = style.name().toLowerCase(Locale.ROOT);
				wool.put(style, new Strip(textures, "rago_overlay_wool_" + name, WOOL_SHOWN.length));
				if (style != BlockArt.Style.KNIT) {
					clay.put(style, new Strip(textures, "rago_overlay_clay_" + name, CLAY_SHOWN.length));
				}
			}
		}
		String colors = TextColors.palette() + "|" + m.teamColorsOption().get();
		if (!colors.equals(colorsShown)) {
			colorsShown = colors;
			for (Map.Entry<BlockArt.Style, Strip> e : wool.entrySet()) {
				for (int i = 0; i < WOOL_SHOWN.length; i++) {
					int dye = WOOL_SHOWN[i];
					e.getValue().set(i, BlockArt.pixels(e.getKey(), BlockArt.color(dye, false, m.teamRgb(dye)), 16, false));
				}
				e.getValue().texture.updateDynamicTexture();
			}
			for (Map.Entry<BlockArt.Style, Strip> e : clay.entrySet()) {
				for (int i = 0; i < CLAY_SHOWN.length; i++) {
					int dye = CLAY_SHOWN[i];
					e.getValue().set(i, BlockArt.pixels(e.getKey(), BlockArt.color(dye, true, m.teamRgb(dye)), 16, true));
				}
				e.getValue().texture.updateDynamicTexture();
			}
		}
		if (packReloads != m.reloads()) {
			packReloads = m.reloads();
			readPack(textures, packWool, "wool_colored_", WOOL_SHOWN);
			readPack(textures, packClay, "hardened_clay_stained_", CLAY_SHOWN);
		}
	}

	private static void readPack(TextureManager textures, ResourceLocation[] into, String prefix, int[] dyes) {
		for (int i = 0; i < into.length; i++) {
			if (into[i] != null) {
				textures.deleteTexture(into[i]);
				into[i] = null;
			}
			BufferedImage img = AtlasPaint.packFrame(prefix + BlockArt.DYES[dyes[i]]);
			if (img == null) {
				continue;
			}
			into[i] = textures.getDynamicTextureLocation("rago_overlay_pack", new DynamicTexture(img));
			if (img.getWidth() > 32) {
				// a detailed pack picture made small: smoothed rather than speckled
				smooth(textures.getTexture(into[i]));
			}
		}
	}

	private static void smooth(ITextureObject texture) {
		if (texture == null) {
			return;
		}
		GlStateManager.bindTexture(texture.getGlTextureId());
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
	}

	private float teamColorsRow(BooleanOption option, float x, float y, float w, boolean inside) {
		boolean palette = TextColors.palette() != TextColors.NORMAL;
		float h = 40;
		rowFrame(x, y, w, h);
		float right = x + w - 12;
		String label = Ui.ellipsize(Ui.REGULAR, t("rago.overlays.team_colors"), 10.5f, w - 90);
		Ui.text(Ui.REGULAR, label, x + 12, y + 7, 10.5f, Theme.TEXT);
		String note = t(palette ? "rago.overlays.team_colors.on" : "rago.overlays.team_colors.none");
		Ui.text(Ui.REGULAR, Ui.ellipsize(Ui.REGULAR, note, 8.5f, w - 90), x + 12, y + 23, 8.5f, Theme.DIM);
		Widgets.pill("ovl:team", right - Widgets.PILL_W, y + 10, option.get());
		if (inside) {
			Ui.click(x, y, w, h, option::toggle);
			if (Ui.hover(x, y, w, h)) {
				tooltip.accept(t("rago.overlays.team_colors.help"));
			}
		}
		return y + h;
	}

	// ---- skies ----------------------------------------------------------------------------------

	private float skyCards(ChoiceOption option, float x, float y, float w, boolean inside) {
		thumbnails();
		List<String> ids = option.ids();
		int cols = w >= 380 ? 4 : 3;
		float cw = (w - GAP * (cols - 1)) / cols;
		float tw = cw - 10;
		float th = tw * THUMB_H / THUMB_W;
		float ch = 5 + th + 26;
		float cy = y;
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			int col = i % cols;
			if (i > 0 && col == 0) {
				cy += ch + GAP;
			}
			float cx = x + col * (cw + GAP);
			boolean selected = option.is(id);
			card("sky:" + id, cx, cy, cw, ch, selected);
			ResourceLocation thumb = skyThumbs != null && i < skyThumbs.length ? skyThumbs[i] : null;
			if (thumb != null) {
				Ui.texture(thumb, cx + 5, cy + 5, tw, th, 0, 0, 1, 1, 0xFFFFFFFF);
			} else {
				Ui.round(cx + 5, cy + 5, tw, th, 5, 0xFF1D1C21);
				Ui.icon("loader-circle", cx + 5 + tw / 2 - 7, cy + 5 + th / 2 - 7, 14, Theme.DIM);
			}
			if (selected) {
				// the check over the picture, on a dark disc so it reads on a bright sky
				Ui.circle(cx + cw - 11, cy + 11, 8, 0x99000000);
				Ui.circle(cx + cw - 11, cy + 11, 6.5f, Theme.ACCENT);
				Ui.icon("check", cx + cw - 15.5f, cy + 6.5f, 9, 0xFFFFFFFF);
			}
			cardLabel(t("rago.option." + option.key() + "." + id), cx + cw / 2, cy + ch - 18, cw - 10, selected);
			if (inside) {
				Ui.click(cx, cy, cw, ch, () -> option.select(id));
			}
		}
		return cy + ch;
	}

	private float packSkiesRow(Overlays m, float x, float y, float w, boolean inside) {
		BooleanOption option = m.packSkiesOption();
		float h = 40;
		rowFrame(x, y, w, h);
		Ui.text(Ui.REGULAR, t("rago.overlays.pack_skies"), x + 12, y + 7, 10.5f, Theme.TEXT);
		String status;
		if (OptiFineFields.installed()) {
			status = t("rago.overlays.pack_skies.optifine");
		} else if (m.packSky().isEmpty()) {
			status = t("rago.overlays.pack_skies.none");
		} else {
			status = t("rago.overlays.pack_skies.found", m.packSky().layerCount());
		}
		if (!m.skyOption().is("off")) {
			status += " · " + t("rago.overlays.pack_skies.covered");
		}
		Ui.text(Ui.REGULAR, Ui.ellipsize(Ui.REGULAR, status, 8.5f, w - 90), x + 12, y + 23, 8.5f, Theme.DIM);
		Widgets.pill("ovl:packsky", x + w - 12 - Widgets.PILL_W, y + 10, option.get());
		if (inside) {
			Ui.click(x, y, w, h, option::toggle);
			if (Ui.hover(x, y, w, h)) {
				tooltip.accept(t("rago.overlays.pack_skies.help"));
			}
		}
		return y + h;
	}

	/** Starts painting the sky views once, then puts them in textures when they are ready. */
	private static void thumbnails() {
		if (skyThumbs != null) {
			return;
		}
		if (skyJob == null) {
			skyJob = CompletableFuture.supplyAsync(OverlaysPanel::paintThumbnails, UserOverlays.worker());
		}
		if (!skyJob.isDone()) {
			return;
		}
		int[][] pictures;
		try {
			pictures = skyJob.join();
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("Overlays: could not paint the sky views", e);
			skyThumbs = new ResourceLocation[0];
			return;
		}
		TextureManager textures = Minecraft.getMinecraft().getTextureManager();
		skyThumbs = new ResourceLocation[pictures.length];
		for (int i = 0; i < pictures.length; i++) {
			DynamicTexture d = new DynamicTexture(THUMB_W, THUMB_H);
			System.arraycopy(pictures[i], 0, d.getTextureData(), 0, THUMB_W * THUMB_H);
			d.updateDynamicTexture();
			skyThumbs[i] = textures.getDynamicTextureLocation("rago_overlay_sky", d);
			smooth(d);
		}
	}

	static int[][] paintThumbnails() {
		SkyArt.Kind[] kinds = SkyArt.Kind.values();
		int[][] out = new int[kinds.length + 1][];
		out[0] = round(gameSky(THUMB_W, THUMB_H), THUMB_W, THUMB_H, 9);
		for (int i = 0; i < kinds.length; i++) {
			out[i + 1] = round(SkyArt.preview(kinds[i], THUMB_W, THUMB_H), THUMB_W, THUMB_H, 9);
		}
		return out;
	}

	/** The game's own sky at noon, as the same view: blue above, the fog's paler blue at the horizon. */
	static int[] gameSky(int w, int h) {
		int[] out = new int[w * h];
		double pitch = Math.toRadians(14);
		double f = 1 / Math.tan(Math.toRadians(50));
		int sky = 0x79A7FF;
		int fog = 0xC0D8FF;
		for (int y = 0; y < h; y++) {
			double sy = -(2 * (y + 0.5) / h - 1) * h / w;
			double cy = sy * Math.cos(pitch) + f * Math.sin(pitch);
			double cz = f * Math.cos(pitch) - sy * Math.sin(pitch);
			double el = Math.toDegrees(Math.atan2(cy, cz));
			double k = Math.max(0, Math.min(1, el / 28));
			int c = mixRgb(fog, sky, Math.pow(k, 0.7));
			if (el < 0) {
				c = mixRgb(fog, 0x9DB7E6, Math.min(1, -el / 20));
			}
			for (int x = 0; x < w; x++) {
				out[y * w + x] = 0xFF000000 | c;
			}
		}
		return out;
	}

	private static int mixRgb(int a, int b, double t) {
		int r = (int) Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
		int g = (int) Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return r << 16 | g << 8 | bl;
	}

	/** Rounds the corners of a picture ({@code r} pixels), smoothly, through its alpha. */
	static int[] round(int[] px, int w, int h, int r) {
		for (int y = 0; y < r; y++) {
			for (int x = 0; x < r; x++) {
				double dx = r - x - 0.5;
				double dy = r - y - 0.5;
				double a = Math.max(0, Math.min(1, r - Math.sqrt(dx * dx + dy * dy) + 0.5));
				for (int corner = 0; corner < 4; corner++) {
					int cx = (corner & 1) == 0 ? x : w - 1 - x;
					int cy = (corner & 2) == 0 ? y : h - 1 - y;
					int i = cy * w + cx;
					int alpha = (int) Math.round((px[i] >>> 24) * a);
					px[i] = alpha << 24 | px[i] & 0xFFFFFF;
				}
			}
		}
		return px;
	}

	// ---- block hits -----------------------------------------------------------------------------

	private float hitCards(ChoiceOption option, float x, float y, float w, boolean inside) {
		List<String> ids = option.ids();
		int n = ids.size();
		int cols = w >= 420 ? n : 3;
		float cw = (w - GAP * (cols - 1)) / cols;
		float ch = 74;
		float size = Math.min(40, cw - 24);
		TextureAtlasSprite sword = mc.getRenderItem().getItemModelMesher().getParticleIcon(Items.diamond_sword);
		ITextureObject atlas = mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture);
		if (atlas != null) {
			atlas.setBlurMipmap(false, false);
		}
		float cy = y;
		for (int i = 0; i < n; i++) {
			String id = ids.get(i);
			int col = i % cols;
			if (i > 0 && col == 0) {
				cy += ch + GAP;
			}
			float cx = x + col * (cw + GAP);
			boolean selected = option.is(id);
			boolean hover = inside && Ui.hover(cx, cy, cw, ch);
			card("hit:" + id, cx, cy, cw, ch, selected);
			Overlays.BlockStyle style = "off".equals(id) ? null : Overlays.BlockStyle.valueOf(id.toUpperCase(Locale.ROOT));
			if (sword != null) {
				sword(sword, style, cx + cw / 2, cy + 8 + size / 2, size, selected || hover);
			}
			cardLabel(t("rago.option." + option.key() + "." + id), cx + cw / 2, cy + ch - 19, cw - 8, selected);
			if (inside) {
				Ui.click(cx, cy, cw, ch, () -> option.select(id));
				if (hover) {
					tooltip.accept(t("rago.overlays.hit." + id + ".help"));
				}
			}
		}
		if (atlas != null) {
			atlas.restoreLastBlurMipmap();
		}
		return cy + ch;
	}

	/**
	 * The pack's sword held up to block, moved as the style moves it on each hit (a flat picture of
	 * the movement: turns, slides and the sword coming closer).
	 */
	private static void sword(TextureAtlasSprite sprite, Overlays.BlockStyle style, float cx, float cy, float size, boolean moving) {
		float swing = 0;
		if (moving) {
			float t = (System.currentTimeMillis() % HIT_PERIOD_MS) / 300f;
			swing = t < 1 ? t : 0;
		}
		float s = MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
		float angle = -62;
		float sx = 1;
		float sy = 1;
		float dx = 0;
		float dy = 0;
		if (style != null) {
			switch (style) {
				case CLASSIC:
					angle -= 30 * s;
					dy += size * 0.1f * s;
					break;
				case LOW:
					sx = sy = 0.85f;
					dy += size * 0.1f;
					angle -= 30 * s;
					dy += size * 0.1f * s;
					break;
				case SLIDE:
					dx -= size * 0.12f * s;
					dy += size * 0.22f * s;
					angle -= 14 * s;
					break;
				case THRUST:
					sx = sy = 1 + 0.3f * s;
					dy -= size * 0.05f * s;
					break;
				case TILT:
					sy = MathHelper.cos((float) Math.toRadians(38 * s));
					angle -= 12 * s;
					break;
				default:
					break;
			}
		}
		float a = (float) Math.toRadians(angle);
		float cos = MathHelper.cos(a);
		float sin = MathHelper.sin(a);
		float hw = size / 2 * sx;
		float hh = size / 2 * sy;
		float[][] corners = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
		float[] p = new float[8];
		for (int i = 0; i < 4; i++) {
			float lx = corners[i][0];
			float ly = corners[i][1];
			p[i * 2] = cx + dx + lx * cos - ly * sin;
			p[i * 2 + 1] = cy + dy + lx * sin + ly * cos;
		}
		Ui.textureQuad(TextureMap.locationBlocksTexture, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7],
			sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV(), 0xFFFFFFFF);
	}

	// ---- the player's overlay packs ---------------------------------------------------------------

	private float userOverlays(float x, float y, float w, boolean inside) {
		long now = System.currentTimeMillis();
		if (scanning == null && now - scannedAt > RESCAN_MS) {
			scannedAt = now;
			try {
				scanning = UserOverlays.scan();
			} catch (RuntimeException e) {
				RagoClient.LOG.warn("Overlays: could not read the resourcepacks folder", e);
			}
		}
		if (scanning != null && scanning.isDone()) {
			try {
				found = scanning.join();
			} catch (RuntimeException e) {
				RagoClient.LOG.warn("Overlays: could not read the overlay packs", e);
			}
			scanning = null;
			scannedOnce = true;
		}
		float cy = y;
		if (found.isEmpty()) {
			List<String> lines = Ui.wrap(Ui.REGULAR, t(scannedOnce ? "rago.overlays.none" : "rago.overlays.looking"), 9.5f, w - 48);
			float h = 22 + lines.size() * 13;
			rowFrame(x, cy, w, h);
			Ui.icon("folder", x + 14, cy + 12, 13, Theme.DIM);
			float ly = cy + 12;
			for (String line : lines) {
				Ui.text(Ui.REGULAR, line, x + 36, ly, 9.5f, Theme.MUTED);
				ly += 13;
			}
			cy += h + 8;
		}
		for (UserOverlays.Found f : found) {
			cy = overlayRow(f, x, cy, w, inside) + 4;
		}
		cy += 4;
		float bw = Math.min(200, (w - GAP) / 2);
		Widgets.button(x, cy, bw, 26, "folder-open", t("rago.overlays.open_folder"), false, 10);
		if (inside) {
			Ui.click(x, cy, bw, 26, this::openFolder);
		}
		if (!pending.isEmpty()) {
			Widgets.button(x + bw + GAP, cy, bw, 26, "refresh-cw", t("rago.overlays.apply"), true, 10);
			if (inside) {
				Ui.click(x + bw + GAP, cy, bw, 26, this::apply);
			}
			cy += 32;
			Ui.text(Ui.REGULAR, t("rago.overlays.pending", pending.size()), x + 2, cy, 9, Theme.DIM);
			cy += 12;
		} else {
			cy += 26;
		}
		return cy;
	}

	private float overlayRow(UserOverlays.Found f, float x, float y, float w, boolean inside) {
		float h = 34;
		ResourcePackRepository.Entry entry = f.entry;
		boolean active = UserOverlays.active(entry);
		Boolean wanted = pending.get(entry);
		boolean on = wanted != null ? wanted : active;
		rowFrame(x, y, w, h);
		float right = x + w - 12;
		Widgets.pill("ovl:pack:" + f.name(), right - Widgets.PILL_W, y + 7, on);
		// what it changes, as small chips left of the switch
		float chipsRight = right - Widgets.PILL_W - 10;
		float cx = chipsRight;
		for (UserOverlays.Part part : f.parts) {
			String label = t("rago.overlays.part." + part.name().toLowerCase(Locale.ROOT));
			float cw = Ui.textWidth(Ui.MEDIUM, label, 8.5f) + 12;
			cx -= cw;
			Ui.roundBorder(cx, y + 9, cw, 16, 8, 0xCC141316, 0x1FFFFFFF);
			Ui.text(Ui.MEDIUM, label, cx + 6, y + 9 + (16 - 8.5f * 1.21f) / 2, 8.5f, 0xFFC4C2CA);
			cx -= 4;
		}
		String name = Ui.ellipsize(Ui.REGULAR, plain(f.name()), 10.5f, Math.max(40, cx - x - 24));
		Ui.text(Ui.REGULAR, name, x + 12, y + (h - 10.5f * 1.21f) / 2, 10.5f, Theme.TEXT);
		if (wanted != null) {
			// changed here, applied when the menu closes
			Ui.circle(x + 6, y + h / 2, 2, Theme.ACCENT);
		}
		if (inside) {
			Ui.click(x, y, w, h, () -> {
				boolean next = !(pending.containsKey(entry) ? pending.get(entry) : UserOverlays.active(entry));
				if (next == UserOverlays.active(entry)) {
					pending.remove(entry);
				} else {
					pending.put(entry, next);
				}
			});
		}
		return y + h;
	}

	private void openFolder() {
		File dir = mc.getResourcePackRepository().getDirResourcepacks();
		try {
			Desktop.getDesktop().open(dir);
		} catch (Exception e) {
			Sys.openURL(dir.toURI().toString());
		}
	}

	/** A pack's name without its colour codes and without ".zip". */
	static String plain(String name) {
		String p = EnumChatFormatting.getTextWithoutFormattingCodes(name == null ? "" : name);
		p = p == null ? "" : p.trim();
		return p.toLowerCase(Locale.ROOT).endsWith(".zip") ? p.substring(0, p.length() - 4) : p;
	}
}
