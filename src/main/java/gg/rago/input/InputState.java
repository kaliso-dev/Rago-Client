package gg.rago.input;

import net.minecraft.client.Minecraft;

/** CPS for the attack and use buttons, keyed by whatever those bindings are set to. */
public final class InputState {
	private final ClickTracker attack = new ClickTracker();
	private final ClickTracker use = new ClickTracker();

	/** Called with the key code of every press vanilla processes while no screen is open. */
	public void onPress(int keyCode, long nowNs) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) {
			return;
		}
		if (keyCode == mc.options.attackKey.getKeyCode()) {
			attack.press(nowNs);
		} else if (keyCode == mc.options.useKey.getKeyCode()) {
			use.press(nowNs);
		}
	}

	public int attackCps(long nowNs) {
		return attack.cps(nowNs);
	}

	public int useCps(long nowNs) {
		return use.cps(nowNs);
	}
}
