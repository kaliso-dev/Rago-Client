package gg.rago.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ModuleRegistry {
	private final Map<String, Module> modules = new LinkedHashMap<>();

	public <T extends Module> T register(T module) {
		PolicyGate.check(module);
		if (modules.putIfAbsent(module.id(), module) != null) {
			throw new IllegalArgumentException("Module id already registered: " + module.id());
		}
		return module;
	}

	public Optional<Module> get(String id) {
		return Optional.ofNullable(modules.get(id));
	}

	public List<Module> all() {
		return Collections.unmodifiableList(new ArrayList<>(modules.values()));
	}

	public <T extends Module> List<T> allOf(Class<T> type) {
		return modules.values().stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
	}

	/** How many modules are registered; changes only while the client starts. */
	public int size() {
		return modules.size();
	}

	public void tick() {
		boolean timed = gg.rago.debug.FrameProfile.ON;
		for (Module m : modules.values()) {
			if (m.isEnabled()) {
				long a = timed ? gg.rago.debug.FrameProfile.bytes() : 0;
				long t = timed ? System.nanoTime() : 0;
				m.tick();
				if (timed) {
					long ns = System.nanoTime() - t;
					gg.rago.debug.FrameProfile.element(gg.rago.debug.FrameProfile.tickLabel(m.id()), ns, gg.rago.debug.FrameProfile.bytesSince(a));
				}
			}
		}
	}
}
