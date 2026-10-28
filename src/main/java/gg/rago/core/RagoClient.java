package gg.rago.core;

import gg.rago.netdiag.LatencySampler;
import gg.rago.perf.FrameCapture;
import gg.rago.perf.FrameStats;
import gg.rago.perf.FrameTimer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.TextRenderer;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rago 0.0.1: a frame-time and ping probe. Shows FPS, frame-time percentiles and the
 * server-reported ping, and records frame timings to CSV for {@code bench/analyse.py}.
 * Purely informational: nothing here changes gameplay or what is sent to the server.
 */
public final class RagoClient implements ClientModInitializer {
	public static final Logger LOG = LogManager.getLogger("Rago");

	/** 60 s at up to ~4,000 FPS; about 4 MB of longs, allocated only while capturing. */
	private static final int CAPTURE_MAX_FRAMES = 240_000;
	private static final long CAPTURE_DURATION_NS = 60_000_000_000L;
	private static final int TICKS_PER_PING_SAMPLE = 20;

	// Feather/LWJGL key codes: F10 and F12 are unbound in vanilla 1.8.9. Both can be rebound in Controls.
	private static final int KEY_F10 = 68;
	private static final int KEY_F12 = 88;
	private static final int GL_RENDERER = 0x1F01;
	private static final int GL_VERSION = 0x1F02;

	private static final FrameTimer FRAMES = new FrameTimer();
	private static final LatencySampler PING = new LatencySampler();

	private static KeyBinding toggleOverlayKey;
	private static KeyBinding captureKey;
	private static boolean overlayVisible = true;
	private static int tickCounter;
	private static volatile String status = "";
	private static Map<String, String> captureMeta;

	@Override
	public void onInitializeClient() {
		toggleOverlayKey = new KeyBinding("key.rago.toggle_overlay", KEY_F10, "key.categories.rago");
		captureKey = new KeyBinding("key.rago.capture", KEY_F12, "key.categories.rago");
		KeybindEvents.REGISTER_KEYBINDS.register(() -> {
			KeybindRegistry.register(toggleOverlayKey);
			KeybindRegistry.register(captureKey);
		});
		MinecraftClientEvents.TICK_END.register(RagoClient::onTick);
		LOG.info("Rago {} loaded", FabricLoader.getInstance().getModContainer("rago")
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
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
		status = "capturing";
		LOG.info("Frame capture started");
	}

	private static void save(FrameCapture capture) {
		Map<String, String> meta = captureMeta != null ? captureMeta : new LinkedHashMap<>();
		meta.put("frames", Integer.toString(capture.count()));
		String name = "frames-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
		Path file = FabricLoader.getInstance().getGameDir().resolve("rago").resolve(name);
		status = "saving";
		Thread writer = new Thread(() -> {
			try {
				capture.writeCsv(file, meta);
				status = "saved " + name;
				LOG.info("Saved {} frames to {}", capture.count(), file);
			} catch (Exception e) {
				status = "save failed (see log)";
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
		if (!overlayVisible) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		TextRenderer text = mc.textRenderer;
		FrameStats s = FRAMES.stats();
		int x = 4;
		int y = 4;
		int line = text.fontHeight + 2;

		text.drawWithShadow(String.format(Locale.ROOT, "FPS %.0f  1%% low %.0f", s.avgFps, s.onePercentLowFps), x, y, 0xFFFFFF);
		y += line;
		text.drawWithShadow(String.format(Locale.ROOT, "frame p50 %.2f ms  p99 %.2f ms", s.p50Ms, s.p99Ms), x, y, 0xFFFFFF);
		y += line;
		int ping = PING.latest();
		String pingText = ping < 0 ? "ping (server) --" : String.format(Locale.ROOT, "ping (server) %d ms  jitter %.1f ms", ping, PING.jitterMs());
		text.drawWithShadow(pingText, x, y, 0xFFFFFF);
		y += line;
		if (FRAMES.isCapturing()) {
			long elapsed = FRAMES.capture().elapsedNs(System.nanoTime()) / 1_000_000_000L;
			text.drawWithShadow("REC " + elapsed + "s / 60s", x, y, 0xFF5555);
		} else if (!status.isEmpty()) {
			text.drawWithShadow(status, x, y, 0xAAAAAA);
		}
	}
}
