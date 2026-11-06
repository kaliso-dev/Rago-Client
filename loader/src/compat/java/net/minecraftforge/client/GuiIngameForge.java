package net.minecraftforge.client;

/** The switches mods flip on Forge's HUD; Rago's loader reads {@link #renderObjective} for the scoreboard. */
public final class GuiIngameForge {
	public static boolean renderVignette = true;
	public static boolean renderHelmet = true;
	public static boolean renderPortal = true;
	public static boolean renderHotbar = true;
	public static boolean renderCrosshairs = true;
	public static boolean renderBossHealth = true;
	public static boolean renderHealth = true;
	public static boolean renderArmor = true;
	public static boolean renderFood = true;
	public static boolean renderHealthMount = true;
	public static boolean renderAir = true;
	public static boolean renderExperiance = true;
	public static boolean renderJumpBar = true;
	public static boolean renderObjective = true;
	public static int left_height = 39;
	public static int right_height = 39;

	private GuiIngameForge() {
	}
}
