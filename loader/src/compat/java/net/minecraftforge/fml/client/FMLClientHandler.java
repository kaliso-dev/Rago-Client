package net.minecraftforge.fml.client;

import net.minecraft.client.Minecraft;

public final class FMLClientHandler {
	private static final FMLClientHandler INSTANCE = new FMLClientHandler();

	public static FMLClientHandler instance() {
		return INSTANCE;
	}

	public Minecraft getClient() {
		return Minecraft.func_71410_x();
	}

	/** OptiFine is not loaded by Rago's loader (yet). */
	public boolean hasOptifine() {
		return false;
	}

	/** Forge clears its mod list cache of servers here; there is none without Forge. */
	public void setupServerList() {
	}
}
