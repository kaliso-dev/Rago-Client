package gg.rago.optimize;

import java.util.function.BooleanSupplier;

/** Engine settings read by game hooks, from the Performance settings. */
public final class EngineTweaks {
	private final BooleanSupplier fastWorldSwitch;
	private final BooleanSupplier unstackedItems;
	private final BooleanSupplier particleCollisions;
	private final BooleanSupplier lightGlint;
	private final BooleanSupplier nearbyChunksOnly;

	EngineTweaks(BooleanSupplier fastWorldSwitch, BooleanSupplier unstackedItems, BooleanSupplier particleCollisions,
		BooleanSupplier lightGlint, BooleanSupplier nearbyChunksOnly) {
		this.fastWorldSwitch = fastWorldSwitch;
		this.unstackedItems = unstackedItems;
		this.particleCollisions = particleCollisions;
		this.lightGlint = lightGlint;
		this.nearbyChunksOnly = nearbyChunksOnly;
	}

	/** Skip the full garbage collection vanilla forces on every world change (the freeze when switching servers). */
	public boolean fastWorldSwitch() {
		return fastWorldSwitch.getAsBoolean();
	}

	/** Draw one copy of a dropped stack instead of up to five (generators in Bed Wars). */
	public boolean unstackedItems() {
		return unstackedItems.getAsBoolean();
	}

	/**
	 * Particles bounce on blocks as in vanilla. Off, a new particle moves without the collision
	 * test each tick (it looks up the blocks and entities around it), and goes through blocks.
	 */
	public boolean particleCollisions() {
		return particleCollisions.getAsBoolean();
	}

	/**
	 * The enchantment shimmer is drawn once instead of twice: vanilla draws an enchanted armour piece
	 * or item three times (the piece, then two shimmer layers at different angles); with this, twice.
	 */
	public boolean lightGlint() {
		return lightGlint.getAsBoolean();
	}

	/**
	 * Each tick, a world lists the chunks around every player to re-check their light: with the
	 * render distance at 12, 625 chunks per player, a new object for each, so tens of thousands a
	 * tick in a lobby full of players and NPCs. A client only needs the chunks around its own player (the
	 * others' chunks out of sight are not even loaded): with this, only those are listed.
	 */
	public boolean nearbyChunksOnly() {
		return nearbyChunksOnly.getAsBoolean();
	}
}
