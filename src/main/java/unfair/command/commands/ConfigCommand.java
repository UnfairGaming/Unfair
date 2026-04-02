package unfair.command.commands;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.ClickEvent.Action;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import unfair.Unfair;
import unfair.command.Command;
import unfair.config.Config;
import unfair.enums.ChatColors;
import unfair.util.ChatUtil;

import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class ConfigCommand extends Command {
    private static final FileFilter FILE_FILTER = new WildcardFileFilter("*.json", IOCase.INSENSITIVE);

    public ConfigCommand() {
        super(new ArrayList<>(Arrays.asList("config", "cfg", "c")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            String command = args.get(0).toLowerCase(Locale.ROOT);
            ChatUtil.sendFormatted(
                    String.format("%sUsage: .%s &oload&r/&osave&r <&oname&r> | .%s &olist&r | .%s &ofolder&r", Unfair.clientName, command, command, command)
            );
        } else {
            String subCommand = args.get(1);
            if (subCommand.equalsIgnoreCase("l")) {
                subCommand = args.size() < 3 ? "list" : "load";
            }
            String sub = subCommand.toLowerCase(Locale.ROOT);
            switch (sub) {
                case "load":
                case "reload":
                    if (args.size() < 3) {
                        ChatUtil.sendFormatted(
                                String.format("%sMissing config name (use '&odefault&r' or '&o!&r' to load default config)&r", Unfair.clientName)
                        );
                        return;
                    }
                    new Config(args.get(2), false).load();
                    return;
                case "s":
                case "save":
                    if (args.size() < 3) {
                        new Config(Config.lastConfig, true).save();
                        return;
                    }
                    new Config(args.get(2), true).save();
                    return;
                case "list":
                    try {
                        File[] configs = new File("./config/Unfair/").listFiles(FILE_FILTER);
                        if (configs == null) {
                            throw new Exception();
                        }
                        if (configs.length == 0) {
                            ChatUtil.sendFormatted(String.format("%sNo configs found (&o%s&r)&r", Unfair.clientName, "./config/Unfair/"));
                        }
                        Arrays.sort(configs, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
                        ChatUtil.sendFormatted(String.format("%sConfigs:&r", Unfair.clientName));
                        for (File file : configs) {
                            String formatted = ChatColors.formatColor(String.format("&7»&r &o%s&r", file.getName()));
                            String config = String.format(".config load %s", FilenameUtils.removeExtension(file.getName()));
                            ChatUtil.send(
                                    new ChatComponentText(formatted)
                                            .setChatStyle(
                                                    new ChatStyle()
                                                            .setChatClickEvent(new ClickEvent(Action.RUN_COMMAND, config))
                                                            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(config)))
                                            )
                            );
                        }
                    } catch (Exception e) {
                        ChatUtil.sendFormatted(String.format("%sFailed to read (&o%s&r)&r", Unfair.clientName, "./config/Unfair/"));
                    }
                    return;
                case "f":
                case "folder":
                case "dir":
                case "directory":
                    try {
                        Desktop.getDesktop().open(new File("./config/Unfair/"));
                    } catch (Exception e) {
                        ChatUtil.sendFormatted(String.format("%sFailed to open (&o%s&r)&r", Unfair.clientName, "./config/Unfair/"));
                    }
                    return;
                default:
                    ChatUtil.sendFormatted(String.format("%sInvalid argument (&o%s&r)&r", Unfair.clientName, args.get(1)));
            }
        }
    }
}
