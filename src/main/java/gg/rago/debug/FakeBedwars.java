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
 * {@code /rago testholo [n]} floating texts like the ones over Hypixel's generators, and
 * {@code /rago testgame [n]} fills the tab list with players in four teams and two bots, and
 * {@code /rago testchat [seconds]} adds a Bed Wars chat line every second.
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
	 * Puts {@code n} floating texts (client side only) around you as Hypixel shows them over its
	 * generators and shop keepers: invisible armour stands with a name always shown, three lines a
	 * generator, to measure what they cost to draw.
	 */
	public static void holograms(int n) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) {
			return;
		}
		String[][] groups = {
			{"\u00a7eTier \u00a7cII", "\u00a7b\u00a7lDiamond", "\u00a7eSpawns in \u00a7c23 \u00a7eseconds"},
			{"\u00a7eTier \u00a7cIII", "\u00a72\u00a7lEmerald", "\u00a7eSpawns in \u00a7c41 \u00a7eseconds"},
			{"\u00a7bITEM SHOP", "\u00a7e\u00a7lRIGHT CLICK", "\u00a7bSOLO UPGRADES"},
		};
		int sets = (n + 2) / 3;
		for (int i = 0; i < n; i++) {
			int set = i / 3;
			int line = i % 3;
			double a = Math.PI * 2 * set / sets;
			double r = 7 + set % 3 * 3;
			net.minecraft.entity.item.EntityArmorStand stand = new net.minecraft.entity.item.EntityArmorStand(mc.theWorld,
				mc.thePlayer.posX + Math.cos(a) * r, mc.thePlayer.posY + 0.6 - line * 0.3, mc.thePlayer.posZ + Math.sin(a) * r);
			net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
			tag.setBoolean("Invisible", true);
			tag.setBoolean("NoGravity", true);
			stand.readEntityFromNBT(tag);
			stand.setCustomNameTag(groups[set % groups.length][line]);
			stand.setAlwaysRenderNameTag(true);
			mc.theWorld.addEntityToWorld(-2000 - i, stand);
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
		if (event.phase == TickEvent.Phase.END && chatLeft > 0 && ticks % 20 == 0) {
			chatLeft--;
			chatLine();
		}
	}

	private int chatLeft;
	private int chatCount;

	/** {@code /rago testchat [seconds]}: a Bed Wars chat line every second, so the chat stays full. */
	public static void chat(int seconds) {
		start(running == null ? 337 : running.left);
		running.chatLeft = seconds;
	}

	private void chatLine() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.ingameGUI == null) {
			return;
		}
		int n = chatCount++;
		String[] lines = {
			"§9Enemy" + n % 7 + " §7was knocked into the void by §cRagoTest§7. §b§lFINAL KILL!",
			"§7[§6" + (100 + n % 400) + "✫§7] §cRagoTest§f: gg",
			"§a+" + (n % 3 + 1) + " Emeralds",
			"§eEnemy" + n % 5 + " §7fell into the void.",
			"§7[§b" + (300 + n % 200) + "✫§7] §9Enemy" + n % 4 + "§f: where is the diamond gen",
			"§c§lBED DESTRUCTION > §fGreen Bed §7was destroyed by §9Enemy" + n % 4 + "§7!",
		};
		mc.ingameGUI.getChatGUI().printChatMessage(new net.minecraft.util.ChatComponentText(lines[n % lines.length]));
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
		// as Hypixel's eight-team sidebar: 14 lines
		String[] lines = {
			"§711/01/26 §8m42B",
			"",
			"Diamond II in §a" + left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60,
			" ",
			"§cR §fRed: §a✔ §7YOU",
			"§9B §fBlue: §a✔",
			"§aG §fGreen: §c✘",
			"§eY §fYellow: §a2",
			"§bA §fAqua: §a✔",
			"§fW §fWhite: §c✘",
			"§dP §fPink: §a1",
			"§8S §fGray: §a✔",
			"  ",
			"§ewww.hypixel.net",
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
