package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.lwjgl.Sys;

import javax.imageio.ImageIO;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Buttons after the "Saved screenshot" line in chat: copy the picture to the clipboard (to paste it
 * straight into Discord), open it, open the folder, or delete it. The copy can also happen on its
 * own after every screenshot.
 */
public final class Screenshots extends Module {
	private final BooleanOption autoCopy = addOption(new BooleanOption("screenshot_auto_copy", false));

	public Screenshots() {
		super("screenshots", FeatureClass.VISUAL, true);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	/** ScreenShotHelper.saveScreenshot's chat line, with the buttons added when it saved a file. */
	public IChatComponent decorate(IChatComponent line) {
		File file = savedFile(line);
		if (file == null) {
			return line;
		}
		IChatComponent out = line.createCopy();
		// the buttons on a line of their own, so they never wrap in the middle
		out.appendText("\n");
		out.appendSibling(button("rago.shot.copy", "rago.shot.copy_tip", "copy", file, EnumChatFormatting.GREEN));
		out.appendText(" ");
		out.appendSibling(button("rago.shot.open", "rago.shot.open_tip", "open", file, EnumChatFormatting.AQUA));
		out.appendText(" ");
		out.appendSibling(button("rago.shot.folder", "rago.shot.folder_tip", "folder", file, EnumChatFormatting.YELLOW));
		out.appendText(" ");
		out.appendSibling(button("rago.shot.delete", "rago.shot.delete_tip", "delete", file, EnumChatFormatting.RED));
		if (autoCopy.get()) {
			copy(file);
		}
		return out;
	}

	/** The file named by the game's "Saved screenshot as ..." line, or null for another line. */
	private static File savedFile(IChatComponent line) {
		if (!(line instanceof ChatComponentTranslation) || !"screenshot.success".equals(((ChatComponentTranslation) line).getKey())) {
			return null;
		}
		Object[] args = ((ChatComponentTranslation) line).getFormatArgs();
		if (args.length == 0 || !(args[0] instanceof IChatComponent)) {
			return null;
		}
		ClickEvent click = ((IChatComponent) args[0]).getChatStyle().getChatClickEvent();
		return click == null || click.getAction() != ClickEvent.Action.OPEN_FILE ? null : new File(click.getValue());
	}

	private static IChatComponent button(String label, String tip, String action, File file, EnumChatFormatting color) {
		IChatComponent b = new ChatComponentText("[" + I18n.format(label) + "]");
		b.setChatStyle(new ChatStyle().setColor(color).setBold(true)
			.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rago screenshot " + action + " " + file.getName()))
			.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(I18n.format(tip)))));
		return b;
	}

	/** Runs a button of the chat line: {@code /rago screenshot <copy|open|folder|delete> <file name>}. */
	public static void command(String action, String name) {
		File dir = new File(Minecraft.getMinecraft().mcDataDir, "screenshots");
		// only a plain file name, inside the screenshots folder
		if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
			return;
		}
		File file = new File(dir, name);
		if (!file.isFile() && !"folder".equals(action)) {
			say("rago.shot.missing", EnumChatFormatting.RED);
			return;
		}
		switch (action) {
			case "copy":
				copy(file);
				break;
			case "open":
				Sys.openURL(file.toURI().toString());
				break;
			case "folder":
				Sys.openURL(dir.toURI().toString());
				break;
			case "delete":
				say(file.delete() ? "rago.shot.deleted" : "rago.shot.missing", file.exists() ? EnumChatFormatting.RED : EnumChatFormatting.GRAY);
				break;
			default:
				break;
		}
	}

	private static void copy(File file) {
		Thread t = new Thread(() -> {
			try {
				BufferedImage read = ImageIO.read(file);
				if (read == null) {
					throw new IOException("not an image");
				}
				setClipboard(read);
				Minecraft.getMinecraft().addScheduledTask(() -> say("rago.shot.copied", EnumChatFormatting.GREEN));
			} catch (IOException | RuntimeException | LinkageError e) {
				RagoClient.LOG.warn("Could not copy the screenshot {}", file, e);
				Minecraft.getMinecraft().addScheduledTask(() -> say("rago.shot.copy_failed", EnumChatFormatting.RED));
			}
		}, "Rago screenshot copy");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Copies a picture to the clipboard, away from the game's thread; {@code done} is then told on the
	 * game's thread whether it worked.
	 */
	public static void copyImage(BufferedImage image, Consumer<Boolean> done) {
		Thread t = new Thread(() -> {
			boolean ok;
			try {
				setClipboard(image);
				ok = true;
			} catch (RuntimeException | LinkageError e) {
				RagoClient.LOG.warn("Could not copy the picture", e);
				ok = false;
			}
			boolean copied = ok;
			Minecraft.getMinecraft().addScheduledTask(() -> done.accept(copied));
		}, "Rago picture copy");
		t.setDaemon(true);
		t.start();
	}

	/** The clipboard gets the picture without alpha: some programs paste a transparent picture as black. */
	private static void setClipboard(BufferedImage image) {
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		rgb.getGraphics().drawImage(image, 0, 0, null);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransfer(rgb), null);
	}

	private static void say(String key, EnumChatFormatting color) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null) {
			return;
		}
		IChatComponent c = new ChatComponentText(I18n.format(key));
		c.setChatStyle(new ChatStyle().setColor(color));
		mc.ingameGUI.getChatGUI().printChatMessage(c);
	}

	private static final class ImageTransfer implements Transferable {
		private final BufferedImage image;

		ImageTransfer(BufferedImage image) {
			this.image = image;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] {DataFlavor.imageFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return DataFlavor.imageFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
			if (!isDataFlavorSupported(flavor)) {
				throw new UnsupportedFlavorException(flavor);
			}
			return image;
		}
	}
}
