package gg.rago.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Joins a server from the main menu and, once in, sends the command the player picked (for
 * example Hypixel's {@code /play bedwars_eight_two}). The command is typed into chat exactly as
 * the player would type it, two seconds after joining.
 */
public final class QuickJoin {
	private static final int DELAY_TICKS = 40;
	private static final long EXPIRE_NS = 60_000_000_000L;

	private static String pendingCommand;
	private static long requestedNs;
	private static int ticksInGame;
	private static String lastMode;

	private QuickJoin() {
	}

	public static void join(GuiScreen parent, String name, String address, String command, String mode) {
		Minecraft mc = Minecraft.getMinecraft();
		pendingCommand = command;
		requestedNs = System.nanoTime();
		ticksInGame = 0;
		if (mode != null) {
			lastMode = mode;
		}
		mc.displayGuiScreen(new GuiConnecting(parent, mc, new ServerData(name, address, false)));
	}

	/** The quick-play mode last picked this session, for the "last played" highlight. */
	public static String lastMode() {
		return lastMode;
	}

	static void tick(Minecraft mc) {
		if (pendingCommand == null) {
			return;
		}
		if (System.nanoTime() - requestedNs > EXPIRE_NS) {
			pendingCommand = null;
			return;
		}
		if (mc.thePlayer == null || mc.getCurrentServerData() == null) {
			ticksInGame = 0;
			return;
		}
		if (++ticksInGame >= DELAY_TICKS) {
			mc.thePlayer.sendChatMessage(pendingCommand);
			pendingCommand = null;
		}
	}
}
