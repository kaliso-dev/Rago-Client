package net.minecraftforge.event.entity;

import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.Event;

public class EntityEvent extends Event {
	public final Entity entity;

	public EntityEvent(Entity entity) {
		this.entity = entity;
	}
}
