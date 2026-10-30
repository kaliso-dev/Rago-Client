package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.hypixel.Hypixel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faster Bed Wars shopping on Hypixel, in a shop window (a chest window whose items show a
 * "Cost:" line):
 * <ul>
 *   <li>Fast clicks: a click on a shop item buys it without the item landing on your cursor. The
 *   client sends the same click it always sends, but skips its own guess of the result (picking the
 *   item up until the server puts it back), so the next click can follow at once, which matters most
 *   on high ping. Still one click sent per click, as Meowtils' shop helper does.</li>
 *   <li>Affordable items get a background in the colour of the resource they cost.</li>
 *   <li>No duplicates: a click that would buy a sword or knockback stick you already carry is not
 *   sent.</li>
 * </ul>
 * Input class, off by default.
 */
public final class FastShop extends Module {
	/**
	 * A price in the lore, in the languages Hypixel offers most ("Cost: 24 Iron", "Coût : 24 Fer",
	 * "Kosten: 24 Eisen", "Costo: 24 Hierro"...): a word, a colon, an amount and a resource.
	 */
	private static final Pattern COST = Pattern.compile(
		"^\\s*[\\p{L} ]{3,20}?\\s*:\\s*(\\d+)\\s+(\\p{L}+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	/** Each resource's name in those languages, singular and plural, without accents. */
	private static final String[][] RESOURCES = {
		{"iron", "iron", "fer", "eisen", "hierro", "ferro", "ijzer", "zelazo", "zelezo"},
		{"gold", "gold", "golds", "or", "oro", "ouro", "goud", "zloto", "zlato"},
		{"diamond", "diamond", "diamonds", "diamant", "diamants", "diamanten", "diamante", "diamantes", "diamanti", "diament", "diamenty"},
		{"emerald", "emerald", "emeralds", "emeraude", "emeraudes", "smaragd", "smaragde", "esmeralda", "esmeraldas", "smeraldo", "smeraldi", "szmaragd", "szmaragdy"},
	};

	private final BooleanOption fastClicks = addOption(new BooleanOption("shop_fast_clicks", true));
	private final BooleanOption highlight = addOption(new BooleanOption("shop_highlight", true));
	private final IntOption opacity = addOption(new IntOption("shop_highlight_opacity", 35, 5, 100));
	private final BooleanOption noDuplicates = addOption(new BooleanOption("shop_no_duplicates", true));

	public FastShop() {
		super("fast_shop", FeatureClass.INPUT, false);
		opacity.showIf(highlight::get);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	/** Called for every click in a container window; true when this sent (or dropped) the click. */
	public boolean onClick(GuiContainer gui, Slot slot, int slotId, int button, int type) {
		if (!(fastClicks.get() || noDuplicates.get()) || type > 1 || button > 1 || !isShopSlot(gui, slot)) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		ItemStack stack = slot.getStack();
		if (noDuplicates.get() && alreadyOwned(mc.thePlayer, stack)) {
			return true;
		}
		if (!fastClicks.get()) {
			return false;
		}
		// PlayerControllerMP.windowClick without Container.slotClick: the packet is the one a normal
		// click sends (the slot's stack as the clicked item), the cursor stays empty
		Container c = gui.inventorySlots;
		short action = c.getNextTransactionID(mc.thePlayer.inventory);
		mc.getNetHandler().addToSendQueue(new C0EPacketClickWindow(c.windowId, slot.slotNumber, button, type,
			stack.copy(), action));
		return true;
	}

	/** Before a slot is drawn (coordinates relative to the window): the affordable background. */
	public void beforeSlot(GuiContainer gui, Slot slot) {
		if (!highlight.get() || !isShopSlot(gui, slot)) {
			return;
		}
		Cost cost = cost(slot.getStack());
		if (cost == null || count(Minecraft.getMinecraft().thePlayer.inventory, cost.item) < cost.amount) {
			return;
		}
		int alpha = Math.round(opacity.get() * 2.55f) << 24;
		int x = slot.xDisplayPosition;
		int y = slot.yDisplayPosition;
		Gui.drawRect(x, y, x + 16, y + 16, alpha | cost.color);
	}

	private boolean isShopSlot(GuiContainer gui, Slot slot) {
		if (!(gui instanceof GuiChest) || slot == null || !slot.getHasStack()
			|| slot.inventory instanceof InventoryPlayer || !Hypixel.connected()) {
			return false;
		}
		return cost(slot.getStack()) != null;
	}

	private static boolean alreadyOwned(EntityPlayerSP player, ItemStack wanted) {
		Item item = wanted.getItem();
		boolean kbStick = item == Items.stick;
		if (!(item instanceof ItemSword) && !kbStick) {
			return false;
		}
		for (ItemStack s : player.inventory.mainInventory) {
			if (s != null && s.getItem() == item && (!kbStick || s.isItemEnchanted())) {
				return true;
			}
		}
		return false;
	}

	private static int count(InventoryPlayer inv, Item item) {
		int n = 0;
		for (ItemStack s : inv.mainInventory) {
			if (s != null && s.getItem() == item) {
				n += s.stackSize;
			}
		}
		return n;
	}

	private static final class Cost {
		final int amount;
		final Item item;
		final int color;

		Cost(int amount, Item item, int color) {
			this.amount = amount;
			this.item = item;
			this.color = color;
		}
	}

	/** The price in an item's lore ("Cost: 24 Iron"), or null. */
	static Cost cost(ItemStack stack) {
		NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
		if (tag == null || !tag.hasKey("display", 10)) {
			return null;
		}
		NBTTagList lore = tag.getCompoundTag("display").getTagList("Lore", 8);
		for (int i = 0; i < lore.tagCount(); i++) {
			String[] price = price(EnumChatFormatting.getTextWithoutFormattingCodes(lore.getStringTagAt(i)));
			if (price != null) {
				return cost(Integer.parseInt(price[0]), price[1]);
			}
		}
		return null;
	}

	/** {amount, resource} from a lore line without colour codes, such as "Cost: 24 Iron", or null. */
	static String[] price(String line) {
		Matcher m = line == null ? null : COST.matcher(line);
		if (m == null || !m.find()) {
			return null;
		}
		String word = Normalizer.normalize(m.group(2), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
		for (String[] r : RESOURCES) {
			for (int i = 1; i < r.length; i++) {
				if (r[i].equals(word)) {
					return new String[] {m.group(1), r[0]};
				}
			}
		}
		return null;
	}

	static Cost cost(int amount, String resource) {
		switch (resource.toLowerCase(Locale.ROOT)) {
			case "iron":
				return new Cost(amount, Items.iron_ingot, 0xFFFFFF);
			case "gold":
				return new Cost(amount, Items.gold_ingot, 0xFFAA00);
			case "diamond":
				return new Cost(amount, Items.diamond, 0x55FFFF);
			default:
				return new Cost(amount, Items.emerald, 0x55FF55);
		}
	}
}
