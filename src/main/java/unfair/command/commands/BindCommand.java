package unfair.command.commands;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import unfair.Unfair;
import unfair.command.Command;
import unfair.module.Module;
import unfair.util.ChatUtil;
import unfair.util.KeyBindUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BindCommand extends Command {
    public BindCommand() {
        super(new ArrayList<>(Arrays.asList("bind", "b")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 3) {
            if (args.size() == 2 && (args.get(1).equalsIgnoreCase("l") || args.get(1).equalsIgnoreCase("list"))) {
                List<Module> modules = Unfair.moduleManager.modules.values().stream().filter(module -> module.getKey() != 0).collect(Collectors.toList());
                if (modules.isEmpty()) {
                    ChatUtil.sendFormatted(String.format("%sNo binds&r", Unfair.clientName));
                } else {
                    ChatUtil.sendFormatted(String.format("%sBinds:&r", Unfair.clientName));
                    for (Module module : modules) {
                        ChatUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
                    }
                }
            } else {
                ChatUtil.sendFormatted(
                        String.format(
                                "%sUsage: .%s <&omodule&r> <&okey&r>&r | .%s <&omodule&r> &onone&r | .%s &olist&r",
                                Unfair.clientName,
                                args.get(0).toLowerCase(Locale.ROOT),
                                args.get(0).toLowerCase(Locale.ROOT),
                                args.get(0).toLowerCase(Locale.ROOT)
                        )
                );
            }
        } else {
            String keyInput = args.get(2).toUpperCase();
            int keyIndex = 0;

            if (keyInput.equalsIgnoreCase("NONE") || keyInput.equalsIgnoreCase("NULL") || keyInput.equalsIgnoreCase("0")) {
                keyIndex = 0;
            } else {
                keyIndex = Keyboard.getKeyIndex(keyInput);

                if (keyIndex == 0) {
                    int buttonIndex = Mouse.getButtonIndex(keyInput);
                    if (buttonIndex != -1) {
                        keyIndex = buttonIndex - 100;
                    }
                }
            }

            if (!args.get(1).equals("*")) {
                Module module = Unfair.moduleManager.getModule(args.get(1));
                if (module == null) {
                    ChatUtil.sendFormatted(String.format("%sModule not found (&o%s&r)&r", Unfair.clientName, args.get(1)));
                } else {
                    module.setKey(keyIndex);
                    if (keyIndex == 0) {
                        ChatUtil.sendFormatted(
                                String.format("%sUnbind &o%s&r", Unfair.clientName, module.getName())
                        );
                    } else {
                        ChatUtil.sendFormatted(
                                String.format("%sBound &o%s&r to &l[%s]&r", Unfair.clientName, module.getName(), KeyBindUtil.getKeyName(keyIndex))
                        );
                    }
                }
            } else {
                for (Module module : Unfair.moduleManager.modules.values()) {
                    module.setKey(keyIndex);
                }
                if (keyIndex == 0) {
                    ChatUtil.sendFormatted(
                            String.format("%sUnbind all modules&r", Unfair.clientName)
                    );
                } else {
                    ChatUtil.sendFormatted(
                            String.format("%sBind all modules to &l[%s]&r", Unfair.clientName, KeyBindUtil.getKeyName(keyIndex))
                    );
                }
            }
        }
    }
}
