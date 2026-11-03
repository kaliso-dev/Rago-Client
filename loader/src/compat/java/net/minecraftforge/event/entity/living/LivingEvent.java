package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.EntityEvent;

public class LivingEvent extends EntityEvent {
	public final EntityLivingBase entityLiving;

	public LivingEvent(EntityLivingBase entity) {
		super(entity);
		this.entityLiving = entity;
	}
}
