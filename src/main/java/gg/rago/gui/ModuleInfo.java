package gg.rago.gui;

import gg.rago.core.Module;

import java.util.HashMap;
import java.util.Map;

/** Icon (a Lucide icon name) for each module in the menu. */
final class ModuleInfo {
	private static final Map<String, String> ICONS = new HashMap<>();

	static {
		ICONS.put("fps", "gauge");
		ICONS.put("frame_time", "timer");
		ICONS.put("ping", "wifi");
		ICONS.put("capture", "activity");
		ICONS.put("cps", "mouse-pointer-click");
		ICONS.put("coords", "map-pin");
		ICONS.put("keystrokes", "keyboard");
		ICONS.put("armor", "shield");
		ICONS.put("potions", "flask-conical");
		ICONS.put("zoom", "zoom-in");
		ICONS.put("crosshair", "crosshair");
		ICONS.put("fullbright", "sun");
		ICONS.put("toggle_sprint", "chevrons-right");
		ICONS.put("main_menu", "house");
		ICONS.put("competitive_profile", "zap");
	}

	private ModuleInfo() {
	}

	static String icon(Module m) {
		String icon = ICONS.get(m.id());
		return icon == null ? "box" : icon;
	}

	static String descKey(Module m) {
		return "rago.module." + m.id() + ".desc";
	}
}
