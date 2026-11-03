package gg.rago.modules;

import gg.rago.config.ChoiceOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Clear Inventory: behind your inventory (E) the world stays as it is, with no blur and no dark
 * shade, only a light vignette at the edges if you want one. It can also apply to every window
 * (chests, Hypixel's menus and shops).
 *
 * <p>Costs less than the game's own background: the dark shade Minecraft lays over the whole screen
 * is made fully see-through, and the vignette is one quad with the game's own vignette texture, drawn
 * before the HUD, only while such a window is open. Inventory Blur steps aside for the windows this
 * clears.
 */
public final class ClearInventory extends Module {
	private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
	/** A window opened again within this time is the same opening: no new fade (Hypixel opens one menu after another). */
	private static final long SAME_OPENING_NS = 250_000_000L;
	private static final int FADE_MS = 120;

	private final ChoiceOption windows = addOption(new ChoiceOption("clear_inv_windows", "inventory", "inventory", "all"));
	private final IntOption vignette = addOption(new IntOption("clear_inv_vignette", 25, 0, 100));

	private long openedAt;
	private long lastDrawn;

	public ClearInventory() {
		super("clear_inventory", FeatureClass.VISUAL, false);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** Whether the background behind {@code screen} is left clear, in a world. */
	public boolean clears(GuiScreen screen) {
		if (!isEnabled() || screen == null || Minecraft.getMinecraft().theWorld == null) {
			return false;
		}
		return clears(screen, windows.is("all"));
	}

	static boolean clears(GuiScreen screen, boolean everyWindow) {
		return everyWindow ? screen instanceof GuiContainer : screen instanceof GuiInventory || screen instanceof GuiContainerCreative;
	}

	/** The vignette, before the HUD (after the world and the other screen effects). */
	@SubscribeEvent(priority = EventPriority.LOW)
	public void onOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL || vignette.get() == 0) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (!clears(mc.currentScreen)) {
			return;
		}
		long now = System.nanoTime();
		if (now - lastDrawn > SAME_OPENING_NS) {
			openedAt = now;
		}
		lastDrawn = now;
		float k = vignette.get() / 100f * InventoryBlur.fadeIn(now - openedAt, FADE_MS);
		if (k > 0) {
			draw(mc, event.resolution, k);
		}
	}

	/** The game's vignette texture over the screen: each colour kept at 1 - k x the texture's brightness. */
	private static void draw(Minecraft mc, ScaledResolution res, float k) {
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.disableAlpha();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
		GlStateManager.color(k, k, k, 1);
		mc.getTextureManager().bindTexture(VIGNETTE);
		double w = res.getScaledWidth_double();
		double h = res.getScaledHeight_double();
		Tessellator t = Tessellator.getInstance();
		WorldRenderer r = t.getWorldRenderer();
		r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		r.pos(0, h, -90).tex(0, 1).endVertex();
		r.pos(w, h, -90).tex(1, 1).endVertex();
		r.pos(w, 0, -90).tex(1, 0).endVertex();
		r.pos(0, 0, -90).tex(0, 0).endVertex();
		t.draw();
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.enableAlpha();
		GlStateManager.depthMask(true);
		GlStateManager.enableDepth();
	}
}
