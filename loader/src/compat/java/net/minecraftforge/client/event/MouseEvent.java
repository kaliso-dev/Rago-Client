package net.minecraftforge.client.event;

import net.minecraftforge.fml.common.eventhandler.Event;
import org.lwjgl.input.Mouse;

public class MouseEvent extends Event {
	public final int x = Mouse.getEventX();
	public final int y = Mouse.getEventY();
	public final int dx = Mouse.getEventDX();
	public final int dy = Mouse.getEventDY();
	public final int dwheel = Mouse.getEventDWheel();
	public final int button = Mouse.getEventButton();
	public final boolean buttonstate = Mouse.getEventButtonState();
	public final long nanoseconds = Mouse.getEventNanoseconds();
}
