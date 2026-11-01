package gg.rago.optimize;

import java.lang.ref.WeakReference;

/**
 * Whether a client world's list of the chunks around the player can stay as it was built last
 * tick. The game empties and builds it again every tick, a new object for each chunk (625 with the
 * render distance at 12); around one player, it only changes when the player enters another chunk,
 * the render distance changes or the world does. The list must also still hold every chunk of the
 * square, so a list changed by anything else is built again.
 */
public final class ChunkListKeeper {
	private WeakReference<Object> world = new WeakReference<>(null);
	private int chunkX;
	private int chunkZ;
	private int distance;
	private boolean built;

	/**
	 * True when the list was built for this world, chunk and distance and still holds its
	 * (2 &times; distance + 1)&sup2; chunks. Otherwise false, and these are remembered for the list
	 * about to be built.
	 */
	public boolean same(Object world, int chunkX, int chunkZ, int distance, int listSize) {
		int side = 2 * distance + 1;
		boolean same = built && this.world.get() == world && this.chunkX == chunkX && this.chunkZ == chunkZ
			&& this.distance == distance && listSize == side * side;
		if (!same) {
			if (this.world.get() != world) {
				this.world = new WeakReference<>(world);
			}
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.distance = distance;
			built = true;
		}
		return same;
	}

	/** The next list is built in full (the setting was off, or the player was missing). */
	public void forget() {
		built = false;
	}
}
