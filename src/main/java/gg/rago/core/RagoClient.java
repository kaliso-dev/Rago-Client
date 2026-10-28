package gg.rago.core;

import gg.rago.config.ConfigStore;
import gg.rago.gui.HudEditorScreen;
import gg.rago.gui.RagoMenuScreen;
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
import gg.rago.modules.Crosshair;
import gg.rago.modules.Fullbright;
import gg.rago.modules.MainMenu;
import gg.rago.modules.Zoom;
import gg.rago.netdiag.LatencySampler;
import gg.rago.perf.FrameCapture;
import gg.rago.perf.FrameTimer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.options.KeyBinding;
import net.ornithemc.osl.keybinds.api.KeybindEvents;
import net.ornithemc.osl.keybinds.api.KeybindRegistry;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Client entrypoint: owns the module registry, the HUD, the config file and the keybinds.
 * Frame timings come from {@code MinecraftMixin}; the HUD is drawn from {@code GameGuiMixin}.
 */
public final class RagoClient implements ClientModInitializer {
	public static final Logger LOG = LogManager.getLogger("Rago");

	/** 60 s at up to ~4,000 FPS; about 4 MB of longs, allocated only while capturing. */
	private static final int CAPTURE_MAX_FRAMES = 240_000;
	private static final long CAPTURE_DURATION_NS = 60_000_000_000L;
	private static final int TICKS_PER_PING_SAMPLE = 20;

	// LWJGL key codes. F10 and F12 are unbound in vanilla 1.8.9; all three can be rebound in Controls.
	private static final int KEY_F10 = 68;
	private static final int KEY_F12 = 88;
	private static final int KEY_RSHIFT = 54;
	private static final int KEY_C = 46;
	private static final int GL_RENDERER = 0x1F01;
	private static final int GL_VERSION = 0x1F02;

	private static final FrameTimer FRAMES = new FrameTimer();
	private static final LatencySampler PING = new LatencySampler();
	private static final InputState INPUT = new InputState();

	private static final ModuleRegistry MODULES = new ModuleRegistry();
	private static final HudManager HUD = new HudManager(MODULES);
	private static ConfigStore config;

	private static KeyBinding toggleOverlayKey;
	private static KeyBinding captureKey;
	private static KeyBinding menuKey;
	private static KeyBinding zoomKey;
	private static Zoom zoom;
	private static Crosshair crosshair;
	private static ToggleSprintHud toggleSprint;
	private static MainMenu mainMenu;
	private static boolean overlayVisible = true;
	private static int tickCounter;
	private static volatile String status = "";
	private static volatile long statusTimeNs;
	private static Map<String, String> captureMeta;

	@Override
	public void onInitializeClient() {
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
		toggleSprint = MODULES.register(new ToggleSprintHud());
		mainMenu = MODULES.register(new MainMenu());

		config = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("rago.json"));
		try {
			ConfigStore.LoadResult result = config.load(MODULES);
			if (result == ConfigStore.LoadResult.BROKEN_FILE_REPLACED) {
				LOG.warn("config/rago.json could not be read; it was renamed to rago.json.broken-* and defaults are used");
			}
		} catch (Exception e) {
			LOG.error("Could not load {}, using defaults", config.file(), e);
		}

		toggleOverlayKey = new KeyBinding("key.rago.toggle_overlay", KEY_F10, "key.categories.rago");
		captureKey = new KeyBinding("key.rago.capture", KEY_F12, "key.categories.rago");
		menuKey = new KeyBinding("key.rago.menu", KEY_RSHIFT, "key.categories.rago");
		KeybindEvents.REGISTER_KEYBINDS.register(() -> {
			KeybindRegistry.register(menuKey);
			KeybindRegistry.register(toggleOverlayKey);
			KeybindRegistry.register(captureKey);
			KeybindRegistry.register(zoomKey);
		});
		MinecraftClientEvents.TICK_END.register(RagoClient::onTick);
		LOG.info("Rago {} loaded with {} modules", version(), MODULES.all().size());
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer("rago")
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
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

	public static void saveConfig() {
		try {
			config.save(MODULES);
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

	public static Crosshair crosshair() {
		return crosshair;
	}

	/** Whether a binding should read as held because toggle sprint/sneak holds it. */
	public static boolean forceHeld(KeyBinding binding) {
		return toggleSprint != null && toggleSprint.forceHeld(binding);
	}

	/** Every key or mouse press vanilla processes in game (from KeyBindingMixin). */
	public static void onKeyPress(int keyCode) {
		INPUT.onPress(keyCode, System.nanoTime());
	}

	public static void onFrameStart(long nowNs) {
		FRAMES.startFrame(nowNs);
	}

	public static void onFrameEnd(long nowNs) {
		FrameCapture finished = FRAMES.endFrame(nowNs);
		if (finished != null) {
			save(finished);
		}
	}

	private static void onTick(Minecraft mc) {
		while (menuKey.consumeClick()) {
			if (mc.screen == null) {
				mc.openScreen(new RagoMenuScreen(null));
			}
		}
		while (toggleOverlayKey.consumeClick()) {
			overlayVisible = !overlayVisible;
		}
		while (captureKey.consumeClick()) {
			if (FRAMES.isCapturing()) {
				save(FRAMES.stopCapture());
			} else {
				startCapture(mc);
			}
		}
		if (++tickCounter >= TICKS_PER_PING_SAMPLE) {
			tickCounter = 0;
			PING.add(readServerPing(mc));
		}
		MODULES.tick();
	}

	/** Server-reported latency of the local player from the tab list, or -1 when not on a server. */
	private static int readServerPing(Minecraft mc) {
		if (mc.player == null || mc.isIntegratedServerRunning()) {
			return -1;
		}
		ClientPlayNetworkHandler handler = mc.getNetworkHandler();
		if (handler == null) {
			return -1;
		}
		PlayerInfo info = handler.getOnlinePlayer(mc.player.getUuid());
		return info == null ? -1 : info.getPing();
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
		Path file = FabricLoader.getInstance().getGameDir().resolve("rago").resolve(name);
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
		meta.put("rago_version", FabricLoader.getInstance().getModContainer("rago")
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
		meta.put("started", LocalDateTime.now().toString());
		meta.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
		meta.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
		meta.put("cpu_threads", Integer.toString(Runtime.getRuntime().availableProcessors()));
		meta.put("max_heap_mb", Long.toString(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
		meta.put("gpu", GL11.glGetString(GL_RENDERER));
		meta.put("gl_version", GL11.glGetString(GL_VERSION));
		meta.put("window", mc.width + "x" + mc.height);
		meta.put("render_distance", Integer.toString(mc.options.viewDistance));
		meta.put("vsync", Boolean.toString(mc.options.vsync));
		meta.put("world", mc.isIntegratedServerRunning() ? "singleplayer"
			: mc.getCurrentServerEntry() != null ? "multiplayer" : "none");
		meta.put("mods", FabricLoader.getInstance().getAllMods().stream()
			.map(ModContainer::getMetadata)
			.map(m -> m.getId() + "@" + m.getVersion().getFriendlyString())
			.sorted()
			.collect(Collectors.joining(" ")));
		return meta;
	}

	public static void renderOverlay() {
		Minecraft mc = Minecraft.getInstance();
		crosshair.render();
		if (overlayVisible && !(mc.screen instanceof HudEditorScreen)) {
			HUD.render();
		}
	}
}
