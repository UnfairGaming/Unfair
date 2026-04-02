package unfair.command.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import unfair.Unfair;
import unfair.command.Command;
import unfair.enums.ChatColors;
import unfair.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class PlayerCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public PlayerCommand() {
        super(new ArrayList<>(Arrays.asList("playerlist", "players")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        ArrayList<String> players = new ArrayList<>();
        for (NetworkPlayerInfo playerInfo : mc.getNetHandler().getPlayerInfoMap()) {
            players.add(playerInfo.getGameProfile().getName().replace("§", "&"));
        }
        if (players.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sNo players&r", Unfair.clientName));
        } else {
            ChatUtil.sendRaw(
                    String.format(
                            ChatColors.formatColor("%sPlayers:&r %s"),
                            ChatColors.formatColor(Unfair.clientName),
                            String.join(", ", players)
                    )
            );
        }
    }
}
