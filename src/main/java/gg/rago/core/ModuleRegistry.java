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

	public void tick() {
		for (Module m : modules.values()) {
			if (m.isEnabled()) {
				m.tick();
			}
		}
	}
}
