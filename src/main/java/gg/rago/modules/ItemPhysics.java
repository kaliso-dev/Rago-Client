package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;

/**
 * Dropped items lie on the ground instead of floating and spinning: flat items lie flat, blocks sit
 * on the ground, each turned its own way. While falling they can tumble. Only how your client draws
 * them changes; the items are where the server puts them.
 */
public final class ItemPhysics extends Module {
	private final BooleanOption tumble = addOption(new BooleanOption("physics_tumble", true));
	private final IntOption tumbleSpeed = addOption(new IntOption("physics_tumble_speed", 100, 10, 300));

	public ItemPhysics() {
		super("item_physics", FeatureClass.VISUAL, false);
		tumbleSpeed.showIf(tumble::get);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/**
	 * Replaces vanilla's float-and-spin transform. RenderEntityItem pushes the matrix before its
	 * transform, so popping and pushing again starts over from the item's render origin.
	 */
	public void apply(EntityItem item, double x, double y, double z, float partialTicks, IBakedModel model) {
		GlStateManager.popMatrix();
		GlStateManager.pushMatrix();
		// the same random turn vanilla starts the spin from, so each item keeps its own direction
		float yaw = item.hoverStart * (180 / (float) Math.PI);
		// onGround is not kept up to date for items on the client: an item that moved last tick is in the air
		double dx = item.posX - item.prevPosX;
		double dy = item.posY - item.prevPosY;
		double dz = item.posZ - item.prevPosZ;
		boolean falling = dx * dx + dy * dy + dz * dz > 1e-4;
		float air = tumble.get() && falling ? (item.getAge() + partialTicks) * 12 * tumbleSpeed.get() / 100f : 0;
		if (model.isGui3d()) {
			float height = model.getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.GROUND).scale.y;
			GlStateManager.translate((float) x, (float) y + 0.25f * height, (float) z);
			GlStateManager.rotate(yaw, 0, 1, 0);
			if (air != 0) {
				GlStateManager.rotate(air, 1, 0, 1);
			}
		} else {
			GlStateManager.translate((float) x, (float) y + 0.02f, (float) z);
			GlStateManager.rotate(yaw, 0, 1, 0);
			// face up, the copies of a stack piling upward
			GlStateManager.rotate(-90 + air, 1, 0, 0);
		}
	}
}
