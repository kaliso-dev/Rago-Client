package gg.rago.gui;

import gg.rago.bedwars.Debrief;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static gg.rago.gui.DebriefCard.firstColor;
import static gg.rago.gui.DebriefCard.plain;
import static gg.rago.gui.DebriefCard.starOf;

/**
 * What the debrief pictures share: Rago's colours and fonts, Minecraft's font, icons and experience
 * bar, your name, and the drawing of panels and glows. Each picture ({@link DebriefCard} and the
 * other styles) lays them out its own way, at its own size.
 */
abstract class CardPainter {
	static final Color BG = new Color(0x0B0A0D);
	static final Color PANEL = new Color(0x141317);
	static final Color PANEL_TOP = new Color(0x1B191F);
	static final Color INSET = new Color(0x1D1B22);
	static final Color LINE = new Color(255, 255, 255, 18);
	static final Color TEXT = new Color(0xF4F4F5);
	static final Color MUTED = new Color(0x9A98A0);
	static final Color DIM = new Color(0x6B6970);
	static final Color ACCENT = new Color(0xF2392C);
	/** Minecraft's chat colours, for what is written in its font. */
	static final int MC_GOLD = 0xFFAA00;
	static final int MC_YELLOW = 0xFFFF55;
	static final int MC_GREEN = 0x55FF55;
	static final int MC_RED = 0xFF5555;
	static final int MC_GRAY = 0xAAAAAA;
	static final int MC_DARK_GRAY = 0x555555;
	static final int MC_WHITE = 0xFFFFFF;
	static final int MC_AQUA = 0x55FFFF;
	static final int MC_BLUE = 0x5555FF;
	static final int MC_PINK = 0xFF55FF;
	static final int MC_PURPLE = 0xAA00AA;
	/** The colour of the experience level over the hotbar. */
	static final int XP_GREEN = 0x80FF20;
	static final int SILVER = 0xC9CED6;
	static final int BRONZE = 0xD08A4E;

	private static final Map<String, Font> FONTS = new HashMap<>();
	private static BufferedImage logo;

	final Debrief d;
	final DebriefCard.Session session;
	final DebriefCard.Text t;
	final Locale locale;
	final boolean signature;
	final CardArt art;
	final String name;
	final Debrief.Career career;
	/** The picture's size. */
	final int cw;
	final int ch;
	Graphics2D g;

	CardPainter(Debrief d, DebriefCard.Session session, DebriefCard.Text t, Locale locale, boolean signature, CardArt art,
		String name, int cw, int ch) {
		this.d = d;
		this.session = session;
		this.t = t;
		this.locale = locale;
		this.signature = signature;
		this.art = art;
		this.name = name == null || name.isEmpty() ? d.player : name;
		this.career = d.careerAfter() != null ? d.careerAfter() : d.careerBefore();
		this.cw = cw;
		this.ch = ch;
	}

	/** Lays the picture out with {@link #g}. */
	abstract void draw();

	/** Draws the picture. */
	final BufferedImage paint() {
		BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			this.g = g;
			draw();
		} finally {
			g.dispose();
			this.g = null;
		}
		return img;
	}

	float chip(BufferedImage icon, String label, float x, float y, float h) {
		return chip(icon, label, x, y, h, 1);
	}

	/** A chip with an icon and a label, {@code k} times the size of the landscape picture's; returns its width. */
	float chip(BufferedImage icon, String label, float x, float y, float h, float k) {
		Font f = font("inter-600.ttf", 18 * k, 0);
		String s = fit(label, f, 300 * k);
		float w = chipWidth(icon, label, k);
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, h, h);
		g.setColor(new Color(255, 255, 255, 12));
		g.fill(r);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
		float tx = x + 18 * k;
		if (icon != null) {
			int size = Math.round(24 * k);
			pixels(icon, tx - 2 * k, y + (h - size) / 2, size);
			tx += 30 * k;
		}
		text(s, f, TEXT, tx, y + h / 2 + 6.5f * k);
		return w;
	}

	/** How wide {@link #chip} draws. */
	float chipWidth(BufferedImage icon, String label, float k) {
		Font f = font("inter-600.ttf", 18 * k, 0);
		return (18 + (icon != null ? 30 : 0) + 18) * k + width(fit(label, f, 300 * k), f);
	}

	/** Chips in a row centred on {@code cx}: {icon, label} each, the last ones left out when they do not fit in {@code max}. */
	void chipRow(List<Object[]> chips, float cx, float y, float h, float max, float k) {
		float total = -10 * k;
		int n = 0;
		for (Object[] c : chips) {
			float w = chipWidth((BufferedImage) c[0], (String) c[1], k) + 10 * k;
			if (total + w > max) {
				break;
			}
			total += w;
			n++;
		}
		float x = cx - total / 2;
		for (int i = 0; i < n; i++) {
			x += chip((BufferedImage) chips.get(i)[0], (String) chips.get(i)[1], x, y, h, k) + 10 * k;
		}
	}

	/** The game's chips: your team's wool and the mode, the map, the length. */
	List<Object[]> gameChips() {
		List<Object[]> chips = new ArrayList<>();
		chips.add(new Object[] {art == null ? null : art.wool(d.teamColor() == 0 ? 'f' : d.teamColor()), mode()});
		if (!d.map().isEmpty()) {
			chips.add(new Object[] {item("map_filled"), d.map()});
		}
		chips.add(new Object[] {item("clock"), DebriefCard.clock(d.seconds())});
		return chips;
	}

	/** You in 3D, {@code h} tall, standing at ({@code cx}, {@code feet}) on a soft shadow. */
	void model(float cx, float feet, int h, double yaw) {
		float sw = h * 0.42f;
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, feet), sw, new float[] {0, 1},
			new Color[] {new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(new java.awt.geom.Ellipse2D.Float(cx - sw, feet - h * 0.045f, sw * 2, h * 0.09f));
		if (art == null || art.skin == null) {
			return;
		}
		try {
			int w = Math.round(h * 0.75f);
			BufferedImage img = SkinRender.render(art.skin, art.slim, w, h, yaw, 9);
			g.drawImage(img, Math.round(cx - w / 2f), Math.round(feet - h + 2), null);
		} catch (RuntimeException e) {
			// a broken skin leaves the place empty rather than the whole picture
		}
	}

	/** The colour of the game's result: gold for a win, grey when left, else Rago's red. */
	Color resultColor() {
		Debrief.Result r = d.result();
		return r == Debrief.Result.WIN ? new Color(MC_GOLD) : r == Debrief.Result.LEFT || r == null ? new Color(MC_GRAY) : ACCENT;
	}

	/** Your grade for the game, or "-" for a game you left. */
	String gradeShown() {
		return d.result() == null || d.result() == Debrief.Result.LEFT ? "-" : DebriefCard.grade(d);
	}

	/** The Discord pill, right-aligned at {@code right}; returns its width. */
	float discord(float right, float y, float h, float size) {
		String discord = "discord.gg/ragooptimizer";
		Font pf = font("inter-600.ttf", size, 0);
		float pw = width(discord, pf) + h * 0.9f;
		RoundRectangle2D pill = new RoundRectangle2D.Float(right - pw, y, pw, h, h, h);
		g.setColor(alpha(ACCENT, 0.16f));
		g.fill(pill);
		g.setColor(alpha(ACCENT, 0.7f));
		g.setStroke(new BasicStroke(1.5f));
		g.draw(pill);
		text(discord, pf, TEXT, right - pw + h * 0.45f, y + h / 2 + size * 0.36f);
		return pw;
	}

	/** The date and time the game ended, in the game's language. */
	String date(boolean time) {
		if (d.endedAt() <= 0) {
			return "";
		}
		DateFormat f = time ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) : DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
		// newer Javas put narrow no-break spaces in dates, which Rago's fonts do not have
		return f.format(new Date(d.endedAt())).replace('\u202F', ' ').replace('\u00A0', ' ');
	}

	String mode() {
		if (!d.mode().isEmpty()) {
			return t.get("rago.debrief.mode." + d.mode());
		}
		return d.teams() > 0 ? t.get("rago.debrief.mode.teams", d.teams()) : "Bed Wars";
	}

	/** Your name with your rank's colour and the rank before it, when the Hypixel API told them. */
	String[] nameParts() {
		String rank = career == null ? "" : career.rank;
		char color = career == null ? 'f' : firstColor(rank, '7');
		return new String[] {rank, "§" + color + name};
	}

	/** How tall {@link #nameLine} draws: one line, or the rank above a long name. */
	float nameHeight(float max, int most) {
		String[] p = nameParts();
		String both = p[0].isEmpty() ? p[1] : p[0] + " " + p[1];
		int scale = fitScale(both, max, most, 2);
		if (scale >= most - 1 || p[0].isEmpty()) {
			return 8 * scale;
		}
		return 8 * (most - 2) + 8 + 8 * fitScale(p[1], max, most, 2);
	}

	/** Your name in big letters, at scale {@code most} at most; returns the line under it. */
	float nameLine(float cx, float y, float max, int most) {
		String[] p = nameParts();
		String both = p[0].isEmpty() ? p[1] : p[0] + " " + p[1];
		int scale = fitScale(both, max, most, 2);
		if (scale >= most - 1 || p[0].isEmpty()) {
			mc(both, cx - mcWidth(both, scale) / 2, y, scale, MC_WHITE);
			return y + 8 * scale;
		}
		// a long rank and name: the rank on its own line, smaller
		mc(p[0], cx - mcWidth(p[0], most - 2) / 2, y, most - 2, MC_WHITE);
		y += 8 * (most - 2) + 8;
		scale = fitScale(p[1], max, most, 2);
		mc(p[1], cx - mcWidth(p[1], scale) / 2, y, scale, MC_WHITE);
		return y + 8 * scale;
	}

	/** The star you are at: worked out from your experience when known, as the bar under it is. */
	int star() {
		return starOf(career);
	}

	void box(float x, float y, float w, float h) {
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 22, 22);
		g.setColor(INSET);
		g.fill(r);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
	}

	/** Minecraft's experience bar from the game's icons, {@code scale} pixels per texel, filled to {@code progress}. */
	void xpBar(float x, float y, int scale, double progress) {
		int fill = (int) Math.round(182 * Math.max(0, Math.min(1, progress)));
		if (art != null && art.icons != null) {
			BufferedImage icons = art.icons;
			int k = Math.max(1, icons.getWidth() / 256);
			Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			int ix = Math.round(x);
			int iy = Math.round(y);
			g.drawImage(icons, ix, iy, ix + 182 * scale, iy + 5 * scale, 0, 64 * k, 182 * k, 69 * k, null);
			if (fill > 0) {
				g.drawImage(icons, ix, iy, ix + fill * scale, iy + 5 * scale, 0, 69 * k, fill * k, 74 * k, null);
			}
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			return;
		}
		g.setColor(new Color(0x26252B));
		g.fill(new RoundRectangle2D.Float(x, y, 182 * scale, 5 * scale, 5 * scale, 5 * scale));
		g.setColor(new Color(XP_GREEN));
		g.fill(new RoundRectangle2D.Float(x, y, fill * scale, 5 * scale, 5 * scale, 5 * scale));
	}

	String number(double v, int decimals) {
		return String.format(locale, "%." + decimals + "f", v);
	}

	/** A count with the language's thousands separator; no-break spaces (French) as plain ones, which every font has. */
	String group(long v) {
		return String.format(locale, "%,d", v).replace('\u202F', ' ').replace('\u00A0', ' ');
	}

	void panel(float x, float y, float w, float h) {
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 30, 30);
		g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + h, PANEL));
		g.fill(r);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
	}

	void glow(float x, float y, float r, Color c, float alpha) {
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), r, new float[] {0, 1},
			new Color[] {alpha(c, alpha), alpha(c, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillRect(0, 0, cw, ch);
	}

	BufferedImage item(String name) {
		return art == null ? null : art.item(name);
	}

	/** A pixel-art picture at {@code size} pixels, texels kept sharp. */
	void pixels(BufferedImage img, float x, float y, int size) {
		Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(img, Math.round(x), Math.round(y), size, size, null);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
	}

	/** Width of text in Minecraft's font at {@code scale} (Rago's font when the game's is missing). */
	float mcWidth(String s, int scale) {
		if (art != null && art.font != null) {
			return art.font.width(s) * scale;
		}
		Font f = mcFallback(scale);
		StringBuilder b = new StringBuilder(plain(s));
		for (int i = 0; i < b.length(); i++) {
			if (!f.canDisplay(b.charAt(i))) {
				b.setCharAt(i, '*');
			}
		}
		return width(b.toString(), f);
	}

	/** Text in Minecraft's font with its shadow, top left at (x, y), colour codes followed. */
	float mc(String s, float x, float y, int scale, int rgb) {
		if (art != null && art.font != null) {
			return art.font.draw(g, s, x, y, scale, rgb, true);
		}
		return coded(s, mcFallback(scale), x, y + 7.2f * scale, rgb);
	}

	/** Text in Minecraft's font with a dark outline, as the game writes your experience level. */
	void outlined(String s, float x, float y, int scale, int rgb) {
		if (art != null && art.font != null) {
			for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
				art.font.draw(g, s, x + o[0] * scale, y + o[1] * scale, scale, 0, false);
			}
			art.font.draw(g, s, x, y, scale, rgb, false);
			return;
		}
		coded(s, mcFallback(scale), x, y + 7.2f * scale, rgb);
	}

	/** The largest scale from {@code most} down to {@code least} at which {@code s} fits in {@code max}. */
	int fitScale(String s, float max, int most, int least) {
		for (int scale = most; scale > least; scale--) {
			if (mcWidth(s, scale) <= max) {
				return scale;
			}
		}
		return least;
	}

	static Font mcFallback(int scale) {
		return font("poppins-600.ttf", 8.6f * scale, 0);
	}

	/** Text with Minecraft colour codes in one of Rago's fonts (when the game's font is missing). */
	float coded(String s, Font f, float x, float baseline, int rgb) {
		float x0 = x;
		int color = rgb;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				int code = "0123456789abcdef".indexOf(Character.toLowerCase(s.charAt(++i)));
				if (code >= 0 || Character.toLowerCase(s.charAt(i)) == 'r') {
					x += text(run.toString(), f, new Color(color), x, baseline);
					run.setLength(0);
					color = code >= 0 ? PixelFont.COLORS[code] : rgb;
				}
				continue;
			}
			// the prestige stars are only in Minecraft's font
			run.append(f.canDisplay(c) ? c : '*');
		}
		x += text(run.toString(), f, new Color(color), x, baseline);
		return x - x0;
	}

	static Color alpha(Color c, float a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(a * 255));
	}

	float text(String s, Font f, Color c, float x, float baseline) {
		g.setFont(f);
		g.setColor(c);
		g.drawString(s, x, baseline);
		return width(s, f);
	}

	float width(String s, Font f) {
		return (float) f.getStringBounds(s, g.getFontRenderContext()).getWidth();
	}

	String fit(String s, Font f, float max) {
		if (width(s, f) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && width(out + "…", f) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out.trim() + "…";
	}

	/** One of Rago's fonts at a size, with extra space between letters ({@code tracking}, in ems). */
	static synchronized Font font(String file, float size, float tracking) {
		String key = file + "/" + size + "/" + tracking;
		Font f = FONTS.get(key);
		if (f == null) {
			Font base = FONTS.get(file);
			if (base == null) {
				try (InputStream in = DebriefCard.class.getResourceAsStream("/assets/rago/fonts/" + file)) {
					base = in == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : Font.createFont(Font.TRUETYPE_FONT, in);
				} catch (IOException | FontFormatException | RuntimeException e) {
					base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
				}
				FONTS.put(file, base);
			}
			Map<TextAttribute, Object> attrs = new HashMap<>();
			attrs.put(TextAttribute.SIZE, size);
			if (tracking != 0) {
				attrs.put(TextAttribute.TRACKING, tracking);
			}
			f = base.deriveFont(attrs);
			FONTS.put(key, f);
		}
		return f;
	}

	/** Rago's emblem in its red. */
	static synchronized BufferedImage logo() {
		if (logo == null) {
			try (InputStream in = DebriefCard.class.getResourceAsStream("/assets/rago/textures/logo.png")) {
				BufferedImage src = in == null ? null : ImageIO.read(in);
				if (src == null) {
					return null;
				}
				BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
				int rgb = ACCENT.getRGB() & 0xFFFFFF;
				for (int y = 0; y < src.getHeight(); y++) {
					for (int x = 0; x < src.getWidth(); x++) {
						out.setRGB(x, y, src.getRGB(x, y) & 0xFF000000 | rgb);
					}
				}
				logo = out;
			} catch (IOException | RuntimeException e) {
				return null;
			}
		}
		return logo;
	}
}
