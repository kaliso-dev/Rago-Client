package gg.rago.core;

import gg.rago.config.BooleanOption;
import gg.rago.config.ConfigStore;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.optimize.OptiFineFields;
import gg.rago.optimize.Optimizations;
import gg.rago.optimize.PerformancePreset;
import gg.rago.optimize.VideoRows;
import gg.rago.profile.ModProfiles;
import gg.rago.settings.Setting;
import gg.rago.settings.Settings;
import gg.rago.gui.HudEditorScreen;
import gg.rago.gui.RagoMenuScreen;
import gg.rago.gui.RagoTitleScreen;
import gg.rago.hud.HudManager;
import gg.rago.hud.elements.ArmorHud;
import gg.rago.hud.elements.CaptureHud;
import gg.rago.hud.elements.CoordsHud;
import gg.rago.hud.elements.CpsHud;
import gg.rago.hud.elements.FpsHud;
import gg.rago.hud.elements.FrameTimeHud;
import gg.rago.hud.elements.KeystrokesHud;
import gg.rago.hud.elements.PingHud;
import gg.rago.hud.elements.PotionHud;
import gg.rago.hud.elements.ToggleSprintHud;
import gg.rago.input.InputState;
import gg.rago.modules.CompetitiveProfile;
import gg.rago.modules.Crosshair;
import gg.rago.modules.Fullbright;
import gg.rago.modules.MainMenu;
import gg.rago.modules.Zoom;
import gg.rago.netdiag.LatencySampler;
import gg.rago.perf.FrameCapture;
import gg.rago.perf.FrameTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Forge entrypoint: owns the module registry, the HUD, the config file and the keybinds, and
 * hooks into the game through Forge events (no class patching).
 */
@Mod(modid = RagoClient.MOD_ID, name = "Rago Client", useMetadata = true, clientSideOnly = true,
	acceptedMinecraftVersions = "[1.8.9]")
public final class RagoClient {
	public static final String MOD_ID = "rago";
	public static final Logger LOG = LogManager.getLogger("Rago");

	/** 60 s at up to ~4,000 FPS; about 4 MB of longs, allocated only while capturing. */
	private static final int CAPTURE_MAX_FRAMES = 240_000;
	private static final long CAPTURE_DURATION_NS = 60_000_000_000L;
	private static final int TICKS_PER_PING_SAMPLE = 20;

	// LWJGL key codes. F10 and F12 are unbound in vanilla 1.8.9; all of them can be rebound in Controls.
	private static final int KEY_F10 = 68;
	private static final int KEY_F12 = 88;
	private static final int KEY_RSHIFT = 54;
	private static final int KEY_C = 46;

	private static final FrameTimer FRAMES = new FrameTimer();
	private static final LatencySampler PING = new LatencySampler();
	private static final InputState INPUT = new InputState();

	private static final ModuleRegistry MODULES = new ModuleRegistry();
	private static final HudManager HUD = new HudManager(MODULES);
	private static final Settings SETTINGS = new Settings();
	private static final ModProfiles PROFILES = new ModProfiles();
	private static Optimizations optimizations;
	private static BooleanOption menuAnimations;
	private static BooleanOption menuBlur;
	private static BooleanOption glassEffect;
	private static BooleanOption menuVideo;
	private static BooleanOption hudInDebug;
	private static ConfigStore config;
	private static Path configDir;

	private static KeyBinding toggleOverlayKey;
	private static KeyBinding captureKey;
	private static KeyBinding menuKey;
	private static KeyBinding zoomKey;
	private static Zoom zoom;
	private static Crosshair crosshair;
	private static MainMenu mainMenu;
	private static CompetitiveProfile profile;
	private static boolean overlayVisible = true;
	private static int tickCounter;
	private static volatile String status = "";
	private static volatile long statusTimeNs;
	private static Map<String, String> captureMeta;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		configDir = event.getModConfigurationDirectory().toPath();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		MODULES.register(new FpsHud(FRAMES));
		MODULES.register(new FrameTimeHud(FRAMES));
		MODULES.register(new PingHud(PING));
		MODULES.register(new CaptureHud(FRAMES, () -> status, () -> statusTimeNs));
		MODULES.register(new CpsHud(INPUT));
		MODULES.register(new CoordsHud());
		MODULES.register(new KeystrokesHud(INPUT));
		MODULES.register(new ArmorHud());
		MODULES.register(new PotionHud());
		zoomKey = new KeyBinding("key.rago.zoom", KEY_C, "key.categories.rago");
		zoom = MODULES.register(new Zoom(zoomKey));
		crosshair = MODULES.register(new Crosshair());
		MODULES.register(new Fullbright());
		MODULES.register(new ToggleSprintHud());
		mainMenu = MODULES.register(new MainMenu());
		profile = MODULES.register(new CompetitiveProfile(configDir));
		registerGeneralSettings();
		registerPresetRows();
		optimizations = new Optimizations(SETTINGS);
		VideoRows.register(SETTINGS);

		config = new ConfigStore(configDir.resolve("rago.json"));
		try {
			ConfigStore.LoadResult result = config.load(MODULES, SETTINGS, PROFILES);
			if (result == ConfigStore.LoadResult.BROKEN_FILE_REPLACED) {
				LOG.warn("config/rago.json could not be read; it was renamed to rago.json.broken-* and defaults are used");
			}
		} catch (Exception e) {
			LOG.error("Could not load {}, using defaults", config.file(), e);
		}
		profile.markReady();
		PROFILES.ensureDefaults(MODULES);
		Anim.setEnabled(menuAnimations.get());
		Ui.setGlass(glassEffect.get());
		Ui.setMenuVideo(menuVideo.get());
		optimizations.start();
		if (OptiFineFields.installed()) {
			LOG.info("OptiFine settings: {}", OptiFineFields.describe());
		}

		toggleOverlayKey = new KeyBinding("key.rago.toggle_overlay", KEY_F10, "key.categories.rago");
		captureKey = new KeyBinding("key.rago.capture", KEY_F12, "key.categories.rago");
		menuKey = new KeyBinding("key.rago.menu", KEY_RSHIFT, "key.categories.rago");
		ClientRegistry.registerKeyBinding(menuKey);
		ClientRegistry.registerKeyBinding(toggleOverlayKey);
		ClientRegistry.registerKeyBinding(captureKey);
		ClientRegistry.registerKeyBinding(zoomKey);
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(optimizations);
		LOG.info("Rago {} loaded with {} modules", version(), MODULES.all().size());
	}

	public static String version() {
		ModContainer mod = Loader.instance().getIndexedModList().get(MOD_ID);
		return mod == null ? "?" : mod.getVersion();
	}

	public static ModuleRegistry modules() {
		return MODULES;
	}

	public static HudManager hud() {
		return HUD;
	}

	/** Key code of the menu binding, so the menu can close with the same key that opened it. */
	public static int menuKeyCode() {
		return menuKey.getKeyCode();
	}

	/** The one-click presets at the top of the Performance page. */
	private static void registerPresetRows() {
		Setting.Tab p = Setting.Tab.PERFORMANCE;
		String presets = "rago.section.presets";
		PerformancePreset preset = new PerformancePreset(SETTINGS, configDir);
		SETTINGS.button(p, presets, "max_performance", () -> {
			int changed = preset.apply();
			LOG.info("Max performance preset changed {} settings", changed);
			saveConfig();
		}).primary();
		SETTINGS.button(p, presets, "undo_max_performance", () -> {
			int restored = preset.undo();
			LOG.info("Max performance preset undone, {} settings restored", restored);
			saveConfig();
		}).visibleWhen(preset::canUndo);
	}

	private static void registerGeneralSettings() {
		Setting.Tab g = Setting.Tab.GENERAL;
		String ui = "rago.section.interface";
		menuAnimations = SETTINGS.bool(g, ui, "menu_animations", true, Anim::setEnabled);
		menuBlur = SETTINGS.bool(g, ui, "menu_blur", true, null);
		glassEffect = SETTINGS.bool(g, ui, "glass_effect", true, Ui::setGlass);
		menuVideo = SETTINGS.bool(g, ui, "menu_video", true, Ui::setMenuVideo);
		SETTINGS.choice(g, ui, "main_menu_style", () -> mainMenu.isEnabled() ? 0 : 1, i -> mainMenu.setEnabled(i == 0),
			"rago.value.rago", "rago.value.vanilla");
		hudInDebug = SETTINGS.bool(g, "rago.section.hud", "hud_in_debug", false, null);
		SETTINGS.toggle(g, "rago.section.hud", "hud_visible", () -> overlayVisible, v -> overlayVisible = v);
		SETTINGS.toggle(g, "rago.section.competitive", "competitive_profile", () -> profile.isEnabled(), v -> profile.setEnabled(v));
		SETTINGS.button(g, "rago.section.competitive", "edit_hud", () -> {
			Minecraft mc = Minecraft.getMinecraft();
			mc.displayGuiScreen(new HudEditorScreen(mc.currentScreen));
		});
	}

	public static Settings settings() {
		return SETTINGS;
	}

	public static ModProfiles profiles() {
		return PROFILES;
	}

	/** Whether menus may blur the world behind them. */
	public static boolean menuBlur() {
		return menuBlur.get();
	}

	public static void saveConfig() {
		try {
			PROFILES.capture(MODULES);
			config.save(MODULES, SETTINGS, PROFILES);
		} catch (Exception e) {
			LOG.error("Could not save {}", config.file(), e);
		}
	}

	private static void setStatus(String text) {
		status = text;
		statusTimeNs = System.nanoTime();
	}

	public static Zoom zoom() {
		return zoom;
	}

	/** Whether Rago's main menu replaces the vanilla title screen. */
	public static boolean useMainMenu() {
		return mainMenu != null && mainMenu.isEnabled();
	}

	public static CompetitiveProfile competitiveProfile() {
		return profile;
	}

	public static Crosshair crosshair() {
		return crosshair;
	}

	public static FrameTimer frames() {
		return FRAMES;
	}

	public static LatencySampler ping() {
		return PING;
	}

	// ---- Forge events ----------------------------------------------------------------------------

	/** Key presses in game, for the CPS counters (mouse buttons use vanilla's codes: button - 100). */
	@SubscribeEvent
	public void onKeyInput(InputEvent.KeyInputEvent event) {
		if (Keyboard.getEventKeyState()) {
			INPUT.onPress(Keyboard.getEventKey(), System.nanoTime());
		}
	}

	@SubscribeEvent
	public void onMouse(MouseEvent event) {
		if (event.button >= 0 && event.buttonstate) {
			INPUT.onPress(event.button - 100, System.nanoTime());
		}
		if (event.dwheel != 0 && Minecraft.getMinecraft().currentScreen == null && zoom.onScroll(event.dwheel)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onFov(EntityViewRenderEvent.FOVModifier event) {
		event.setFOV(zoom.apply(event.getFOV()));
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		long now = System.nanoTime();
		if (event.phase == TickEvent.Phase.START) {
			FRAMES.startFrame(now);
		} else {
			FrameCapture finished = FRAMES.endFrame(now);
			if (finished != null) {
				save(finished);
			}
		}
	}

	@SubscribeEvent
	public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
		if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && crosshair.replaceVanilla()) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onOverlayPost(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.gameSettings.showDebugInfo && !hudInDebug.get()) {
			return;
		}
		if (overlayVisible && !(mc.currentScreen instanceof HudEditorScreen)) {
			HUD.render();
		}
	}

	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent event) {
		if (event.gui instanceof GuiMainMenu && useMainMenu()) {
			event.gui = new RagoTitleScreen();
		}
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			onTick(Minecraft.getMinecraft());
		}
	}

	private static void onTick(Minecraft mc) {
		while (menuKey.isPressed()) {
			if (mc.currentScreen == null) {
				mc.displayGuiScreen(new RagoMenuScreen(null));
			}
		}
		while (toggleOverlayKey.isPressed()) {
			overlayVisible = !overlayVisible;
		}
		while (captureKey.isPressed()) {
			if (FRAMES.isCapturing()) {
				save(FRAMES.stopCapture());
			} else {
				startCapture(mc);
			}
		}
		QuickJoin.tick(mc);
		if (++tickCounter >= TICKS_PER_PING_SAMPLE) {
			tickCounter = 0;
			PING.add(readServerPing(mc));
		}
		MODULES.tick();
	}

	/** Server-reported latency of the local player from the tab list, or -1 when not on a server. */
	private static int readServerPing(Minecraft mc) {
		if (mc.thePlayer == null || mc.isIntegratedServerRunning()) {
			return -1;
		}
		NetHandlerPlayClient handler = mc.getNetHandler();
		if (handler == null) {
			return -1;
		}
		NetworkPlayerInfo info = handler.getPlayerInfo(mc.thePlayer.getUniqueID());
		return info == null ? -1 : info.getResponseTime();
	}

	private static void startCapture(Minecraft mc) {
		captureMeta = collectMetadata(mc);
		FRAMES.startCapture(new FrameCapture(CAPTURE_MAX_FRAMES, CAPTURE_DURATION_NS));
		setStatus("capturing");
		LOG.info("Frame capture started");
	}

	private static void save(FrameCapture capture) {
		Map<String, String> meta = captureMeta != null ? captureMeta : new LinkedHashMap<>();
		meta.put("frames", Integer.toString(capture.count()));
		String name = "frames-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
		Path file = Minecraft.getMinecraft().mcDataDir.toPath().resolve("rago").resolve(name);
		setStatus("saving");
		Thread writer = new Thread(() -> {
			try {
				capture.writeCsv(file, meta);
				setStatus("saved " + name);
				LOG.info("Saved {} frames to {}", capture.count(), file);
			} catch (Exception e) {
				setStatus("save failed (see log)");
				LOG.error("Could not write frame capture to {}", file, e);
			}
		}, "Rago capture writer");
		writer.setDaemon(false);
		writer.start();
	}

	/** Called on the render thread at capture start, so GL queries are allowed. */
	private static Map<String, String> collectMetadata(Minecraft mc) {
		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("rago_version", version());
		meta.put("started", LocalDateTime.now().toString());
		meta.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
		meta.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
		meta.put("cpu_threads", Integer.toString(Runtime.getRuntime().availableProcessors()));
		meta.put("max_heap_mb", Long.toString(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
		meta.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
		meta.put("gl_version", GL11.glGetString(GL11.GL_VERSION));
		meta.put("window", mc.displayWidth + "x" + mc.displayHeight);
		meta.put("render_distance", Integer.toString(mc.gameSettings.renderDistanceChunks));
		meta.put("vsync", Boolean.toString(mc.gameSettings.enableVsync));
		meta.put("world", mc.isIntegratedServerRunning() ? "singleplayer"
			: mc.getCurrentServerData() != null ? "multiplayer" : "none");
		meta.put("mods", Loader.instance().getActiveModList().stream()
			.map(m -> m.getModId() + "@" + m.getVersion())
			.sorted()
			.collect(Collectors.joining(" ")));
		return meta;
	}
}
