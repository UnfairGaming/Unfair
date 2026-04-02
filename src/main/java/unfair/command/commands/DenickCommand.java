package unfair.command.commands;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import unfair.Unfair;
import unfair.command.Command;
import unfair.enums.ChatColors;
import unfair.util.ChatUtil;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;

public class DenickCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public DenickCommand() {
        super(new ArrayList<>(Collections.singletonList("denick")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            ChatUtil.sendFormatted(String.format("%sUsage: .%s <&oname&r>&r", Unfair.clientName, args.get(0).toLowerCase(Locale.ROOT)));
        } else {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(ChatColors.formatColor(args.get(1)));
            if (playerInfo != null) {
                GameProfile gameProfile = playerInfo.getGameProfile();
                Property property = Iterables.getFirst(gameProfile.getProperties().get("textures"), null);
                if (property != null) {
                    String code = new String(Base64.getDecoder().decode(property.getValue().getBytes(StandardCharsets.UTF_8)));
                    String name = code.contains("profileName\" : \"") ? code.split("profileName\" : \"")[1].split("\"")[0] : "?";
                    String uuid = code.contains("profileId\" : \"") ? code.split("profileId\" : \"")[1].split("\"")[0] : "?";
                    ChatUtil.sendRaw(
                            String.format(
                                    ChatColors.formatColor("%s%s&r -> %s (&o%s&r)&r"),
                                    ChatColors.formatColor(Unfair.clientName),
                                    gameProfile.getName().replace("§", "&"),
                                    name,
                                    uuid
                            )
                    );
                    if (!uuid.isEmpty() && !uuid.equals("?")) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(uuid), null);
                    }
                } else {
                    ChatUtil.sendRaw(
                            String.format(
                                    ChatColors.formatColor("%sNo textures for entity with name &o%s&r"),
                                    ChatColors.formatColor(Unfair.clientName),
                                    args.get(1)
                            )
                    );
                }
            } else {
                ChatUtil.sendRaw(
                        String.format(
                                ChatColors.formatColor("%sNo entity with name &o%s&r"),
                                ChatColors.formatColor(Unfair.clientName),
                                args.get(1)
                        )
                );
            }
        }
    }
}
