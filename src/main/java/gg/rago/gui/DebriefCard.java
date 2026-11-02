package gg.rago.gui;

import gg.rago.bedwars.Debrief;
import gg.rago.hypixel.BedwarsLevel;
import gg.rago.hypixel.Hypixel;

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
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The debrief of a Bed Wars game drawn as one picture to share (1600 x 900): the result and the
 * game (mode, map, length), your grade, your four counts coloured by how good they are, the game's
 * kill leaderboard with your place, the game's highlights, and on the right you, in 3D with your
 * name, your stars, the experience to the next one and your ratios, in Minecraft's own font and
 * icons on Rago's dark red and black; Rago's logo and Discord at the foot. What the debrief screen
 * shows is exactly what is copied or saved. Drawn with Java 2D, so it needs no OpenGL, can be drawn
 * off the client thread, and can be tested on its own.
 */
public final class DebriefCard extends CardPainter {
	public static final int W = 1600;
	public static final int H = 900;
	private static final int M = 48;
	/** The player's column, on the right. */
	private static final int PX = 1072;
	private static final int PW = W - M - PX;
	/** The game's column, on the left. */
	private static final int LW = PX - 32 - M;
	private static final int TOP = 40;
	private static final int BOTTOM = 812;

	/** Text in the game's language: a translation key and its arguments. */
	public interface Text {
		String get(String key, Object... args);
	}

	/** The games of this session so far. */
	public static final class Session {
		final int games;
		final int wins;
		final int finalKills;

		public Session(int games, int wins, int finalKills) {
			this.games = games;
			this.wins = wins;
			this.finalKills = finalKills;
		}

		/** The counts of {@code games}. */
		public static Session of(List<Debrief> games) {
			int wins = 0;
			int finals = 0;
			for (Debrief g : games) {
				wins += g.result() == Debrief.Result.WIN ? 1 : 0;
				finals += g.finalKills();
			}
			return new Session(games.size(), wins, finals);
		}
	}

	/** The ways the debrief is drawn: the landscape picture, a story, a collectible card, the session's recap. */
	public enum Style {
		CLASSIC("classic", W, H), STORY("story", StoryCard.W, StoryCard.H), CARD("card", CollectorCard.W, CollectorCard.H),
		SESSION("session", SessionCard.W, SessionCard.H);

		public final String id;
		public final int width;
		public final int height;

		Style(String id, int width, int height) {
			this.id = id;
			this.width = width;
			this.height = height;
		}

		/** The style saved as {@code id}, or the landscape picture. */
		public static Style of(String id) {
			for (Style s : values()) {
				if (s.id.equals(id)) {
					return s;
				}
			}
			return CLASSIC;
		}
	}

	/** How good a count is, against your own average per game when the Hypixel API gives it. */
	enum Rating {
		ELITE(0xFFAA00), GOOD(0x55FF55), AVERAGE(0xFFFFFF), POOR(0xFF5555);

		final int rgb;

		Rating(int rgb) {
			this.rgb = rgb;
		}
	}

	/** A highlight of the game: its text's key, its item, the arguments of its description. */
	static final class Badge {
		final String key;
		final String icon;
		final Object[] args;

		Badge(String key, String icon, Object... args) {
			this.key = key;
			this.icon = icon;
			this.args = args;
		}
	}

	private DebriefCard(Debrief d, Session session, Text t, Locale locale, boolean signature, CardArt art, String name) {
		super(d, session, t, locale, signature, art, name, W, H);
	}

	/** The picture with Rago's fonts only, and Minecraft's default skin. */
	public static BufferedImage render(Debrief d, Session session, Text t, Locale locale, boolean signature) {
		return render(d, session, t, locale, signature, null, null);
	}

	/**
	 * The picture, drawn with the game's pictures in {@code art} (null: Rago's fonts only, no icons,
	 * no skin) and {@code name} for you (the Nick Hider's; null: your name).
	 */
	static BufferedImage render(Debrief d, Session session, Text t, Locale locale, boolean signature, CardArt art, String name) {
		return new DebriefCard(d, session, t, locale, signature, art, name).paint();
	}

	/**
	 * Game {@code index} of the session's {@code games} (oldest first) drawn in {@code style}. The
	 * session's recap is of the games played with that game's name, up to the last one.
	 */
	static BufferedImage render(Style style, List<Debrief> games, int index, Text t, Locale locale, boolean signature, CardArt art, String name) {
		Debrief d = games.get(index);
		Session session = Session.of(games);
		switch (style) {
			case STORY:
				return new StoryCard(d, session, t, locale, signature, art, name).paint();
			case CARD:
				return new CollectorCard(d, session, t, locale, signature, art, name).paint();
			case SESSION:
				List<Debrief> mine = new ArrayList<>();
				for (Debrief g : games) {
					if (g.player.equals(d.player)) {
						mine.add(g);
					}
				}
				return new SessionCard(mine, t, locale, signature, art, name).paint();
			default:
				return render(d, session, t, locale, signature, art, name);
		}
	}

	@Override
	void draw() {
		background();
		header();
		float y = result();
		chips(y);
		grade();
		tiles();
		board();
		highlights();
		player();
		footer();
	}

	// ---- the left column: the game ----

	private void background() {
		g.setColor(BG);
		g.fillRect(0, 0, W, H);
		glow(160, -140, 780, ACCENT, 0.20f);
		glow(W - 280, H + 120, 620, ACCENT, 0.08f);
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
		x += text("RAGO", font("poppins-600.ttf", 22, 0.08f), ACCENT, x, TOP + 31) + 14;
		g.setColor(LINE);
		g.fillRect((int) x, TOP + 12, 2, 24);
		x += 16;
		text(t.get("rago.debrief.card_title").toUpperCase(locale), font("poppins-600.ttf", 22, 0.08f), TEXT, x, TOP + 31);
	}

	/** The result in big letters, as Hypixel's title shows it; returns the line under it. */
	private float result() {
		Debrief.Result r = d.result() == null ? Debrief.Result.LEFT : d.result();
		String text = t.get("rago.debrief.big." + r.name().toLowerCase(Locale.ROOT));
		int color = r == Debrief.Result.WIN ? MC_GOLD : r == Debrief.Result.LEFT ? MC_GRAY : MC_RED;
		String s = "§l" + text;
		int scale = fitScale(s, LW - 200, 9, 3);
		float y = TOP + 72;
		mc(s, M - scale / 2f, y, scale, color);
		return y + 8 * scale + 22;
	}

	/** The game: your team's wool and the mode, the map, its length and how many played. */
	private void chips(float y) {
		float x = M;
		float h = 44;
		x += chip(art == null ? null : art.wool(d.teamColor() == 0 ? 'f' : d.teamColor()), mode(), x, y, h) + 10;
		if (!d.map().isEmpty()) {
			x += chip(item("map_filled"), d.map(), x, y, h) + 10;
		}
		x += chip(item("clock"), DebriefCard.clock(d.seconds()), x, y, h) + 10;
		if (d.players() >= 2 && x < M + LW - 380) {
			chip(item("name_tag"), t.get("rago.debrief.players", d.players()), x, y, h);
		}
	}

	/** Your grade for the game, S+ to D, in a badge at the top right of the game's column. */
	private void grade() {
		boolean left = d.result() == null || d.result() == Debrief.Result.LEFT;
		String grade = left ? "-" : grade(d);
		int color = left ? MC_GRAY : gradeColor(grade);
		float s = 150;
		float x = M + LW - s;
		float y = TOP + 64;
		Color c = new Color(color);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x + s / 2, y + s / 2), s * 0.75f, new float[] {0, 1},
			new Color[] {alpha(c, 0.22f), alpha(c, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(new Ellipse2D.Float(x - s * 0.25f, y - s * 0.25f, s * 1.5f, s * 1.5f));
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, s, s, 34, 34);
		g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + s, PANEL));
		g.fill(r);
		g.setColor(alpha(c, 0.55f));
		g.setStroke(new BasicStroke(2.5f));
		g.draw(r);
		int scale = grade.length() > 1 ? 9 : 10;
		float gw = mcWidth("§l" + grade, scale);
		mc("§l" + grade, x + (s - gw) / 2, y + 22, scale, color);
		Font f = font("inter-600.ttf", 13, 0.14f);
		String label = t.get("rago.debrief.grade").toUpperCase(locale);
		text(label, f, MUTED, x + (s - width(label, f)) / 2, y + s - 18);
	}

	/** Your four counts, each coloured by how good it is, with your average per game when known. */
	private void tiles() {
		float gap = 16;
		float w = (LW - 3 * gap) / 4;
		float y = 286;
		float h = 176;
		double perGameFinals = career == null ? Double.NaN : career.perGame(career.finalKills);
		double perGameKills = career == null ? Double.NaN : career.perGame(career.kills);
		double perGameBeds = career == null ? Double.NaN : career.perGame(career.bedsBroken);
		double perGameDeaths = career == null ? Double.NaN : career.deathsPerGame();
		String voids = d.voidDeaths() > 0 ? t.get("rago.debrief.void_deaths", d.voidDeaths()) : null;
		tile(M, y, w, h, "diamond_sword", t.get("rago.debrief.final_kills"), d.finalKills(),
			rate(d.finalKills(), perGameFinals, 1, 2, 4), perGameFinals, null);
		tile(M + (w + gap), y, w, h, "iron_sword", t.get("rago.debrief.kills"), d.kills(),
			rate(d.kills(), perGameKills, 1, 3, 6), perGameKills, null);
		Rating beds = d.beds() == 0 ? Rating.AVERAGE : rate(d.beds(), perGameBeds, 0, 1, 2);
		tile(M + 2 * (w + gap), y, w, h, "bed", t.get("rago.debrief.beds"), d.beds(), beds, perGameBeds, bedFact());
		tile(M + 3 * (w + gap), y, w, h, "bone", t.get("rago.debrief.deaths"), d.deaths(),
			rateDeaths(d.deaths(), perGameDeaths, d.seconds()), perGameDeaths, voids);
	}

	private String bedFact() {
		Debrief.Moment lost = d.first(Debrief.Kind.BED_LOST);
		if (lost != null) {
			return t.get("rago.debrief.bed_fell", clock(lost.at));
		}
		return d.bedSeen() ? t.get("rago.debrief.bed_stood") : null;
	}

	private void tile(float x, float y, float w, float h, String icon, String label, int value, Rating rating, double average, String fact) {
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 30, 30);
		g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + h, PANEL));
		g.fill(r);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
		// a line of the rating's colour along the top
		Shape old = g.getClip();
		g.clip(r);
		g.setColor(alpha(new Color(rating.rgb), 0.85f));
		g.fillRect((int) x, (int) y, (int) w + 1, 4);
		g.setClip(old);
		BufferedImage img = item(icon);
		float lx = x + 22;
		if (img != null) {
			pixels(img, x + 18, y + 20, 32);
			lx = x + 60;
		}
		Font lf = font("inter-600.ttf", 15, 0.06f);
		text(fit(label.toUpperCase(locale), lf, x + w - 16 - lx), lf, MUTED, lx, y + 42);
		String v = String.valueOf(value);
		mc(v, x + 20, y + 62, 8, rating.rgb);
		// the rating, and your average or a fact
		Font rf = font("inter-600.ttf", 15, 0);
		String word = t.get("rago.debrief.rating." + rating.name().toLowerCase(Locale.ROOT));
		float rw = text(word, rf, new Color(rating.rgb), x + 22, y + h - 22);
		String caption = !Double.isNaN(average) ? t.get("rago.debrief.average", number(average, 1)) : fact;
		if (caption != null) {
			Font cf = font("inter-500.ttf", 15, 0);
			text(fit("·  " + caption, cf, w - 44 - rw - 8), cf, DIM, x + 22 + rw + 8, y + h - 22);
		}
	}

	/** The game's kill leaderboard: the top five (you among them, else after them), with your place. */
	private void board() {
		float x = M;
		float y = 480;
		float w = 560;
		float h = BOTTOM - y;
		panel(x, y, w, h);
		Font tf = font("inter-600.ttf", 14, 0.14f);
		text(t.get("rago.debrief.board").toUpperCase(locale), tf, DIM, x + 24, y + 34);
		List<Debrief.Killer> board = d.board();
		if (board.size() <= 1 && board.get(0).kills() == 0) {
			Font f = font("inter-500.ttf", 17, 0);
			text(t.get("rago.debrief.board_empty"), f, DIM, x + 24, y + 86);
			return;
		}
		int rank = d.rank();
		int total = Math.max(board.size(), d.players());
		// your place, top right
		String place = "#" + rank;
		String of = total > 1 ? t.get("rago.debrief.board_of", total) : "";
		Font of_ = font("inter-500.ttf", 15, 0);
		float ow = of.isEmpty() ? 0 : width(of, of_) + 8;
		float pw = mcWidth("§l" + place, 3);
		mc("§l" + place, x + w - 24 - ow - pw, y + 16, 3, medal(rank));
		if (!of.isEmpty()) {
			text(of, of_, MUTED, x + w - 24 - ow + 8, y + 36);
		}
		// the rows: the top five, or the top four, a gap and you
		List<Integer> rows = new ArrayList<>();
		int you = -1;
		for (int i = 0; i < board.size(); i++) {
			if (board.get(i).name.equals(d.player)) {
				you = i;
			}
		}
		int n = Math.min(6, board.size());
		for (int i = 0; i < n; i++) {
			rows.add(i);
		}
		if (you >= n) {
			rows.set(n - 1, -1);
			rows.add(you);
		}
		int max = Math.max(1, board.get(0).kills());
		float ry = y + 56;
		float rh = 42;
		for (int i : rows) {
			if (i < 0) {
				mc("...", x + 30, ry + 6, 2, MC_GRAY);
				ry += rh * 0.6f;
				continue;
			}
			Debrief.Killer k = board.get(i);
			int place2 = placeOf(board, i);
			boolean mine = i == you;
			if (mine) {
				RoundRectangle2D r = new RoundRectangle2D.Float(x + 12, ry - 4, w - 24, rh - 2, 16, 16);
				g.setColor(alpha(ACCENT, 0.14f));
				g.fill(r);
				g.setColor(alpha(ACCENT, 0.55f));
				g.setStroke(new BasicStroke(1.5f));
				g.draw(r);
			}
			mc("#" + place2, x + 26, ry + 6, 2, medal(place2));
			int c = k.color() == 0 ? MC_WHITE : PixelFont.COLORS["0123456789abcdef".indexOf(k.color())];
			String who = mine ? name : k.name;
			float nameMax = 196;
			int scale = mcWidth(who, 3) <= nameMax ? 3 : 2;
			mc(who, x + 82, ry + (scale == 3 ? 3 : 7), scale, c);
			// the bar
			float bx = x + 296;
			float bw = 150;
			g.setColor(new Color(255, 255, 255, 14));
			g.fill(new RoundRectangle2D.Float(bx, ry + 13, bw, 8, 8, 8));
			g.setColor(alpha(new Color(c), 0.8f));
			g.fill(new RoundRectangle2D.Float(bx, ry + 13, Math.max(8, bw * k.kills() / max), 8, 8, 8));
			// kills, and finals under them
			String kills = String.valueOf(k.kills());
			float kw = mcWidth(kills, 3);
			mc(kills, x + w - 70 - kw, ry + 3, 3, MC_WHITE);
			if (k.finals() > 0) {
				Font ff = font("inter-600.ttf", 14, 0);
				text(k.finals() + " FK", ff, new Color(MC_GOLD), x + w - 60, ry + 23);
			}
			ry += rh;
		}
	}

	/** The place shown for row {@code i}: players with as many kills share it. */
	static int placeOf(List<Debrief.Killer> board, int i) {
		int place = i;
		while (place > 0 && board.get(place - 1).kills() == board.get(i).kills()) {
			place--;
		}
		return place + 1;
	}

	static int medal(int place) {
		return place == 1 ? MC_GOLD : place == 2 ? SILVER : place == 3 ? BRONZE : MC_GRAY;
	}

	/** The game's highlights, and facts about it when there are fewer than four. */
	private void highlights() {
		float x = M + 576;
		float y = 480;
		float w = LW - 576;
		float h = BOTTOM - y;
		panel(x, y, w, h);
		Font tf = font("inter-600.ttf", 14, 0.14f);
		text(t.get("rago.debrief.highlights").toUpperCase(locale), tf, DIM, x + 24, y + 34);
		List<String[]> rows = new ArrayList<>();
		for (Badge b : badges(d)) {
			rows.add(new String[] {b.icon, t.get("rago.debrief.badge." + b.key), t.get("rago.debrief.badge." + b.key + ".detail", b.args), "1"});
		}
		for (String[] f : facts()) {
			if (rows.size() >= 4) {
				break;
			}
			rows.add(f);
		}
		float ry = y + 54;
		float rh = 66;
		Font nf = font("inter-600.ttf", 18, 0);
		Font df = font("inter-500.ttf", 15, 0);
		for (String[] row : rows) {
			boolean badge = "1".equals(row[3]);
			RoundRectangle2D sq = new RoundRectangle2D.Float(x + 20, ry, 50, 50, 16, 16);
			g.setColor(badge ? new Color(255, 170, 0, 30) : new Color(255, 255, 255, 12));
			g.fill(sq);
			g.setColor(badge ? new Color(255, 170, 0, 90) : LINE);
			g.setStroke(new BasicStroke(1.5f));
			g.draw(sq);
			BufferedImage icon = item(row[0]);
			if (icon != null) {
				pixels(icon, x + 29, ry + 9, 32);
			}
			float tx = x + 84;
			text(fit(row[1], nf, x + w - 20 - tx), nf, badge ? TEXT : MUTED, tx, ry + 21);
			text(fit(row[2], df, x + w - 20 - tx), df, badge ? MUTED : TEXT, tx, ry + 43);
			ry += rh;
		}
	}

	/** Facts about the game for the highlights panel: {icon, label, value, "0"}. */
	private List<String[]> facts() {
		List<String[]> facts = new ArrayList<>();
		if (d.count(Debrief.Kind.FINAL_DEATH) > 0) {
			String k = d.finalKiller();
			facts.add(new String[] {"bone", t.get("rago.debrief.eliminated_by"), k.isEmpty() ? t.get("rago.debrief.the_void") : k, "0"});
		}
		Debrief.Moment up = d.first(Debrief.Kind.UPGRADE);
		if (up != null) {
			facts.add(new String[] {"diamond_pickaxe", t.get("rago.debrief.first_upgrade"),
				(up.detail.isEmpty() ? "" : up.detail + "  ·  ") + clock(up.at), "0"});
		}
		String[] nemesis = most(d, Debrief.Kind.DEATH, Debrief.Kind.FINAL_DEATH);
		if (nemesis != null) {
			facts.add(new String[] {"bone", t.get("rago.debrief.nemesis"), nemesis[0] + "  \u00d7" + nemesis[1], "0"});
		}
		String[] victim = most(d, Debrief.Kind.KILL, Debrief.Kind.FINAL_KILL);
		if (victim != null) {
			facts.add(new String[] {"diamond_sword", t.get("rago.debrief.victim"), victim[0] + "  \u00d7" + victim[1], "0"});
		}
		facts.add(new String[] {"iron_sword", t.get("rago.debrief.ratio"),
			number((d.kills() + d.finalKills()) / (double) Math.max(1, d.deaths()), 2), "0"});
		Debrief.Moment first = d.first(Debrief.Kind.FINAL_KILL);
		if (first != null) {
			facts.add(new String[] {"clock", t.get("rago.debrief.first_final"), clock(first.at), "0"});
		}
		return facts;
	}

	/** The player named most often in your moments of these kinds (twice at least): {name, times}, or null. */
	static String[] most(Debrief d, Debrief.Kind a, Debrief.Kind b) {
		Map<String, Integer> counts = new HashMap<>();
		String best = null;
		int bestN = 1;
		for (Debrief.Moment m : d.moments()) {
			if ((m.kind == a || m.kind == b) && !m.detail.isEmpty()) {
				int n = counts.merge(m.detail, 1, Integer::sum);
				if (n > bestN) {
					best = m.detail;
					bestN = n;
				}
			}
		}
		return best == null ? null : new String[] {best, String.valueOf(bestN)};
	}

	// ---- the right column: you ----

	private void player() {
		float x = PX;
		float y = TOP;
		float w = PW;
		float h = BOTTOM - y;
		RoundRectangle2D r = new RoundRectangle2D.Float(x, y, w, h, 34, 34);
		g.setPaint(new GradientPaint(x, y, PANEL_TOP, x, y + h, PANEL));
		g.fill(r);
		Debrief.Result res = d.result();
		Color glow = res == Debrief.Result.WIN ? new Color(MC_GOLD) : res == Debrief.Result.LEFT || res == null ? new Color(MC_GRAY) : ACCENT;
		float cx = x + w / 2;

		// what goes under you is measured first; you take the room left above it, as large as it allows
		boolean stats = career != null && career.star >= 0;
		boolean sessionShown = !stats && session != null && session.games > 0;
		float boxesTop = BOTTOM - 22 - 96;
		float bottom = stats ? boxesTop - 18 : sessionShown ? boxesTop - 40 : BOTTOM - 28;
		float info = nameHeight(w - 48, 5) + (stats ? 14 + starsHeight() : 0);
		float avail = bottom - (y + 28);
		float modelH = Math.max(240, Math.min(400, avail - info - 24));
		float top = y + 28 + Math.max(0, (avail - modelH - 24 - info) / 2);
		float feet = top + modelH;

		Shape old = g.getClip();
		g.clip(r);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, top + modelH * 0.45f), 300, new float[] {0, 1},
			new Color[] {alpha(glow, 0.22f), alpha(glow, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillRect((int) x, (int) y, (int) w, (int) h);
		g.setClip(old);
		g.setColor(LINE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);

		// the floor under the feet, then you
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, feet), 120, new float[] {0, 1},
			new Color[] {new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(new Ellipse2D.Float(cx - 120, feet - 16, 240, 32));
		if (art != null && art.skin != null) {
			try {
				int mh = Math.round(modelH);
				BufferedImage model = SkinRender.render(art.skin, art.slim, 300, mh, -24, 9);
				g.drawImage(model, (int) (cx - 150), (int) (feet - mh + 2), null);
			} catch (RuntimeException e) {
				// a broken skin leaves the place empty rather than the whole picture
			}
		}

		float ny = nameLine(cx, feet + 24, w - 48, 5);
		if (stats) {
			stars(cx, ny + 14);
			ratios(x + 22, boxesTop, w - 44);
		} else if (sessionShown) {
			sessionBox(x + 22, boxesTop, w - 44);
		}
	}

	private float starsHeight() {
		return career.xp < 0 ? 32 : 154;
	}

	/** Your star, and the experience to the next one on Minecraft's experience bar. */
	private void stars(float cx, float y) {
		String tag = Hypixel.bedwarsStar(star());
		mc(tag, cx - mcWidth(tag, 4) / 2, y, 4, MC_GRAY);
		if (career.xp < 0) {
			return;
		}
		long into = BedwarsLevel.into(career.xp);
		long cost = BedwarsLevel.cost(star());
		long toGo = BedwarsLevel.toNext(career.xp);
		// [312✫] ========------ [313✫]
		String from = Hypixel.bedwarsStar(star());
		String to = Hypixel.bedwarsStar(star() + 1);
		float bw = 182 * 2;
		float bx = cx - bw / 2;
		float tagsY = y + 52;
		mc(from, bx, tagsY, 2, MC_GRAY);
		mc(to, bx + bw - mcWidth(to, 2), tagsY, 2, MC_GRAY);
		float barY = tagsY + 24;
		xpBar(bx, barY, 2, BedwarsLevel.progress(career.xp));
		// the experience to go, as the game writes your level: green, outlined
		String go = t.get("rago.debrief.xp_to_go", group(toGo));
		float goY = barY + 22;
		outlined(go, cx - mcWidth(go, 3) / 2, goY, 3, XP_GREEN);
		Font f = font("inter-500.ttf", 16, 0);
		String into2 = t.get("rago.debrief.xp_into", group(into), group(cost));
		long gained = d.xpGained();
		boolean oneGame = d.careerBefore() != null && d.careerAfter() != null && d.careerAfter().games == d.careerBefore().games + 1;
		float line = goY + 24 + 28;
		if (gained > 0 && oneGame) {
			String plus = t.get("rago.debrief.xp_gained", group(gained));
			Font pf = font("inter-600.ttf", 16, 0);
			float total = width(into2, f) + 16 + width(plus, pf);
			float lx = cx - total / 2;
			lx += text(into2, f, MUTED, lx, line) + 16;
			text(plus, pf, new Color(MC_GOLD), lx, line);
		} else {
			text(into2, f, MUTED, cx - width(into2, f) / 2, line);
		}
	}

	/** FKDR, BBLR and WLR in three boxes, with what this game changed when Hypixel has saved it. */
	private void ratios(float x, float y, float w) {
		Debrief.Career before = d.careerBefore();
		Debrief.Career after = d.careerAfter();
		String[] labels = {"FKDR", "BBLR", "WLR"};
		double[] now = {career.fkdr(), career.bblr(), career.wlr()};
		double[] was = before == null || after == null ? null : new double[] {before.fkdr(), before.bblr(), before.wlr()};
		float gap = 12;
		float bw = (w - 2 * gap) / 3;
		for (int i = 0; i < 3; i++) {
			float bx = x + i * (bw + gap);
			box(bx, y, bw, 96);
			Font lf = font("inter-600.ttf", 13, 0.14f);
			text(labels[i], lf, MUTED, bx + 16, y + 26);
			if (Double.isNaN(now[i])) {
				mc("-", bx + 16, y + 40, 3, MC_GRAY);
				continue;
			}
			String v = number(now[i], 2);
			mc(v, bx + 16, y + 40, 3, ratioColor(now[i]));
			if (was != null && !Double.isNaN(was[i])) {
				double diff = now[i] - was[i];
				if (Math.abs(diff) >= 0.005) {
					Font df = font("inter-600.ttf", 13, 0);
					String ds = (diff > 0 ? "+" : "−") + number(Math.abs(diff), 2);
					text(ds, df, new Color(diff > 0 ? MC_GREEN : MC_RED), bx + 16, y + 84);
				}
			}
		}
	}

	/** Without the Hypixel API: this session's games, wins and final kills in the same boxes. */
	private void sessionBox(float x, float y, float w) {
		Font tf = font("inter-600.ttf", 13, 0.14f);
		Font lf = font("inter-600.ttf", 12, 0.08f);
		String[] labels = {t.get("rago.debrief.session_games"), t.get("rago.debrief.session_wins"), t.get("rago.debrief.session_finals")};
		int[] values = {session.games, session.wins, session.finalKills};
		int[] colors = {MC_WHITE, MC_GREEN, MC_GOLD};
		text(t.get("rago.debrief.session_title").toUpperCase(locale), tf, DIM, x + 2, y - 14);
		float gap = 12;
		float bw = (w - 2 * gap) / 3;
		for (int i = 0; i < 3; i++) {
			float bx = x + i * (bw + gap);
			box(bx, y, bw, 96);
			text(fit(labels[i].toUpperCase(locale), lf, bw - 28), lf, MUTED, bx + 16, y + 26);
			mc(String.valueOf(values[i]), bx + 16, y + 42, 4, colors[i]);
		}
	}

	// ---- the foot: Rago ----

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
		Font f = font("inter-500.ttf", 16, 0);
		x += text(t.get("rago.debrief.ad"), f, MUTED, x, base);

		float right = W - M;
		if (signature) {
			String discord = "discord.gg/ragooptimizer";
			Font pf = font("inter-600.ttf", 18, 0);
			float pw = width(discord, pf) + 40;
			RoundRectangle2D pill = new RoundRectangle2D.Float(right - pw, y + 12, pw, 44, 44, 44);
			g.setColor(alpha(ACCENT, 0.16f));
			g.fill(pill);
			g.setColor(alpha(ACCENT, 0.7f));
			g.setStroke(new BasicStroke(1.5f));
			g.draw(pill);
			text(discord, pf, TEXT, right - pw + 20, base - 1);
			right -= pw + 24;
		}
		// the date, and this session when the player's column does not show it
		List<String> parts = new ArrayList<>();
		if (career != null && career.star >= 0 && session != null && session.games > 0) {
			parts.add(t.get("rago.debrief.session", plural(t, locale, "rago.debrief.games", session.games),
				plural(t, locale, "rago.debrief.wins", session.wins), plural(t, locale, "rago.debrief.finals", session.finalKills)));
		}
		if (d.endedAt() > 0) {
			// newer Javas put narrow no-break spaces in dates, which Rago's fonts do not have
			parts.add(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(new Date(d.endedAt()))
				.replace('\u202F', ' ').replace('\u00A0', ' '));
		}
		Font sf = font("inter-500.ttf", 15, 0);
		String s = String.join("   ·   ", parts);
		float room = right - x - 40;
		if (!s.isEmpty() && room > 120) {
			s = fit(s, sf, room);
			text(s, sf, DIM, right - width(s, sf), base);
		}
	}

	// ---- what the picture says, worked out ----

	/** The points your grade is given from: final kills, kills, beds, deaths and the result. */
	static double score(Debrief d) {
		return d.finalKills() * 3 + d.kills() + d.beds() * 4 - d.deaths() * 1.5 + (d.result() == Debrief.Result.WIN ? 6 : 0);
	}

	/** Your grade for the game, from your counts and the result: S+, S, A, B, C or D. */
	static String grade(Debrief d) {
		double score = score(d);
		return score >= 30 ? "S+" : score >= 20 ? "S" : score >= 12 ? "A" : score >= 6 ? "B" : score >= 2 ? "C" : "D";
	}

	static int gradeColor(String grade) {
		switch (grade) {
			case "S+":
				return MC_PINK;
			case "S":
				return MC_GOLD;
			case "A":
				return MC_GREEN;
			case "B":
				return MC_AQUA;
			case "C":
				return MC_WHITE;
			default:
				return MC_RED;
		}
	}

	/**
	 * How good a count is: against your average per game when known ({@code average}), else
	 * against fixed steps ({@code average}, {@code good}, {@code elite}: the least for each).
	 */
	static Rating rate(int value, double average, int averageAt, int goodAt, int eliteAt) {
		if (!Double.isNaN(average) && average > 0) {
			if (value >= average * 2 && value >= average + 2) {
				return Rating.ELITE;
			}
			if (value > average && value >= average * 1.2) {
				return Rating.GOOD;
			}
			return value < average * 0.6 ? Rating.POOR : Rating.AVERAGE;
		}
		return value >= eliteAt ? Rating.ELITE : value >= goodAt ? Rating.GOOD : value >= averageAt ? Rating.AVERAGE : Rating.POOR;
	}

	/** Deaths: fewer is better; none in a game of five minutes or more is elite. */
	static Rating rateDeaths(int deaths, double average, int seconds) {
		if (deaths == 0) {
			return seconds >= 300 ? Rating.ELITE : Rating.GOOD;
		}
		if (!Double.isNaN(average) && average > 0) {
			return deaths <= average * 0.6 ? Rating.GOOD : deaths <= average * 1.25 ? Rating.AVERAGE : Rating.POOR;
		}
		return deaths <= 2 ? Rating.GOOD : deaths <= 4 ? Rating.AVERAGE : Rating.POOR;
	}

	/** A ratio's colour: grey under 1, white, green from 2, gold from 4, pink from 8. */
	static int ratioColor(double r) {
		return r < 1 ? MC_GRAY : r < 2 ? MC_WHITE : r < 4 ? MC_GREEN : r < 8 ? MC_GOLD : MC_PINK;
	}

	/** The game's highlights, the best first, four at most. */
	static List<Badge> badges(Debrief d) {
		List<Badge> out = new ArrayList<>();
		Debrief.Career before = d.careerBefore();
		Debrief.Career after = d.careerAfter();
		if (before != null && after != null && starOf(before) >= 0 && starOf(after) > starOf(before)) {
			out.add(new Badge("level_up", "experience_bottle", starOf(after)));
		}
		int mine = d.kills() + d.finalKills();
		if (d.rank() == 1 && mine >= 3 && d.board().size() >= 2) {
			out.add(new Badge("mvp", "nether_star"));
		}
		if (d.deaths() == 0 && (d.result() == Debrief.Result.WIN || d.seconds() >= 300)) {
			out.add(new Badge("flawless", "apple_golden"));
		}
		int spree = spree(d, 30);
		if (spree >= 3) {
			out.add(new Badge("spree", "blaze_powder", spree));
		}
		if (d.finalKills() >= 5) {
			out.add(new Badge("final_boss", "fireball", d.finalKills()));
		}
		if (d.beds() >= 2) {
			out.add(new Badge("bed_breaker", "diamond_pickaxe", d.beds()));
		}
		if (d.result() == Debrief.Result.WIN && d.first(Debrief.Kind.BED_LOST) != null) {
			out.add(new Badge("clutch", "ender_pearl"));
		}
		if (d.result() == Debrief.Result.WIN && d.seconds() > 0 && d.seconds() < 600) {
			out.add(new Badge("speedrun", "sugar", clock(d.seconds())));
		}
		if (d.firstBlood()) {
			out.add(new Badge("first_blood", "redstone_dust"));
		}
		return out.size() > 4 ? out.subList(0, 4) : out;
	}

	/** The star of a career: from its experience when known (the API's star can lag behind), else Hypixel's. */
	static int starOf(Debrief.Career c) {
		return c.xp >= 0 ? BedwarsLevel.star(c.xp) : c.star;
	}

	/** The most kills of yours (finals too) within {@code seconds} of each other. */
	static int spree(Debrief d, int seconds) {
		List<Integer> at = new ArrayList<>();
		for (Debrief.Moment m : d.moments()) {
			if (m.kind == Debrief.Kind.KILL || m.kind == Debrief.Kind.FINAL_KILL) {
				at.add(m.at);
			}
		}
		int best = 0;
		int start = 0;
		for (int i = 0; i < at.size(); i++) {
			while (at.get(i) - at.get(start) > seconds) {
				start++;
			}
			best = Math.max(best, i - start + 1);
		}
		return best;
	}

	/** The first colour code in {@code s} ("§b[MVP§c+§b]" gives 'b'), or {@code fallback}. */
	static char firstColor(String s, char fallback) {
		for (int i = 0; i + 1 < s.length(); i++) {
			if (s.charAt(i) == '§' && "0123456789abcdef".indexOf(Character.toLowerCase(s.charAt(i + 1))) >= 0) {
				return Character.toLowerCase(s.charAt(i + 1));
			}
		}
		return fallback;
	}

	/** {@code s} without its colour codes. */
	static String plain(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '§' && i + 1 < s.length()) {
				i++;
			} else {
				b.append(s.charAt(i));
			}
		}
		return b.toString();
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

	// ---- drawing ----

}
