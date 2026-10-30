package gg.rago.hud.elements;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.bedwars.GameEvents;
import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.World;

/**
 * Bed Wars event timers: how long until Diamond II, Emerald II, Diamond III, Emerald III, the
 * beds going, sudden death and the end of the game. The sidebar's countdown to the next event sets
 * the game clock twice a second; the events after it follow Hypixel's usual schedule. The text is
 * rebuilt once a second, never per frame.
 */
public final class EventTimersHud extends TextHudElement {
	/** What the editor shows: a game 3:23 in. */
	private static final int SAMPLE_ELAPSED = 3 * 60 + 23;

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
			shownSecond = -1;
		}
		if (w == null || ticks % 10 != 0) {
			return;
		}
		if (!BedwarsGame.playing()) {
			elapsed = -1;
			other = null;
			text = "";
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
		}
	}

	/** Seconds since the game started, counted on from the last reading. */
	private int now() {
		return elapsed < 0 ? -1 : elapsed + (ticks - readAt) / 20;
	}

	private String build(int now, boolean placeholder) {
		StringBuilder b = new StringBuilder();
		int rows = 0;
		if (now >= 0) {
			for (GameEvents.Event e : GameEvents.SCHEDULE) {
				if (e.at <= now || !shown(e.kind)) {
					continue;
				}
				if (nextOnly.get() && rows == 2) {
					break;
				}
				row(b, name(e), color(e.kind), e.at - now);
				rows++;
			}
		} else if (other != null && !placeholder) {
			row(b, other.label, "§e", other.left - (ticks - otherAt) / 20);
		}
		return b.toString();
	}

	private void row(StringBuilder b, String name, String color, int left) {
		if (b.length() > 0) {
			b.append('\n');
		}
		if (colors.get()) {
			b.append(color).append(name).append(" §f").append(GameEvents.clock(left));
		} else {
			b.append(name).append(' ').append(GameEvents.clock(left));
		}
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
