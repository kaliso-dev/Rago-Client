package gg.rago.hud.elements;

import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.TextHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

/** The name of the resource pack in use (the top one if there are several). */
public final class PackDisplayHud extends TextHudElement {
	private ResourcePackRepository.Entry shown;
	private String name;

	public PackDisplayHud() {
		super("pack_display", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 116);
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
		if (top != shown || name == null) {
			shown = top;
			name = top == null ? "Default" : clean(top.getResourcePackName());
		}
	}

	static String clean(String file) {
		String s = EnumChatFormatting.getTextWithoutFormattingCodes(file);
		if (s == null) {
			return "";
		}
		s = s.replace('_', ' ').trim();
		return s.regionMatches(true, Math.max(0, s.length() - 4), ".zip", 0, 4) ? s.substring(0, s.length() - 4).trim() : s;
	}

	@Override
	protected String value(boolean placeholder) {
		return placeholder || name == null ? "Rago 16x" : name;
	}
}
