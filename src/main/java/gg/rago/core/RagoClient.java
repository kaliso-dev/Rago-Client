package gg.rago.core;

import gg.rago.accounts.Accounts;
import gg.rago.accounts.Skins;
import gg.rago.gui.AccountsButton;
import gg.rago.config.BooleanOption;
import gg.rago.config.ConfigStore;
import gg.rago.debug.FrameProfile;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.optimize.OptiFineFields;
import gg.rago.optimize.PlayPreset;
import gg.rago.optimize.Optimizations;
import gg.rago.optimize.PerformancePreset;
import gg.rago.optimize.VideoRows;
import gg.rago.profile.ModProfiles;
import gg.rago.settings.Setting;
import gg.rago.settings.Settings;
import gg.rago.gui.HudEditorScreen;
import gg.rago.gui.RagoMenuScreen;
import gg.rago.gui.RagoTitleScreen;
import gg.rago.gui.AccountsScreen;
import gg.rago.gui.PacksScreen;
import gg.rago.hud.HudManager;
import gg.rago.hooks.Hooks;
import gg.rago.hud.elements.ArmorHud;
import gg.rago.hud.elements.BedwarsQuestsHud;
import gg.rago.hud.elements.BedwarsResourcesHud;
import gg.rago.hud.elements.BlockCounterHud;
import gg.rago.hud.elements.DirectionHud;
import gg.rago.hud.elements.HeightLimitHud;
import gg.rago.hud.elements.PackDisplayHud;
import gg.rago.hud.elements.BossBarHud;
import gg.rago.hud.elements.ClockHud;
import gg.rago.hud.elements.ComboHud;
import gg.rago.hud.elements.CoordsHud;
import gg.rago.hud.elements.CpsHud;
import gg.rago.hud.elements.FpsHud;
import gg.rago.hud.elements.FrameTimeHud;
import gg.rago.hud.elements.GeneratorTimersHud;
import gg.rago.hud.elements.ItemCounterHud;
import gg.rago.hud.elements.KeystrokesHud;
import gg.rago.hud.elements.MemoryHud;
import gg.rago.hud.elements.PingHud;
import gg.rago.hud.elements.PotionHud;
import gg.rago.hud.elements.ReachHud;
import gg.rago.hud.elements.ScoreboardHud;
import gg.rago.hud.elements.ServerHud;
import gg.rago.hud.elements.SpeedHud;
import gg.rago.hud.elements.ToggleSprintHud;
import gg.rago.hypixel.HypixelApi;
import gg.rago.input.InputState;
import gg.rago.modules.BlockOverlay;
import gg.rago.modules.ActionSounds;
import gg.rago.modules.BedwarsStats;
import gg.rago.modules.Chat;
import gg.rago.modules.ClientFixes;
import gg.rago.modules.ColorGrading;
import gg.rago.modules.Cosmetics;
import gg.rago.modules.CompetitiveProfile;
import gg.rago.modules.Crosshair;
import gg.rago.modules.F5Crosshair;
import gg.rago.modules.Fullbright;
import gg.rago.modules.GlintColor;
import gg.rago.modules.HideAchievements;
import gg.rago.modules.Hitboxes;
import gg.rago.modules.HitParticles;
import gg.rago.modules.ItemPhysics;
import gg.rago.modules.ItemScale;
import gg.rago.modules.TntTimer;
import gg.rago.modules.TrapNotifier;
import gg.rago.modules.LevelHead;
import gg.rago.modules.LowFire;
import gg.rago.modules.ModernSounds;
import gg.rago.modules.MotionBlur;
import gg.rago.modules.MainMenu;
import gg.rago.modules.NoHurtCam;
import gg.rago.modules.NoPumpkin;
import gg.rago.modules.NoWaterOverlay;
import gg.rago.modules.OldAnimations;
import gg.rago.modules.RagoCape;
import gg.rago.modules.RawInput;
import gg.rago.modules.Screenshots;
import gg.rago.modules.SkinChanger;
import gg.rago.modules.FastShop;
import gg.rago.modules.GameplayFixes;
import gg.rago.modules.HitColor;
import gg.rago.modules.HitDelayFix;
import gg.rago.modules.Nametags;
import gg.rago.modules.TabList;
import gg.rago.modules.TabPing;
import gg.rago.modules.TimeChanger;
import gg.rago.modules.WeatherChanger;
import gg.rago.modules.Zoom;
import gg.rago.netdiag.LatencySampler;
import gg.rago.perf.FrameCapture;
import gg.rago.perf.FrameTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Forge entrypoint: owns the module registry, the HUD, the config file and the keybinds. Hooks into
 * the game through Forge events, plus a few small class patches ({@code gg.rago.asm}) that call
 * {@link Hooks}.
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
	private static HypixelApi hypixel;

	/** Forge's config folder, where Rago keeps its files. */
	public static Path configDir() {
		return configDir;
	}

	private static KeyBinding toggleOverlayKey;
	private static KeyBinding captureKey;
	private static KeyBinding menuKey;
	private static KeyBinding zoomKey;
	private static Zoom zoom;
	private static Crosshair crosshair;
	private static MainMenu mainMenu;
	private static CompetitiveProfile profile;
	private static boolean overlayVisible = true;
	private static boolean openMenu;
	private static int tickCounter;
	private static Map<String, String> captureMeta;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		configDir = event.getModConfigurationDirectory().toPath();
		// before the first resource load, so recent sounds need no reload
		ModernSounds.install();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		MODULES.register(new FpsHud(FRAMES));
		MODULES.register(new FrameTimeHud(FRAMES));
		MODULES.register(new PingHud(PING));
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
		MODULES.register(new ClockHud());
		MODULES.register(new MemoryHud());
		MODULES.register(new SpeedHud());
		MODULES.register(new ServerHud());
		MODULES.register(new ReachHud());
		MODULES.register(new ComboHud());
		MODULES.register(new ScoreboardHud());
		MODULES.register(new BossBarHud());
		MODULES.register(new BedwarsResourcesHud());
		MODULES.register(new HeightLimitHud(configDir));
		MODULES.register(new BlockCounterHud());
		MODULES.register(new ItemCounterHud());
		MODULES.register(new GeneratorTimersHud());
		MODULES.register(new DirectionHud());
		MODULES.register(new PackDisplayHud());
		MODULES.register(new BlockOverlay());
		MODULES.register(new Hitboxes());
		Hooks.hitParticles = MODULES.register(new HitParticles());
		Hooks.itemScale = MODULES.register(new ItemScale());
		Hooks.itemPhysics = MODULES.register(new ItemPhysics());
		MODULES.register(new TntTimer());
		TabPing tabPing = MODULES.register(new TabPing());
		Hooks.tabList = MODULES.register(new TabList());
		Hooks.hitDelay = MODULES.register(new HitDelayFix());
		Hooks.fastShop = MODULES.register(new FastShop());
		Chat chat = MODULES.register(new Chat());
		Nametags nametags = MODULES.register(new Nametags());
		hypixel = new HypixelApi(configDir);
		MODULES.register(new LevelHead(hypixel, nametags));
		Hooks.bedwarsStats = MODULES.register(new BedwarsStats(hypixel));
		MODULES.register(new BedwarsQuestsHud());
		MODULES.register(new TrapNotifier());
		MODULES.register(new ActionSounds());
		MODULES.register(new RawInput());
		NoHurtCam noHurtCam = MODULES.register(new NoHurtCam());
		OldAnimations animations = MODULES.register(new OldAnimations());
		GameplayFixes fixes = MODULES.register(new GameplayFixes());
		Hooks.clientFixes = MODULES.register(new ClientFixes());
		Hooks.screenshots = MODULES.register(new Screenshots());
		HitColor hitColor = MODULES.register(new HitColor());
		MODULES.register(new LowFire());
		MODULES.register(new NoPumpkin());
		MODULES.register(new NoWaterOverlay());
		MODULES.register(new HideAchievements());
		MODULES.register(new F5Crosshair());
		MODULES.register(new WeatherChanger());
		MODULES.register(new TimeChanger());
		MODULES.register(new MotionBlur(zoom));
		MODULES.register(new ColorGrading());
		RagoCape cape = MODULES.register(new RagoCape());
		MODULES.register(new Cosmetics());
		SkinChanger skinChanger = MODULES.register(new SkinChanger());
		GlintColor glint = MODULES.register(new GlintColor());
		mainMenu = MODULES.register(new MainMenu());
		profile = MODULES.register(new CompetitiveProfile(configDir));
		registerGeneralSettings();
		registerPresetRows();
		optimizations = new Optimizations(SETTINGS);
		VideoRows.register(SETTINGS);
		Hooks.culler = optimizations.culler();
		Hooks.noHurtCam = noHurtCam;
		Hooks.animations = animations;
		Hooks.tabPing = tabPing;
		Hooks.chat = chat;
		Hooks.engine = optimizations.engine();
		Hooks.fixes = fixes;
		Hooks.hitColor = hitColor;
		Hooks.cape = cape;
		Hooks.skins = skinChanger;
		Skins.load(configDir);
		Hooks.glint = glint;
		ClientCommandHandler.instance.registerCommand(new RagoCommand(hypixel, () -> openMenu = true));

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
		gg.rago.debug.NametagBench bench = gg.rago.debug.NametagBench.fromProperty();
		if (bench != null) {
			MinecraftForge.EVENT_BUS.register(bench);
		}
		MinecraftForge.EVENT_BUS.register(optimizations);
		// modules with @SubscribeEvent methods check isEnabled() themselves
		for (Module m : MODULES.all()) {
			MinecraftForge.EVENT_BUS.register(m);
		}
		LOG.info("Rago {} loaded with {} modules", version(), MODULES.all().size());
	}

	public static String version() {
		ModContainer mod = Loader.instance().getIndexedModList().get(MOD_ID);
		return mod == null ? "?" : mod.getVersion();
	}

	public static ModuleRegistry modules() {
		return MODULES;
	}

	/** The Hypixel API with the player's own key. */
	public static HypixelApi hypixel() {
		return hypixel;
	}

	/** The signed-in account's UUID, or null when the session has none (offline). */
	public static UUID ownId() {
		try {
			return Minecraft.getMinecraft().getSession().getProfile().getId();
		} catch (RuntimeException e) {
			return null;
		}
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
		PlayPreset play = new PlayPreset(SETTINGS, MODULES, configDir);
		SETTINGS.button(p, presets, "preset_ranked_bedwars", () -> {
			LOG.info("Ranked Bedwars preset changed {} mods and settings", play.apply(PlayPreset.Kind.RANKED_BEDWARS));
			saveConfig();
		}).primary();
		SETTINGS.button(p, presets, "preset_quality", () -> {
			LOG.info("Quality preset changed {} mods and settings", play.apply(PlayPreset.Kind.QUALITY));
			saveConfig();
		});
		SETTINGS.button(p, presets, "undo_play_preset", () -> {
			play.undo();
			LOG.info("Mods and settings from before the presets restored");
			saveConfig();
		}).visibleWhen(play::canUndo);
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

	/** Frame capture progress, shown in chat (may be called from the writer thread). */
	private static void setStatus(String text) {
		Minecraft mc = Minecraft.getMinecraft();
		mc.addScheduledTask(() -> {
			if (mc.thePlayer != null) {
				mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
					net.minecraft.util.EnumChatFormatting.RED + "Rago " + net.minecraft.util.EnumChatFormatting.GRAY + "frame capture: " + text));
			}
		});
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
		if (mc.currentScreen instanceof HudEditorScreen) {
			return;
		}
		boolean ownHud = overlayVisible && !(mc.gameSettings.showDebugInfo && !hudInDebug.get());
		long t = FrameProfile.ON ? System.nanoTime() : 0;
		HUD.render(!ownHud);
		if (FrameProfile.ON) {
			FrameProfile.add(FrameProfile.HUD, System.nanoTime() - t);
		}
	}

	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent event) {
		if (event.gui instanceof GuiMainMenu && useMainMenu()) {
			event.gui = new RagoTitleScreen();
		} else if (event.gui != null && event.gui.getClass().getName().startsWith("cc.polyfrost.oneconfig.gui.")
			&& menuKeyHeld()) {
			// the bundled Patcher's settings library opens on Right Shift too; that key is Rago's
			// menu (Patcher's settings open with /patcher)
			event.gui = new RagoMenuScreen(null);
		} else if (event.gui instanceof GuiScreenResourcePacks && !PacksScreen.takeVanilla()) {
			GuiScreen prev = null;
			try {
				prev = ReflectionHelper.getPrivateValue(GuiScreenResourcePacks.class, (GuiScreenResourcePacks) event.gui,
					"parentScreen", "field_146965_f");
			} catch (RuntimeException e) {
				LOG.debug("Resource packs screen without a previous screen", e);
			}
			event.gui = new PacksScreen(prev);
		} else if (event.gui != null && "the_fireplace.ias.gui.GuiAccountSelector".equals(event.gui.getClass().getName())) {
			// the account switcher's own list (its button in the server list): show Rago's instead
			GuiScreen prev = null;
			try {
				prev = (GuiScreen) event.gui.getClass().getField("prev").get(event.gui);
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOG.debug("Account list without a previous screen", e);
			}
			event.gui = new AccountsScreen(prev);
		}
	}

	private static boolean menuKeyHeld() {
		int code = menuKey.getKeyCode();
		return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
	}

	/** The server list gets a button for the Accounts screen, right of Cancel. */
	@SubscribeEvent
	public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (event.gui instanceof GuiMultiplayer && Accounts.available()) {
			event.buttonList.add(new AccountsButton(event.gui.width / 2 + 159, event.gui.height - 28));
		}
	}

	@SubscribeEvent
	public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
		if (event.gui instanceof GuiMultiplayer && event.button.id == AccountsButton.ID) {
			Minecraft.getMinecraft().displayGuiScreen(new AccountsScreen(event.gui));
		}
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			long t = FrameProfile.ON ? System.nanoTime() : 0;
			onTick(Minecraft.getMinecraft());
			if (FrameProfile.ON) {
				FrameProfile.add(FrameProfile.TICK, System.nanoTime() - t);
			}
			warnConflicts(Minecraft.getMinecraft());
		}
	}

	private static boolean conflictsChecked;

	/**
	 * Once per launch, in the player's own chat: mods known to break something Rago players do. The
	 * separate "Rago NoHitDelay" test mod lets go of right click as soon as the client has counted a
	 * full bite, which is before the server has (by the ping), so the server cancels the golden
	 * apple and the bite starts over. Rago's own Hit Delay Fix replaces it.
	 */
	private static void warnConflicts(Minecraft mc) {
		if (conflictsChecked || mc.thePlayer == null) {
			return;
		}
		conflictsChecked = true;
		if (net.minecraftforge.fml.common.Loader.isModLoaded("ragonohitdelay")) {
			LOG.warn("The separate Rago NoHitDelay mod is loaded; its NoUseDelay cancels eating on servers");
			mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
				net.minecraft.util.EnumChatFormatting.RED + "[Rago] " + net.minecraft.util.EnumChatFormatting.RESET
					+ I18n.format("rago.conflict.nohitdelay")));
		}
	}

	private static void onTick(Minecraft mc) {
		while (menuKey.isPressed()) {
			if (mc.currentScreen == null) {
				mc.displayGuiScreen(new RagoMenuScreen(null));
			}
		}
		if (openMenu) {
			// typed as /rago: the chat screen has closed by now
			openMenu = false;
			mc.displayGuiScreen(new RagoMenuScreen(null));
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
