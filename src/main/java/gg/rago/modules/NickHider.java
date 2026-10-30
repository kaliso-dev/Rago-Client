package gg.rago.modules;

import gg.rago.accounts.Skins;
import gg.rago.config.ChoiceOption;
import gg.rago.config.StringOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

/**
 * Nick Hider, for streamers: your name is replaced by the one you choose wherever this client
 * draws text (chat, tab list, name tags, scoreboard, menus), and your skin by Steve, Alex or a skin
 * from your locker, in third person and in the tab list. Only your screen changes: the server and
 * the other players still see your real name and skin.
 */
public final class NickHider extends Module {
	private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
	private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/alex.png");

	private final StringOption nick = addOption(new StringOption("nick_name", "You", 32));
	private final ChoiceOption skin = addOption(new ChoiceOption("nick_skin", "own", "own", "steve", "alex", "locker"));
	private final StringOption lockerSkin = addOption(new StringOption("nick_locker_skin", "qRago", 16));

	/** The name and nick the replacements were worked out for, and their results. */
	private String forName;
	private String forNick;
	private String formatted;
	private String plain;

	public NickHider() {
		super("nick_hider", FeatureClass.VISUAL, false);
		lockerSkin.showIf(() -> skin.is("locker"));
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** Uses the locker skin {@code name} as the nick's skin. */
	public void useLockerSkin(String name) {
		lockerSkin.set(name);
		skin.select("locker");
	}

	/** The locker skin the nick wears, or null when it wears another. */
	public String lockerSkinName() {
		return skin.is("locker") ? lockerSkin.get() : null;
	}

	private static boolean own(UUID id) {
		return id != null && id.equals(RagoClient.ownId());
	}

	/** {@code s} with your name replaced by the nick and its & colour codes, for text drawn with § codes. */
	public String formatted(String s) {
		update();
		return replace(s, forName, formatted, true);
	}

	/** {@code s} with your name replaced by the nick without colours, for text drawn as it is. */
	public String plain(String s) {
		update();
		return replace(s, forName, plain, false);
	}

	private void update() {
		String name = Minecraft.getMinecraft().getSession().getUsername();
		String n = nick.get();
		if (!n.equals(forNick) || name == null || !name.equals(forName)) {
			forName = name;
			forNick = n;
			formatted = colours(n.isEmpty() ? "You" : n);
			plain = strip(formatted);
		}
	}

	/** "&cName" as "§cName": & followed by a colour or style code. */
	static String colours(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '&' && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
				b.append('§');
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	static String strip(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				i++;
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	/**
	 * Every whole-word {@code name} in {@code s} (any case) replaced by {@code nick}. With
	 * {@code codes}, the colours in force before the name come back after a nick that has its own.
	 */
	static String replace(String s, String name, String nick, boolean codes) {
		if (s == null || name == null || name.isEmpty() || s.length() < name.length()) {
			return s;
		}
		int n = name.length();
		char lower = Character.toLowerCase(name.charAt(0));
		char upper = Character.toUpperCase(name.charAt(0));
		boolean nickHasCodes = codes && nick.indexOf('§') >= 0;
		StringBuilder out = null;
		int from = 0;
		for (int i = 0; i + n <= s.length(); i++) {
			char c = s.charAt(i);
			if ((c == lower || c == upper) && s.regionMatches(true, i, name, 0, n) && edge(s, i - 1, true) && edge(s, i + n, false)) {
				if (out == null) {
					out = new StringBuilder(s.length() + nick.length());
				}
				out.append(s, from, i).append(nick);
				if (nickHasCodes) {
					out.append('§').append('r').append(formatBefore(s, i));
				}
				from = i + n;
				i = from - 1;
			}
		}
		return out == null ? s : out.append(s, from, s.length()).toString();
	}

	/** Whether position {@code i} is outside a name: the ends, a § code or anything but a letter, digit or _. */
	private static boolean edge(String s, int i, boolean before) {
		if (i < 0 || i >= s.length()) {
			return true;
		}
		if (before && i > 0 && s.charAt(i - 1) == '§') {
			return true;
		}
		char c = s.charAt(i);
		return !(c == '_' || Character.isLetterOrDigit(c));
	}

	/** The § codes in force at {@code end}: the last colour and the styles after it. */
	private static String formatBefore(String s, int end) {
		StringBuilder f = new StringBuilder();
		for (int i = 0; i + 1 < end; i++) {
			if (s.charAt(i) == '§') {
				char k = Character.toLowerCase(s.charAt(i + 1));
				if ("0123456789abcdefr".indexOf(k) >= 0) {
					f.setLength(0);
				}
				if (k != 'r') {
					f.append('§').append(k);
				}
				i++;
			}
		}
		return f.toString();
	}

	/** Your skin as the nick shows it, or null to keep {@code vanilla}. */
	private ResourceLocation skin(UUID id) {
		if (!own(id) || skin.is("own")) {
			return null;
		}
		if (skin.is("steve")) {
			return STEVE;
		}
		if (skin.is("alex")) {
			return ALEX;
		}
		Skins.Skin s = Skins.find(lockerSkin.get());
		return s == null ? null : s.texture();
	}

	public ResourceLocation skinFor(AbstractClientPlayer player, ResourceLocation vanilla) {
		ResourceLocation r = skin(player.getUniqueID());
		return r != null ? r : vanilla;
	}

	/** The head drawn in the tab list. */
	public ResourceLocation skinFor(NetworkPlayerInfo info, ResourceLocation vanilla) {
		ResourceLocation r = skin(info.getGameProfile().getId());
		return r != null ? r : vanilla;
	}

	/** "slim" or "default" for the nick's skin, or null to keep the game's. */
	public String modelFor(AbstractClientPlayer player) {
		if (skin(player.getUniqueID()) == null) {
			return null;
		}
		if (skin.is("locker")) {
			Skins.Skin s = Skins.find(lockerSkin.get());
			return s != null && s.slim ? "slim" : "default";
		}
		return skin.is("alex") ? "slim" : "default";
	}
}
