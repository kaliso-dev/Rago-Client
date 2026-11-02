package gg.rago.gui;

import gg.rago.bedwars.Debrief;
import gg.rago.hypixel.Hypixel;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Random;

/**
 * You as a collectible card (1080 x 1512), drawn as one of Minecraft's item tooltips: your name in
 * the colour of the card's rarity (from your prestige, as Hypixel colours its items' rarities), you
 * in 3D on your prestige's colours, your overall rating and your talent worked out from your stats,
 * your stats as the item's lines, and the card's rarity at the foot. A game graded S+ makes the card
 * holographic, with the shine of enchanted items over it.
 */
final class CollectorCard extends CardPainter {
	static final int W = 1080;
	static final int H = 1512;
	/** The size of one of the tooltip's pixels. */
	private static final int P = 8;
	private static final int CX0 = 56;
	private static final int CY0 = 52;
	private static final int CW = W - 2 * CX0;
	private static final int CH = 1268;
	/** The content's left and right edges inside the card. */
	private static final int L = CX0 + 48;
	private static final int R = CX0 + CW - 48;

	/** A card's rarity, from the star: its colour code and its colour. */
	enum Rarity {
		COMMON('f', 0xFFFFFF), UNCOMMON('a', 0x55FF55), RARE('9', 0x5555FF), EPIC('5', 0xAA00AA), LEGENDARY('6', 0xFFAA00),
		MYTHIC('d', 0xFF55FF);

		final char code;
		final int rgb;

		Rarity(char code, int rgb) {
			this.code = code;
			this.rgb = rgb;
		}
	}

	/** Your talent: its text's key and the number its description shows (null: none). */
	static final class Talent {
		final String key;
		final Object arg;

		Talent(String key, Object arg) {
			this.key = key;
			this.arg = arg;
		}
	}

	CollectorCard(Debrief d, DebriefCard.Session session, DebriefCard.Text t, Locale locale, boolean signature, CardArt art, String name) {
		super(d, session, t, locale, signature, art, name, W, H);
	}

	// ---- what the card says, worked out ----

	/** The rarity of a card at {@code star} (negative: unknown, common). */
	static Rarity rarity(int star) {
		return star >= 2000 ? Rarity.MYTHIC : star >= 1000 ? Rarity.LEGENDARY : star >= 500 ? Rarity.EPIC
			: star >= 300 ? Rarity.RARE : star >= 100 ? Rarity.UNCOMMON : Rarity.COMMON;
	}

	/**
	 * Your overall rating, 1 to 99: from your FKDR, WLR, BBLR and star with the Hypixel API, else
	 * from this game's grade.
	 */
	static int overall(Debrief.Career c, Debrief d) {
		if (c == null || c.star < 0 || Double.isNaN(c.fkdr())) {
			double score = d.finalKills() * 3 + d.kills() + d.beds() * 4 - d.deaths() * 1.5 + (d.result() == Debrief.Result.WIN ? 6 : 0);
			return (int) Math.max(1, Math.min(99, Math.round(40 + score * 1.5)));
		}
		double fk = Math.sqrt(Math.min(1, Math.max(0, c.fkdr()) / 10));
		double wl = Double.isNaN(c.wlr()) ? 0 : Math.sqrt(Math.min(1, Math.max(0, c.wlr()) / 5));
		double bb = Double.isNaN(c.bblr()) ? 0 : Math.sqrt(Math.min(1, Math.max(0, c.bblr()) / 5));
		double st = Math.min(1, Math.log10(1 + Math.max(0, DebriefCard.starOf(c))) / Math.log10(3001));
		return (int) Math.max(1, Math.min(99, Math.round(35 + 64 * (0.4 * fk + 0.25 * wl + 0.15 * bb + 0.2 * st))));
	}

	/** Your talent: what your stats are best at, against what makes a good player at each. */
	static Talent talent(Debrief.Career c) {
		if (c == null || c.star < 0 || c.games <= 0) {
			return new Talent("no_api", null);
		}
		double kpg = c.perGame(c.kills);
		double[] score = {
			Double.isNaN(c.fkdr()) ? 0 : c.fkdr() / 3,
			Double.isNaN(c.bblr()) ? 0 : c.bblr() / 2,
			Double.isNaN(c.wlr()) ? 0 : c.wlr() / 2,
			Double.isNaN(kpg) ? 0 : kpg / 4};
		String[] keys = {"finisher", "bed_breaker", "winner", "rusher"};
		double[] values = {c.fkdr(), c.bblr(), c.wlr(), kpg};
		int best = 0;
		for (int i = 1; i < score.length; i++) {
			if (score[i] > score[best]) {
				best = i;
			}
		}
		if (score[best] >= 1) {
			return new Talent(keys[best], values[best]);
		}
		return c.games >= 1000 ? new Talent("veteran", c.games) : new Talent("all_rounder", null);
	}

	// ---- the picture ----

	@Override
	void draw() {
		int star = career != null && career.star >= 0 ? star() : -1;
		Rarity rarity = rarity(star);
		int prestige = star < 0 ? MC_GRAY : PixelFont.COLORS["0123456789abcdef".indexOf(DebriefCard.firstColor(Hypixel.bedwarsStar(star), '7'))];
		String grade = gradeShown();
		boolean holo = grade.equals("S+");

		background(prestige, rarity);
		Shape card = tooltip(CX0, CY0, CW, CH, rarity);
		// your name as the item's name, and what it is under it
		String title = "§" + rarity.code + "§l" + name;
		int ns = fitScale(title, R - L, 7, 3);
		mc(title, L, CY0 + 52, ns, rarity.rgb);
		String rank = career == null ? "" : career.rank;
		String type = "§8" + t.get("rago.debrief.card.type") + (rank.isEmpty() ? "" : "  " + rank);
		mc(type, L, CY0 + 52 + 8 * ns + 16, 3, MC_DARK_GRAY);

		float wy = CY0 + 172;
		float wh = 650;
		window(L, wy, R - L, wh, prestige, rarity, star, grade, holo);

		// your talent, as an item's ability
		float y = wy + wh + 34;
		Talent talent = talent(career);
		String role = t.get("rago.debrief.role." + talent.key);
		mc(t.get("rago.debrief.card.talent", "§6" + role), L, y, 4, MC_GOLD);
		String detail = talent.arg == null ? t.get("rago.debrief.role." + talent.key + ".detail")
			: t.get("rago.debrief.role." + talent.key + ".detail", talent.arg instanceof Double ? number((Double) talent.arg, 2) : group(((Number) talent.arg).longValue()));
		mc("§7" + detail, L, y + 44, 3, MC_GRAY);

		// your stats as the item's lines
		y += 96;
		statLines(y);
		y += 3 * 40 + 22;
		lastGame(y);

		// the rarity, as Hypixel writes it at the foot of an item
		float ry = CY0 + CH - 3 * P - 52;
		String rarityLine = "§" + rarity.code + "§l" + t.get("rago.debrief.card.rarity." + rarity.name().toLowerCase(Locale.ROOT)).toUpperCase(locale);
		if (holo) {
			rarityLine += " §d§l✦ HOLO";
		}
		mc(rarityLine, L, ry, 4, rarity.rgb);
		String date = date(false);
		if (!date.isEmpty()) {
			Font df = font("inter-500.ttf", 20, 0);
			text(date, df, DIM, R - width(date, df), ry + 26);
		}
		if (holo) {
			sheen(card);
		}
		footer(holo);
	}

	private void background(int prestige, Rarity rarity) {
		g.setColor(BG);
		g.fillRect(0, 0, W, H);
		glow(W / 2f, CY0 + 500, 760, new Color(prestige), 0.20f);
		glow(-60, -60, 700, ACCENT, 0.18f);
		glow(W + 40, H - 200, 600, new Color(rarity.rgb), 0.10f);
	}

	/** Minecraft's tooltip at {@code P} pixels to its pixel, its border in the rarity's colours; returns its shape. */
	private Shape tooltip(int x, int y, int w, int h, Rarity rarity) {
		Area shape = new Area(new Rectangle2D.Float(x + P, y, w - 2 * P, h));
		shape.add(new Area(new Rectangle2D.Float(x, y + P, w, h - 2 * P)));
		g.setColor(new Color(16, 0, 16, 240));
		g.fill(shape);
		Color top = alpha(new Color(rarity.rgb), 0.85f);
		Color bottom = alpha(new Color(darker(rarity.rgb, 0.45f)), 0.85f);
		g.setColor(top);
		g.fillRect(x + P, y + P, w - 2 * P, P);
		g.setColor(bottom);
		g.fillRect(x + P, y + h - 2 * P, w - 2 * P, P);
		g.setPaint(new GradientPaint(0, y + 2 * P, top, 0, y + h - 2 * P, bottom));
		g.fillRect(x + P, y + 2 * P, P, h - 4 * P);
		g.fillRect(x + w - 2 * P, y + 2 * P, P, h - 4 * P);
		return shape;
	}

	/** The window you stand in: your prestige's colours, rays, sparkles, your rating, your star and your grade. */
	private void window(float x, float y, float w, float h, int prestige, Rarity rarity, int star, String grade, boolean holo) {
		Area shape = new Area(new Rectangle2D.Float(x + P, y, w - 2 * P, h));
		shape.add(new Area(new Rectangle2D.Float(x, y + P, w, h - 2 * P)));
		Color pc = new Color(prestige);
		g.setPaint(new GradientPaint(0, y, alpha(pc, 0.42f), 0, y + h, new Color(14, 8, 16)));
		g.fill(shape);
		Shape clip = g.getClip();
		g.clip(shape);
		float cx = x + w / 2;
		float cy = y + h * 0.52f;
		// rays from behind you
		Path2D rays = new Path2D.Float();
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8;
			rays.moveTo(cx, cy);
			rays.lineTo(cx + Math.cos(a) * 1200, cy + Math.sin(a) * 1200);
			rays.lineTo(cx + Math.cos(a + Math.PI / 16) * 1200, cy + Math.sin(a + Math.PI / 16) * 1200);
			rays.closePath();
		}
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), w * 0.7f, new float[] {0, 1},
			new Color[] {new Color(255, 255, 255, 34), new Color(255, 255, 255, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(rays);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), w * 0.42f, new float[] {0, 1},
			new Color[] {alpha(pc, 0.45f), alpha(pc, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(shape);
		// sparkles, always in the same places for the same player
		Random random = new Random(name.hashCode());
		for (int i = 0; i < 26; i++) {
			float sx = x + 20 + random.nextFloat() * (w - 40);
			float sy = y + 20 + random.nextFloat() * (h - 40);
			int size = 1 + random.nextInt(3);
			sparkle(sx, sy, size * 3, i % 3 == 0 ? new Color(rarity.rgb) : Color.WHITE, 0.25f + random.nextFloat() * 0.45f);
		}
		if (holo) {
			foil(shape, 0.16f, 0.30f);
		}
		g.setClip(clip);
		model(cx, y + h - 26, (int) (h - 70), -20);
		g.setColor(alpha(new Color(rarity.rgb), 0.55f));
		g.setStroke(new BasicStroke(3));
		g.draw(shape);

		// your overall rating, top left
		int overall = overall(career, d);
		String o = "§l" + overall;
		mc(o, x + 32, y + 30, 12, MC_WHITE);
		Font lf = font("inter-600.ttf", 20, 0.16f);
		text(t.get("rago.debrief.card.overall").toUpperCase(locale), lf, TEXT, x + 36, y + 30 + 96 + 34);
		// your star, top right
		if (star >= 0) {
			String tag = Hypixel.bedwarsStar(star);
			mc(tag, x + w - 32 - mcWidth(tag, 5), y + 34, 5, MC_GRAY);
		}
		// this game's grade as a stamp, bottom right
		if (!grade.equals("-")) {
			int gc = DebriefCard.gradeColor(grade);
			float s = 132;
			float gx = x + w - 32 - s;
			float gy = y + h - 32 - s;
			AffineTransform old = g.getTransform();
			g.rotate(Math.toRadians(-8), gx + s / 2, gy + s / 2);
			Area stamp = new Area(new Rectangle2D.Float(gx + P, gy, s - 2 * P, s));
			stamp.add(new Area(new Rectangle2D.Float(gx, gy + P, s, s - 2 * P)));
			g.setColor(new Color(16, 0, 16, 220));
			g.fill(stamp);
			g.setColor(alpha(new Color(gc), 0.9f));
			g.setStroke(new BasicStroke(4));
			g.draw(stamp);
			int scale = grade.length() > 1 ? 8 : 9;
			float gw = mcWidth("§l" + grade, scale);
			mc("§l" + grade, gx + (s - gw) / 2, gy + 20, scale, gc);
			Font sf = font("inter-600.ttf", 14, 0.12f);
			String label = t.get("rago.debrief.grade").toUpperCase(locale);
			text(fit(label, sf, s - 16), sf, MUTED, gx + (s - Math.min(s - 16, width(label, sf))) / 2, gy + s - 18);
			g.setTransform(old);
		}
	}

	/** A four-pointed sparkle. */
	private void sparkle(float x, float y, float r, Color c, float a) {
		Path2D s = new Path2D.Float();
		s.moveTo(x, y - r);
		s.lineTo(x + r * 0.25f, y - r * 0.25f);
		s.lineTo(x + r, y);
		s.lineTo(x + r * 0.25f, y + r * 0.25f);
		s.lineTo(x, y + r);
		s.lineTo(x - r * 0.25f, y + r * 0.25f);
		s.lineTo(x - r, y);
		s.lineTo(x - r * 0.25f, y - r * 0.25f);
		s.closePath();
		g.setColor(alpha(c, a));
		g.fill(s);
	}

	/** Your stats as the lines of an item: name in grey, value in its colour, in two columns. */
	private void statLines(float y) {
		String[][] left;
		String[][] right;
		if (career != null && career.star >= 0) {
			left = new String[][] {ratio("FKDR", career.fkdr()), ratio("WLR", career.wlr()), ratio("BBLR", career.bblr())};
			right = new String[][] {
				{t.get("rago.debrief.final_kills"), "§6" + group(career.finalKills)},
				{t.get("rago.debrief.session_wins"), "§a" + group(career.wins)},
				{t.get("rago.debrief.beds"), "§b" + group(career.bedsBroken)}};
		} else {
			left = new String[][] {
				{t.get("rago.debrief.final_kills"), "§6" + d.finalKills()},
				{t.get("rago.debrief.kills"), "§f" + d.kills()}};
			right = new String[][] {
				{t.get("rago.debrief.beds"), "§b" + d.beds()},
				{t.get("rago.debrief.deaths"), "§c" + d.deaths()}};
		}
		float col = (R - L) / 2f;
		for (int i = 0; i < left.length; i++) {
			mc(fitLine(left[i], col - 16), L, y + i * 40, 3, MC_GRAY);
		}
		for (int i = 0; i < right.length; i++) {
			mc(fitLine(right[i], col - 16), L + col, y + i * 40, 3, MC_GRAY);
		}
	}

	private String[] ratio(String label, double v) {
		if (Double.isNaN(v)) {
			return new String[] {label, "§7-"};
		}
		int c = DebriefCard.ratioColor(v);
		int code = 0;
		for (int i = 0; i < 16; i++) {
			if (PixelFont.COLORS[i] == c) {
				code = i;
			}
		}
		return new String[] {label, "§" + "0123456789abcdef".charAt(code) + number(v, 2)};
	}

	/** "§7Label: value", the label shortened when the line is too long. */
	private String fitLine(String[] kv, float max) {
		String label = kv[0];
		String line = "§7" + t.get("rago.debrief.card.line", label, kv[1]);
		while (mcWidth(line, 3) > max && label.length() > 3) {
			label = label.substring(0, label.length() - 2).trim() + ".";
			line = "§7" + t.get("rago.debrief.card.line", label, kv[1]);
		}
		return line;
	}

	/** This game in one line: its result, your grade, your final kills and the map. */
	private void lastGame(float y) {
		Debrief.Result r = d.result() == null ? Debrief.Result.LEFT : d.result();
		String result = t.get("rago.debrief.result." + r.name().toLowerCase(Locale.ROOT));
		char rc = r == Debrief.Result.WIN ? '6' : r == Debrief.Result.LEFT ? '7' : 'c';
		String grade = gradeShown();
		char gc = '7';
		for (int i = 0; i < 16; i++) {
			if (!grade.equals("-") && PixelFont.COLORS[i] == DebriefCard.gradeColor(grade)) {
				gc = "0123456789abcdef".charAt(i);
			}
		}
		String base = "§8" + t.get("rago.debrief.card.last_game") + " §" + rc + result + " §8· §" + gc + grade + " §8· §7"
			+ d.finalKills() + " FK";
		String withMap = d.map().isEmpty() ? base : base + " §8· §7" + d.map();
		mc(mcWidth(withMap, 3) <= R - L ? withMap : base, L, y, 3, MC_DARK_GRAY);
	}

	/** The foil of a holographic card in {@code shape}: the rainbow, and the glint of enchanted items over it. */
	private void foil(Shape shape, float rainbow, float glint) {
		Shape clip = g.getClip();
		Composite old = g.getComposite();
		g.clip(shape);
		Rectangle2D b = shape.getBounds2D();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rainbow));
		g.setPaint(rainbow(b));
		g.fill(shape);
		if (art != null && art.glint != null) {
			Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glint));
			AffineTransform tf = g.getTransform();
			g.rotate(Math.toRadians(-28), b.getCenterX(), b.getCenterY());
			int size = art.glint.getWidth() * 4;
			g.setPaint(new TexturePaint(art.glint, new Rectangle2D.Float(0, 0, size, size)));
			double r = Math.hypot(b.getWidth(), b.getHeight());
			g.fill(new Rectangle2D.Double(b.getCenterX() - r, b.getCenterY() - r, 2 * r, 2 * r));
			g.setTransform(tf);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
		}
		g.setComposite(old);
		g.setClip(clip);
	}

	/** Over the whole holographic card: a faint rainbow and a band of light, which leave the text readable. */
	private void sheen(Shape card) {
		Shape clip = g.getClip();
		Composite old = g.getComposite();
		g.clip(card);
		Rectangle2D b = card.getBounds2D();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.07f));
		g.setPaint(rainbow(b));
		g.fill(card);
		g.setComposite(old);
		float bx = (float) b.getX();
		float by = (float) b.getY();
		g.setPaint(new LinearGradientPaint(bx, by + 120, bx + 760, by + 760, new float[] {0, 0.44f, 0.5f, 0.56f, 1},
			new Color[] {new Color(255, 255, 255, 0), new Color(255, 255, 255, 0), new Color(255, 255, 255, 30), new Color(255, 255, 255, 0),
				new Color(255, 255, 255, 0)}));
		g.fill(card);
		g.setClip(clip);
	}

	/** The colours of a holographic foil, across {@code b} diagonally. */
	private static LinearGradientPaint rainbow(Rectangle2D b) {
		float[] at = {0, 0.17f, 0.34f, 0.5f, 0.67f, 0.84f, 1};
		Color[] hues = new Color[at.length];
		for (int i = 0; i < at.length; i++) {
			hues[i] = Color.getHSBColor(at[i], 0.65f, 1f);
		}
		return new LinearGradientPaint((float) b.getX(), (float) b.getY(), (float) (b.getX() + b.getWidth() * 0.6),
			(float) (b.getY() + b.getHeight() * 0.45), at, hues, MultipleGradientPaint.CycleMethod.REFLECT);
	}

	/** Rago at the foot: the client, how to get your own card, and its Discord. */
	private void footer(boolean holo) {
		float y = CY0 + CH + 36;
		float h = H - y - 40;
		BufferedImage l = logo();
		float x = CX0;
		if (l != null) {
			g.drawImage(l, (int) x, (int) (y + (h - 64) / 2), 64, 64, null);
			x += 84;
		}
		float right = CX0 + CW;
		if (signature) {
			right -= discord(right, y + (h - 52) / 2, 52, 20) + 20;
		}
		Font tf = font("poppins-600.ttf", 30, 0.01f);
		text("Rago Client", tf, TEXT, x, y + h / 2 - 6);
		Font sf = font("inter-500.ttf", 19, 0);
		String sub = t.get(holo ? "rago.debrief.card.cta" : "rago.debrief.card.cta_holo");
		text(fit(sub, sf, right - x), sf, MUTED, x, y + h / 2 + 26);
	}

	private static int darker(int rgb, float f) {
		int r = Math.round((rgb >> 16 & 0xFF) * f);
		int gr = Math.round((rgb >> 8 & 0xFF) * f);
		int b = Math.round((rgb & 0xFF) * f);
		return r << 16 | gr << 8 | b;
	}
}
