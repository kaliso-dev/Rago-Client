package net.minecraftforge.client.event.sound;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;

public class PlaySoundEvent extends SoundEvent {
	public final String name;
	public final ISound sound;
	public ISound result;

	public PlaySoundEvent(SoundManager manager, ISound sound) {
		super(manager);
		this.sound = sound;
		this.name = sound.func_147650_b().func_110623_a();
		this.result = sound;
	}
}
