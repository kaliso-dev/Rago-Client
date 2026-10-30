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
 * {@code /rago testplayers [n]} puts other players around you to measure name tags and hitboxes.
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

	/** Puts {@code n} other players (client side only) in a ring around you, to measure name tags and hitboxes. */
	public static void players(int n) {
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
			mc.theWorld.addEntityToWorld(-1000 - i, e);
		}
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
