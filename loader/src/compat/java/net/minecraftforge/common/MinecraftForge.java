package net.minecraftforge.common;

import net.minecraftforge.fml.common.eventhandler.EventBus;

public final class MinecraftForge {
	public static final EventBus EVENT_BUS = new EventBus();
	public static final EventBus TERRAIN_GEN_BUS = new EventBus();
	public static final EventBus ORE_GEN_BUS = new EventBus();

	private MinecraftForge() {
	}
}
