package gg.rago.hud.elements;

import gg.rago.bedwars.HeightLimits;
import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * How many blocks you can still stack before the map's build limit, for Bed Wars. The limit is
 * learned from the server itself (see {@link HeightLimits}) and remembered for each map, whose name
 * is read from the "Map:" line of the lobby scoreboard; it can also be set by hand.
 */
public final class HeightLimitHud extends TextHudElement {
	/** The text shown, made again only when it changes. */
	private final Shown shown = new Shown();
	/** Ticks the server has to take a block back before it counts as kept. */
	private static final int SETTLE_TICKS = 30;

	private final IntOption manual = addOption(new IntOption("manual_limit", 0, 0, 255));
	private final IntOption warnAt = addOption(new IntOption("warn_blocks", 5, 0, 20));
	private final BooleanOption showY = addOption(new BooleanOption("show_y", false));
	private final HeightLimits limits;
	private final List<Placement> pending = new ArrayList<>();
	private World world;
	private int ticks;

	private static final class Placement {
		final BlockPos pos;
		final int due;
		boolean placed;

		Placement(BlockPos pos, int due) {
			this.pos = pos;
			this.due = due;
		}
	}

	public HeightLimitHud(Path configDir) {
		super("height_limit", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 92);
		limits = new HeightLimits(configDir == null ? null : configDir.resolve("rago-height-limits.json"));
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	private int limit() {
		return manual.get() > 0 ? manual.get() : limits.limit();
	}

	@SubscribeEvent
	public void onInteract(PlayerInteractEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || event.entityPlayer != mc.thePlayer || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
			|| event.pos == null || event.face == null || mc.theWorld == null || pending.size() > 32) {
			return;
		}
		ItemStack held = mc.thePlayer.getHeldItem();
		if (held == null || !(held.getItem() instanceof ItemBlock)) {
			return;
		}
		Block clicked = mc.theWorld.getBlockState(event.pos).getBlock();
		BlockPos at = clicked.isReplaceable(mc.theWorld, event.pos) ? event.pos : event.pos.offset(event.face);
		pending.add(new Placement(at, ticks + SETTLE_TICKS));
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			pending.clear();
			limits.setMap(null);
		}
		if (world == null || mc.isIntegratedServerRunning()) {
			return;
		}
		if (ticks % 20 == 0) {
			String map = mapName(world.getScoreboard());
			if (map != null) {
				limits.setMap(serverKey(mc.getCurrentServerData()) + "/" + map);
			}
		}
		for (Iterator<Placement> it = pending.iterator(); it.hasNext(); ) {
			Placement p = it.next();
			boolean air = world.isAirBlock(p.pos);
			if (!p.placed) {
				// the client places the block at once; if it did not, the click placed nothing
				if (air) {
					it.remove();
					continue;
				}
				p.placed = true;
			}
			if (ticks >= p.due) {
				if (air) {
					limits.rejected(p.pos.getY());
				} else {
					limits.kept(p.pos.getY());
				}
				it.remove();
			}
		}
	}

	/** The map name from a "Map: X" scoreboard line, or null. */
	static String mapName(Scoreboard board) {
		ScoreObjective o = board.getObjectiveInDisplaySlot(1);
		if (o == null) {
			return null;
		}
		for (Score s : board.getSortedScores(o)) {
			ScorePlayerTeam team = board.getPlayersTeam(s.getPlayerName());
			String line = EnumChatFormatting.getTextWithoutFormattingCodes(ScorePlayerTeam.formatPlayerName(team, s.getPlayerName()));
			String map = mapFromLine(line);
			if (map != null) {
				return map;
			}
		}
		return null;
	}

	static String mapFromLine(String line) {
		if (line == null) {
			return null;
		}
		String t = line.trim();
		if (!t.regionMatches(true, 0, "Map:", 0, 4)) {
			return null;
		}
		String map = t.substring(4).replaceAll("[^\\p{L}\\p{N} '\\-]", "").trim();
		return map.isEmpty() ? null : map;
	}

	/** Maps are kept per network: "mc.hypixel.net" and "hypixel.net" are the same. */
	static String serverKey(ServerData data) {
		if (data == null || data.serverIP == null) {
			return "unknown";
		}
		String host = data.serverIP.toLowerCase(Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}
		String[] parts = host.split("\\.");
		return parts.length > 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : host;
	}

	/** Blocks you can still stack: the next pillar block goes where your feet are. */
	private int left(EntityPlayerSP p) {
		return limit() - MathHelper.floor_double(p.posY) + 1;
	}

	@Override
	public boolean hasContent() {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		return p != null && limit() > 0;
	}

	@Override
	protected String label(boolean after) {
		return after ? "blocks" : "Limit";
	}

	@Override
	protected String value(boolean placeholder) {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		if (placeholder || p == null) {
			return showY.get() ? "12 (Y 88)" : "12";
		}
		int left = Math.max(0, left(p));
		if (!showY.get()) {
			return Shown.of(left);
		}
		int limit = limit();
		if (!shown.same(left, limit, 0)) {
			shown.set(left + " (Y " + limit + ")");
		}
		return shown.text();
	}

	@Override
	protected int valueColor(boolean placeholder) {
		EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
		int left = placeholder || p == null ? 12 : left(p);
		int alpha = textColor.get() & 0xFF000000;
		if (left <= 0) {
			return alpha | 0xFF5555;
		}
		return left <= warnAt.get() ? alpha | 0xFFAA00 : super.valueColor(placeholder);
	}
}
