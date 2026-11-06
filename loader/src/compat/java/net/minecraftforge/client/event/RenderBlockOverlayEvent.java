package net.minecraftforge.client.event;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.Event;

public class RenderBlockOverlayEvent extends Event {
	public enum OverlayType {
		FIRE, BLOCK, WATER
	}

	public final EntityPlayer player;
	public final float renderPartialTicks;
	public final OverlayType overlayType;
	public final IBlockState blockForOverlay;
	public final BlockPos blockPos;

	public RenderBlockOverlayEvent(EntityPlayer player, float renderPartialTicks, OverlayType type, IBlockState block, BlockPos blockPos) {
		this.player = player;
		this.renderPartialTicks = renderPartialTicks;
		this.overlayType = type;
		this.blockForOverlay = block;
		this.blockPos = blockPos;
	}
}
