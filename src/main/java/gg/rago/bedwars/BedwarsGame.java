package gg.rago.bedwars;

import gg.rago.core.RagoClient;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Whether you are in a Hypixel Bed Wars lobby or game, from the sidebar's title. */
public final class BedwarsGame {
	/** The line Hypixel prints when a game starts. */
	public static final String START = "Protect your bed and destroy the enemy beds";
	private static Field titleField;
	private static boolean titleFieldMissing;

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

	/**
	 * The big title on screen now, without colours, or "" (Hypixel shows "TRAP TRIGGERED!",
	 * "VICTORY!" and "GAME OVER!" this way; titles fire no event).
	 */
	public static String title() {
		GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
		if (titleFieldMissing || gui == null) {
			return "";
		}
		try {
			if (titleField == null) {
				titleField = ReflectionHelper.findField(GuiIngame.class, "displayedTitle", "field_175201_x");
			}
			String t = (String) titleField.get(gui);
			return t == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(t);
		} catch (ReflectiveOperationException | RuntimeException e) {
			titleFieldMissing = true;
			RagoClient.LOG.warn("Cannot read titles: {}", e.toString());
			return "";
		}
	}
}
