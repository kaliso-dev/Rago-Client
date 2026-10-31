package gg.rago.render;

import gg.rago.core.RagoClient;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;

/**
 * The item drawn in your hand (ItemRenderer's own state), for Instant item switch: a new hotbar
 * slot's item goes in the hand at once, fully raised, where vanilla lowers the old item and raises
 * the new one over about a third of a second. Only changes how your hand is drawn: the server is
 * told of the new slot as vanilla always does, before any hit or use.
 */
public final class HeldItem {
	private static Field item;
	private static Field progress;
	private static Field prevProgress;
	private static Field slot;
	private static boolean missing;

	private HeldItem() {
	}

	/**
	 * Puts {@code stack}, from hotbar slot {@code current}, in the hand at once when the slot is not
	 * the one drawn; false when it is (vanilla's update then goes on as usual) or the fields are missing.
	 */
	public static boolean switchAtOnce(ItemRenderer renderer, int current, ItemStack stack) {
		if (!find()) {
			return false;
		}
		try {
			if (slot.getInt(renderer) == current) {
				return false;
			}
			item.set(renderer, stack);
			slot.setInt(renderer, current);
			progress.setFloat(renderer, 1f);
			prevProgress.setFloat(renderer, 1f);
			return true;
		} catch (IllegalAccessException | RuntimeException e) {
			RagoClient.LOG.warn("Instant item switch unavailable", e);
			missing = true;
			return false;
		}
	}

	private static boolean find() {
		if (slot != null) {
			return true;
		}
		if (missing) {
			return false;
		}
		Field i = field("field_78453_b", "itemToRender");
		Field p = field("field_78454_c", "equippedProgress");
		Field pp = field("field_78451_d", "prevEquippedProgress");
		Field s = field("field_78450_g", "equippedItemSlot");
		if (i == null || p == null || pp == null || s == null) {
			RagoClient.LOG.warn("Instant item switch unavailable: ItemRenderer's fields were not found");
			missing = true;
			return false;
		}
		item = i;
		progress = p;
		prevProgress = pp;
		slot = s;
		return true;
	}

	/** A field of ItemRenderer by its name in the game (srg) or in development (mcp). */
	private static Field field(String srg, String mcp) {
		for (String name : new String[] {srg, mcp}) {
			try {
				Field f = ItemRenderer.class.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException | RuntimeException e) {
				// the other name
			}
		}
		return null;
	}
}
