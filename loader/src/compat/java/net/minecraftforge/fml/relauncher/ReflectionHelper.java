package net.minecraftforge.fml.relauncher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Private field and method access by any of several names (the first that exists wins). */
public final class ReflectionHelper {
	public static class UnableToFindFieldException extends RuntimeException {
		public UnableToFindFieldException(String[] names, Exception e) {
			super(String.join(", ", names), e);
		}
	}

	public static class UnableToAccessFieldException extends RuntimeException {
		public UnableToAccessFieldException(String[] names, Exception e) {
			super(String.join(", ", names), e);
		}
	}

	public static class UnableToFindMethodException extends RuntimeException {
		public UnableToFindMethodException(String[] names, Exception e) {
			super(String.join(", ", names), e);
		}
	}

	public static Field findField(Class<?> cls, String... names) {
		Exception last = null;
		for (String n : names) {
			try {
				Field f = cls.getDeclaredField(n);
				f.setAccessible(true);
				return f;
			} catch (Exception e) {
				last = e;
			}
		}
		throw new UnableToFindFieldException(names, last);
	}

	@SuppressWarnings("unchecked")
	public static <T, E> T getPrivateValue(Class<? super E> cls, E instance, int index) {
		try {
			Field f = cls.getDeclaredFields()[index];
			f.setAccessible(true);
			return (T) f.get(instance);
		} catch (Exception e) {
			throw new UnableToAccessFieldException(new String[0], e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T, E> T getPrivateValue(Class<? super E> cls, E instance, String... names) {
		try {
			return (T) findField(cls, names).get(instance);
		} catch (IllegalAccessException e) {
			throw new UnableToAccessFieldException(names, e);
		}
	}

	public static <T, E> void setPrivateValue(Class<? super T> cls, T instance, E value, int index) {
		try {
			Field f = cls.getDeclaredFields()[index];
			f.setAccessible(true);
			f.set(instance, value);
		} catch (Exception e) {
			throw new UnableToAccessFieldException(new String[0], e);
		}
	}

	public static <T, E> void setPrivateValue(Class<? super T> cls, T instance, E value, String... names) {
		try {
			findField(cls, names).set(instance, value);
		} catch (IllegalAccessException e) {
			throw new UnableToAccessFieldException(names, e);
		}
	}

	public static <E> Method findMethod(Class<? super E> cls, E instance, String[] names, Class<?>... params) {
		Exception last = null;
		for (String n : names) {
			try {
				Method m = cls.getDeclaredMethod(n, params);
				m.setAccessible(true);
				return m;
			} catch (Exception e) {
				last = e;
			}
		}
		throw new UnableToFindMethodException(names, last);
	}

	private ReflectionHelper() {
	}
}
