package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.FeatureClass;
import gg.rago.core.RagoClient;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.List;

/**
 * The resource pack in use (the top one if there are several): its logo (pack.png) and its name,
 * in the colours the pack's author gave it (§ codes in the file name), each optional.
 */
public final class PackDisplayHud extends TextHudElement {
	private static final int GAP = 2;

	private final BooleanOption logo = addOption(new BooleanOption("pack_logo", true));
	private final IntOption logoSize = addOption(new IntOption("pack_logo_size", 28, 12, 64));
	private final BooleanOption colors = addOption(new BooleanOption("pack_colors", true));

	private ResourcePackRepository.Entry shown;
	private String raw;
	private ResourceLocation defaultIcon;
	private boolean defaultIconFailed;

	public PackDisplayHud() {
		super("pack_display", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 116);
		logoSize.showIf(logo::get);
	}

	@Override
	protected String label(boolean after) {
		return after ? null : "Pack";
	}

	@Override
	public void tick() {
		List<ResourcePackRepository.Entry> packs = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries();
		// the last entry is drawn over the others and listed first in the pack menu
		ResourcePackRepository.Entry top = packs.isEmpty() ? null : packs.get(packs.size() - 1);
		if (top != shown || raw == null) {
			shown = top;
			raw = top == null ? "Default" : top.getResourcePackName();
		}
	}

	/** The pack's file name without ".zip" and underscores; with its colour codes when {@code keepColors}. */
	static String clean(String file, boolean keepColors) {
		if (file == null) {
			return "";
		}
		String s = keepColors ? file : EnumChatFormatting.getTextWithoutFormattingCodes(file);
		s = s.replace('_', ' ');
		String plain = EnumChatFormatting.getTextWithoutFormattingCodes(s);
		if (plain.regionMatches(true, Math.max(0, plain.length() - 4), ".zip", 0, 4)) {
			// cut ".zip" and any colour codes around it
			int i = s.length();
			int letters = 4;
			while (i > 0 && letters > 0) {
				if (i >= 2 && s.charAt(i - 2) == '§') {
					i -= 2;
				} else {
					i--;
					letters--;
				}
			}
			s = s.substring(0, i);
		}
		while (s.length() >= 2 && s.charAt(s.length() - 2) == '§') {
			s = s.substring(0, s.length() - 2);
		}
		return squeeze(s);
	}

	/**
	 * Drops the characters packs put in front of their name to sort first ("! ", "#", "~") and runs
	 * of spaces, keeping the colour codes where they are.
	 */
	static String squeeze(String s) {
		StringBuilder out = new StringBuilder(s.length());
		boolean started = false;
		boolean space = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				if (space) {
					out.append(' ');
					space = false;
				}
				out.append(c).append(s.charAt(++i));
				continue;
			}
			if (Character.isWhitespace(c)) {
				space = started;
				continue;
			}
			if (!started && "!#.-~".indexOf(c) >= 0) {
				continue;
			}
			if (space) {
				out.append(' ');
				space = false;
			}
			started = true;
			out.append(c);
		}
		return out.toString();
	}

	static String clean(String file) {
		return clean(file, false);
	}

	@Override
	protected String value(boolean placeholder) {
		if (placeholder || raw == null) {
			return colors.get() ? "§cRago §f16x" : "Rago 16x";
		}
		return clean(raw, colors.get());
	}

	private int iconSize() {
		return logo.get() ? logoSize.get() : 0;
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		int icon = iconSize();
		return boxWidth(placeholder) + (icon > 0 ? icon + GAP : 0);
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		return Math.max(boxHeight(placeholder), iconSize());
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		int icon = iconSize();
		if (icon <= 0) {
			super.render(text, placeholder);
			return;
		}
		if (bindIcon(placeholder)) {
			GlStateManager.enableTexture2D();
			GlStateManager.enableBlend();
			GlStateManager.color(1, 1, 1, 1);
			Gui.drawScaledCustomSizeModalRect(0, 0, 0, 0, 1, 1, icon, icon, 1, 1);
		}
		// the name sits level with the middle of the logo
		int boxH = boxHeight(placeholder);
		GlStateManager.pushMatrix();
		GlStateManager.translate(icon + GAP, Math.max(0, (icon - boxH) / 2f), 0);
		super.render(text, placeholder);
		GlStateManager.popMatrix();
	}

	/** Binds the logo of the pack shown, or the default pack's; returns false when there is none. */
	private boolean bindIcon(boolean placeholder) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!placeholder && shown != null) {
			shown.bindTexturePackIcon(mc.getTextureManager());
			return true;
		}
		if (defaultIcon == null && !defaultIconFailed) {
			try {
				defaultIcon = mc.getTextureManager().getDynamicTextureLocation("rago_default_pack",
					new DynamicTexture(mc.getResourcePackRepository().rprDefaultResourcePack.getPackImage()));
			} catch (IOException | RuntimeException e) {
				defaultIconFailed = true;
				RagoClient.LOG.debug("No default pack icon: {}", e.toString());
			}
		}
		if (defaultIcon == null) {
			return false;
		}
		mc.getTextureManager().bindTexture(defaultIcon);
		return true;
	}
}
