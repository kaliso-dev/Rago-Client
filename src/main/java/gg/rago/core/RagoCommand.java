package gg.rago.core;

import gg.rago.modules.Screenshots;
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
				api.setKey(args[1].trim(), RagoClient.ownId());
				sender.addChatMessage(message("rago.command.key_saved", EnumChatFormatting.GREEN));
			} catch (IOException e) {
				RagoClient.LOG.error("Could not save the Hypixel API key", e);
				sender.addChatMessage(message("rago.command.key_failed", EnumChatFormatting.RED));
			}
			return;
		}
		if (args[0].equalsIgnoreCase("debug")) {
			boolean on = gg.rago.debug.InputTrace.toggle();
			sender.addChatMessage(message(on ? "rago.command.debug_on" : "rago.command.debug_off", EnumChatFormatting.GOLD));
			return;
		}
		if (args[0].equalsIgnoreCase("network")) {
			gg.rago.modules.RagoNetwork n = gg.rago.hooks.Hooks.network;
			IChatComponent c = new ChatComponentTranslation("rago.command.network", n != null ? n.status() : "off");
			c.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
			sender.addChatMessage(c);
			return;
		}
		if (args[0].equalsIgnoreCase("testplayers") && gg.rago.debug.FakeBedwars.ON) {
			gg.rago.debug.FakeBedwars.players(args.length > 1 ? Integer.parseInt(args[1]) : 16, args.length > 2 && args[2].equals("armor"));
			return;
		}
		if (args[0].equalsIgnoreCase("testgame") && gg.rago.debug.FakeBedwars.ON) {
			gg.rago.debug.FakeBedwars.game(args.length > 1 ? Integer.parseInt(args[1]) : 12);
			return;
		}
		if (args[0].equalsIgnoreCase("testmod") && args.length >= 3 && gg.rago.debug.FakeBedwars.ON) {
			// "testmod <module> on|off" as its switch in the menu; "testmod <module> <option> <value>" sets an option
			RagoClient.modules().get(args[1]).ifPresent(m -> {
				if (args.length == 3) {
					m.setEnabled(args[2].equalsIgnoreCase("on"));
					return;
				}
				m.options().stream().filter(o -> o.key().equals(args[2])).findFirst()
					.ifPresent(o -> o.fromJson(new com.google.gson.JsonParser().parse(args[3])));
			});
			return;
		}
		if (args[0].equalsIgnoreCase("testset") && args.length == 3 && gg.rago.debug.FakeBedwars.ON) {
			// "testset <setting> <value>": a setting of the Settings tabs, as in rago.json
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.add(args[1], new com.google.gson.JsonParser().parse(args[2]));
			RagoClient.settings().fromJson(o);
			return;
		}
		if (args[0].equalsIgnoreCase("testbw") && gg.rago.debug.FakeBedwars.ON) {
			gg.rago.debug.FakeBedwars.start(args.length > 1 ? Integer.parseInt(args[1]) : 337);
			return;
		}
		if (args[0].equalsIgnoreCase("screenshot") && args.length >= 3) {
			// the buttons after "Saved screenshot as ..."; the file name may hold spaces
			Screenshots.command(args[1], String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
			return;
		}
		sender.addChatMessage(message("rago.command.usage", EnumChatFormatting.GRAY));
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		return args.length == 1 ? getListOfStringsMatchingLastWord(args, "hypixelkey", "debug", "network") : Collections.<String>emptyList();
	}

	private static IChatComponent message(String key, EnumChatFormatting color) {
		IChatComponent c = new ChatComponentTranslation(key);
		c.setChatStyle(new ChatStyle().setColor(color));
		return c;
	}
}
