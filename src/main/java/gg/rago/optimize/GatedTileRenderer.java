package gg.rago.optimize;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Wraps a tile entity renderer so it can be hidden, or skipped beyond a distance. The game passes
 * the position relative to the camera, so the distance check is three multiplications.
 */
final class GatedTileRenderer<T extends TileEntity> extends TileEntitySpecialRenderer<T> {
	final TileEntitySpecialRenderer<T> inner;
	private final BooleanSupplier hidden;
	private final DoubleSupplier maxDistanceSq;

	GatedTileRenderer(TileEntitySpecialRenderer<T> inner, BooleanSupplier hidden, DoubleSupplier maxDistanceSq) {
		this.inner = inner;
		this.hidden = hidden;
		this.maxDistanceSq = maxDistanceSq;
	}

	@Override
	public void renderTileEntityAt(T te, double x, double y, double z, float partialTicks, int destroyStage) {
		if (hidden.getAsBoolean()) {
			return;
		}
		double max = maxDistanceSq.getAsDouble();
		if (max > 0 && x * x + y * y + z * z > max && !inner.forceTileEntityRender()) {
			return;
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
