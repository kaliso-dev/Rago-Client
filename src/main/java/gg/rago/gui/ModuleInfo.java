package gg.rago.gui;

import gg.rago.core.Module;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Icon (a Lucide icon name) for each module in the menu. */
final class ModuleInfo {
	private static final Map<String, String> ICONS = new HashMap<>();
	private static final Set<String> HYPIXEL_KEY = new HashSet<>(Arrays.asList("level_head", "bedwars_stats", "bedwars_quests"));

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
		ICONS.put("motion_blur", "blend");
		ICONS.put("rago_cape", "flag");
		ICONS.put("glint_color", "wand-sparkles");
		ICONS.put("height_limit", "arrow-up-to-line");
		ICONS.put("block_counter", "blocks");
		ICONS.put("direction", "compass");
		ICONS.put("pack_display", "layers");
		ICONS.put("tab_list", "table-properties");
		ICONS.put("color_grading", "contrast");
		ICONS.put("hit_delay_fix", "swords");
		ICONS.put("fast_shop", "shopping-cart");
		ICONS.put("cosmetics", "crown");
		ICONS.put("item_scale", "scaling");
		ICONS.put("tnt_timer", "bomb");
		ICONS.put("item_counter", "apple");
		ICONS.put("gen_timers", "timer");
		ICONS.put("item_physics", "package");
		ICONS.put("bedwars_stats", "trophy");
		ICONS.put("bedwars_quests", "list-checks");
		ICONS.put("trap_notifier", "bell");
		ICONS.put("action_sounds", "activity");
		ICONS.put("client_fixes", "shield");
		ICONS.put("screenshots", "camera");
		ICONS.put("raw_input", "mouse-pointer-click");
		ICONS.put("emotes", "party-popper");
		ICONS.put("auto_text", "message-square");
		ICONS.put("auto_messages", "sparkles");
		ICONS.put("rago_badge", "award");
		ICONS.put("anti_armor", "shirt");
	}

	private ModuleInfo() {
	}

	static String icon(Module m) {
		String icon = ICONS.get(m.id());
		return icon == null ? "box" : icon;
	}

	/** Whether a module reads the Hypixel API, so its page shows the key field. */
	static boolean usesHypixelKey(Module m) {
		return HYPIXEL_KEY.contains(m.id());
	}

	static String descKey(Module m) {
		return "rago.module." + m.id() + ".desc";
	}
}
