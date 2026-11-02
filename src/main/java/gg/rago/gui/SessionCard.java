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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The recap of your session (1600 x 900), drawn as a year's recap is: the title your session earned,
 * your totals in big numbers, each game as a tile with its grade (the best one crowned), and on the
 * right you, the stars you gained and what stood out: your MVPs, your best run of wins, your
 * nemesis, your lucky map.
 */
final class SessionCard extends CardPainter {
	static final int W = 1600;
	static final int H = 900;
	private static final int M = 48;
	private static final int PX = 1072;
	private static final int PW = W - M - PX;
	private static final int LW = PX - 32 - M;
	private static final int TOP = 40;
	private static final int BOTTOM = 812;
	/** The games shown as tiles, the last ones. */
	private static final int TILES = 10;

	private final List<Debrief> games;
	private final Recap recap;

	/** What the session's games add up to. */
	static final class Recap {
		int games;
		int wins;
		int finals;
		int kills;
		int beds;
		int deaths;
		int seconds;
		int mvps;
		int bestRun;
		int starsGained;
		long xp;
		Debrief best;
		String nemesis;
		int nemesisTimes;
		String luckyMap;
		int luckyWins;
		int starFrom = -1;
		int starTo = -1;

		Recap(List<Debrief> list) {
			int run = 0;
			double bestScore = Double.NEGATIVE_INFINITY;
			Map<String, Integer> killers = new HashMap<>();
			Map<String, Integer> maps = new LinkedHashMap<>();
			for (Debrief d : list) {
				games++;
				boolean win = d.result() == Debrief.Result.WIN;
				wins += win ? 1 : 0;
				run = win ? run + 1 : 0;
				bestRun = Math.max(bestRun, run);
				finals += d.finalKills();
				kills += d.kills();
				beds += d.beds();
				deaths += d.deaths();
				seconds += d.seconds();
				xp += Math.max(0, d.xpGained());
				if (d.rank() == 1 && d.kills() + d.finalKills() >= 3 && d.board().size() >= 2) {
					mvps++;
				}
				Debrief.Career before = d.careerBefore();
				Debrief.Career after = d.careerAfter();
				if (before != null && after != null && before.star >= 0 && after.star >= 0) {
					starsGained += Math.max(0, DebriefCard.starOf(after) - DebriefCard.starOf(before));
				}
				if (before != null && before.star >= 0 && starFrom < 0) {
					starFrom = DebriefCard.starOf(before);
				}
				Debrief.Career last = after != null ? after : before;
				if (last != null && last.star >= 0) {
					starTo = DebriefCard.starOf(last);
				}
				boolean left = d.result() == null || d.result() == Debrief.Result.LEFT;
				double score = DebriefCard.score(d);
				if (!left && score > bestScore) {
					bestScore = score;
					best = d;
				}
				for (Debrief.Moment m : d.moments()) {
					if ((m.kind == Debrief.Kind.DEATH || m.kind == Debrief.Kind.FINAL_DEATH) && !m.detail.isEmpty()) {
						int n = killers.merge(m.detail, 1, Integer::sum);
						if (n >= 2 && n > nemesisTimes) {
							nemesis = m.detail;
							nemesisTimes = n;
						}
					}
				}
				if (win && !d.map().isEmpty()) {
					int n = maps.merge(d.map(), 1, Integer::sum);
					if (n > luckyWins) {
						luckyMap = d.map();
						luckyWins = n;
					}
				}
			}
		}

		/** The title the session earned: its key, and the count it is for with the key that writes that count. */
		Object[] title() {
			if (games >= 2 && wins == games) {
				return new Object[] {"unbeaten", "rago.debrief.wins", wins};
			}
			if (finals >= 4 * games && finals > 0) {
				return new Object[] {"final_machine", "rago.debrief.finals", finals};
			}
			if (beds * 2 >= 3 * games && beds > 0) {
				return new Object[] {"bed_destroyer", "rago.debrief.recap.beds", beds};
			}
			if (deaths == 0 && wins > 0) {
				return new Object[] {"untouchable", "rago.debrief.games", games};
			}
			if (kills >= 6 * games && kills > 0) {
				return new Object[] {"rusher", "rago.debrief.recap.kills", kills};
			}
			if (wins > 0 && wins * 2 >= games) {
				return new Object[] {"on_fire", "rago.debrief.wins", wins};
			}
			if (games >= 8) {
				return new Object[] {"marathon", "rago.debrief.games", games};
			}
			return new Object[] {"warming_up", "rago.debrief.games", games};
		}
	}

	SessionCard(List<Debrief> games, DebriefCard.Text t, Locale locale, boolean signature, CardArt art, String name) {
		super(games.get(games.size() - 1), DebriefCard.Session.of(games), t, locale, signature, art, name, W, H);
		this.games = games;
		this.recap = new Recap(games);
	}

	@Override
	void draw() {
		background();
		header();
		float y = title();
		tiles(Math.max(286, y + 30));
		strip();
		player();
		footer();
	}

	private void background() {
		g.setColor(BG);
		g.fillRect(0, 0, W, H);
		glow(160, -140, 780, ACCENT, 0.22f);
		glow(W - 280, H + 120, 620, new Color(MC_GOLD), 0.07f);
		// bold bands across, as recaps of the year have
		AffineTransform old = g.getTransform();
		g.rotate(Math.toRadians(-12), W / 2f, H / 2f);
		g.setPaint(new GradientPaint(0, 0, alpha(ACCENT, 0), W, 0, alpha(ACCENT, 0.16f)));
		g.fill(new RoundRectangle2D.Float(-300, 470, W + 600, 130, 130, 130));
		g.setPaint(new GradientPaint(0, 0, alpha(new Color(MC_GOLD), 0.12f), W, 0, alpha(new Color(MC_GOLD), 0)));
		g.fill(new RoundRectangle2D.Float(-300, 620, W + 600, 60, 60, 60));
		g.setTransform(old);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(2));
		g.drawRect(1, 1, W - 3, H - 3);
	}

	private void header() {
		BufferedImage l = logo();
		if (l != null) {
			g.drawImage(l, M, TOP + 2, 40, 40, null);
		}
		float x = M + 54;
		Font f = font("poppins-600.ttf", 22, 0.08f);
		x += text("RAGO", f, ACCENT, x, TOP + 31) + 14;
		g.setColor(LINE);
		g.fillRect((int) x, TOP + 12, 2, 24);
		x += 16;
		text(t.get("rago.debrief.recap.card_title").toUpperCase(locale), f, TEXT, x, TOP + 31);
		String when = date(false);
		String played = t.get("rago.debrief.recap.played", DebriefCard.plural(t, locale, "rago.debrief.games", recap.games),
			DebriefCard.clock(recap.seconds));
		String s = when.isEmpty() ? played : when + "   ·   " + played;
		Font df = font("inter-500.ttf", 16, 0);
		text(s, df, MUTED, M + LW - width(s, df), TOP + 29);
	}

	/** The title your session earned, in big letters; returns the line under it. */
	private float title() {
		Object[] title = recap.title();
		String key = "rago.debrief.recap.title." + title[0];
		Font lf = font("inter-600.ttf", 14, 0.16f);
		float y = TOP + 92;
		text(t.get("rago.debrief.recap.title_label").toUpperCase(locale), lf, DIM, M + 2, y);
		String s = "§l" + t.get(key).toUpperCase(locale);
		int scale = fitScale(s, LW, 10, 4);
		mc(s, M - scale / 2f, y + 18, scale, MC_GOLD);
		float line = y + 18 + 8 * scale + 40;
		Font df = font("inter-500.ttf", 22, 0);
		String count = DebriefCard.plural(t, locale, (String) title[1], (Integer) title[2]);
		text(t.get(key + ".detail", count, DebriefCard.plural(t, locale, "rago.debrief.games", recap.games)), df, MUTED, M + 2, line);
		return line;
	}

	/** The session's totals, six big numbers. */
	private void tiles(float y) {
		float gap = 16;
		float w = (LW - 2 * gap) / 3;
		float h = 146;
		int n = Math.max(1, recap.games);
		boolean good = recap.wins * 2 >= recap.games && recap.wins > 0;
		tile(M, y, w, h, "nether_star", t.get("rago.debrief.session_wins"), recap.wins + "/" + recap.games, good ? MC_GOLD : MC_WHITE,
			t.get("rago.debrief.recap.winrate", Math.round(100.0 * recap.wins / n)));
		tile(M + w + gap, y, w, h, "diamond_sword", t.get("rago.debrief.final_kills"), String.valueOf(recap.finals), MC_GOLD,
			t.get("rago.debrief.recap.per_game", number(recap.finals / (double) n, 1)));
		tile(M + 2 * (w + gap), y, w, h, "iron_sword", t.get("rago.debrief.kills"), String.valueOf(recap.kills), MC_WHITE,
			t.get("rago.debrief.recap.per_game", number(recap.kills / (double) n, 1)));
		float y2 = y + h + 14;
		tile(M, y2, w, h, "bed", t.get("rago.debrief.beds"), String.valueOf(recap.beds), MC_AQUA,
			t.get("rago.debrief.recap.per_game", number(recap.beds / (double) n, 1)));
		tile(M + w + gap, y2, w, h, "bone", t.get("rago.debrief.deaths"), String.valueOf(recap.deaths), recap.deaths == 0 ? MC_GREEN : MC_WHITE,
			t.get("rago.debrief.recap.kd", number((recap.kills + recap.finals) / (double) Math.max(1, recap.deaths), 2)));
		if (recap.xp > 0) {
			String sub = recap.starsGained > 0 ? DebriefCard.plural(t, locale, "rago.debrief.recap.stars", recap.starsGained)
				: t.get("rago.debrief.recap.time", DebriefCard.clock(recap.seconds));
			tile(M + 2 * (w + gap), y2, w, h, "experience_bottle", t.get("rago.debrief.recap.xp"), "+" + group(recap.xp), XP_GREEN, sub);
		} else {
			tile(M + 2 * (w + gap), y2, w, h, "clock", t.get("rago.debrief.recap.time_label"), DebriefCard.clock(recap.seconds), MC_WHITE,
				t.get("rago.debrief.recap.average", DebriefCard.clock(recap.seconds / n)));
		}
	}

	private void tile(float x, float y, float w, float h, String icon, String label, String value, int color, String sub) {
		panel(x, y, w, h);
		Shape old = g.getClip();
		g.clip(new RoundRectangle2D.Float(x, y, w, h, 30, 30));
		g.setColor(alpha(new Color(color), 0.85f));
		g.fillRect((int) x, (int) y, (int) w + 1, 4);
		g.setClip(old);
		BufferedImage img = item(icon);
		float lx = x + 22;
		if (img != null) {
			pixels(img, x + 18, y + 18, 32);
			lx = x + 60;
		}
		Font lf = font("inter-600.ttf", 15, 0.06f);
		text(fit(label.toUpperCase(locale), lf, x + w - 16 - lx), lf, MUTED, lx, y + 40);
		int scale = fitScale(value, w - 44, 7, 4);
		mc(value, x + 20, y + 60, scale, color);
		Font sf = font("inter-500.ttf", 15, 0);
		text(fit(sub, sf, w - 44), sf, DIM, x + 22, y + h - 16);
	}

	/** Each game of the session as a tile: its result, your grade, the map and your final kills; the best one crowned. */
	private void strip() {
		float x = M;
		float y = 618;
		float w = LW;
		float h = BOTTOM - y;
		panel(x, y, w, h);
		Font tf = font("inter-600.ttf", 14, 0.14f);
		text(t.get("rago.debrief.recap.games").toUpperCase(locale), tf, DIM, x + 24, y + 34);
		if (recap.best != null) {
			String s = t.get("rago.debrief.recap.best", DebriefCard.grade(recap.best),
				recap.best.map().isEmpty() ? mode(recap.best) : recap.best.map());
			Font bf = font("inter-500.ttf", 15, 0);
			text(s, bf, MUTED, x + w - 24 - width(s, bf), y + 34);
		}
		List<Debrief> shown = games.size() > TILES ? games.subList(games.size() - TILES, games.size()) : games;
		int n = shown.size();
		float gap = 10;
		float cw = Math.min(150, (w - 40 - (n - 1) * gap) / n);
		float cy = y + 54;
		float ch = h - 54 - 20;
		for (int i = 0; i < n; i++) {
			cell(shown.get(i), x + 20 + i * (cw + gap), cy, cw, ch);
		}
	}

	private void cell(Debrief game, float x, float y, float w, float h) {
		boolean best = game == recap.best && recap.games > 1;
		Debrief.Result r = game.result();
		boolean left = r == null || r == Debrief.Result.LEFT;
		int rc = r == Debrief.Result.WIN ? MC_GOLD : left ? MC_GRAY : MC_RED;
		RoundRectangle2D box = new RoundRectangle2D.Float(x, y, w, h, 20, 20);
		g.setColor(INSET);
		g.fill(box);
		Shape old = g.getClip();
		g.clip(box);
		g.setColor(alpha(new Color(rc), 0.9f));
		g.fillRect((int) x, (int) y, (int) w + 1, 5);
		g.setClip(old);
		g.setColor(best ? alpha(new Color(MC_GOLD), 0.85f) : LINE);
		g.setStroke(new BasicStroke(best ? 2.5f : 1.5f));
		g.draw(box);
		String grade = left ? "-" : DebriefCard.grade(game);
		int gc = left ? MC_GRAY : DebriefCard.gradeColor(grade);
		String gs = "§l" + grade;
		int scale = fitScale(gs, w - 20, 5, 3);
		mc(gs, x + (w - mcWidth(gs, scale)) / 2, y + 20, scale, gc);
		if (best) {
			BufferedImage star = item("nether_star");
			if (star != null) {
				pixels(star, x + w - 26, y + 10, 20);
			}
		}
		Font mf = font("inter-600.ttf", 13, 0);
		String map = fit(game.map().isEmpty() ? mode(game) : game.map(), mf, w - 14);
		text(map, mf, TEXT, x + (w - width(map, mf)) / 2, y + h - 34);
		Font sf = font("inter-500.ttf", 13, 0);
		String s = fit(game.finalKills() + " FK  ·  " + game.kills() + " K", sf, w - 14);
		text(s, sf, DIM, x + (w - width(s, sf)) / 2, y + h - 14);
	}

	private String mode(Debrief game) {
		if (!game.mode().isEmpty()) {
			return t.get("rago.debrief.mode." + game.mode());
		}
		return "Bed Wars";
	}

	/** You, the stars you gained this session, and what stood out. */
	private void player() {
		float x = PX;
		float y = TOP;
		float w = PW;
		float h = BOTTOM - y;
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 34, 34);
		g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + h, PANEL));
		g.fill(r);
		float cx = x + w / 2;
		Shape old = g.getClip();
		g.clip(r);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, y + 200), 280, new float[] {0, 1},
			new Color[] {alpha(new Color(MC_GOLD), 0.18f), alpha(new Color(MC_GOLD), 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillRect((int) x, (int) y, (int) w, (int) h);
		g.setClip(old);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);

		float feet = y + 368;
		model(cx, feet, 330, -24);
		float ny = nameLine(cx, feet + 20, w - 48, 4);
		float sy = ny + 14;
		if (recap.starTo >= 0) {
			String to = Hypixel.bedwarsStar(recap.starTo);
			if (recap.starFrom >= 0 && recap.starTo > recap.starFrom) {
				String from = Hypixel.bedwarsStar(recap.starFrom);
				String s = from + " §7→ " + to;
				mc(s, cx - mcWidth(s, 3) / 2, sy, 3, MC_GRAY);
			} else {
				mc(to, cx - mcWidth(to, 3) / 2, sy, 3, MC_GRAY);
			}
			sy += 24 + 14;
			if (career != null && career.xp >= 0) {
				xpBar(cx - 182, sy, 2, BedwarsLevel.progress(career.xp));
				Font f = font("inter-500.ttf", 15, 0);
				String go = t.get("rago.debrief.xp_to_go", group(BedwarsLevel.toNext(career.xp)));
				text(go, f, MUTED, cx - width(go, f) / 2, sy + 34);
				sy += 46;
			}
		}
		facts(x + 20, Math.max(sy + 14, BOTTOM - 22 - 3 * 64), w - 40);
	}

	/** Up to three things that stood out in the session. */
	private void facts(float x, float y, float w) {
		List<String[]> rows = new ArrayList<>();
		if (recap.mvps > 0) {
			rows.add(new String[] {"nether_star", t.get("rago.debrief.recap.mvps"), DebriefCard.plural(t, locale, "rago.debrief.recap.times", recap.mvps)});
		}
		if (recap.bestRun >= 2) {
			rows.add(new String[] {"blaze_powder", t.get("rago.debrief.recap.run"), t.get("rago.debrief.recap.run.detail", recap.bestRun)});
		}
		if (recap.nemesis != null) {
			rows.add(new String[] {"bone", t.get("rago.debrief.nemesis"), recap.nemesis + "  ×" + recap.nemesisTimes});
		}
		if (recap.luckyMap != null) {
			rows.add(new String[] {"map_filled", t.get("rago.debrief.recap.lucky_map"), recap.luckyMap});
		}
		rows.add(new String[] {"clock", t.get("rago.debrief.recap.time_label"), DebriefCard.clock(recap.seconds)});
		Font nf = font("inter-600.ttf", 17, 0);
		Font df = font("inter-500.ttf", 15, 0);
		float ry = y;
		for (int i = 0; i < Math.min(3, rows.size()); i++) {
			String[] row = rows.get(i);
			RoundRectangle2D sq = new RoundRectangle2D.Float(x, ry, 48, 48, 16, 16);
			g.setColor(new Color(255, 170, 0, 30));
			g.fill(sq);
			g.setColor(new Color(255, 170, 0, 90));
			g.setStroke(new BasicStroke(1.5f));
			g.draw(sq);
			BufferedImage icon = item(row[0]);
			if (icon != null) {
				pixels(icon, x + 8, ry + 8, 32);
			}
			float tx = x + 62;
			text(fit(row[1], nf, x + w - tx), nf, TEXT, tx, ry + 20);
			text(fit(row[2], df, x + w - tx), df, MUTED, tx, ry + 42);
			ry += 64;
		}
	}

	private void footer() {
		float y = BOTTOM + 16;
		float base = y + 46;
		BufferedImage l = logo();
		float x = M;
		if (l != null) {
			g.drawImage(l, M, (int) (y + 14), 40, 40, null);
			x += 52;
		}
		x += text("Rago Client", font("poppins-600.ttf", 23, 0.02f), TEXT, x, base) + 14;
		float right = W - M;
		if (signature) {
			right -= discord(right, y + 12, 44, 18) + 24;
		}
		Font f = font("inter-500.ttf", 16, 0);
		text(fit(t.get("rago.debrief.recap.ad"), f, right - x), f, MUTED, x, base);
	}
}
