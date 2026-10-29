package gg.rago.core;

import gg.rago.hypixel.HypixelApi;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * {@code /rago}: opens the Rago menu; {@code /rago hypixelkey <key>} stores the Hypixel API key.
 * A client command: it is handled by the game and never sent to the server.
 */
final class RagoCommand extends CommandBase {
	private final HypixelApi api;
	private final Runnable openMenu;

	RagoCommand(HypixelApi api, Runnable openMenu) {
		this.api = api;
		this.openMenu = openMenu;
	}

	@Override
	public String getCommandName() {
		return "rago";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/rago [hypixelkey <key>]";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if (args.length == 0) {
			openMenu.run();
			return;
		}
		if (args[0].equalsIgnoreCase("hypixelkey")) {
			if (args.length != 2 || !HypixelApi.isKey(args[1].trim())) {
				sender.addChatMessage(message("rago.command.key_format", EnumChatFormatting.RED));
				return;
			}
			try {
				api.setKey(args[1].trim());
				sender.addChatMessage(message("rago.command.key_saved", EnumChatFormatting.GREEN));
			} catch (IOException e) {
				RagoClient.LOG.error("Could not save the Hypixel API key", e);
				sender.addChatMessage(message("rago.command.key_failed", EnumChatFormatting.RED));
			}
			return;
		}
		sender.addChatMessage(message("rago.command.usage", EnumChatFormatting.GRAY));
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		return args.length == 1 ? getListOfStringsMatchingLastWord(args, "hypixelkey") : Collections.<String>emptyList();
	}

	private static IChatComponent message(String key, EnumChatFormatting color) {
		IChatComponent c = new ChatComponentTranslation(key);
		c.setChatStyle(new ChatStyle().setColor(color));
		return c;
	}
}
