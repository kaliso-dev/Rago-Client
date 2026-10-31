package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.Theme;
import gg.rago.hooks.Hooks;
import gg.rago.network.NetworkClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Rago logo next to the names of players who use Rago, in the tab list and on name tags: yours in
 * the colour you choose (on your name tag in third person, with Nametags showing your own name), and
 * other Rago players' as {@link RagoNetwork} tells (Rago's red, or their role's colour).
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
	/** The tab lines given a logo lately, with its colour, to know them again when they are drawn. */
	private final Map<String, Integer> tabLines = new HashMap<>();

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

	/**
	 * The logo's GL texture (smoothed by its .mcmeta); 0 if it could not be loaded. Asked of the
	 * texture manager each time: a resource reload (a pack change) gives the logo a new texture and
	 * frees the old one, which the next texture made (a menu icon) can take.
	 */
	public int texture() {
		TextureManager textures = Minecraft.getMinecraft().getTextureManager();
		ITextureObject tex = textures.getTexture(LOGO);
		if (tex == null) {
			textures.bindTexture(LOGO);
			tex = textures.getTexture(LOGO);
		}
		return tex == null ? 0 : tex.getGlTextureId();
	}

	/**
	 * The colour of the logo before the name of the player {@code id}: yours, or another Rago player's
	 * (Rago's red, or their role's colour); 0 for someone not using Rago.
	 */
	public int logoColor(UUID id) {
		Minecraft mc = Minecraft.getMinecraft();
		if (id == null) {
			return 0;
		}
		if (mc.thePlayer != null && id.equals(mc.thePlayer.getUniqueID())) {
			return color.get();
		}
		RagoNetwork network = Hooks.network;
		NetworkClient.Player p = network != null ? network.player(id) : null;
		return p == null ? 0 : RagoNetwork.roleColor(p.role, Theme.ACCENT);
	}

	/** GuiPlayerTabOverlay.getPlayerName: room for the logo before the names of Rago players. */
	public String tabName(String name, NetworkPlayerInfo info) {
		if (!isEnabled() || !inTab.get() || Minecraft.getMinecraft().thePlayer == null || info == null) {
			return name;
		}
		int c = logoColor(info.getGameProfile().getId());
		if (c == 0) {
			return name;
		}
		String line = TAB_GAP + name;
		if (tabLines.size() > 512) {
			tabLines.clear();
		}
		tabLines.put(line, c);
		return line;
	}

	/** Whether {@code text} is a tab line with the logo, drawn before it; draw the text from {@link #tabText}. */
	public boolean tabLogo(String text, float x, float y) {
		Integer c = tabLines.get(text);
		int texture = c != null ? texture() : 0;
		if (texture == 0) {
			return false;
		}
		int argb = c;
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
