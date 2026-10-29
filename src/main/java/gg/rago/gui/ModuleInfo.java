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
		ICONS.put("no_hurt_cam", "camera-off");
		ICONS.put("old_animations", "swords");
		ICONS.put("low_fire", "flame");
		ICONS.put("no_pumpkin", "eye-off");
		ICONS.put("no_water_overlay", "droplets");
		ICONS.put("hide_achievements", "trophy");
		ICONS.put("hide_f5_crosshair", "scan-eye");
		ICONS.put("weather_changer", "cloud-rain");
		ICONS.put("time_changer", "sunset");
		ICONS.put("hit_particles", "sparkles");
		ICONS.put("block_overlay", "square-dashed");
		ICONS.put("hitboxes", "scan");
		ICONS.put("tab_ping", "signal");
		ICONS.put("chat", "message-square");
		ICONS.put("nametags", "tag");
		ICONS.put("gameplay_fixes", "check");
		ICONS.put("hit_color", "palette");
		ICONS.put("level_head", "star");
		ICONS.put("scoreboard", "list-ordered");
		ICONS.put("boss_bar", "skull");
		ICONS.put("clock", "clock");
		ICONS.put("memory", "memory-stick");
		ICONS.put("speed", "wind");
		ICONS.put("server_address", "server");
		ICONS.put("reach", "ruler");
		ICONS.put("combo", "trending-up");
		ICONS.put("bedwars_resources", "gem");
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
