package net.minecraftforge.client.event;

import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.eventhandler.Event;

public class ClientChatReceivedEvent extends Event {
	public IChatComponent message;
	public final byte type;

	public ClientChatReceivedEvent(byte type, IChatComponent message) {
		this.type = type;
		this.message = message;
	}
}
