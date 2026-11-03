package net.minecraftforge.fml.common;

import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Field access by name; the game already runs with SRG names, so names are used as given. */
public final class ObfuscationReflectionHelper {
	public static <T, E> T getPrivateValue(Class<? super E> cls, E instance, String... names) {
		return ReflectionHelper.getPrivateValue(cls, instance, names);
	}

	public static <T, E> T getPrivateValue(Class<? super E> cls, E instance, int index) {
		return ReflectionHelper.getPrivateValue(cls, instance, index);
	}

	public static <T, E> void setPrivateValue(Class<? super T> cls, T instance, E value, String... names) {
		ReflectionHelper.setPrivateValue(cls, instance, value, names);
	}

	public static <T, E> void setPrivateValue(Class<? super T> cls, T instance, E value, int index) {
		ReflectionHelper.setPrivateValue(cls, instance, value, index);
	}

	private ObfuscationReflectionHelper() {
	}
}
