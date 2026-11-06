package net.minecraftforge.client;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.Arrays;

/** Commands that run on the client only: a chat line starting with "/" and a registered name. */
public class ClientCommandHandler extends CommandHandler {
	public static final ClientCommandHandler instance = new ClientCommandHandler();

	@Override
	public int func_71556_a(ICommandSender sender, String message) {
		message = message.trim();
		if (!message.startsWith("/")) {
			return 0;
		}
		String[] words = message.substring(1).split(" ");
		ICommand command = (ICommand) func_71555_a().get(words[0]);
		if (command == null) {
			return 0;
		}
		String[] args = Arrays.copyOfRange(words, 1, words.length);
		try {
			if (command.func_71519_b(sender)) {
				command.func_71515_b(sender, args);
				return 1;
			}
			sender.func_145747_a(red(new ChatComponentTranslation("commands.generic.permission")));
		} catch (CommandException e) {
			sender.func_145747_a(red(new ChatComponentTranslation(e.getMessage(), e.func_74844_a())));
		} catch (Throwable t) {
			sender.func_145747_a(red(new ChatComponentTranslation("commands.generic.exception")));
			org.apache.logging.log4j.LogManager.getLogger("RagoLoader").error("Command /{} failed", words[0], t);
		}
		return -1;
	}

	private static IChatComponent red(ChatComponentTranslation c) {
		c.func_150256_b().func_150238_a(EnumChatFormatting.RED);
		return c;
	}
}
