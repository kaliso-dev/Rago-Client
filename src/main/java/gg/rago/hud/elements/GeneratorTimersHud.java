package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import gg.rago.render.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Bed Wars diamond and emerald generator timers, read from the floating text above the generators
 * ("Diamond", "Tier II", "Spawns in 23 seconds"). The text is read every few ticks while a
 * generator is in range; out of range, the last time read keeps counting down and starts over with
 * the length of the cycle seen for that tier.
 */
public final class GeneratorTimersHud extends HudElement {
	private static final int ROW = 17;
	private static final int ICON = 16;
	private static final String[] TIERS = {"", "I", "II", "III", "IV"};

	private final BooleanOption showDiamonds = addOption(new BooleanOption("gen_diamonds", true));
	private final BooleanOption showEmeralds = addOption(new BooleanOption("gen_emeralds", true));
	private final BooleanOption showTier = addOption(new BooleanOption("gen_tier", true));
	private final BooleanOption showIcons = addOption(new BooleanOption("show_icons", true));
	private final ChoiceOption format = addOption(new ChoiceOption("gen_format", "seconds", "seconds", "clock"));
	private final IntOption soonAt = addOption(new IntOption("gen_soon", 5, 0, 30));
	private final ColorOption soonColor = addOption(new ColorOption("gen_soon_color", 0xFF55FF55));

	private final Gen diamond = new Gen("Diamond", new ItemStack(Items.diamond), new int[] {30, 30, 23, 12, 12});
	private final Gen emerald = new Gen("Emerald", new ItemStack(Items.emerald), new int[] {65, 65, 50, 35, 35});
	private final List<EntityArmorStand> stands = new ArrayList<>();
	/** The rows shown, worked out each tick rather than each frame. */
	private final List<Gen> live = new ArrayList<>(2);
	private final List<Gen> sample = new ArrayList<>(2);
	private World world;
	private int ticks;

	public GeneratorTimersHud() {
		super("gen_timers", FeatureClass.INFORMATIONAL, false, Anchor.MIDDLE_LEFT, 4, -50);
		soonColor.showIf(() -> soonAt.get() > 0);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	private static final class Gen {
		final String name;
		final ItemStack icon;
		/** The usual cycle length by tier, until one has been seen. */
		final int[] cycles;
		int tier;
		int seconds = -1;
		int seenAt;
		int cycle;

		Gen(String name, ItemStack icon, int[] cycles) {
			this.name = name;
			this.icon = icon;
			this.cycles = cycles;
		}

		/** Seconds left now, counted down from the last reading. */
		int left(int now) {
			if (seconds < 0) {
				return -1;
			}
			int s = seconds - (now - seenAt) / 20;
			int c = cycle > 0 ? cycle : cycles[Math.min(tier, cycles.length - 1)];
			while (s < 0 && c > 0) {
				s += c;
			}
			return s;
		}
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		ticks++;
		if (mc.theWorld != world) {
			world = mc.theWorld;
			diamond.seconds = -1;
			emerald.seconds = -1;
		}
		fill(live, false);
		if (world == null || ticks % 5 != 0) {
			return;
		}
		stands.clear();
		for (Entity e : world.loadedEntityList) {
			if (e instanceof EntityArmorStand && e.hasCustomName()) {
				stands.add((EntityArmorStand) e);
			}
		}
		int bestDiamond = Integer.MAX_VALUE;
		int bestEmerald = Integer.MAX_VALUE;
		for (EntityArmorStand timer : stands) {
			String raw = timer.getCustomNameTag();
			if (raw.indexOf("Spawns") < 0) {
				continue; // most named stands are not timers: no need to strip their colours (4 times a second)
			}
			String text = EnumChatFormatting.getTextWithoutFormattingCodes(raw);
			int at = text.indexOf("Spawns in ");
			if (at < 0) {
				continue;
			}
			int seconds = number(text, at + 10);
			if (seconds < 0) {
				continue;
			}
			// the generator's name and tier float just above its timer
			Gen gen = null;
			int tier = 0;
			for (EntityArmorStand above : stands) {
				if (above == timer || Math.abs(above.posX - timer.posX) > 0.5 || Math.abs(above.posZ - timer.posZ) > 0.5
					|| above.posY < timer.posY || above.posY - timer.posY > 1.5) {
					continue;
				}
				String name = EnumChatFormatting.getTextWithoutFormattingCodes(above.getCustomNameTag()).trim();
				if (name.startsWith("Diamond")) {
					gen = diamond;
				} else if (name.startsWith("Emerald")) {
					gen = emerald;
				} else if (name.startsWith("Tier ")) {
					tier = roman(name.substring(5).trim());
				}
			}
			if (gen == diamond && seconds < bestDiamond) {
				bestDiamond = seconds;
				read(gen, seconds, tier);
			} else if (gen == emerald && seconds < bestEmerald) {
				bestEmerald = seconds;
				read(gen, seconds, tier);
			}
		}
		stands.clear();
		fill(live, false);
	}

	private void read(Gen gen, int seconds, int tier) {
		if (tier != gen.tier) {
			gen.tier = tier;
			gen.cycle = 0;
		}
		// the longest time seen since the tier changed is the cycle
		gen.cycle = Math.max(gen.cycle, seconds);
		gen.seconds = seconds;
		gen.seenAt = ticks;
	}

	private static int number(String s, int from) {
		int n = 0;
		int i = from;
		while (i < s.length() && Character.isDigit(s.charAt(i))) {
			n = n * 10 + s.charAt(i) - '0';
			i++;
		}
		return i == from ? -1 : n;
	}

	private static int roman(String s) {
		for (int i = TIERS.length - 1; i > 0; i--) {
			if (s.startsWith(TIERS[i])) {
				return i;
			}
		}
		return 0;
	}

	private List<Gen> rows(boolean placeholder) {
		if (placeholder) {
			fill(sample, true);
			return sample;
		}
		return live;
	}

	private void fill(List<Gen> list, boolean placeholder) {
		list.clear();
		if (showDiamonds.get() && (placeholder || diamond.seconds >= 0)) {
			list.add(diamond);
		}
		if (showEmeralds.get() && (placeholder || emerald.seconds >= 0)) {
			list.add(emerald);
		}
	}

	@Override
	public boolean hasContent() {
		return !rows(false).isEmpty();
	}

	private String text(Gen g, boolean placeholder) {
		int s = placeholder ? (g == diamond ? 23 : 41) : g.left(ticks);
		int tier = placeholder ? 2 : g.tier;
		String time = format.is("clock") ? s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60 : s + "s";
		StringBuilder b = new StringBuilder();
		if (!showIcons.get()) {
			b.append(g.name).append(' ');
		}
		if (showTier.get() && tier > 0) {
			b.append(TIERS[tier]).append(' ');
		}
		return b.append(time).toString();
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		int w = 0;
		for (Gen g : rows(placeholder)) {
			w = Math.max(w, (showIcons.get() ? ICON + 2 : 0) + textWidth(text(g, placeholder)));
		}
		return Math.max(ICON, w);
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		return Math.max(1, rows(placeholder).size()) * (showIcons.get() ? ROW : 10) - 1;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		List<Gen> rows = rows(placeholder);
		boolean icons = showIcons.get();
		int row = icons ? ROW : 10;
		if (icons) {
			RenderHelper.enableGUIStandardItemLighting();
			GlStateManager.enableRescaleNormal();
			for (int k = 0; k < rows.size(); k++) {
				Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(rows.get(k).icon, 0, k * row);
			}
			GlStateManager.disableRescaleNormal();
			RenderHelper.disableStandardItemLighting();
		}
		// every timer in one draw call
		HudText.Batch batch = labels.start(font.get());
		boolean shade = shadow.get();
		for (int k = 0; k < rows.size(); k++) {
			Gen g = rows.get(k);
			int left = placeholder ? 23 : g.left(ticks);
			int color = soonAt.get() > 0 && left <= soonAt.get() ? soonColor.get() : textColor.get();
			batch.add(text(g, placeholder), icons ? ICON + 2 : 0, k * row + (icons ? 4 : 0), color, shade);
		}
		batch.end();
		GlStateManager.color(1, 1, 1, 1);
	}

	/** The timers' text, reused every frame. */
	private final HudText.Batch labels = new HudText.Batch();
}
