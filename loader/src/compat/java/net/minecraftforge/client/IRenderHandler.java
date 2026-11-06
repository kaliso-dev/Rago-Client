package net.minecraftforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

public abstract class IRenderHandler {
	public abstract void render(float partialTicks, WorldClient world, Minecraft mc);
}
