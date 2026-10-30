package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.config.StringOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.gui.QuickplayScreen;
import gg.rago.gui.Theme;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Quickplay for Hypixel Bed Wars: a key opens a menu of the modes, and a click sends that mode's
 * {@code /play} command, exactly as if you typed it (one click, one command). Modes can be hidden
 * or pinned as favourites, your own commands added, and the last mode joined again with a key.
 */
public final class Quickplay extends Module {
	/** One entry of the menu: what it is called, its icon and the command it sends. */
	public static final class Mode {
		public final String id;
		public final String group;
		public final String icon;
		public final String command;
		/** A label typed by you, for your own commands; null for the built-in modes. */
		public final String label;

		Mode(String id, String group, String icon, String command, String label) {
			this.id = id;
			this.group = group;
			this.icon = icon;
			this.command = command;
			this.label = label;
		}

		public String name() {
			return label != null ? label : I18n.format("rago.quickplay.mode." + id);
		}
	}

	public static final String CORE = "core";
	public static final String DREAM = "dream";
	public static final String OTHER = "other";

	/** Hypixel's /play names: bedwars_(teams)_(players per team), then the Dream mode. */
	private static final List<Mode> BUILT_IN = Collections.unmodifiableList(Arrays.asList(
		new Mode("solo", CORE, "user", "/play bedwars_eight_one", null),
		new Mode("doubles", CORE, "users", "/play bedwars_eight_two", null),
		new Mode("threes", CORE, "users-round", "/play bedwars_four_three", null),
		new Mode("fours", CORE, "grid-2x2", "/play bedwars_four_four", null),
		new Mode("four_v_four", CORE, "swords", "/play bedwars_two_four", null),
		new Mode("rush_doubles", DREAM, "rocket", "/play bedwars_eight_two_rush", null),
		new Mode("rush_fours", DREAM, "rocket", "/play bedwars_four_four_rush", null),
		new Mode("ultimate_doubles", DREAM, "crown", "/play bedwars_eight_two_ultimate", null),
		new Mode("ultimate_fours", DREAM, "crown", "/play bedwars_four_four_ultimate", null),
		new Mode("lucky_doubles", DREAM, "clover", "/play bedwars_eight_two_lucky", null),
		new Mode("lucky_fours", DREAM, "clover", "/play bedwars_four_four_lucky", null),
		new Mode("armed_doubles", DREAM, "crosshair", "/play bedwars_eight_two_armed", null),
		new Mode("armed_fours", DREAM, "crosshair", "/play bedwars_four_four_armed", null),
		new Mode("voidless_doubles", DREAM, "cloud-off", "/play bedwars_eight_two_voidless", null),
		new Mode("voidless_fours", DREAM, "cloud-off", "/play bedwars_four_four_voidless", null),
		new Mode("castle", DREAM, "castle", "/play bedwars_castle", null),
		new Mode("lobby", OTHER, "house", "/lobby", null)));

	private static final int CUSTOM_SLOTS = 4;
	/** Hypixel refuses commands sent too quickly; a double click must not send twice. */
	private static final long COOLDOWN_MS = 1500;
	/** How long a first click on a mode during a game waits for the second one. */
	private static final long CONFIRM_MS = 3000;

	private final KeyBinding key = new KeyBinding("key.rago.quickplay", Keyboard.KEY_N, "key.categories.rago");
	private final KeyBinding replayKey = new KeyBinding("key.rago.quickplay_replay", 0, "key.categories.rago");

	private final ChoiceOption layout = addOption(new ChoiceOption("quickplay_layout", "grid", "grid", "list"));
	private final IntOption columns = addOption(new IntOption("quickplay_columns", 4, 2, 6));
	private final IntOption size = addOption(new IntOption("quickplay_size", 100, 70, 150));
	private final IntOption radius = addOption(new IntOption("quickplay_radius", 10, 0, 16));
	private final ColorOption accent = addOption(new ColorOption("quickplay_accent", Theme.ACCENT));
	private final ColorOption tile = addOption(new ColorOption("quickplay_tile", 0xE6141316));
	private final IntOption dim = addOption(new IntOption("quickplay_dim", 45, 0, 90));
	private final BooleanOption icons = addOption(new BooleanOption("quickplay_icons", true));
	private final BooleanOption commands = addOption(new BooleanOption("quickplay_commands", false));
	private final BooleanOption dream = addOption(new BooleanOption("quickplay_dream", true));
	private final BooleanOption close = addOption(new BooleanOption("quickplay_close", true));
	private final BooleanOption confirm = addOption(new BooleanOption("quickplay_confirm", true));
	private final StringOption[] custom = new StringOption[CUSTOM_SLOTS];
	/** Kept by the menu itself (right click, edit mode), so not shown on the mod's page. */
	private final StringOption hidden = addOption(new StringOption("quickplay_hidden", "", 600));
	private final StringOption favourites = addOption(new StringOption("quickplay_favourites", "", 600));
	private final StringOption last = addOption(new StringOption("quickplay_last", "", 64));

	private long sentAt;
	private String confirming;
	private long confirmingAt;

	public Quickplay() {
		// a key turns into a command, like Auto Text: off until you turn it on
		super("quickplay", FeatureClass.INPUT, false);
		columns.showIf(() -> layout.is("grid"));
		for (int i = 0; i < CUSTOM_SLOTS; i++) {
			custom[i] = addOption(new StringOption("quickplay_custom_" + (i + 1), "", 64));
		}
		hidden.showIf(() -> false);
		favourites.showIf(() -> false);
		last.showIf(() -> false);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	public KeyBinding key() {
		return key;
	}

	public KeyBinding replayKey() {
		return replayKey;
	}

	public boolean grid() {
		return layout.is("grid");
	}

	public int columns() {
		return columns.get();
	}

	public float scale() {
		return size.get() / 100f;
	}

	public int radius() {
		return radius.get();
	}

	public int accent() {
		return accent.get();
	}

	public int tileColor() {
		return tile.get();
	}

	public float dim() {
		return dim.get() / 100f;
	}

	public boolean showIcons() {
		return icons.get();
	}

	public boolean showCommands() {
		return commands.get();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		while (key.isPressed()) {
			if (mc.currentScreen == null && mc.thePlayer != null) {
				mc.displayGuiScreen(new QuickplayScreen(this));
			}
		}
		while (replayKey.isPressed()) {
			Mode m = find(last.get());
			if (m == null) {
				actionBar(I18n.format("rago.quickplay.no_last"));
			} else if (mc.thePlayer != null) {
				String result = join(m);
				if (result != null) {
					actionBar(result);
				}
			}
		}
	}

	private static void actionBar(String text) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.ingameGUI != null) {
			mc.ingameGUI.setRecordPlaying(text, false);
		}
	}

	/** Every entry, built-in modes then your own commands, in menu order; hidden ones included. */
	public List<Mode> all() {
		List<Mode> out = new ArrayList<>(BUILT_IN.size() + CUSTOM_SLOTS);
		for (Mode m : BUILT_IN) {
			if (dream.get() || !m.group.equals(DREAM)) {
				out.add(m);
			}
		}
		for (int i = 0; i < CUSTOM_SLOTS; i++) {
			Mode m = parseCustom("custom_" + (i + 1), custom[i].get());
			if (m != null) {
				out.add(m);
			}
		}
		return out;
	}

	/** "Name: /command" (or just "/command") as an entry; null when empty or not a command. */
	static Mode parseCustom(String id, String text) {
		String s = text == null ? "" : text.trim();
		// the command starts at the first / at the start or after a space, : or = (a name may hold a /)
		int slash = -1;
		for (int i = 0; i < s.length() && slash < 0; i++) {
			if (s.charAt(i) == '/' && (i == 0 || " :=".indexOf(s.charAt(i - 1)) >= 0)) {
				slash = i;
			}
		}
		if (slash < 0) {
			return null;
		}
		String command = s.substring(slash).trim();
		String label = s.substring(0, slash).trim();
		if (label.endsWith(":") || label.endsWith("=")) {
			label = label.substring(0, label.length() - 1).trim();
		}
		if (command.length() < 2 || command.indexOf(' ') == 1) {
			return null;
		}
		return new Mode(id, OTHER, "terminal", command, label.isEmpty() ? command : label);
	}

	private Mode find(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (Mode m : all()) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		return null;
	}

	/** The id of the mode joined last (empty when none), for its mark in the menu. */
	public String lastId() {
		return last.get();
	}

	public boolean isHidden(Mode m) {
		return ids(hidden.get()).contains(m.id);
	}

	public boolean isFavourite(Mode m) {
		return ids(favourites.get()).contains(m.id);
	}

	public void toggleHidden(Mode m) {
		toggle(hidden, m.id);
	}

	public void toggleFavourite(Mode m) {
		toggle(favourites, m.id);
	}

	private static Set<String> ids(String list) {
		Set<String> out = new LinkedHashSet<>();
		for (String s : list.split(",")) {
			if (!s.trim().isEmpty()) {
				out.add(s.trim());
			}
		}
		return out;
	}

	private static void toggle(StringOption list, String id) {
		Set<String> set = ids(list.get());
		if (!set.remove(id)) {
			set.add(id);
		}
		list.set(String.join(",", set));
		RagoClient.saveConfig();
	}

	/** Whether {@code m} waits for a second click (a game is running and leaving it needs confirming). */
	public boolean confirming(Mode m) {
		return m.id.equals(confirming) && System.currentTimeMillis() - confirmingAt < CONFIRM_MS;
	}

	public boolean closeAfterJoin() {
		return close.get();
	}

	/**
	 * Sends {@code m}'s command, unless it must not be sent yet. Returns null when it was sent, or
	 * what to tell you: not on Hypixel, sent a moment ago, or click again to leave the game.
	 */
	public String join(Mode m) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null) {
			return null;
		}
		boolean own = m.id.startsWith("custom_");
		if (!own && !Hypixel.connected()) {
			return I18n.format("rago.quickplay.not_hypixel");
		}
		long now = System.currentTimeMillis();
		if (now - sentAt < COOLDOWN_MS) {
			return I18n.format("rago.quickplay.wait");
		}
		if (confirm.get() && BedwarsGame.playing() && !confirming(m)) {
			confirming = m.id;
			confirmingAt = now;
			return I18n.format("rago.quickplay.confirm");
		}
		confirming = null;
		sentAt = now;
		if (!m.id.equals("lobby")) {
			last.set(m.id);
			RagoClient.saveConfig();
		}
		// as the chat box does: Forge's client commands first, then the server
		if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, m.command) == 0) {
			mc.thePlayer.sendChatMessage(m.command);
		}
		return null;
	}
}
