package gg.rago.modules;

import gg.rago.bedwars.BedwarsGame;
import gg.rago.config.BooleanOption;
import gg.rago.config.ColorOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.Theme;
import gg.rago.hooks.Hooks;
import gg.rago.network.NetworkClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
 *
 * <p>The creator of Rago stands out: the logo in a pink gradient, the name in moving shades of pink
 * (in a Bed Wars game it keeps its team's colour) and a crown and a sparkle after it.
 */
public final class RagoBadge extends Module {
	/** Size of the logo, in name tag and tab list units (the height of a line of text). */
	public static final float LOGO_SIZE = 8;
	/** Three spaces in the Minecraft font: room for the logo before your name in the tab list. */
	private static final String TAB_GAP = "§r   ";
	private static final ResourceLocation LOGO = new ResourceLocation("rago", "textures/logo_small.png");
	/** The creator's logo, in its own pink gradient, and the crown and sparkle after the name. */
	private static final ResourceLocation CREATOR_LOGO = new ResourceLocation("rago", "textures/badge_creator.png");
	private static final ResourceLocation ICONS = new ResourceLocation("rago", "textures/badge_icons.png");
	/** Size of the crown and sparkle, in name tag and tab list units. */
	public static final float ICONS_W = 16;
	public static final float ICONS_H = 8;
	/** Room for them after the name: a little space, the icons, a little space. */
	public static final float ICONS_SPACE = 1.5f + ICONS_W + 1;
	/** Five spaces in the Minecraft font: their room in a tab line, so its column is wide enough. */
	private static final String ICON_GAP = "     ";
	/** The creator's name goes from light to deep pink and back. */
	static final int CREATOR_LIGHT = 0xFFC8E4;
	static final int CREATOR_DEEP = 0xFF3E9E;
	/** How long the shades take to go along the name, in milliseconds. */
	private static final long SHIMMER_MS = 2400;

	private final BooleanOption inTab = addOption(new BooleanOption("badge_tab", true));
	private final BooleanOption onName = addOption(new BooleanOption("badge_nametag", true));
	private final ColorOption color = addOption(new ColorOption("badge_color", Theme.ACCENT).fixedAlpha(0xFF));
	/** The tab lines given a logo lately, with what to draw, to know them again when they are drawn. */
	private final Map<String, TabLine> tabLines = new HashMap<>();

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
		return texture(LOGO);
	}

	/** The GL texture of the logo before the name of the player {@code id} (the creator's own). */
	public int texture(UUID id) {
		return texture(creator(id) ? CREATOR_LOGO : LOGO);
	}

	/** The crown and sparkle's GL texture. */
	public int iconsTexture() {
		return texture(ICONS);
	}

	private static int texture(ResourceLocation location) {
		TextureManager textures = Minecraft.getMinecraft().getTextureManager();
		ITextureObject tex = textures.getTexture(location);
		if (tex == null) {
			textures.bindTexture(location);
			tex = textures.getTexture(location);
		}
		return tex == null ? 0 : tex.getGlTextureId();
	}

	/** Whether the player {@code id} is Rago's creator, as Rago Network tells. */
	public boolean creator(UUID id) {
		RagoNetwork network = Hooks.network;
		if (id == null || network == null) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer != null && id.equals(mc.thePlayer.getUniqueID())) {
			return "creator".equals(network.ownRole());
		}
		NetworkClient.Player p = network.player(id);
		return p != null && "creator".equals(p.role);
	}

	/** Whether the creator's name is shown in pink now: not in a Bed Wars game, where it keeps its team's colour. */
	public static boolean pinkName() {
		return !BedwarsGame.playing();
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
		if (creator(id)) {
			return 0xFFFFFFFF; // the creator's logo has its own colours
		}
		if (mc.thePlayer != null && id.equals(mc.thePlayer.getUniqueID())) {
			return color.get();
		}
		RagoNetwork network = Hooks.network;
		NetworkClient.Player p = network != null ? network.player(id) : null;
		return p == null ? 0 : RagoNetwork.roleColor(p.role, Theme.ACCENT);
	}

	/**
	 * GuiPlayerTabOverlay.getPlayerName: room for the logo before the names of Rago players, and for
	 * the creator's crown and sparkle after the name.
	 */
	public String tabName(String name, NetworkPlayerInfo info) {
		if (!isEnabled() || !inTab.get() || Minecraft.getMinecraft().thePlayer == null || info == null) {
			return name;
		}
		UUID id = info.getGameProfile().getId();
		int c = logoColor(id);
		if (c == 0) {
			return name;
		}
		TabLine tab;
		String line;
		if (creator(id)) {
			String player = info.getGameProfile().getName();
			int at = nameAt(name, player);
			int end = at < 0 ? name.length() : at + player.length();
			line = TAB_GAP + name.substring(0, end) + ICON_GAP + name.substring(end);
			tab = new TabLine(c, true, at < 0 ? null : player, at, end);
		} else {
			line = TAB_GAP + name;
			tab = new TabLine(c, false, null, -1, -1);
		}
		if (tabLines.size() > 512) {
			tabLines.clear();
		}
		tabLines.put(line, tab);
		return line;
	}

	/**
	 * Draws {@code text} if it is a tab line with the logo: the logo, the line, and for the creator
	 * the name in pink and the crown and sparkle. False for other lines, left to the caller.
	 */
	public boolean tabLine(FontRenderer font, String text, float x, float y, int color, boolean shadow) {
		TabLine tab = tabLines.get(text);
		int logo = tab != null ? texture(tab.creator ? CREATOR_LOGO : LOGO) : 0;
		if (logo == 0) {
			return false;
		}
		quad(logo, x, y, LOGO_SIZE, LOGO_SIZE, tab.color);
		text = text.substring(TAB_GAP.length());
		x += LOGO_SIZE + 2;
		if (!tab.creator) {
			font.drawString(text, x, y, color, shadow);
			return true;
		}
		String before = text.substring(0, tab.end);
		String after = codesAt(before) + text.substring(tab.end + ICON_GAP.length());
		float nameEnd;
		if (tab.name != null && pinkName()) {
			String start = text.substring(0, tab.at);
			if (!start.isEmpty()) {
				font.drawString(start, x, y, color, shadow);
			}
			float cx = x + font.getStringWidth(start);
			String style = stylesAt(start);
			long now = Minecraft.getSystemTime();
			for (int i = 0; i < tab.name.length(); i++) {
				String ch = style + tab.name.charAt(i);
				font.drawString(ch, cx, y, color & 0xFF000000 | creatorShade(i, tab.name.length(), now), shadow);
				cx += font.getStringWidth(ch);
			}
			nameEnd = cx;
		} else {
			font.drawString(before, x, y, color, shadow);
			nameEnd = x + font.getStringWidth(before);
		}
		int icons = iconsTexture();
		if (icons != 0) {
			quad(icons, nameEnd + 1.5f, y - 0.5f, ICONS_W, ICONS_H, 0xFFFFFFFF);
		}
		font.drawString(after, x + font.getStringWidth(before + ICON_GAP), y, color, shadow);
		return true;
	}

	/** One tab line with the logo: its colour, and for the creator where the name is. */
	private static final class TabLine {
		final int color;
		final boolean creator;
		/** The creator's name, null when it is not in the line as it is (a nick). */
		final String name;
		/** Where the name starts and ends in the line without the logo's room (end: the icons' place). */
		final int at;
		final int end;

		TabLine(int color, boolean creator, String name, int at, int end) {
			this.color = color;
			this.creator = creator;
			this.name = name;
			this.at = at;
			this.end = end;
		}
	}

	/** A texture over (x, y, w, h), tinted {@code argb}, as the tab list draws. */
	private static void quad(int texture, float x, float y, float w, float h, int argb) {
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.enableTexture2D();
		GlStateManager.bindTexture(texture);
		GlStateManager.color((argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f, 1);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		wr.pos(x, y + h, 0).tex(0, 1).endVertex();
		wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
		wr.pos(x + w, y, 0).tex(1, 0).endVertex();
		wr.pos(x, y, 0).tex(0, 0).endVertex();
		tess.draw();
		GlStateManager.color(1, 1, 1, 1);
	}

	/** How far right of the logo the text starts. */
	public static float tabShift() {
		return LOGO_SIZE + 2;
	}

	// ---- the creator's name -----------------------------------------------------------------------

	/**
	 * Where the player's {@code name} starts in {@code text} (a tab line or a name tag) as plain text,
	 * not part of a § code; -1 when it is not there.
	 */
	public static int nameAt(String text, String name) {
		if (name == null || name.isEmpty()) {
			return -1;
		}
		int at = text.indexOf(name);
		while (at > 0 && text.charAt(at - 1) == '\u00a7') {
			at = text.indexOf(name, at + 1);
		}
		return at;
	}

	/** The § codes in force at the end of {@code text}: its last colour and the styles after it. */
	public static String codesAt(String text) {
		return codes(text, true);
	}

	/** The styles (bold, italic...) in force at the end of {@code text}, without its colour. */
	public static String stylesAt(String text) {
		return codes(text, false);
	}

	private static String codes(String text, boolean withColour) {
		char colour = 0;
		StringBuilder styles = new StringBuilder();
		for (int i = 0; i + 1 < text.length(); i++) {
			if (text.charAt(i) != '\u00a7') {
				continue;
			}
			char c = Character.toLowerCase(text.charAt(++i));
			if ("0123456789abcdef".indexOf(c) >= 0) {
				colour = c;
				styles.setLength(0);
			} else if (c == 'r') {
				colour = 0;
				styles.setLength(0);
			} else if ("klmno".indexOf(c) >= 0 && styles.indexOf("\u00a7" + c) < 0) {
				styles.append('\u00a7').append(c);
			}
		}
		return (withColour && colour != 0 ? "\u00a7" + colour : "") + styles;
	}

	/**
	 * The colour (RGB) of letter {@code i} of the creator's {@code n} letter name at {@code time}
	 * (milliseconds): light to deep pink and back along the name, the shades moving slowly along it.
	 */
	public static int creatorShade(int i, int n, long time) {
		float t = (float) i / Math.max(1, n) * 0.75f - (float) (time % SHIMMER_MS) / SHIMMER_MS;
		t -= (float) Math.floor(t);
		float k = 1 - Math.abs(2 * t - 1); // 0 light, 1 deep
		return mix(CREATOR_LIGHT, CREATOR_DEEP, k);
	}

	private static int mix(int a, int b, float k) {
		int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * k);
		int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return r << 16 | g << 8 | bl;
	}
}
