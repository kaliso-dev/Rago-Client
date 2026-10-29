package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.render.WorldDraw;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Replaces the thin black outline around the block you look at with a coloured outline and fill. */
public final class BlockOverlay extends Module {
	private final BooleanOption outline = addOption(new BooleanOption("outline", true));
	private final ColorOption outlineColor = addOption(new ColorOption("outline_color", 0xFFFFFFFF));
	private final IntOption outlineOpacity = addOption(new IntOption("outline_opacity", 70, 10, 100));
	private final IntOption outlineWidth = addOption(new IntOption("outline_width", 2, 1, 10));
	private final BooleanOption fill = addOption(new BooleanOption("fill", true));
	private final ColorOption fillColor = addOption(new ColorOption("fill_color", 0xFFFFFFFF));
	private final IntOption fillOpacity = addOption(new IntOption("fill_opacity", 15, 5, 100));

	public BlockOverlay() {
		super("block_overlay", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.PVP;
	}

	@SubscribeEvent
	public void onHighlight(DrawBlockHighlightEvent event) {
		MovingObjectPosition hit = event.target;
		if (!isEnabled() || event.subID != 0 || hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
			return;
		}
		event.setCanceled(true);
		World world = Minecraft.getMinecraft().theWorld;
		BlockPos pos = hit.getBlockPos();
		Block block = world.getBlockState(pos).getBlock();
		if (block.getMaterial() == Material.air || !world.getWorldBorder().contains(pos)) {
			return;
		}
		EntityPlayer p = event.player;
		float pt = event.partialTicks;
		double cx = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
		double cy = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
		double cz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
		block.setBlockBoundsBasedOnState(world, pos);
		AxisAlignedBB box = block.getSelectedBoundingBox(world, pos).expand(0.002, 0.002, 0.002).offset(-cx, -cy, -cz);
		if (fill.get()) {
			WorldDraw.filled(box, withAlpha(fillColor.get(), fillOpacity.get()));
		}
		if (outline.get()) {
			WorldDraw.outline(box, withAlpha(outlineColor.get(), outlineOpacity.get()), outlineWidth.get());
		}
	}

	static int withAlpha(int rgb, int percent) {
		return Math.round(percent * 2.55f) << 24 | rgb & 0xFFFFFF;
	}
}
