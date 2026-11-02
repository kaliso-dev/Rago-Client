package gg.rago.gui;

import gg.rago.bedwars.Debrief;
import gg.rago.hypixel.BedwarsLevel;
import gg.rago.hypixel.Hypixel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * The debrief as a story (1080 x 1920), for TikTok, Instagram or Snapchat: the result, you large in
 * the middle with your grade and your place as stickers around you, your stars and the experience
 * to the next one, your four counts, your ratios, and a challenge to beat your grade with Rago.
 * What phones cover (the top and the reply bar at the foot) is kept clear of what matters.
 */
final class StoryCard extends CardPainter {
	static final int W = 1080;
	static final int H = 1920;
	private static final int M = 64;
	private static final float CX = W / 2f;

	StoryCard(Debrief d, DebriefCard.Session session, DebriefCard.Text t, Locale locale, boolean signature, CardArt art, String name) {
		super(d, session, t, locale, signature, art, name, W, H);
	}

	@Override
	void draw() {
		background();
		header();
		float y = result();
		chipRow(gameChips(), CX, y + 28, 60, W - 2 * M, 1.33f);
		hero(y + 110);
		float ny = nameLine(CX, 990, W - 2 * M, 7);
		boolean stats = career != null && career.star >= 0;
		y = ny + 28;
		if (stats) {
			y = stars(y);
		}
		y = tiles(y + 24);
		if (stats) {
			ratios(y + 20);
		} else if (session != null && session.games > 0) {
			sessionRow(y + 20);
		}
		footer();
	}

	private void background() {
		g.setColor(BG);
		g.fillRect(0, 0, W, H);
		glow(80, -160, 900, ACCENT, 0.24f);
		glow(W + 80, H + 60, 760, ACCENT, 0.12f);
		glow(CX, 700, 520, resultColor(), 0.16f);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(2));
		g.drawRect(1, 1, W - 3, H - 3);
	}

	private void header() {
		float y = 96;
		BufferedImage l = logo();
		float x = M;
		if (l != null) {
			g.drawImage(l, M, (int) y, 48, 48, null);
			x += 64;
		}
		Font f = font("poppins-600.ttf", 26, 0.08f);
		x += text("RAGO", f, ACCENT, x, y + 36) + 16;
		g.setColor(LINE);
		g.fillRect((int) x, (int) y + 10, 2, 30);
		x += 18;
		text(fit(t.get("rago.debrief.card_title").toUpperCase(locale), f, W - M - x - 200), f, TEXT, x, y + 36);
		String date = date(false);
		Font df = font("inter-500.ttf", 20, 0);
		text(date, df, MUTED, W - M - width(date, df), y + 34);
	}

	/** The result in big letters, centred; returns the line under it. */
	private float result() {
		Debrief.Result r = d.result() == null ? Debrief.Result.LEFT : d.result();
		String s = "§l" + t.get("rago.debrief.big." + r.name().toLowerCase(Locale.ROOT));
		int color = r == Debrief.Result.WIN ? MC_GOLD : r == Debrief.Result.LEFT ? MC_GRAY : MC_RED;
		int scale = fitScale(s, W - 2 * M, 13, 5);
		float y = 186;
		mc(s, CX - mcWidth(s, scale) / 2, y, scale, color);
		return y + 8 * scale;
	}

	/** You, large, with your grade and your place in the game's kills as stickers, and two highlights. */
	private void hero(float top) {
		float feet = 962;
		int h = (int) Math.min(540, feet - top - 10);
		// rays of light behind you
		Color c = resultColor();
		g.setPaint(new RadialGradientPaint(new Point2D.Float(CX, feet - h * 0.55f), h * 0.75f, new float[] {0, 0.6f, 1},
			new Color[] {alpha(c, 0.30f), alpha(c, 0.08f), alpha(c, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillRect(0, (int) top - 40, W, (int) (feet - top + 80));
		model(CX, feet, h, -22);

		// your grade, top left
		String grade = gradeShown();
		int gc = grade.equals("-") ? MC_GRAY : DebriefCard.gradeColor(grade);
		AffineTransform old = g.getTransform();
		float s = 200;
		float gx = M + 6;
		float gy = top + 20;
		g.rotate(Math.toRadians(-6), gx + s / 2, gy + s / 2);
		badge(gx, gy, s, s, new Color(gc));
		int scale = grade.length() > 1 ? 12 : 14;
		float gw = mcWidth("§l" + grade, scale);
		mc("§l" + grade, gx + (s - gw) / 2, gy + 28, scale, gc);
		Font lf = font("inter-600.ttf", 17, 0.14f);
		String label = t.get("rago.debrief.grade").toUpperCase(locale);
		text(label, lf, MUTED, gx + (s - width(label, lf)) / 2, gy + s - 24);
		g.setTransform(old);

		// your place in the game's kills, top right
		List<Debrief.Killer> board = d.board();
		boolean ranked = !board.isEmpty() && board.get(0).kills() > 0;
		float rx = W - M - s - 6;
		float ry = top + 120;
		if (ranked) {
			int rank = d.rank();
			int total = Math.max(board.size(), d.players());
			g.rotate(Math.toRadians(5), rx + s / 2, ry + s / 2);
			int mc = DebriefCard.medal(rank);
			badge(rx, ry, s, s, new Color(mc));
			String k = t.get("rago.debrief.kills").toUpperCase(locale);
			text(k, lf, MUTED, rx + (s - width(k, lf)) / 2, ry + 40);
			String place = "§l#" + rank;
			int ps = fitScale(place, s - 30, 11, 5);
			mc(place, rx + (s - mcWidth(place, ps)) / 2, ry + 58, ps, mc);
			if (total > 1) {
				Font of = font("inter-500.ttf", 20, 0);
				String o = t.get("rago.debrief.board_of", total);
				text(o, of, TEXT, rx + (s - width(o, of)) / 2, ry + s - 24);
			}
			g.setTransform(old);
		}

		// two highlights as stickers, low on each side
		List<DebriefCard.Badge> badges = DebriefCard.badges(d);
		if (!badges.isEmpty()) {
			sticker(badges.get(0), M, feet - 210, -4, false);
		}
		if (badges.size() > 1) {
			sticker(badges.get(1), W - M, feet - 120, 4, true);
		}
	}

	/** A highlight as a sticker: its item and name on a golden pill, turned a little. */
	private void sticker(DebriefCard.Badge b, float x, float y, double degrees, boolean right) {
		Font f = font("inter-600.ttf", 24, 0);
		String s = fit(t.get("rago.debrief.badge." + b.key), f, 230);
		float h = 64;
		float w = 18 + 36 + 12 + width(s, f) + 24;
		float sx = right ? x - w : x;
		AffineTransform old = g.getTransform();
		g.rotate(Math.toRadians(degrees), sx + w / 2, y + h / 2);
		RoundRectangle2D r = new RoundRectangle2D.Float(sx, y, w, h, h, h);
		g.setColor(new Color(0x1E1A12));
		g.fill(r);
		g.setColor(new Color(255, 170, 0, 40));
		g.fill(r);
		g.setColor(new Color(255, 170, 0, 170));
		g.setStroke(new BasicStroke(2.5f));
		g.draw(r);
		BufferedImage icon = item(b.icon);
		if (icon != null) {
			pixels(icon, sx + 18, y + 14, 36);
		}
		text(s, f, TEXT, sx + 18 + 36 + 12, y + h / 2 + 8.5f);
		g.setTransform(old);
	}

	/** A rounded badge with a soft glow of its colour. */
	private void badge(float x, float y, float w, float h, Color c) {
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x + w / 2, y + h / 2), w * 0.8f, new float[] {0, 1},
			new Color[] {alpha(c, 0.25f), alpha(c, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(new java.awt.geom.Ellipse2D.Float(x - w * 0.3f, y - h * 0.3f, w * 1.6f, h * 1.6f));
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 40, 40);
		g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + h, PANEL));
		g.fill(r);
		g.setColor(alpha(c, 0.6f));
		g.setStroke(new BasicStroke(3));
		g.draw(r);
	}

	/** Your star to the next one on Minecraft's experience bar, and the experience to go; returns the line under it. */
	private float stars(float y) {
		int star = star();
		String from = Hypixel.bedwarsStar(star);
		String to = Hypixel.bedwarsStar(star + 1);
		if (career.xp < 0) {
			mc(from, CX - mcWidth(from, 5) / 2, y, 5, MC_GRAY);
			return y + 40;
		}
		int bs = 3;
		float bw = 182 * bs;
		float fw = mcWidth(from, 4);
		float tw = mcWidth(to, 4);
		float total = fw + 20 + bw + 20 + tw;
		float x = CX - total / 2;
		mc(from, x, y, 4, MC_GRAY);
		xpBar(x + fw + 20, y + 8, bs, BedwarsLevel.progress(career.xp));
		mc(to, x + fw + 20 + bw + 20, y, 4, MC_GRAY);
		String go = t.get("rago.debrief.xp_to_go", group(BedwarsLevel.toNext(career.xp)));
		float goY = y + 52;
		outlined(go, CX - mcWidth(go, 4) / 2, goY, 4, XP_GREEN);
		Font f = font("inter-500.ttf", 22, 0);
		String into = t.get("rago.debrief.xp_into", group(BedwarsLevel.into(career.xp)), group(BedwarsLevel.cost(star)));
		long gained = d.xpGained();
		boolean oneGame = d.careerBefore() != null && d.careerAfter() != null && d.careerAfter().games == d.careerBefore().games + 1;
		float line = goY + 32 + 36;
		if (gained > 0 && oneGame) {
			String plus = t.get("rago.debrief.xp_gained", group(gained));
			Font pf = font("inter-600.ttf", 22, 0);
			float w = width(into, f) + 20 + width(plus, pf);
			float lx = CX - w / 2;
			lx += text(into, f, MUTED, lx, line) + 20;
			text(plus, pf, new Color(MC_GOLD), lx, line);
		} else {
			text(into, f, MUTED, CX - width(into, f) / 2, line);
		}
		return line + 8;
	}

	/** Your four counts in a row, each coloured by how good it is; returns the line under them. */
	private float tiles(float y) {
		float gap = 16;
		float w = (W - 2 * M - 3 * gap) / 4;
		float h = 206;
		double perGameFinals = career == null ? Double.NaN : career.perGame(career.finalKills);
		double perGameKills = career == null ? Double.NaN : career.perGame(career.kills);
		double perGameBeds = career == null ? Double.NaN : career.perGame(career.bedsBroken);
		double perGameDeaths = career == null ? Double.NaN : career.deathsPerGame();
		tile(M, y, w, h, "diamond_sword", t.get("rago.debrief.final_kills"), d.finalKills(),
			DebriefCard.rate(d.finalKills(), perGameFinals, 1, 2, 4));
		tile(M + (w + gap), y, w, h, "iron_sword", t.get("rago.debrief.kills"), d.kills(),
			DebriefCard.rate(d.kills(), perGameKills, 1, 3, 6));
		tile(M + 2 * (w + gap), y, w, h, "bed", t.get("rago.debrief.beds"), d.beds(),
			d.beds() == 0 ? DebriefCard.Rating.AVERAGE : DebriefCard.rate(d.beds(), perGameBeds, 0, 1, 2));
		tile(M + 3 * (w + gap), y, w, h, "bone", t.get("rago.debrief.deaths"), d.deaths(),
			DebriefCard.rateDeaths(d.deaths(), perGameDeaths, d.seconds()));
		return y + h;
	}

	private void tile(float x, float y, float w, float h, String icon, String label, int value, DebriefCard.Rating rating) {
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 32, 32);
		g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + h, PANEL));
		g.fill(r);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
		Shape clip = g.getClip();
		g.clip(r);
		g.setColor(alpha(new Color(rating.rgb), 0.85f));
		g.fillRect((int) x, (int) y, (int) w + 1, 5);
		g.setClip(clip);
		BufferedImage img = item(icon);
		if (img != null) {
			pixels(img, x + 20, y + 22, 36);
		}
		Font lf = font("inter-600.ttf", 16, 0.06f);
		String l = label.toUpperCase(locale);
		// the label under the icon, on two lines at most
		float lw = w - 40;
		String[] lines = width(l, lf) <= lw || !l.contains(" ") ? new String[] {fit(l, lf, lw)} : l.split(" ", 2);
		float ly = y + 82;
		for (String line : lines) {
			text(fit(line, lf, lw), lf, MUTED, x + 20, ly);
			ly += 20;
		}
		String v = String.valueOf(value);
		int scale = fitScale(v, w - 40, 9, 5);
		mc(v, x + 20, y + h - 38 - 8 * scale, scale, rating.rgb);
		Font rf = font("inter-600.ttf", 18, 0);
		text(t.get("rago.debrief.rating." + rating.name().toLowerCase(Locale.ROOT)), rf, new Color(rating.rgb), x + 20, y + h - 18);
	}

	/** FKDR, BBLR and WLR in three boxes, with what this game changed when Hypixel has saved it. */
	private void ratios(float y) {
		Debrief.Career before = d.careerBefore();
		Debrief.Career after = d.careerAfter();
		String[] labels = {"FKDR", "BBLR", "WLR"};
		double[] now = {career.fkdr(), career.bblr(), career.wlr()};
		double[] was = before == null || after == null ? null : new double[] {before.fkdr(), before.bblr(), before.wlr()};
		float gap = 16;
		float bw = (W - 2 * M - 2 * gap) / 3;
		float h = 104;
		for (int i = 0; i < 3; i++) {
			float bx = M + i * (bw + gap);
			box(bx, y, bw, h);
			Font lf = font("inter-600.ttf", 16, 0.14f);
			text(labels[i], lf, MUTED, bx + 22, y + 36);
			if (Double.isNaN(now[i])) {
				mc("-", bx + 22, y + 52, 4, MC_GRAY);
				continue;
			}
			String v = number(now[i], 2);
			mc(v, bx + 22, y + 50, 4, DebriefCard.ratioColor(now[i]));
			if (was != null && !Double.isNaN(was[i])) {
				double diff = now[i] - was[i];
				if (Math.abs(diff) >= 0.005) {
					Font df = font("inter-600.ttf", 18, 0);
					String ds = (diff > 0 ? "+" : "−") + number(Math.abs(diff), 2);
					text(ds, df, new Color(diff > 0 ? MC_GREEN : MC_RED), bx + bw - 22 - width(ds, df), y + 36);
				}
			}
		}
	}

	/** Without the Hypixel API: this session's games, wins and final kills. */
	private void sessionRow(float y) {
		String[] labels = {t.get("rago.debrief.session_games"), t.get("rago.debrief.session_wins"), t.get("rago.debrief.session_finals")};
		int[] values = {session.games, session.wins, session.finalKills};
		int[] colors = {MC_WHITE, MC_GREEN, MC_GOLD};
		float gap = 16;
		float bw = (W - 2 * M - 2 * gap) / 3;
		float h = 104;
		Font lf = font("inter-600.ttf", 15, 0.08f);
		for (int i = 0; i < 3; i++) {
			float bx = M + i * (bw + gap);
			box(bx, y, bw, h);
			text(fit(labels[i].toUpperCase(locale), lf, bw - 40), lf, MUTED, bx + 22, y + 36);
			mc(String.valueOf(values[i]), bx + 22, y + 50, 4, colors[i]);
		}
	}

	/** Rago at the foot: a challenge to beat your grade, the client, and its Discord. */
	private void footer() {
		float h = 150;
		float y = H - 164 - h;
		float x = M;
		float w = W - 2 * M;
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 36, 36);
		g.setPaint(new GradientPaint(x, y, new Color(0x2A1210), x + w, y + h, new Color(0x141317)));
		g.fill(r);
		g.setColor(alpha(ACCENT, 0.55f));
		g.setStroke(new BasicStroke(2));
		g.draw(r);
		BufferedImage l = logo();
		float tx = x + 32;
		if (l != null) {
			g.drawImage(l, (int) tx, (int) (y + (h - 72) / 2), 72, 72, null);
			tx += 96;
		}
		String grade = gradeShown();
		boolean challenge = grade.equals("S+") || grade.equals("S") || grade.equals("A");
		String title = challenge ? t.get("rago.debrief.story.challenge", grade) : "Rago Client";
		float right = x + w - 28;
		if (signature) {
			right -= discord(right, y + h - 28 - 52, 52, 20) + 16;
		}
		Font tf = font("poppins-600.ttf", 34, 0.01f);
		text(fit(title, tf, x + w - 28 - tx), tf, TEXT, tx, y + 64);
		Font sf = font("inter-500.ttf", 20, 0);
		String sub = t.get(challenge ? "rago.debrief.story.cta_challenge" : "rago.debrief.story.cta");
		text(fit(sub, sf, right - tx), sf, MUTED, tx, y + h - 46);
		// for the feeds that read hashtags
		Font hf = font("inter-600.ttf", 20, 0.04f);
		String tags = "#ragoclient   #bedwars   #hypixel";
		text(tags, hf, DIM, CX - width(tags, hf) / 2, y + h + 64);
	}
}
