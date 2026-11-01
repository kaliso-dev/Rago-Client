package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import gg.rago.render.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Arrays;

/**
 * A column (or row) of item icons, each with how many of that item you carry. The inventory is
 * counted once per tick, and the rows to show are worked out then too, so a frame only draws.
 */
abstract class ItemCountHud extends HudElement {
	private static final int ROW = 17;
	private static final int ICON = 16;
	private static final int GAP = 6;

	protected final BooleanOption hideZero = addOption(new BooleanOption("hide_zero", true));
	protected final BooleanOption horizontal = addOption(new BooleanOption("horizontal", false));
	protected final BooleanOption showIcons = addOption(new BooleanOption("show_icons", true));

	private final Item[] items;
	private final ItemStack[] icons;
	private final int[] sample;
	private final int[] counts;
	/** Indexes of the rows shown this tick, and how many there are. */
	private final int[] shown;
	private int shownCount;
	private final int[] sampleShown;
	private int sampleCount;

	ItemCountHud(String id, Anchor anchor, int x, int y, Item[] items, int[] sample) {
		super(id, FeatureClass.INFORMATIONAL, false, anchor, x, y);
		this.items = items;
		this.sample = sample;
		icons = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++) {
			icons[i] = new ItemStack(items[i]);
		}
		counts = new int[items.length];
		shown = new int[items.length];
		sampleShown = new int[items.length];
	}

	/** Whether the row of item i is turned on. */
	protected boolean wanted(int i) {
		return true;
	}

	/** Whether a stack counts for item i; by default, the same item. */
	protected boolean counts(int i, ItemStack s) {
		return s.getItem() == items[i];
	}

	/** The item's short name, shown before its count when the icons are hidden. */
	protected abstract String name(int i);

	/** The colour of a count; by default the text colour. */
	protected int countColor(int i, int count) {
		return textColor.get();
	}

	@Override
	public void tick() {
		Arrays.fill(counts, 0);
		EntityPlayer p = Minecraft.getMinecraft().thePlayer;
		if (p != null) {
			for (ItemStack s : p.inventory.mainInventory) {
				if (s == null) {
					continue;
				}
				for (int i = 0; i < items.length; i++) {
					if (counts(i, s)) {
						counts[i] += s.stackSize;
						break;
					}
				}
			}
		}
		shownCount = 0;
		sampleCount = 0;
		for (int i = 0; i < items.length; i++) {
			if (!wanted(i)) {
				continue;
			}
			sampleShown[sampleCount++] = i;
			if (counts[i] > 0 || !hideZero.get()) {
				shown[shownCount++] = i;
			}
		}
	}

	@Override
	public boolean hasContent() {
		return shownCount > 0;
	}

	private int count(int i, boolean placeholder) {
		return placeholder ? sample[i] : counts[i];
	}

	private String text(int i, boolean placeholder) {
		String c = Integer.toString(count(i, placeholder));
		return showIcons.get() ? c : name(i) + ": " + c;
	}

	private int cell(int i, boolean placeholder) {
		return (showIcons.get() ? ICON + 2 : 0) + textWidth(text(i, placeholder));
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		int n = placeholder ? sampleCount : shownCount;
		int[] rows = placeholder ? sampleShown : shown;
		int w = 0;
		for (int k = 0; k < n; k++) {
			w = horizontal.get() ? w + cell(rows[k], placeholder) + GAP : Math.max(w, cell(rows[k], placeholder));
		}
		return Math.max(ICON, horizontal.get() && n > 0 ? w - GAP : w);
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		int n = Math.max(1, placeholder ? sampleCount : shownCount);
		int row = showIcons.get() ? ROW : 10;
		return horizontal.get() ? row - 1 : n * row - 1;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		int n = placeholder ? sampleCount : shownCount;
		int[] rows = placeholder ? sampleShown : shown;
		boolean icons = showIcons.get();
		int row = icons ? ROW : 10;
		if (icons) {
			Minecraft mc = Minecraft.getMinecraft();
			RenderHelper.enableGUIStandardItemLighting();
			GlStateManager.enableRescaleNormal();
			int x = 0;
			int y = 0;
			for (int k = 0; k < n; k++) {
				mc.getRenderItem().renderItemAndEffectIntoGUI(this.icons[rows[k]], x, y);
				if (horizontal.get()) {
					x += cell(rows[k], placeholder) + GAP;
				} else {
					y += row;
				}
			}
			GlStateManager.disableRescaleNormal();
			RenderHelper.disableStandardItemLighting();
		}
		int x = 0;
		int y = 0;
		// every count in one draw call
		HudText.Batch batch = labels.start(font.get());
		boolean shade = shadow.get();
		for (int k = 0; k < n; k++) {
			int i = rows[k];
			int c = count(i, placeholder);
			batch.add(text(i, placeholder), x + (icons ? ICON + 2 : 0), y + (icons ? 4 : 0), countColor(i, c), shade);
			if (horizontal.get()) {
				x += cell(i, placeholder) + GAP;
			} else {
				y += row;
			}
		}
		batch.end();
		GlStateManager.color(1, 1, 1, 1);
	}

	/** The counts' text, reused every frame. */
	private final HudText.Batch labels = new HudText.Batch();
}
