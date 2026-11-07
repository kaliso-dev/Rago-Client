package gg.rago.modules;

import gg.rago.asm.RagoTransformer;
import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.fixes.FixHooks;
import gg.rago.fixes.FrameCharts;
import gg.rago.fixes.GlyphWidths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.util.Locale;

/**
 * Display and safety fixes for 1.8, in the spirit of Patcher's, none of which changes what you
 * can do in game:
 * <ul>
 *   <li>Inventory position: the inventory stays in the middle of the screen with potion effects,
 *   instead of moving right to make room for them.</li>
 *   <li>Parallax fix: in first person the camera sits at your eyes, not a tenth of a block in
 *   front of them, so the world no longer slides a little when you look around.</li>
 *   <li>Server resource pack check: a server can no longer find out which files are on your
 *   computer through a crafted resource pack link (a known 1.8 flaw); such links are treated as a
 *   pack that does not exist, as the game would for any missing pack.</li>
 * </ul>
 * The other options are the fixes PolyPatcher makes, with its defaults; the game methods they need
 * call {@link gg.rago.fixes.FixHooks}, which explains each one.
 */
public final class ClientFixes extends Module {
	/** A pack that cannot exist, for the game to refuse as it refuses any missing pack. */
	private static final String REFUSED = "level://rago-refused-pack/resources.zip";

	private final BooleanOption inventory = addOption(new BooleanOption("inventory_position_fix", true));
	private final BooleanOption parallax = addOption(new BooleanOption("parallax_fix", false));
	private final BooleanOption packCheck = addOption(new BooleanOption("resource_pack_check", true));
	private final BooleanOption keepShaders = addOption(new BooleanOption("keep_shaders", true));
	private final BooleanOption cullingFix = addOption(new BooleanOption("culling_fix", false));
	private final BooleanOption tabHatLayer = addOption(new BooleanOption("tab_hat_layer", true));
	private final BooleanOption voidRendering = addOption(new BooleanOption("void_rendering", true));
	private final BooleanOption alexArms = addOption(new BooleanOption("alex_arms", true));
	private final BooleanOption bookBackground = addOption(new BooleanOption("book_background", false));
	private final BooleanOption vanillaItemLighting = addOption(new BooleanOption("vanilla_item_lighting", true));
	private final BooleanOption customSkyFix = addOption(new BooleanOption("custom_sky_fix", true));
	private final BooleanOption internalFixes = addOption(new BooleanOption("internal_fixes", true));
	private final BooleanOption simplifyFps = addOption(new BooleanOption("simplify_fps", true));
	private final BooleanOption vanillaMetrics = addOption(new BooleanOption("vanilla_metrics", true));
	private static Field guiLeft;
	private static Field xSize;
	private static boolean guiLeftMissing;
	/** Whether the sky planes were last built with the custom sky fix, or null before the first tick. */
	private Boolean skyPlanesFixed;

	public ClientFixes() {
		super("client_fixes", FeatureClass.VISUAL, true);
		GlyphWidths.setSwitch(() -> isEnabled() && internalFixes.get());
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	@SubscribeEvent
	public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (!isEnabled() || !inventory.get() || !(event.gui instanceof GuiInventory) || guiLeftMissing) {
			return;
		}
		GuiContainer gui = (GuiContainer) event.gui;
		try {
			if (guiLeft == null) {
				xSize = ReflectionHelper.findField(GuiContainer.class, "field_146999_f", "xSize");
				guiLeft = ReflectionHelper.findField(GuiContainer.class, "field_147003_i", "guiLeft");
			}
			guiLeft.setInt(gui, (gui.width - xSize.getInt(gui)) / 2);
		} catch (ReflectiveOperationException | RuntimeException e) {
			guiLeftMissing = true;
			RagoClient.LOG.warn("Inventory position fix unavailable: {}", e.toString());
		}
	}

	/**
	 * The game builds the sky planes when its renderers load; when the custom sky fix (or this
	 * module) is turned on or off they are built again at once. Runs whether or not the module is on.
	 */
	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		if (!RagoTransformer.patcherLoaded()) {
			GlyphWidths.syncGlyph(Minecraft.getMinecraft().fontRendererObj, isEnabled() && internalFixes.get());
		}
		boolean fixed = isEnabled() && customSkyFix.get();
		if (skyPlanesFixed == null || skyPlanesFixed != fixed) {
			boolean first = skyPlanesFixed == null;
			skyPlanesFixed = fixed;
			if (!first || fixed) {
				FixHooks.rebuildSkyPlanes();
			}
		}
	}

	/** Use Vanilla Metrics Renderer: each frame is timed from its start, whether or not the charts show. */
	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START && !RagoTransformer.patcherLoaded()) {
			FrameCharts.frameStart();
		}
	}

	/** Use Vanilla Metrics Renderer: each tick of the singleplayer server, as it times them (its thread). */
	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && !RagoTransformer.patcherLoaded()) {
			FrameCharts.serverTick(MinecraftServer.getServer());
		}
	}

	/** Use Vanilla Metrics Renderer: with F3 and ALT+F3, the charts, drawn with F3's text (under the chat). */
	@SubscribeEvent
	public void onOverlayText(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.TEXT || !isEnabled() || !vanillaMetrics.get()
			|| RagoTransformer.patcherLoaded()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.gameSettings.showDebugInfo && mc.gameSettings.showLagometer) {
			FrameCharts.draw(mc, event.resolution);
		}
	}

	/**
	 * In spectator mode the pumpkin you wore stays on your head, and vanilla kept drawing its mask
	 * over the screen; with the internal fixes it is not drawn in spectator mode.
	 */
	@SubscribeEvent
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (!isEnabled() || !internalFixes.get() || event.type != RenderGameOverlayEvent.ElementType.HELMET) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer != null && mc.thePlayer.isSpectator()) {
			ItemStack helmet = mc.thePlayer.inventory.armorItemInSlot(3);
			if (helmet != null && helmet.getItem() == Item.getItemFromBlock(Blocks.pumpkin)) {
				event.setCanceled(true);
			}
		}
	}

	public boolean keepShaders() {
		return keepShaders.get();
	}

	public boolean cullingFix() {
		return cullingFix.get();
	}

	public boolean tabHatLayer() {
		return tabHatLayer.get();
	}

	public boolean voidRendering() {
		return voidRendering.get();
	}

	public boolean alexArms() {
		return alexArms.get();
	}

	public boolean bookBackground() {
		return bookBackground.get();
	}

	public boolean vanillaItemLighting() {
		return vanillaItemLighting.get();
	}

	public boolean customSkyFix() {
		return customSkyFix.get();
	}

	public boolean internalFixes() {
		return internalFixes.get();
	}

	/** PolyPatcher's Simplify FPS Counter: OptiFine's lowest frame rate left out of its FPS counters. */
	public boolean simplifyFps() {
		return simplifyFps.get();
	}

	/** PolyPatcher's Use Vanilla Metrics Renderer: ALT+F3 draws the frame and tick time charts of later versions. */
	public boolean vanillaMetrics() {
		return vanillaMetrics.get();
	}

	/** The first-person camera's offset from the eyes (vanilla: -0.1, a tenth of a block forward). */
	public float cameraOffset(float vanilla) {
		return parallax.get() ? 0.05f : vanilla;
	}

	/** The resource pack link a server sent, or a pack that cannot exist when the link is unsafe. */
	public String resourcePackUrl(String url) {
		if (!packCheck.get() || url == null) {
			return url;
		}
		return safePackUrl(url) ? url : REFUSED;
	}

	/** Web links, and "level://" links to a world's own resources.zip without leaving its folder. */
	static boolean safePackUrl(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return true;
		}
		if (!lower.startsWith("level://")) {
			return false;
		}
		String path;
		try {
			path = URLDecoder.decode(url.substring("level://".length()), "UTF-8");
		} catch (UnsupportedEncodingException | IllegalArgumentException e) {
			return false;
		}
		return !path.contains("..") && !path.contains("\\") && !path.startsWith("/") && !path.contains(":")
			&& path.endsWith("/resources.zip") && path.indexOf('/') == path.length() - "/resources.zip".length();
	}
}
