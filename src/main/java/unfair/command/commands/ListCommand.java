package unfair.command.commands;

import unfair.Unfair;
import unfair.command.Command;
import unfair.module.Module;
import unfair.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super(new ArrayList<>(Arrays.asList("list", "l", "modules", "unfair")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!Unfair.moduleManager.modules.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sModules:&r", Unfair.clientName));
            for (Module module : Unfair.moduleManager.modules.values()) {
                ChatUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
            }
        }
    }
}
