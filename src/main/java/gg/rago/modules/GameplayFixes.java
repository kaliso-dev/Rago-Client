package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Fixes for 1.8 annoyances, as Patcher, Lunar and Badlion ship them:
 * <ul>
 *   <li>Mouse delay fix: the crosshair targets what the camera faces this frame, not where the
 *   head was turned a tick earlier.</li>
 *   <li>Keybind fix: movement keys held while a menu closes keep working, as in 1.12+, instead of
 *   having to be pressed again.</li>
 *   <li>Borderless fullscreen: F11 fills the screen with a window without borders, so alt-tab is
 *   instant and the mouse can leave the game on a second monitor.</li>
 * </ul>
 * Off by default because the first two change how input turns into actions.
 */
public final class GameplayFixes extends Module {
	private static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";

	private final BooleanOption mouseDelay = addOption(new BooleanOption("mouse_delay_fix", true));
	private final BooleanOption keybindFix = addOption(new BooleanOption("keybind_fix", true));
	private final BooleanOption borderless = addOption(new BooleanOption("borderless_fullscreen", true));

	private boolean screenWasOpen;
	private int windowW;
	private int windowH;
	private int windowX = -1;
	private int windowY = -1;

	public GameplayFixes() {
		super("gameplay_fixes", FeatureClass.INPUT, false);
	}

	@Override
	public Category category() {
		return Category.FIXES;
	}

	public boolean mouseDelayFix() {
		return mouseDelay.get();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		boolean open = mc.currentScreen != null;
		if (screenWasOpen && !open && keybindFix.get() && mc.gameSettings != null) {
			GameSettings o = mc.gameSettings;
			// only movement: re-pressing attack or use here could start an action the player did not ask for
			for (KeyBinding k : new KeyBinding[] {o.keyBindForward, o.keyBindBack, o.keyBindLeft, o.keyBindRight,
				o.keyBindJump, o.keyBindSneak, o.keyBindSprint}) {
				int code = k.getKeyCode();
				if (code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code)) {
					KeyBinding.setKeyBindState(code, true);
				}
			}
		}
		screenWasOpen = open;
	}

	/** Switches between a borderless window covering the screen and a normal window; false if not handled. */
	public boolean toggleBorderless() {
		if (!borderless.get()) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		boolean full = !mc.isFullScreen();
		try {
			Field fullscreen = ReflectionHelper.findField(Minecraft.class, "field_71431_Q", "fullscreen");
			Method resize = ReflectionHelper.findMethod(Minecraft.class, mc, new String[] {"func_71370_a", "resize"}, int.class, int.class);
			if (full) {
				windowW = Display.getWidth();
				windowH = Display.getHeight();
				windowX = Display.getX();
				windowY = Display.getY();
				System.setProperty(UNDECORATED, "true");
				Display.setFullscreen(false);
				DisplayMode desktop = Display.getDesktopDisplayMode();
				Display.setDisplayMode(new DisplayMode(desktop.getWidth(), desktop.getHeight()));
				Display.setLocation(0, 0);
				Display.setResizable(false);
			} else {
				System.setProperty(UNDECORATED, "false");
				Display.setFullscreen(false);
				Display.setDisplayMode(new DisplayMode(windowW > 0 ? windowW : 854, windowH > 0 ? windowH : 480));
				Display.setResizable(true);
				Display.setLocation(windowX, windowY);
			}
			fullscreen.setBoolean(mc, full);
			mc.gameSettings.fullScreen = full;
			resize.invoke(mc, Math.max(1, Display.getWidth()), Math.max(1, Display.getHeight()));
			Display.setVSyncEnabled(mc.gameSettings.enableVsync);
			mc.updateDisplay();
			return true;
		} catch (Exception | LinkageError e) {
			RagoClient.LOG.warn("Borderless fullscreen failed; using vanilla fullscreen", e);
			System.setProperty(UNDECORATED, "false");
			return false;
		}
	}
}
