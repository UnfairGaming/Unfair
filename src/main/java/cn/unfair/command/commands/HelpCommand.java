package cn.unfair.command.commands;

import cn.unfair.Unfair;
import cn.unfair.command.Command;
import cn.unfair.util.client.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class HelpCommand extends Command {
    public HelpCommand() {
        super(new ArrayList<>(Arrays.asList("help", "commands")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!Unfair.moduleManager.modules.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sCommands:&r", Unfair.clientName));
            for (Command command : Unfair.commandManager.commands) {
                if (!(command instanceof ModuleCommand)) {
                    ChatUtil.sendFormatted(String.format("&7»&r .%s&r", String.join(" &7/&r .", command.names)));
                }
            }
        }
    }
}
