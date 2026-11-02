package gg.rago.hud;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.List;

/**
 * Bed Wars timers shown in the tab list instead of (or as well as) on the screen: their lines are
 * added under the server's header, just above the players. Each timer element keeps its line and
 * makes it again at most once a second; the header is made again only when a line or the server's
 * header changes (compared by reference), never per frame.
 */
public final class TabTimers {
	/** Timers per line, so a line still fits the list on a small window. */
	static final int PER_LINE = 4;
	private static final String GAP = "   ";

	private IChatComponent base;
	private String first;
	private String second;
	private IChatComponent shown;

	/** The header to draw: {@code base} (may be null) with the timers' lines (null when none) under it. */
	public IChatComponent header(IChatComponent base, String first, String second) {
		if (first == null && second == null) {
			return base;
		}
		if (shown == null || base != this.base || first != this.first || second != this.second) {
			this.base = base;
			this.first = first;
			this.second = second;
			shown = new ChatComponentText(joined(base == null ? null : base.getFormattedText(), first, second));
		}
		return shown;
	}

	/** The non-empty parts, one under the other. */
	static String joined(String... parts) {
		StringBuilder b = new StringBuilder();
		for (String p : parts) {
			if (p == null || p.isEmpty()) {
				continue;
			}
			if (b.length() > 0) {
				b.append('\n');
			}
			b.append(p);
		}
		return b.toString();
	}

	/** {@code entries} side by side, {@link #PER_LINE} to a line; null when there are none. */
	public static String lines(List<String> entries) {
		if (entries.isEmpty()) {
			return null;
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < entries.size(); i++) {
			if (i > 0) {
				b.append(i % PER_LINE == 0 ? "\n" : GAP);
			}
			b.append(entries.get(i));
		}
		return b.toString();
	}
}
