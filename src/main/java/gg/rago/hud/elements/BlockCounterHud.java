package gg.rago.hud.elements;

import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.Shown;
import gg.rago.hud.TextHudElement;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** How many building blocks you carry (wool, clay, glass, wood, end stone, obsidian, ladders). */
public final class BlockCounterHud extends TextHudElement {
	/** Loaded on first use, once the game's blocks exist. */
	private static final class Building {
		static final Set<Block> BLOCKS = new HashSet<>(Arrays.asList(
			Blocks.wool, Blocks.hardened_clay, Blocks.stained_hardened_clay, Blocks.glass, Blocks.stained_glass,
			Blocks.planks, Blocks.end_stone, Blocks.obsidian, Blocks.ladder, Blocks.sandstone));
	}

	private final IntOption warnAt = addOption(new IntOption("warn_blocks", 16, 0, 64));
	private int count;

	public BlockCounterHud() {
		super("block_counter", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 104);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	/** Counted once per tick rather than every frame. */
	@Override
	public void tick() {
		EntityPlayer p = Minecraft.getMinecraft().thePlayer;
		int n = 0;
		if (p != null) {
			for (ItemStack s : p.inventory.mainInventory) {
				if (s != null && s.getItem() instanceof ItemBlock && Building.BLOCKS.contains(((ItemBlock) s.getItem()).getBlock())) {
					n += s.stackSize;
				}
			}
		}
		count = n;
	}

	@Override
	public boolean hasContent() {
		return Minecraft.getMinecraft().thePlayer != null;
	}

	@Override
	protected String label(boolean after) {
		return after ? "blocks" : "Blocks";
	}

	@Override
	protected String value(boolean placeholder) {
		return Shown.of(placeholder ? 64 : count);
	}

	@Override
	protected int valueColor(boolean placeholder) {
		int n = placeholder ? 64 : count;
		int alpha = textColor.get() & 0xFF000000;
		if (n == 0) {
			return alpha | 0xFF5555;
		}
		return n <= warnAt.get() ? alpha | 0xFFAA00 : super.valueColor(placeholder);
	}
}
