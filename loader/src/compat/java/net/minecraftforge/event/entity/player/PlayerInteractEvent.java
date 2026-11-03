package net.minecraftforge.event.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Event;

public class PlayerInteractEvent extends PlayerEvent {
	public enum Action {
		RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK, LEFT_CLICK_BLOCK
	}

	public final Action action;
	public final World world;
	public final BlockPos pos;
	public final EnumFacing face;
	public Event.Result useBlock = Event.Result.DEFAULT;
	public Event.Result useItem = Event.Result.DEFAULT;

	public PlayerInteractEvent(EntityPlayer player, Action action, BlockPos pos, EnumFacing face, World world) {
		super(player);
		this.action = action;
		this.pos = pos;
		this.face = face;
		this.world = world;
	}
}
