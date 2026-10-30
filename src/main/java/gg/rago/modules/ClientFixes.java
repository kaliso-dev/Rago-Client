package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
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
 */
public final class ClientFixes extends Module {
	/** A pack that cannot exist, for the game to refuse as it refuses any missing pack. */
	private static final String REFUSED = "level://rago-refused-pack/resources.zip";

	private final BooleanOption inventory = addOption(new BooleanOption("inventory_position_fix", true));
	private final BooleanOption parallax = addOption(new BooleanOption("parallax_fix", false));
	private final BooleanOption packCheck = addOption(new BooleanOption("resource_pack_check", true));
	private static Field guiLeft;
	private static Field xSize;
	private static boolean guiLeftMissing;

	public ClientFixes() {
		super("client_fixes", FeatureClass.VISUAL, true);
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
