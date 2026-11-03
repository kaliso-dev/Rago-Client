package net.minecraftforge.fml.relauncher;

import java.util.ArrayList;
import java.util.List;

public final class CoreModManager {
	private static final List<String> IGNORED = new ArrayList<>();

	public static List<String> getIgnoredMods() {
		return IGNORED;
	}

	private CoreModManager() {
	}
}
