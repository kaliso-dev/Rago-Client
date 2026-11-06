package net.minecraftforge.event.entity.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class AttackEntityEvent extends PlayerEvent {
	public final Entity target;

	public AttackEntityEvent(EntityPlayer player, Entity target) {
		super(player);
		this.target = target;
	}
}
