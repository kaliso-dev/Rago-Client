package net.minecraftforge.fml.common.eventhandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Forge's event bus, rewritten: {@link #register} finds the {@link SubscribeEvent} methods of an
 * object, {@link #post} calls those whose parameter accepts the event, highest priority first.
 *
 * <p>Each handler is called through a generated lambda rather than reflection, and the handlers of
 * each event class are worked out once, so posting costs a list walk.
 */
public class EventBus {
	private static final Logger LOG = LogManager.getLogger("RagoLoader");

	private record Listener(Object owner, Class<?> type, EventPriority priority, boolean receiveCanceled, Consumer<Event> call, String name) { }

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private final Map<Class<?>, Listener[]> byClass = new ConcurrentHashMap<>();
	private final Set<String> failing = ConcurrentHashMap.newKeySet();

	public void register(Object target) {
		Class<?> cls = target instanceof Class<?> c ? c : target.getClass();
		boolean statics = target instanceof Class<?>;
		for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				SubscribeEvent s = m.getAnnotation(SubscribeEvent.class);
				if (s == null || m.getParameterCount() != 1 || !Event.class.isAssignableFrom(m.getParameterTypes()[0])
					|| Modifier.isStatic(m.getModifiers()) != statics) {
					continue;
				}
				listeners.add(new Listener(target, m.getParameterTypes()[0], s.priority(), s.receiveCanceled(), caller(m, target),
					c.getName() + "." + m.getName()));
			}
		}
		byClass.clear();
	}

	public void unregister(Object target) {
		listeners.removeIf(l -> l.owner == target);
		byClass.clear();
	}

	@SuppressWarnings("unchecked")
	private static Consumer<Event> caller(Method m, Object target) {
		try {
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(m.getDeclaringClass(), MethodHandles.lookup());
			MethodHandle h = lookup.unreflect(m);
			boolean statics = Modifier.isStatic(m.getModifiers());
			MethodType erased = MethodType.methodType(void.class, Object.class);
			CallSite site = LambdaMetafactory.metafactory(lookup, "accept",
				statics ? MethodType.methodType(Consumer.class) : MethodType.methodType(Consumer.class, m.getDeclaringClass()),
				erased, h, MethodType.methodType(void.class, m.getParameterTypes()[0]));
			return statics ? (Consumer<Event>) site.getTarget().invoke() : (Consumer<Event>) site.getTarget().invoke(target);
		} catch (Throwable fast) {
			m.setAccessible(true);
			return e -> {
				try {
					m.invoke(target instanceof Class<?> ? null : target, e);
				} catch (ReflectiveOperationException x) {
					throw new RuntimeException(x.getCause() == null ? x : x.getCause());
				}
			};
		}
	}

	private Listener[] listenersFor(Class<?> event) {
		return byClass.computeIfAbsent(event, e -> {
			List<Listener> out = new ArrayList<>();
			for (Listener l : listeners) {
				if (l.type.isAssignableFrom(e)) {
					out.add(l);
				}
			}
			out.sort(Comparator.comparing(Listener::priority));
			return out.toArray(new Listener[0]);
		});
	}

	/** Calls every handler of the event; true when it was canceled. */
	public boolean post(Event event) {
		for (Listener l : listenersFor(event.getClass())) {
			if (event.isCanceled() && !l.receiveCanceled) {
				continue;
			}
			try {
				l.call.accept(event);
			} catch (Throwable t) {
				if (failing.add(l.name)) {
					LOG.error("{} failed on {}; it keeps being called", l.name, event.getClass().getSimpleName(), t);
				}
			}
		}
		return event.isCanceled();
	}
}
