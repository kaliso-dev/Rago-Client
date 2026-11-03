package net.minecraftforge.event.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;

public class PlayerEvent extends LivingEvent {
	public final EntityPlayer entityPlayer;

	public PlayerEvent(EntityPlayer player) {
		super(player);
		this.entityPlayer = player;
	}
}
