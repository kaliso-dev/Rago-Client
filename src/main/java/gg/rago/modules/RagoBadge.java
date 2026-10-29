package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The Rago logo next to your own name: in the tab list, and on your name tag in third person (with
 * Nametags showing your own name). Only on your screen: there is no Rago server telling clients
 * who else plays with Rago, so other players' names are left as they are.
 */
public final class RagoBadge extends Module {
	/** Size of the logo, in name tag and tab list units (the height of a line of text). */
	public static final float LOGO_SIZE = 8;
	/** Three spaces in the Minecraft font: room for the logo before your name in the tab list. */
	private static final String TAB_GAP = "§r   ";
	private static final ResourceLocation LOGO = new ResourceLocation("rago", "textures/logo_small.png");

	private final BooleanOption inTab = addOption(new BooleanOption("badge_tab", true));
	private final BooleanOption onName = addOption(new BooleanOption("badge_nametag", true));
	private final ColorOption color = addOption(new ColorOption("badge_color", Theme.ACCENT).fixedAlpha(0xFF));
	/** Your tab list line as last given to the game, to know it again when it is drawn. */
	private String ownTabLine;
	private int texture;

	public RagoBadge() {
		super("rago_badge", FeatureClass.VISUAL, true);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	public boolean onNametag() {
		return isEnabled() && onName.get();
	}

	public int color() {
		return color.get();
	}

	/** The logo's GL texture, smoothed when made smaller; 0 if it could not be loaded. */
	public int texture() {
		if (texture == 0) {
			Minecraft mc = Minecraft.getMinecraft();
			mc.getTextureManager().bindTexture(LOGO);
			ITextureObject tex = mc.getTextureManager().getTexture(LOGO);
			if (tex == null) {
				return 0;
			}
			texture = tex.getGlTextureId();
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		}
		return texture;
	}

	/** GuiPlayerTabOverlay.getPlayerName: room for the logo before your own name. */
	public String tabName(String name, NetworkPlayerInfo info) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!isEnabled() || !inTab.get() || mc.thePlayer == null || info == null
			|| !mc.thePlayer.getUniqueID().equals(info.getGameProfile().getId())) {
			return name;
		}
		ownTabLine = TAB_GAP + name;
		return ownTabLine;
	}

	/** Whether {@code text} is your tab line, the logo drawn before it; draw the text from {@link #tabText}. */
	public boolean tabLogo(String text, float x, float y) {
		if (ownTabLine == null || !ownTabLine.equals(text) || texture() == 0) {
			return false;
		}
		int argb = color.get();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.enableTexture2D();
		GlStateManager.bindTexture(texture);
		GlStateManager.color((argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f, 1);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		wr.pos(x, y + LOGO_SIZE, 0).tex(0, 1).endVertex();
		wr.pos(x + LOGO_SIZE, y + LOGO_SIZE, 0).tex(1, 1).endVertex();
		wr.pos(x + LOGO_SIZE, y, 0).tex(1, 0).endVertex();
		wr.pos(x, y, 0).tex(0, 0).endVertex();
		tess.draw();
		GlStateManager.color(1, 1, 1, 1);
		return true;
	}

	/** Your tab line without the room left for the logo. */
	public static String tabText(String line) {
		return line.substring(TAB_GAP.length());
	}

	/** How far right of the logo your name starts in the tab list. */
	public static float tabShift() {
		return LOGO_SIZE + 2;
	}
}
