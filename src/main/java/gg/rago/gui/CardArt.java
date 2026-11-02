package gg.rago.gui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The game's own pictures the debrief card is drawn with: Minecraft's font, item icons, the
 * experience bar, team wool and the default skins, plus the player's skin. They are read once, on
 * the client thread (the resource pack's when it has them), so the card can then be drawn on
 * another thread; a picture that cannot be read is left out of the card.
 */
final class CardArt {
	/** The items the card shows (textures/items/NAME.png). */
	static final String[] ITEMS = {"diamond_sword", "iron_sword", "bed", "bone", "nether_star", "apple_golden",
		"redstone_dust", "blaze_powder", "diamond_pickaxe", "fireball", "ender_pearl", "sugar", "experience_bottle",
		"map_filled", "clock", "name_tag"};
	/** Hypixel's team colour codes and the wool of each team. */
	private static final String TEAM_CODES = "c9aebfd8";
	private static final String[] WOOLS = {"red", "blue", "lime", "yellow", "light_blue", "white", "pink", "gray"};
	/** The unicode pages read for the font: Latin letters, punctuation, arrows, and the stars of the Bed Wars prestiges. */
	private static final int[] PAGES = {0x00, 0x20, 0x21, 0x25, 0x26, 0x27};

	/** Minecraft's font, or null when it cannot be read (the card then uses Rago's). */
	final PixelFont font;
	/** {@code textures/gui/icons.png} (the experience bar), or null. */
	final BufferedImage icons;
	/** The player's skin (64 x 64 or 64 x 32), or null when none could be read. */
	final BufferedImage skin;
	final boolean slim;
	private final Map<String, BufferedImage> items;
	/** Minecraft's default skins, Steve's and Alex's, for a player whose own is not known. */
	private final BufferedImage steve;
	private final BufferedImage alex;

	private CardArt(PixelFont font, BufferedImage icons, BufferedImage skin, boolean slim, Map<String, BufferedImage> items,
		BufferedImage steve, BufferedImage alex) {
		this.font = font;
		this.icons = icons;
		this.skin = skin;
		this.slim = slim;
		this.items = items;
		this.steve = steve;
		this.alex = alex;
	}

	/** The same pictures with another player's skin: {@code skin}, or Minecraft's default one (Alex's when {@code slim}). */
	CardArt withSkin(BufferedImage skin, boolean slim) {
		BufferedImage s = skin != null ? skin : slim ? alex : steve;
		return new CardArt(font, icons, s, slim, items, steve, alex);
	}

	/**
	 * Reads the pictures through {@code open}, which gives a file under {@code assets/minecraft/}
	 * (such as {@code textures/font/ascii.png}) or null. {@code skin}: the player's skin, or null for
	 * Minecraft's default one ({@code slim}: Alex, else Steve).
	 */
	static CardArt load(Function<String, InputStream> open, BufferedImage skin, boolean slim) {
		PixelFont font = null;
		BufferedImage ascii = image(open, "textures/font/ascii.png");
		if (ascii != null && ascii.getWidth() >= 16 && ascii.getHeight() >= 16) {
			byte[] sizes = bytes(open, "font/glyph_sizes.bin");
			Map<Integer, BufferedImage> pages = new HashMap<>();
			if (sizes != null) {
				for (int p : PAGES) {
					BufferedImage page = image(open, String.format("textures/font/unicode_page_%02x.png", p));
					if (page != null && page.getWidth() >= 256) {
						pages.put(p, page);
					}
				}
			}
			font = new PixelFont(ascii, sizes, pages::get);
		}
		Map<String, BufferedImage> items = new HashMap<>();
		for (String name : ITEMS) {
			put(items, name, image(open, "textures/items/" + name + ".png"));
		}
		for (int i = 0; i < WOOLS.length; i++) {
			put(items, "wool_" + TEAM_CODES.charAt(i), image(open, "textures/blocks/wool_colored_" + WOOLS[i] + ".png"));
		}
		BufferedImage steve = image(open, "textures/entity/steve.png");
		BufferedImage alex = image(open, "textures/entity/alex.png");
		BufferedImage player = skin != null ? skin : slim ? alex : steve;
		return new CardArt(font, image(open, "textures/gui/icons.png"), player, slim, items, steve, alex);
	}

	/** An item's icon (16 x 16 at the default size), or null. */
	BufferedImage item(String name) {
		return items.get(name);
	}

	/** The wool of the team with colour code {@code code}, or null. */
	BufferedImage wool(char code) {
		return items.get("wool_" + code);
	}

	/** The first frame of an animated texture (the clock's are stacked squares). */
	private static void put(Map<String, BufferedImage> items, String name, BufferedImage img) {
		if (img == null) {
			return;
		}
		if (img.getHeight() > img.getWidth()) {
			img = img.getSubimage(0, 0, img.getWidth(), img.getWidth());
		}
		items.put(name, img);
	}

	private static BufferedImage image(Function<String, InputStream> open, String path) {
		try (InputStream in = open.apply(path)) {
			if (in == null) {
				return null;
			}
			BufferedImage raw = ImageIO.read(in);
			if (raw == null) {
				return null;
			}
			// one pixel format for all: drawing and tinting stay simple
			BufferedImage out = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = out.createGraphics();
			g.drawImage(raw, 0, 0, null);
			g.dispose();
			return out;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static byte[] bytes(Function<String, InputStream> open, String path) {
		try (InputStream in = open.apply(path)) {
			if (in == null) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
