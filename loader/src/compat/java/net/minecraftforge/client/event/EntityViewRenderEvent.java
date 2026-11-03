package net.minecraftforge.client.event;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.Event;

public abstract class EntityViewRenderEvent extends Event {
	public final EntityRenderer renderer;
	public final Entity entity;
	public final Block block;
	public final double renderPartialTicks;

	public EntityViewRenderEvent(EntityRenderer renderer, Entity entity, Block block, double renderPartialTicks) {
		this.renderer = renderer;
		this.entity = entity;
		this.block = block;
		this.renderPartialTicks = renderPartialTicks;
	}

	public static class FogDensity extends EntityViewRenderEvent {
		public float density;

		public FogDensity(EntityRenderer renderer, Entity entity, Block block, double renderPartialTicks, float density) {
			super(renderer, entity, block, renderPartialTicks);
			this.density = density;
		}
	}

	public static class FogColors extends EntityViewRenderEvent {
		public float red;
		public float green;
		public float blue;

		public FogColors(EntityRenderer renderer, Entity entity, Block block, double renderPartialTicks, float red, float green, float blue) {
			super(renderer, entity, block, renderPartialTicks);
			this.red = red;
			this.green = green;
			this.blue = blue;
		}
	}

	public static class FOVModifier extends EntityViewRenderEvent {
		private float fov;

		public FOVModifier(EntityRenderer renderer, Entity entity, Block block, double renderPartialTicks, float fov) {
			super(renderer, entity, block, renderPartialTicks);
			this.fov = fov;
		}

		public float getFOV() {
			return fov;
		}

		public void setFOV(float fov) {
			this.fov = fov;
		}
	}
}
