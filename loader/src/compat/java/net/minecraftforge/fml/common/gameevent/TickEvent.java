package net.minecraftforge.fml.common.gameevent;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;

public class TickEvent extends Event {
	public enum Type {
		WORLD, PLAYER, CLIENT, SERVER, RENDER
	}

	public enum Phase {
		START, END
	}

	public final Type type;
	public final Side side;
	public final Phase phase;

	public TickEvent(Type type, Side side, Phase phase) {
		this.type = type;
		this.side = side;
		this.phase = phase;
	}

	@Override
	public boolean isCancelable() {
		return false;
	}

	public static class ClientTickEvent extends TickEvent {
		public ClientTickEvent(Phase phase) {
			super(Type.CLIENT, Side.CLIENT, phase);
		}
	}

	public static class RenderTickEvent extends TickEvent {
		public final float renderTickTime;

		public RenderTickEvent(Phase phase, float renderTickTime) {
			super(Type.RENDER, Side.CLIENT, phase);
			this.renderTickTime = renderTickTime;
		}
	}
}
