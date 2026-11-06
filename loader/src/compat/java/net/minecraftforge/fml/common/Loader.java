package net.minecraftforge.fml.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What mods are running, as Rago's loader started them. */
public final class Loader {
	private static final Loader INSTANCE = new Loader();
	private final Map<String, ModContainer> mods = new LinkedHashMap<>();
	private LoaderState state = LoaderState.NOINIT;

	public static Loader instance() {
		return INSTANCE;
	}

	public static boolean isModLoaded(String modid) {
		return INSTANCE.mods.containsKey(modid);
	}

	public List<ModContainer> getActiveModList() {
		return Collections.unmodifiableList(new ArrayList<>(mods.values()));
	}

	public List<ModContainer> getModList() {
		return getActiveModList();
	}

	public Map<String, ModContainer> getIndexedModList() {
		return Collections.unmodifiableMap(mods);
	}

	public boolean hasReachedState(LoaderState s) {
		return state.ordinal() >= s.ordinal();
	}

	public void rago$add(ModContainer mod) {
		mods.put(mod.getModId(), mod);
	}

	public void rago$state(LoaderState s) {
		state = s;
	}
}
