package gg.rago.optimize;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * Wraps a tile entity renderer so it can be hidden, skipped when occlusion culling found it hidden
 * behind blocks, or skipped beyond a distance. The game passes the block's corner relative to the
 * camera's entity, so the distance to the centre of the block, the one the game itself measures for
 * its 64 blocks, is three additions and three multiplications.
 */
final class GatedTileRenderer<T extends TileEntity> extends TileEntitySpecialRenderer<T> {
	final TileEntitySpecialRenderer<T> inner;
	private final BooleanSupplier hidden;
	private final Predicate<TileEntity> culled;
	private final DoubleSupplier maxDistanceSq;

	GatedTileRenderer(TileEntitySpecialRenderer<T> inner, BooleanSupplier hidden, Predicate<TileEntity> culled, DoubleSupplier maxDistanceSq) {
		this.inner = inner;
		this.hidden = hidden;
		this.culled = culled;
		this.maxDistanceSq = maxDistanceSq;
	}

	@Override
	public void renderTileEntityAt(T te, double x, double y, double z, float partialTicks, int destroyStage) {
		if (hidden.getAsBoolean() || culled.test(te)) {
			return;
		}
		double max = maxDistanceSq.getAsDouble();
		if (max > 0 && !inner.forceTileEntityRender()) {
			double cx = x + 0.5;
			double cy = y + 0.5;
			double cz = z + 0.5;
			if (cx * cx + cy * cy + cz * cz >= max) {
				return;
			}
		}
		inner.renderTileEntityAt(te, x, y, z, partialTicks, destroyStage);
	}

	@Override
	public void setRendererDispatcher(TileEntityRendererDispatcher dispatcher) {
		super.setRendererDispatcher(dispatcher);
		inner.setRendererDispatcher(dispatcher);
	}

	@Override
	public boolean forceTileEntityRender() {
		return inner.forceTileEntityRender();
	}
}
