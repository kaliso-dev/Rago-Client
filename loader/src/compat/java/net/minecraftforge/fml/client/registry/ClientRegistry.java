package net.minecraftforge.fml.client.registry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import java.util.Arrays;

public final class ClientRegistry {
	public static void registerKeyBinding(KeyBinding key) {
		GameSettings s = Minecraft.func_71410_x().field_71474_y;
		KeyBinding[] keys = Arrays.copyOf(s.field_74324_K, s.field_74324_K.length + 1);
		keys[keys.length - 1] = key;
		s.field_74324_K = keys;
	}

	private ClientRegistry() {
	}
}
