package gg.rago.hud.elements;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.bedwars.GameEvents;
import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TabTimers;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Bed Wars event timers: how long until Diamond II, Emerald II, Diamond III, Emerald III, the
 * beds going, sudden death and the end of the game. The sidebar's countdown to the next event sets
 * the game clock twice a second; the events after it follow Hypixel's usual schedule. The text is
 * rebuilt once a second, never per frame. Shown on the screen, in the tab list ({@link TabTimers}),
 * or both.
 */
public final class EventTimersHud extends TextHudElement {
	/** What the editor shows: a game 3:23 in. */
	private static final int SAMPLE_ELAPSED = 3 * 60 + 23;

	{
		section("timers"); // after the style and background of every HUD element
	}

	/** Where the timers show: "hud", "tab" (the tab list, nothing on the screen) or "both". */
	private final ChoiceOption display = addOption(new ChoiceOption("timers_display", "hud", "hud", "tab", "both"));
	private final BooleanOption diamonds = addOption(new BooleanOption("events_diamond", true));
	private final BooleanOption emeralds = addOption(new BooleanOption("events_emerald", true));
	private final BooleanOption beds = addOption(new BooleanOption("events_bed_gone", true));
	private final BooleanOption suddenDeath = addOption(new BooleanOption("events_sudden_death", true));
	private final BooleanOption gameEnd = addOption(new BooleanOption("events_game_end", true));
	private final BooleanOption nextOnly = addOption(new BooleanOption("events_next_only", false));
	private final BooleanOption roman = addOption(new BooleanOption("events_roman", true));
	private final BooleanOption colors = addOption(new BooleanOption("events_colors", true));

	private World world;
	private int ticks;
	/** Seconds the game had run at tick {@link #readAt}, or -1 before the sidebar said. */
	private int elapsed = -1;
	private int readAt;
	/** A countdown the sidebar shows for an event this mode has but the schedule does not. */
	private GameEvents.Reading other;
	private int otherAt;
	private int shownSecond = -1;
	private String text = "";
	/** The tab list's lines, or null when there is nothing to add there. */
	private String tabText;
	private final List<String> entries = new ArrayList<>();

	public EventTimersHud() {
		super("event_timers", FeatureClass.INFORMATIONAL, false, Anchor.MIDDLE_LEFT, 4, 0);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	public boolean hasContent() {
		return !text.isEmpty();
	}

	@Override
	public void tick() {
		ticks++;
		World w = Minecraft.getMinecraft().theWorld;
		if (w != world) {
			world = w;
			elapsed = -1;
			other = null;
			text = "";
			tabText = null;
			shownSecond = -1;
		}
		if (w == null || ticks % 10 != 0) {
			return;
		}
		if (!BedwarsGame.playing()) {
			elapsed = -1;
			other = null;
			text = "";
			tabText = null;
			return;
		}
		GameEvents.Reading r = GameEvents.read(BedwarsGame.eventLine());
		if (r != null && r.event != null) {
			elapsed = r.elapsed();
			readAt = ticks;
			other = null;
		} else if (r != null) {
			other = r;
			otherAt = ticks;
		}
		int second = now();
		if (second != shownSecond || second < 0 && other != null) {
			shownSecond = second;
			text = build(second, false);
			tabText = display.is("hud") ? null : TabTimers.lines(entries);
		}
	}

	/** Seconds since the game started, counted on from the last reading. */
	private int now() {
		return elapsed < 0 ? -1 : elapsed + (ticks - readAt) / 20;
	}

	/** The timers, one to a line; {@link #entries} keeps them for the tab list. */
	private String build(int now, boolean placeholder) {
		entries.clear();
		int rows = 0;
		if (now >= 0) {
			for (GameEvents.Event e : GameEvents.SCHEDULE) {
				if (e.at <= now || !shown(e.kind)) {
					continue;
				}
				if (nextOnly.get() && rows == 2) {
					break;
				}
				entries.add(row(name(e), color(e.kind), e.at - now));
				rows++;
			}
		} else if (other != null && !placeholder) {
			entries.add(row(other.label, "§e", other.left - (ticks - otherAt) / 20));
		}
		return String.join("\n", entries);
	}

	private String row(String name, String color, int left) {
		return colors.get() ? color + name + " §f" + GameEvents.clock(left) : name + ' ' + GameEvents.clock(left);
	}

	@Override
	public boolean onScreen() {
		return !display.is("tab");
	}

	/** The lines to add to the tab list, or null. */
	public String tabLine() {
		return isEnabled() ? tabText : null;
	}

	private boolean shown(GameEvents.Kind kind) {
		switch (kind) {
			case DIAMOND:
				return diamonds.get();
			case EMERALD:
				return emeralds.get();
			case BED_GONE:
				return beds.get();
			case SUDDEN_DEATH:
				return suddenDeath.get();
			default:
				return gameEnd.get();
		}
	}

	private String name(GameEvents.Event e) {
		switch (e.kind) {
			case DIAMOND:
			case EMERALD:
				String tier = roman.get() ? GameEvents.roman(e.tier) : String.valueOf(e.tier);
				return I18n.format(e.kind == GameEvents.Kind.DIAMOND ? "rago.events.diamond" : "rago.events.emerald") + ' ' + tier;
			case BED_GONE:
				return I18n.format("rago.events.bed_gone");
			case SUDDEN_DEATH:
				return I18n.format("rago.events.sudden_death");
			default:
				return I18n.format("rago.events.game_end");
		}
	}

	private static String color(GameEvents.Kind kind) {
		switch (kind) {
			case DIAMOND:
				return "§b";
			case EMERALD:
				return "§a";
			case BED_GONE:
				return "§c";
			case SUDDEN_DEATH:
				return "§d";
			default:
				return "§6";
		}
	}

	@Override
	protected String value(boolean placeholder) {
		return placeholder ? build(SAMPLE_ELAPSED, true) : text;
	}
}
