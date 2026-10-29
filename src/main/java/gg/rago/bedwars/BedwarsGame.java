package gg.rago.bedwars;

import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.EnumChatFormatting;

/** Whether you are in a Hypixel Bed Wars lobby or game, from the sidebar's title. */
public final class BedwarsGame {
	/** The line Hypixel prints when a game starts. */
	public static final String START = "Protect your bed and destroy the enemy beds";

	private BedwarsGame() {
	}

	public static boolean inBedwars() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || !Hypixel.connected()) {
			return false;
		}
		ScoreObjective sidebar = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
		return sidebar != null && EnumChatFormatting.getTextWithoutFormattingCodes(sidebar.getDisplayName()).contains("BED WARS");
	}
}
