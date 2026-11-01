package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bed Wars shop on Hypixel: a sword (stone, iron or diamond) is not bought again right after you
 * bought it, so spam-clicking it buys it once. Buying another item in between lets you buy it
 * again (sword, blocks, sword is two swords), and so does a pause: the block holds while the
 * clicks on it keep coming, then stops after a moment without one, so you can buy your sword back
 * after a death. A blocked click plays a low note and says why above the shop. Shift-clicks
 * (Hypixel's quick buy editing) are left alone; nothing else is sent. Team upgrades (priced in
 * diamonds; Sharpened Swords shows an iron sword) are never stopped. Input class, off by default.
 */
public final class ShopAntiMisclick extends Module {
	private final IntOption delay = addOption(new IntOption("misclick_delay", 1000, 300, 5000));
	private final BooleanOption sound = addOption(new BooleanOption("misclick_sound", true));

	/** The item of the last shop click that went through, where, and the time of the last click on it. */
	private Item lastItem;
	private int lastWindow = -1;
	private int lastSlot = -1;
	private long lastAt;
	/** Why the last click was stopped, shown above the shop for a moment. */
	private String note;
	private long noteAt;

	public ShopAntiMisclick() {
		super("shop_anti_misclick", FeatureClass.INPUT, false);
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	/** Called for every click in a container window; true when the click is not to be sent. */
	public boolean blocks(GuiContainer gui, Slot slot, int button, int type) {
		// a click (0) or the double click that follows it (6); shift-clicks edit Hypixel's quick buy
		if (slot == null || button > 1 || type != 0 && type != 6) {
			return false;
		}
		long now = System.currentTimeMillis();
		boolean shop = FastShop.isShopSlot(gui, slot);
		if (shop && FastShop.isTeamUpgrade(slot.getStack())) {
			return false;
		}
		// until the server puts the item back, the place of the sword just bought looks empty: a
		// click there would still buy it again
		boolean samePlace = gui.inventorySlots.windowId == lastWindow && slot.slotNumber == lastSlot;
		if (!shop && !(samePlace && guarded(lastItem))) {
			return false;
		}
		Item item = shop ? slot.getStack().getItem() : lastItem;
		if (!blocked(guarded(item), item == lastItem, now - lastAt, delay.get())) {
			if (shop) {
				lastItem = item;
				lastWindow = gui.inventorySlots.windowId;
				lastSlot = slot.slotNumber;
				lastAt = now;
			}
			return false;
		}
		// still spamming: the block holds until the clicks stop for a moment
		lastAt = now;
		note = I18n.format("rago.misclick.again", shop ? slot.getStack().getDisplayName() : I18n.format(item.getUnlocalizedName() + ".name"));
		noteAt = now;
		if (sound.get()) {
			Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.bass"), 0.6f));
		}
		return true;
	}

	/**
	 * Whether a click on a shop item is stopped: a guarded item that is the same as the last one
	 * bought, clicked again {@code sinceLast} ms after the last click on it.
	 */
	static boolean blocked(boolean guarded, boolean sameAsLast, long sinceLast, int delayMs) {
		return guarded && sameAsLast && sinceLast < delayMs;
	}

	/** The items it watches, for now: the stone, iron and diamond swords. */
	static boolean guarded(Item item) {
		if (!(item instanceof ItemSword)) {
			return false;
		}
		String material = ((ItemSword) item).getToolMaterialName();
		// EMERALD is diamond
		return "STONE".equals(material) || "IRON".equals(material) || "EMERALD".equals(material);
	}

	@SubscribeEvent
	public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
		if (note == null || !(event.gui instanceof GuiContainer)) {
			return;
		}
		if (System.currentTimeMillis() - noteAt > 1800) {
			note = null;
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		mc.fontRendererObj.drawStringWithShadow(note, (event.gui.width - mc.fontRendererObj.getStringWidth(note)) / 2f, 6, 0xFFFF5555);
	}
}
