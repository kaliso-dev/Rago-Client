package gg.rago.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Developer testing only, with {@code -Drago.test.hypixel=true}: a single-player world is taken
 * for Hypixel, {@code /rago testbw [seconds]} draws a Bed Wars sidebar on your screen (two teams
 * and "Diamond II in m:ss", counting down) so the Bed Wars HUDs can be tried without a server, and
 * {@code /rago testplayers [n]} puts other players around you to measure name tags and hitboxes,
 * and {@code /rago testgame [n]} fills the tab list with players in four teams and two bots.
 */
public final class FakeBedwars {
	public static final boolean ON = Boolean.getBoolean("rago.test.hypixel");
	private static final String OBJECTIVE = "rago_test";

	private static FakeBedwars running;
	private int left;
	private int ticks;

	private FakeBedwars(int left) {
		this.left = left;
	}

	public static void start(int seconds) {
		if (running == null) {
			running = new FakeBedwars(seconds);
			MinecraftForge.EVENT_BUS.register(running);
		}
		running.left = seconds;
		running.draw();
	}

	/**
	 * Puts {@code n} other players (client side only) in a ring around you, to measure name tags and
	 * hitboxes; with {@code armor}, in enchanted diamond armour, to measure the glint.
	 */
	public static void players(int n, boolean armor) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) {
			return;
		}
		for (int i = 0; i < n; i++) {
			com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Player" + i);
			net.minecraft.client.entity.EntityOtherPlayerMP e = new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld, profile);
			double a = Math.PI * 2 * i / n;
			double r = 4 + i % 3 * 2;
			e.setPositionAndRotation(mc.thePlayer.posX + Math.cos(a) * r, mc.thePlayer.posY, mc.thePlayer.posZ + Math.sin(a) * r, (float) (a * 180 / Math.PI), 0);
			if (armor) {
				net.minecraft.item.Item[] pieces = {net.minecraft.init.Items.diamond_boots, net.minecraft.init.Items.diamond_leggings,
					net.minecraft.init.Items.diamond_chestplate, net.minecraft.init.Items.diamond_helmet};
				for (int slot = 0; slot < 4; slot++) {
					net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(pieces[slot]);
					stack.addEnchantment(net.minecraft.enchantment.Enchantment.protection, 2);
					e.setCurrentItemOrArmor(slot + 1, stack);
				}
			}
			mc.theWorld.addEntityToWorld(-1000 - i, e);
		}
	}

	/**
	 * Puts {@code n} other players (client side only) in the tab list, in four teams with Hypixel's
	 * prefixes and you in Red, the last one nicked, to try the stats in the tab list and the
	 * game-start message.
	 */
	@SuppressWarnings("unchecked")
	public static void game(int n) {
		Minecraft mc = Minecraft.getMinecraft();
		net.minecraft.client.network.NetHandlerPlayClient net = mc.getNetHandler();
		if (mc.theWorld == null || mc.thePlayer == null || net == null) {
			return;
		}
		java.util.Map<java.util.UUID, net.minecraft.client.network.NetworkPlayerInfo> players;
		try {
			java.lang.reflect.Field f;
			try {
				f = net.getClass().getDeclaredField("field_147310_i");
			} catch (NoSuchFieldException e) {
				f = net.getClass().getDeclaredField("playerInfoMap");
			}
			f.setAccessible(true);
			players = (java.util.Map<java.util.UUID, net.minecraft.client.network.NetworkPlayerInfo>) f.get(net);
		} catch (ReflectiveOperationException e) {
			gg.rago.core.RagoClient.LOG.warn("testgame: no tab list", e);
			return;
		}
		Scoreboard board = mc.theWorld.getScoreboard();
		String[][] teams = {{"Red", "c", "R"}, {"Blue", "9", "B"}, {"Green", "a", "G"}, {"Yellow", "e", "Y"}};
		// like Hypixel, a scoreboard team for each player, with their team's prefix
		join(board, mc.thePlayer.getName(), teams[0]);
		java.util.Random random = new java.util.Random();
		for (int i = 0; i < n; i++) {
			java.util.UUID id = java.util.UUID.randomUUID();
			if (i == n - 1) {
				// a nicked player: Hypixel gives them an id that is not a real account's (version 1 here)
				id = new java.util.UUID(random.nextLong() & ~0xF000L | 0x1000L, random.nextLong() & 0x3FFFFFFFFFFFFFFFL | 0x8000000000000000L);
			}
			String name = "Enemy" + i;
			players.put(id, new net.minecraft.client.network.NetworkPlayerInfo(new com.mojang.authlib.GameProfile(id, name)));
			join(board, name, teams[i % teams.length]);
		}
		// two of Hypixel's bots, with no team: a version 2 id, and a name like theirs
		java.util.UUID npc = new java.util.UUID(random.nextLong() & ~0xF000L | 0x2000L, random.nextLong() & 0x3FFFFFFFFFFFFFFFL | 0x8000000000000000L);
		players.put(npc, new net.minecraft.client.network.NetworkPlayerInfo(new com.mojang.authlib.GameProfile(npc, "38qx60s71a")));
		java.util.UUID other = java.util.UUID.randomUUID();
		players.put(other, new net.minecraft.client.network.NetworkPlayerInfo(new com.mojang.authlib.GameProfile(other, "b5jxhjk15i")));
	}

	private static void join(Scoreboard board, String player, String[] t) {
		ScorePlayerTeam team = board.getTeam("p_" + player);
		if (team == null) {
			team = board.createTeam("p_" + player);
		}
		team.setNamePrefix("§" + t[1] + "§l" + t[2] + " §" + t[1]);
		board.addPlayerToTeam(player, team.getRegisteredName());
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END && ++ticks % 20 == 0 && left > 0) {
			left--;
			draw();
		}
	}

	private void draw() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			return;
		}
		Scoreboard board = mc.theWorld.getScoreboard();
		ScoreObjective o = board.getObjective(OBJECTIVE);
		if (o == null) {
			o = board.addScoreObjective(OBJECTIVE, net.minecraft.scoreboard.IScoreObjectiveCriteria.DUMMY);
			o.setDisplayName("§e§lBED WARS");
			board.setObjectiveInDisplaySlot(1, o);
		}
		String[] lines = {
			"Diamond II in " + left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60,
			"",
			"§cR §fRed: §a✓",
			"§9B §fBlue: §a✓",
		};
		for (int i = 0; i < lines.length; i++) {
			String entry = "§" + Integer.toHexString(i);
			ScorePlayerTeam team = board.getTeam("rago_t" + i);
			if (team == null) {
				team = board.createTeam("rago_t" + i);
				board.addPlayerToTeam(entry, team.getRegisteredName());
			}
			team.setNamePrefix(lines[i]);
			board.getValueFromObjective(entry, o).setScorePoints(lines.length - i);
		}
	}
}
