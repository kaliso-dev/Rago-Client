package net.minecraftforge.client.event.sound;

import net.minecraft.client.audio.SoundManager;
import net.minecraftforge.fml.common.eventhandler.Event;

public class SoundEvent extends Event {
	public final SoundManager manager;

	public SoundEvent(SoundManager manager) {
		this.manager = manager;
	}
}
