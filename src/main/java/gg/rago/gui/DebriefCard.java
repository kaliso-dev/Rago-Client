package gg.rago.gui;

import gg.rago.bedwars.Debrief;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The debrief of a Bed Wars game drawn as one picture (1280 x 720, Rago's dark red and black): what
 * the debrief screen shows is exactly what is copied or saved, ready to paste into Discord. Drawn
 * with Java 2D, so it needs no OpenGL and can be tested on its own.
 */
public final class DebriefCard {
	public static final int W = 1280;
	public static final int H = 720;
	private static final float M = 56;

	/** Text in the game's language: a translation key and its arguments. */
	public interface Text {
		String get(String key, Object... args);
	}

	/** The games of this session so far, for the footer. */
	public static final class Session {
		final int games;
		final int wins;
		final int finalKills;

		public Session(int games, int wins, int finalKills) {
			this.games = games;
			this.wins = wins;
			this.finalKills = finalKills;
		}
	}

	private static final Color BG = new Color(0x0B0A0D);
	private static final Color TILE = new Color(0x141317);
	private static final Color LINE = new Color(255, 255, 255, 20);
	private static final Color TRACK = new Color(0x26252B);
	private static final Color TEXT = new Color(0xF4F4F5);
	private static final Color MUTED = new Color(0x9A98A0);
	private static final Color DIM = new Color(0x6B6970);
	private static final Color ACCENT = new Color(0xF2392C);
	private static final Color GOLD = new Color(0xFACC15);
	private static final Color BLUE = new Color(0x60A5FA);
	private static final Color GREEN = new Color(0x34D399);
	private static final Color PURPLE = new Color(0xA78BFA);

	/** The kinds drawn on the timeline (upgrades go under it), in the legend's order. */
	private static final Debrief.Kind[] LEGEND = {Debrief.Kind.FINAL_KILL, Debrief.Kind.KILL, Debrief.Kind.BED,
		Debrief.Kind.DEATH, Debrief.Kind.FINAL_DEATH, Debrief.Kind.BED_LOST, Debrief.Kind.UPGRADE};

	private static final Map<String, Font> FONTS = new HashMap<>();
	private static BufferedImage logo;

	private DebriefCard() {
	}

	public static BufferedImage render(Debrief d, Session session, Text t, Locale locale, boolean signature) {
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			Color result = resultColor(d.result());
			background(g, result);
			header(g, d, t, locale, result);
			tiles(g, d, t);
			timeline(g, d, t);
			facts(g, d, t, locale);
			footer(g, session, t, locale, signature);
		} finally {
			g.dispose();
		}
		return img;
	}

	private static Color resultColor(Debrief.Result r) {
		if (r == Debrief.Result.WIN) {
			return GREEN;
		}
		return r == Debrief.Result.LEFT || r == null ? MUTED : ACCENT;
	}

	private static void background(Graphics2D g, Color result) {
		g.setColor(BG);
		g.fillRect(0, 0, W, H);
		glow(g, 150, -80, 640, ACCENT, 0.22f);
		glow(g, W - 100, H + 60, 560, result, 0.11f);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(2));
		g.drawRect(1, 1, W - 3, H - 3);
	}

	private static void glow(Graphics2D g, float x, float y, float r, Color c, float alpha) {
		Color in = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(alpha * 255));
		Color out = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), r, new float[] {0, 1}, new Color[] {in, out},
			MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillRect(0, 0, W, H);
	}

	private static void header(Graphics2D g, Debrief d, Text t, Locale locale, Color result) {
		BufferedImage l = logo();
		if (l != null) {
			g.drawImage(l, (int) M, 48, 54, 54, null);
		}
		float tx = M + 72;
		text(g, t.get("rago.debrief.card_title").toUpperCase(locale), font("poppins-600.ttf", 30, 0.04f), TEXT, tx, 82);

		// the result, right
		String label = t.get("rago.debrief.result." + (d.result() == null ? "left" : d.result().name().toLowerCase(Locale.ROOT))).toUpperCase(locale);
		Font chipFont = font("poppins-600.ttf", 19, 0.1f);
		float cw = width(g, label, chipFont) + 46;
		float cx = W - M - cw;
		RoundRectangle2D chip = new RoundRectangle2D.Float(cx, 52, cw, 46, 46, 46);
		g.setColor(alpha(result, 0.14f));
		g.fill(chip);
		g.setColor(alpha(result, 0.6f));
		g.setStroke(new BasicStroke(1.5f));
		g.draw(chip);
		text(g, label, chipFont, result, cx + 23, 82);

		List<String> parts = new ArrayList<>();
		parts.add(d.player);
		parts.add(mode(d, t));
		if (!d.map().isEmpty()) {
			parts.add(d.map());
		}
		parts.add(clock(d.seconds()));
		if (d.endedAt() > 0) {
			parts.add(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(new Date(d.endedAt())));
		}
		Font sub = font("inter-500.ttf", 19, 0);
		text(g, fit(g, String.join("  ·  ", parts), sub, cx - tx - 24), sub, MUTED, tx, 112);
	}

	private static String mode(Debrief d, Text t) {
		if (!d.mode().isEmpty()) {
			return t.get("rago.debrief.mode." + d.mode());
		}
		return d.teams() > 0 ? t.get("rago.debrief.mode.teams", d.teams()) : "Bed Wars";
	}

	private static void tiles(Graphics2D g, Debrief d, Text t) {
		float gap = 20;
		float w = (W - 2 * M - 3 * gap) / 4;
		float y = 150;
		float h = 178;
		String voids = d.voidDeaths() > 0 ? t.get("rago.debrief.void_deaths", d.voidDeaths()) : null;
		tile(g, M, y, w, h, t.get("rago.debrief.final_kills"), d.finalKills(), GOLD, null);
		tile(g, M + (w + gap), y, w, h, t.get("rago.debrief.kills"), d.kills(), TEXT, null);
		tile(g, M + 2 * (w + gap), y, w, h, t.get("rago.debrief.beds"), d.beds(), BLUE, null);
		tile(g, M + 3 * (w + gap), y, w, h, t.get("rago.debrief.deaths"), d.deaths(), ACCENT, voids);
	}

	private static void tile(Graphics2D g, float x, float y, float w, float h, String label, int value, Color color, String caption) {
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 40, 40);
		g.setColor(TILE);
		g.fill(r);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
		g.setColor(color);
		g.fill(new Ellipse2D.Float(x + 26, y + 28, 12, 12));
		Font lf = font("inter-500.ttf", 19, 0);
		text(g, fit(g, label, lf, w - 70), lf, MUTED, x + 48, y + 41);
		text(g, String.valueOf(value), font("poppins-600.ttf", 82, 0), TEXT, x + 24, y + 134);
		if (caption != null) {
			Font cf = font("inter-500.ttf", 16, 0);
			text(g, fit(g, caption, cf, w - 48), cf, DIM, x + 26, y + 162);
		}
	}

	private static void timeline(Graphics2D g, Debrief d, Text t) {
		text(g, t.get("rago.debrief.timeline").toUpperCase(Locale.ROOT), font("inter-600.ttf", 15, 0.12f), DIM, M, 382);
		float x0 = M + 10;
		float x1 = W - M - 10;
		float y = 442;
		int total = Math.max(60, d.seconds());
		g.setColor(TRACK);
		g.fill(new RoundRectangle2D.Float(x0, y - 3, x1 - x0, 6, 6, 6));

		// a mark every 5 minutes (10 in a long game), then the start and the end
		Font small = font("inter-500.ttf", 14, 0);
		int step = total > 60 * 60 ? 600 : 300;
		for (int s = step; s < total; s += step) {
			float x = x0 + (x1 - x0) * s / total;
			if (x1 - x < 70) {
				break;
			}
			g.setColor(TRACK);
			g.fill(new RoundRectangle2D.Float(x - 1, y - 9, 2, 18, 2, 2));
			String c = clock(s);
			text(g, c, small, DIM, x - width(g, c, small) / 2, y + 30);
		}
		text(g, "0:00", small, DIM, x0, y + 30);
		String end = clock(d.seconds());
		text(g, end, small, DIM, x1 - width(g, end, small), y + 30);

		Set<Debrief.Kind> seen = EnumSet.noneOf(Debrief.Kind.class);
		// your bed's fall first, as a line across the track, so the marks stay on top
		for (Debrief.Moment m : d.moments()) {
			if (m.kind == Debrief.Kind.BED_LOST) {
				float x = x0 + (x1 - x0) * Math.min(m.at, total) / total;
				g.setColor(ACCENT);
				g.fill(new RoundRectangle2D.Float(x - 1.5f, y - 40, 3, 56, 3, 3));
				seen.add(m.kind);
			} else if (m.kind == Debrief.Kind.UPGRADE) {
				float x = x0 + (x1 - x0) * Math.min(m.at, total) / total;
				marker(g, m.kind, x, y + 16, 1);
				seen.add(m.kind);
			}
		}
		// the rest on the track, raised a step when they would cover the one before
		float[] lastX = {-1e9f, -1e9f, -1e9f};
		for (Debrief.Moment m : d.moments()) {
			if (m.kind == Debrief.Kind.BED_LOST || m.kind == Debrief.Kind.UPGRADE) {
				continue;
			}
			float x = x0 + (x1 - x0) * Math.min(m.at, total) / total;
			int level = 0;
			while (level < 2 && x - lastX[level] < 22) {
				level++;
			}
			lastX[level] = x;
			marker(g, m.kind, x, y - level * 26, 1);
			seen.add(m.kind);
		}

		// the legend: only what happened
		Font lf = font("inter-500.ttf", 15, 0);
		float lx = M;
		for (Debrief.Kind k : LEGEND) {
			if (!seen.contains(k)) {
				continue;
			}
			marker(g, k, lx + 8, 504, 0.8f);
			String label = t.get("rago.debrief.legend." + k.name().toLowerCase(Locale.ROOT));
			lx += 24;
			lx += text(g, label, lf, MUTED, lx, 509) + 30;
		}
		if (seen.isEmpty()) {
			text(g, t.get("rago.debrief.nothing"), lf, DIM, M, 509);
		}
	}

	/** A mark of the timeline, centred on (x, y). */
	private static void marker(Graphics2D g, Debrief.Kind k, float x, float y, float s) {
		switch (k) {
			case KILL:
				g.setColor(BG);
				g.fill(new Ellipse2D.Float(x - 9 * s, y - 9 * s, 18 * s, 18 * s));
				g.setColor(TEXT);
				g.fill(new Ellipse2D.Float(x - 6.5f * s, y - 6.5f * s, 13 * s, 13 * s));
				break;
			case FINAL_KILL:
				g.setColor(BG);
				g.fill(diamond(x, y, 14 * s));
				g.setColor(GOLD);
				g.fill(diamond(x, y, 10.5f * s));
				break;
			case BED:
				g.setColor(BG);
				g.fill(new RoundRectangle2D.Float(x - 10 * s, y - 10 * s, 20 * s, 20 * s, 8 * s, 8 * s));
				g.setColor(BLUE);
				g.fill(new RoundRectangle2D.Float(x - 7 * s, y - 7 * s, 14 * s, 14 * s, 5 * s, 5 * s));
				break;
			case DEATH:
			case FINAL_DEATH:
				float r = (k == Debrief.Kind.FINAL_DEATH ? 8 : 6.5f) * s;
				g.setColor(BG);
				g.fill(new Ellipse2D.Float(x - r - 5 * s, y - r - 5 * s, 2 * r + 10 * s, 2 * r + 10 * s));
				g.setColor(ACCENT);
				g.setStroke(new BasicStroke(3.5f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Line2D.Float(x - r, y - r, x + r, y + r));
				g.draw(new Line2D.Float(x - r, y + r, x + r, y - r));
				if (k == Debrief.Kind.FINAL_DEATH) {
					g.setStroke(new BasicStroke(2 * s));
					g.draw(new Ellipse2D.Float(x - r - 5 * s, y - r - 5 * s, 2 * r + 10 * s, 2 * r + 10 * s));
				}
				break;
			case BED_LOST:
				g.setColor(ACCENT);
				g.fill(new RoundRectangle2D.Float(x - 1.5f * s, y - 10 * s, 3 * s, 20 * s, 3 * s, 3 * s));
				break;
			default:
				Path2D tri = new Path2D.Float();
				tri.moveTo(x, y - 6 * s);
				tri.lineTo(x + 6.5f * s, y + 5 * s);
				tri.lineTo(x - 6.5f * s, y + 5 * s);
				tri.closePath();
				g.setColor(PURPLE);
				g.fill(tri);
				break;
		}
	}

	private static Path2D diamond(float x, float y, float r) {
		Path2D p = new Path2D.Float();
		p.moveTo(x, y - r);
		p.lineTo(x + r, y);
		p.lineTo(x, y + r);
		p.lineTo(x - r, y);
		p.closePath();
		return p;
	}

	/** Up to four short facts under the timeline, the most telling first. */
	private static void facts(Graphics2D g, Debrief d, Text t, Locale locale) {
		List<String[]> facts = new ArrayList<>();
		Debrief.Moment up = d.first(Debrief.Kind.UPGRADE);
		facts.add(new String[] {t.get("rago.debrief.first_upgrade"),
			up == null ? t.get("rago.debrief.none") : (up.detail.isEmpty() ? "" : up.detail + "  ·  ") + clock(up.at)});
		Debrief.Moment lost = d.first(Debrief.Kind.BED_LOST);
		if (lost != null) {
			facts.add(new String[] {t.get("rago.debrief.your_bed"), t.get("rago.debrief.bed_fell", clock(lost.at))});
		} else if (d.bedSeen()) {
			facts.add(new String[] {t.get("rago.debrief.your_bed"), t.get("rago.debrief.bed_stood")});
		}
		if (!Double.isNaN(d.fkdrBefore())) {
			String v = number(locale, d.fkdrBefore());
			if (!Double.isNaN(d.fkdrAfter())) {
				double diff = d.fkdrAfter() - d.fkdrBefore();
				v += "  →  " + number(locale, d.fkdrAfter()) + "  (" + (diff >= 0 ? "+" : "−") + number(locale, Math.abs(diff)) + ")";
			}
			facts.add(new String[] {t.get("rago.debrief.fkdr"), v});
		}
		if (d.count(Debrief.Kind.FINAL_DEATH) > 0) {
			String k = d.finalKiller();
			facts.add(new String[] {t.get("rago.debrief.eliminated_by"), k.isEmpty() ? t.get("rago.debrief.the_void") : k});
		}
		facts.add(new String[] {t.get("rago.debrief.ratio"), number(locale, (d.kills() + d.finalKills()) / (double) Math.max(1, d.deaths()))});

		int n = Math.min(4, facts.size());
		Font lf = font("inter-500.ttf", 16, 0);
		Font vf = font("inter-600.ttf", 21, 0);
		float gap = 20;
		float tw = (W - 2 * M - 3 * gap) / 4;
		float[] need = new float[n];
		boolean fits = true;
		for (int i = 0; i < n; i++) {
			need[i] = Math.max(width(g, facts.get(i)[0], lf), valueWidth(g, facts.get(i)[1], vf));
			fits &= need[i] <= tw;
		}
		// in line with the tiles above; when a value is longer than a tile, the columns take the
		// room the shorter ones leave
		float[] cols = new float[n];
		float[] xs = new float[n];
		if (fits) {
			for (int i = 0; i < n; i++) {
				cols[i] = tw;
				xs[i] = M + i * (tw + gap);
			}
		} else {
			float between = 32;
			float room = W - 2 * M - (n - 1) * between;
			cols = share(need, room);
			float spare = room;
			for (float c : cols) {
				spare -= c;
			}
			float x = M;
			for (int i = 0; i < n; i++) {
				xs[i] = x;
				x += cols[i] + between + (n > 1 ? spare / (n - 1) : 0);
			}
		}
		for (int i = 0; i < n; i++) {
			text(g, fit(g, facts.get(i)[0], lf, cols[i]), lf, DIM, xs[i], 568);
			value(g, facts.get(i)[1], vf, TEXT, xs[i], 598, cols[i]);
		}
	}

	/**
	 * Widths for columns that need {@code need}, in {@code room}: each gets what it needs when that
	 * fits an equal share of what is left, the others share the rest equally.
	 */
	static float[] share(float[] need, float room) {
		int n = need.length;
		float[] out = new float[n];
		boolean[] done = new boolean[n];
		int left = n;
		float rest = room;
		while (left > 0) {
			float each = rest / left;
			boolean placed = false;
			for (int i = 0; i < n; i++) {
				if (!done[i] && need[i] <= each) {
					out[i] = need[i];
					rest -= need[i];
					done[i] = true;
					left--;
					placed = true;
				}
			}
			if (!placed) {
				for (int i = 0; i < n; i++) {
					if (!done[i]) {
						out[i] = each;
					}
				}
				break;
			}
		}
		return out;
	}

	/** How wide {@link #value} draws {@code s}. */
	private static float valueWidth(Graphics2D g, String s, Font f) {
		int i = s.indexOf('\u2192');
		if (i < 0 || f.canDisplay('\u2192')) {
			return width(g, s, f);
		}
		return width(g, s.substring(0, i).trim(), f) + 9 + 16 + 9 + width(g, s.substring(i + 1).trim(), f);
	}

	/** A fact's value; an arrow ("\u2192"), which Rago's fonts lack, is drawn as a line with a head. */
	private static void value(Graphics2D g, String s, Font f, Color c, float x, float baseline, float max) {
		int i = s.indexOf('\u2192');
		if (i < 0 || f.canDisplay('\u2192')) {
			text(g, fit(g, s, f, max), f, c, x, baseline);
			return;
		}
		float lw = text(g, s.substring(0, i).trim(), f, c, x, baseline);
		float aw = 16;
		float ax = x + lw + 9;
		float ay = baseline - f.getSize2D() * 0.36f;
		g.setColor(c);
		g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(ax, ay, ax + aw, ay));
		Path2D head = new Path2D.Float();
		head.moveTo(ax + aw - 5.5f, ay - 5.5f);
		head.lineTo(ax + aw, ay);
		head.lineTo(ax + aw - 5.5f, ay + 5.5f);
		g.draw(head);
		float rx = ax + aw + 9;
		text(g, fit(g, s.substring(i + 1).trim(), f, x + max - rx), f, c, rx, baseline);
	}

	private static void footer(Graphics2D g, Session session, Text t, Locale locale, boolean signature) {
		g.setColor(LINE);
		g.fill(new RoundRectangle2D.Float(M, 640, W - 2 * M, 1.5f, 1, 1));
		Font f = font("inter-500.ttf", 16, 0);
		if (session != null && session.games > 0) {
			String s = t.get("rago.debrief.session", plural(t, locale, "rago.debrief.games", session.games),
				plural(t, locale, "rago.debrief.wins", session.wins), plural(t, locale, "rago.debrief.finals", session.finalKills));
			text(g, s, f, DIM, M, 681);
		}
		Font brand = font("poppins-600.ttf", 17, 0.02f);
		String discord = signature ? "discord.gg/ragooptimizer" : "";
		float right = W - M;
		if (!discord.isEmpty()) {
			float dw = width(g, discord, f);
			text(g, discord, f, MUTED, right - dw, 681);
			right -= dw + 14;
			text(g, "·", f, DIM, right - width(g, "·", f), 681);
			right -= width(g, "·", f) + 14;
		}
		String name = "Rago Client";
		text(g, name, brand, ACCENT, right - width(g, name, brand), 681);
	}

	/** "1 partie", "5 parties": French counts 0 and 1 as one, English only 1. */
	static String plural(Text t, Locale locale, String key, int n) {
		boolean one = "fr".equals(locale.getLanguage()) ? n < 2 : n == 1;
		return t.get(one ? key + "_one" : key, n);
	}

	static String clock(int seconds) {
		int s = Math.max(0, seconds);
		return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
	}

	private static String number(Locale locale, double v) {
		return String.format(locale, "%.2f", v);
	}

	private static Color alpha(Color c, float a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(a * 255));
	}

	private static float text(Graphics2D g, String s, Font f, Color c, float x, float baseline) {
		g.setFont(f);
		g.setColor(c);
		g.drawString(s, x, baseline);
		return width(g, s, f);
	}

	private static float width(Graphics2D g, String s, Font f) {
		return (float) f.getStringBounds(s, g.getFontRenderContext()).getWidth();
	}

	private static String fit(Graphics2D g, String s, Font f, float max) {
		if (width(g, s, f) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && width(g, out + "…", f) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out.trim() + "…";
	}

	/** One of Rago's fonts at a size, with extra space between letters ({@code tracking}, in ems). */
	private static synchronized Font font(String file, float size, float tracking) {
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
	private static synchronized BufferedImage logo() {
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
