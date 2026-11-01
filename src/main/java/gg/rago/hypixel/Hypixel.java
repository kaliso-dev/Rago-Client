package gg.rago.hypixel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;
import java.util.UUID;

/** Small facts about being on Hypixel. */
public final class Hypixel {
	private Hypixel() {
	}

	/** Whether the current server is Hypixel, from the address typed into the server list. */
	public static boolean connected() {
		Minecraft mc = Minecraft.getMinecraft();
		if (gg.rago.debug.FakeBedwars.ON && mc.isIntegratedServerRunning()) {
			return true;
		}
		ServerData server = mc.getCurrentServerData();
		if (server == null || mc.isIntegratedServerRunning() || server.serverIP == null) {
			return false;
		}
		// asked many times a frame (every name tag): the address is only read again when it changes
		if (server.serverIP != checkedIp) {
			checkedIp = server.serverIP;
			String ip = checkedIp.toLowerCase(Locale.ROOT);
			int colon = ip.indexOf(':');
			String host = colon >= 0 ? ip.substring(0, colon) : ip;
			checkedHypixel = host.equals("hypixel.net") || host.endsWith(".hypixel.net");
		}
		return checkedHypixel;
	}

	private static String checkedIp;
	private static boolean checkedHypixel;

	/** Hypixel's NPCs and the bots it adds to games have version 2 UUIDs: never players. */
	public static boolean npc(UUID id) {
		return id == null || id.version() == 2;
	}

	/** Real accounts have version 4 UUIDs; Hypixel's NPCs and nicked players do not. */
	public static boolean realPlayer(UUID id) {
		return id != null && id.version() == 4;
	}

	/**
	 * Each prestige's colours, one per 100 stars from 0 to 10000: the opening bracket, each digit
	 * from the left, the star, then the closing bracket, as Minecraft colour codes. Hypixel's own
	 * default tags: 0 to 3000 checked against Hypixel's list of prestiges, the others as the
	 * community's in-game-checked table has them (Statsify's, as the MIT-licensed hypixel-utils
	 * package lists it).
	 */
	private static final String[] PRESTIGES = {
		"7777777", "fffffff", "6666666", "bbbbbbb", "2222222", "3333333", "4444444", "ddddddd", "9999999", "5555555", // 0-900
		"c6eabd5", "7ffff77", "7eeee67", "7bbbb37", "7aaaa27", "7333397", "7cccc47", "7dddd57", "7999917", "7555587", // 1000-1900
		"87ff778", "ffee666", "66ffb33", "55dd6ee", "bbff778", "ffaa222", "44ccdd5", "eeff888", "aa2266e", "bb33991", // 2000-2900
		"ee66cc4", "993366e", "c4774cc", "999dcc4", "2add552", "cc442aa", "aaab991", "44ccb33", "11955d1", "ccaa399", // 3000-3900
		"55cc66e", "ee6cdd5", "193bf77", "0588550", "22ae65d", "ffbb333", "3bee6d5", "f4cc919", "55c6eb3", "2affaa2", // 4000-4900
		"4459910", "4cc6ef4", "193bfe1", "5defed5", "3a282a3", "2aefbd5", "4cefec4", "4623958", "5c6fb39", "7087ff7", // 5000-5900
		"cffffcf", "6efffb3", "efe66fe", "aeeeea2", "bbcccaa", "33aafa3", "9ddddb9", "5ddddf5", "066eeff", "aaaa228", // 6000-6900
		"3bbbbf3", "4c6ec6e", "2af2af8", "233bba2", "88888d8", "6622fff", "fff77c8", "dcccc6d", "87fffe8", "6f262f6", // 7000-7900
		"2aaac42", "87fb391", "fffffaf", "8844cc8", "fdddaaf", "36666e3", "dffffed", "8666668", "444ccff", "9bbb339", // 8000-8900
		"ddddd58", "0c66cc4", "2dddda2", "f8888ff", "e648888", "008877f", "eee00e0", "dddeebe", "0888880", "87fffef", // 9000-9900
		"9bffffc4", // 10000 and more
	};
	/** The tags already made, by star: the tab list and the name tags ask for them every frame. */
	private static final String[] STAR_TAGS = new String[10_100];

	/** A Bedwars star in Hypixel's colours for its prestige, such as "[312✫]" or "[2100⚝]". */
	public static String bedwarsStar(int star) {
		star = Math.max(0, star);
		if (star < STAR_TAGS.length) {
			String tag = STAR_TAGS[star];
			if (tag == null) {
				STAR_TAGS[star] = tag = starTag(star);
			}
			return tag;
		}
		return starTag(star);
	}

	/**
	 * The star after the number: a new one each thousand from 1100 on (the prestiges up to 1000,
	 * 2000 and 3000 keep the one before, as on Hypixel's list).
	 */
	static char starSymbol(int star) {
		return star < 1100 ? '✫' : star < 2100 ? '✪' : star < 3100 ? '⚝' : star < 4100 ? '✥' : '✭';
	}

	private static String starTag(int star) {
		String colours = PRESTIGES[Math.min(star / 100, PRESTIGES.length - 1)];
		String digits = Integer.toString(star);
		int digitColours = colours.length() - 3;
		StringBuilder b = new StringBuilder(32);
		char[] last = {0};
		append(b, last, colours.charAt(0), '[');
		for (int i = 0; i < digits.length(); i++) {
			// fewer digits than colours (under 1000 stars, where they are all the same): the first ones
			append(b, last, colours.charAt(1 + Math.min(i, digitColours - 1)), digits.charAt(i));
		}
		append(b, last, colours.charAt(colours.length() - 2), starSymbol(star));
		append(b, last, colours.charAt(colours.length() - 1), ']');
		return b.toString();
	}

	/** {@code c} in {@code colour}, with a colour code only where the colour changes. */
	private static void append(StringBuilder b, char[] last, char colour, char c) {
		if (colour != last[0]) {
			b.append('§').append(colour);
			last[0] = colour;
		}
		b.append(c);
	}
}
