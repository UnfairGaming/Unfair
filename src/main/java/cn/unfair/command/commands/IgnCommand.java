package cn.unfair.command.commands;

import cn.unfair.Unfair;
import cn.unfair.command.Command;
import cn.unfair.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraft.util.StringUtils;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;

public class IgnCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public IgnCommand() {
        super(new ArrayList<>(Arrays.asList("username", "name", "ign")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        Session session = mc.getSession();
        if (session != null) {
            String username = session.getUsername();
            if (!StringUtils.isNullOrEmpty(username)) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(username), null);
                    ChatUtil.sendFormatted(String.format("%sYour username has been copied to the clipboard (&o%s&r)&r", Unfair.clientName, username));
                } catch (Exception e) {
                    ChatUtil.sendFormatted(String.format("%sFailed to copy&r", Unfair.clientName));
                }
            }
        }
    }
}
