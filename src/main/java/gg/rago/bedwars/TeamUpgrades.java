package gg.rago.bedwars;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your team's Bed Wars upgrades in one game: enchantment levels, Haste, the forge, the heal pool
 * and the trap queue. Levels come from what you wear and hold (whatever language Hypixel speaks)
 * and from the chat line Hypixel prints when someone in your team buys an upgrade; traps come
 * from those purchases and leave the queue, first one first, when one goes off.
 */
public final class TeamUpgrades {
	public enum Trap {
		BLINDNESS, REVEAL, COUNTER_OFFENSIVE, MINER_FATIGUE
	}

	public static final int MAX_TRAPS = 3;

	private static final String[] VERBS = {" purchased ", " a acheté ", " avez acheté ", " hat gekauft", " compró ", " comprou "};
	private static final Pattern TIER = Pattern.compile("\\b(IV|III|II|I)\\s*$");
	private static final Pattern GOLD = word("gold|golden|or|doree|oro|goldene");
	private static final Pattern MARKS = Pattern.compile("\\p{M}");

	public int sharpness;
	public int protection;
	public int featherFalling;
	public int haste;
	/** 0 none, 1 iron, 2 golden, 3 emerald, 4 molten. */
	public int forge;
	public boolean healPool;
	private final List<Trap> traps = new ArrayList<>(MAX_TRAPS);

	public void reset() {
		sharpness = 0;
		protection = 0;
		featherFalling = 0;
		haste = 0;
		forge = 0;
		healPool = false;
		traps.clear();
	}

	/** The queued traps, the next to go off first. */
	public List<Trap> traps() {
		return traps;
	}

	/** Levels seen on your gear or effects: upgrades never go down within a game. */
	public void seen(int sharpness, int protection, int featherFalling, int haste) {
		this.sharpness = Math.max(this.sharpness, sharpness);
		this.protection = Math.max(this.protection, protection);
		this.featherFalling = Math.max(this.featherFalling, featherFalling);
		this.haste = Math.max(this.haste, haste);
	}

	/** A chat line without colours; returns true when it was a team upgrade or trap bought. */
	public boolean chat(String line) {
		String item = purchase(line);
		if (item == null) {
			return false;
		}
		String w = plain(item);
		Trap trap = trap(w);
		if (trap != null) {
			if (traps.size() < MAX_TRAPS) {
				traps.add(trap);
			}
			return true;
		}
		int f = forge(w);
		if (f > 0) {
			forge = Math.max(forge, f);
			return true;
		}
		if (w.contains("heal") || w.contains("bassin") || w.contains("piscine") || w.contains("guerison")) {
			healPool = true;
			return true;
		}
		int tier = tier(item);
		if (w.contains("sharpened") || w.contains("aiguis") || w.contains("affut")) {
			sharpness = Math.max(sharpness, Math.max(1, tier));
		} else if (w.contains("reinforced") || w.contains("renforc")) {
			protection = Math.max(protection, Math.max(1, tier));
		} else if (w.contains("maniac") || w.contains("mineur fou") || w.contains("forcene")) {
			haste = Math.max(haste, Math.max(1, tier));
		} else if (w.contains("cushioned") || w.contains("amorti") || w.contains("rembourr")) {
			featherFalling = Math.max(featherFalling, Math.max(1, tier));
		} else {
			return false;
		}
		return true;
	}

	/**
	 * A trap of yours went off: the one the message names leaves the queue, or else the first one,
	 * which is the one Hypixel sets off.
	 */
	public void triggered(String text) {
		if (traps.isEmpty()) {
			return;
		}
		Trap named = text == null ? null : namedTrap(plain(text));
		int i = named == null ? -1 : traps.indexOf(named);
		traps.remove(i >= 0 ? i : 0);
	}

	/**
	 * "Steve purchased Iron Forge" (or "Steve a acheté ..."): what was bought, or null. A player's
	 * own chat line has a colon after the name and is left out.
	 */
	static String purchase(String line) {
		if (line == null) {
			return null;
		}
		String s = line.toLowerCase(Locale.ROOT);
		for (String v : VERBS) {
			int i = s.indexOf(v);
			if (i > 0) {
				return s.substring(0, i).contains(":") ? null : line.substring(i + v.length()).trim();
			}
		}
		return null;
	}

	/** The trap an item is, or null when it is not a trap. */
	static Trap trap(String w) {
		if (!w.contains("trap") && !w.contains("piege") && !w.contains("falle") && !w.contains("trampa") && !w.contains("armadilha")) {
			return null;
		}
		Trap named = namedTrap(w);
		// "It's a Trap!" blinds and slows
		return named != null ? named : Trap.BLINDNESS;
	}

	private static Trap namedTrap(String w) {
		if (w.contains("fatigue") || w.contains("minage") || w.contains("miner") || w.contains("abbau")) {
			return Trap.MINER_FATIGUE;
		}
		if (w.contains("counter") || w.contains("contre") || w.contains("gegen")) {
			return Trap.COUNTER_OFFENSIVE;
		}
		if (w.contains("alarm") || w.contains("reveal") || w.contains("revel")) {
			return Trap.REVEAL;
		}
		if (w.contains("blind") || w.contains("aveugl") || w.contains("it's a trap") || w.contains("its a trap")) {
			return Trap.BLINDNESS;
		}
		return null;
	}

	/** The forge an item is: 1 iron, 2 golden, 3 emerald, 4 molten, 0 for something else. */
	static int forge(String w) {
		if (!w.contains("forge") && !w.contains("schmiede") && !w.contains("fragua") && !w.contains("forja")) {
			return 0;
		}
		if (w.contains("molten") || w.contains("fondu") || w.contains("fusion") || w.contains("geschmolzen")) {
			return 4;
		}
		if (w.contains("emerald") || w.contains("emeraude") || w.contains("smaragd") || w.contains("esmeralda")) {
			return 3;
		}
		if (GOLD.matcher(w).find()) {
			return 2;
		}
		// the first forge is the iron one
		return 1;
	}

	private static int tier(String item) {
		Matcher m = TIER.matcher(item.replaceAll("[!.]+$", "").trim());
		if (!m.find()) {
			return 0;
		}
		switch (m.group(1)) {
			case "IV":
				return 4;
			case "III":
				return 3;
			case "II":
				return 2;
			default:
				return 1;
		}
	}

	private static Pattern word(String words) {
		return Pattern.compile("(^|[^\\p{L}])(" + words + ")($|[^\\p{L}])");
	}

	/** Lower case without accents: "Piège" reads "piege". */
	static String plain(String s) {
		return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
	}
}
